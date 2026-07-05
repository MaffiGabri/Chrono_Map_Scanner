# Master Audit Clean: Analisi Architetturale Globale "Nei Map"

## Executive Summary
L'applicazione "Nei Map" maschera profonde fragilità strutturali dietro l'adozione di un moderno tech stack (Compose, Coroutines, Room). La severa ispezione su tutto l'albero del codice ha evidenziato che l'attuale architettura **non scalerebbe in alcun modo al requisito critico di 1000+ elementi con storico multi-foto**. 
Esiste un diffuso problema di "Over-computation & Over-rendering": le pipeline del database restituiscono prodotti cartesiani enormi (`@Relation`), i ViewModel manipolano i flussi clonando oggetti in RAM in mappe temporali gigantesche (`cachedTimelineFlow`) e il motore grafico prova a istanziare contemporaneamente migliaia di nodi interattivi Compose per i marker, strozzando la GPU durante lo zoom.
**Verdetto:** Il refactoring deve sradicare l'accoppiamento tra strati e invertire la logica di calcolo: elaborazioni asincrone su richiesta e pre-filtrate a monte (O(1)), e rendering passivo basato sul Level of Detail (LOD).

---

## Top 5 Red Flags Globali

1. **Il "Memory Nuke" della Mappa Storica (`cachedTimelineFlow` e `@Relation`):**
   L'origine di un collasso OOM garantito. Da un lato, in `MoleDao.kt`, l'annotazione `@Relation` tra `Mole` e `HistoryEntry` provoca l'estrazione inefficace (prodotti cartesiani) da SQLite. Dall'altro, il `BodyMapViewModel` raggruppa brutalmente *tutte le iterazioni possibili* di date e nei in memoria. Per 1000 nei con 50 revisioni fotografiche, l'app tenta di gestire decine di migliaia di `MoleUiModel` simultaneamente, anche per date nascoste.
2. **Collasso del Framerate Compose (`BodyMapScreen.kt` - Over-rendering):**
   Il codice reitera l'intero set di nei tramite `forEach` e istanzia per ciascuno un intero albero Compose (`MoleMarker`). Questo bypassa le regole di ottimizzazione visiva: gestire migliaia di Composable concorrenti fuori da un `LazyLayout` distrugge la fluidità dei 60fps in fase di Panning.
3. **Inquinamento UI nel Dominio (Violazione Clean Architecture):**
   I modelli di dominio (`Mole.kt`) e l'UiModel contengono tipi `androidx.compose.ui.graphics.Color`. Questo annulla la portabilità del Dominio, accoppiando in modo fatale logica di business e framework grafico (che non possono essere testati tramite pura JVM).
4. **Spaghetti State & God Object (`SkinHistoryAppState.kt`):**
   Le variabili di transizione (`movingMoleId`, `pendingPhotoPath`, `editingEntryId`) sono ospitate pubblicamente nell'AppState, violando l'incapsulamento dell'UDF e trasformando lo State Holder in una pattumiera globale per passare ID tra schermi, bypassando l'uso sicuro di argomenti via `NavBackStackEntry` o `SavedStateHandle`.
5. **Recomposition Leak e Thread Blocking (`AutoCameraScreen.kt` e Global Scopes):**
   L'animazione `autoCaptureProgress` infetta l'intero Composable scatenando ricomposizioni inutili dell'intera fotocamera. Peggio ancora, l'algoritmo di calcolo dell'autofocus (`calculateLaplacianVariance`) blocca sincronicamente la pipeline di analisi iterando milioni di pixel. Inoltre, nel Data Layer si creano iniezioni globali anonime come `CoroutineScope(Dispatchers.IO)` che distruggono la testabilità dei Worker.

---

## Piano di Modernizzazione Globale

1. **UDF e Separazione MVI Puro:** Svincolare la navigazione e i dati tramite Type-Safe Routes o `SavedStateHandle`. Disaccoppiare del tutto gli import UI dal Livello Dominio.
2. **Single Responsibility (Use Cases):** I ViewModel obesi (es. Spatial hashing e pipeline temporali in `BodyMapViewModel`) verranno svuotati trasferendo l'elaborazione pesante ad `UseCase` iniettati.
3. **Ottimizzazione Grafica (Culling & Deferring):** Se il `BodyMapScreen` si trova a bassi livelli di zoom (`PreviewSize.COLORED_DOT`), i `MoleMarker` non esisteranno come entità Compose. Tutto sarà disegnato con primitive Canvas (`drawCircle`). Nelle animazioni di sistema, il *read* degli stati fluttuanti verrà relegato rigorosamente all'interno del delegato `DrawScope`.
4. **Edge-to-Edge & Gradle Compliance:** Riconoscimento completo delle barre di sistema (SDK 35) tramite `enableEdgeToEdge()` e migrazione totale delle dipendenze hardcoded (`appcompat`, `mockk`, `turbine`) verso `libs.versions.toml`.

---

## Roadmap di Refactoring Definitiva

L'esecuzione del refactoring è articolata in 5 fasi che partono dalle fondamenta strutturali per concludersi con l'esperienza utente.

### Fase 1: Build & Tooling Foundation (Blocco 5)
- **Azione:** Migrazione massiva al Version Catalog.
- **Dettagli:** Trasferire librerie come `sh.calvin.reorderable`, `mockk`, `turbine`, e gli elementi Hilt (`hilt-work`) non dichiarati, codificandoli strettamente nel `libs.versions.toml`. Eliminazione dell'hardcoding in `app/build.gradle.kts`.

### Fase 2: Domain Layer & Data Purification (Blocco 1 & 2)
- **Dominio:** Sostituzione dei tipi `android.graphics.Color` in stringhe esadecimali `String` (`colorHex`).
- **Database (`MoleDao.kt`):** Demolire `MoleWithHistory` (e `@Relation`). Disegnare query piatte e lasciare l'aggregazione a un Repository reattivo.
- **Dependency Injection & Workers:** Rimozione degli scope globali anonimi. Creazione ed iniezione di `@ApplicationScope` via Hilt. Eliminazione delle stringhe hardcoded non localizzate dai canali di notifica nel `ReminderWorker`.

### Fase 3: ViewModels & UDF Strict Enforcement (Blocco 3)
- **Disinnesco RAM:** Distruzione del `cachedTimelineFlow`. Il ViewModel fornirà solo la lista per la `selectedDate` tramite filtro in real-time `O(N)`.
- **Pulizia Navigazione:** Deprecazione totale dei parametri ibridi in `SkinHistoryAppState`. Implementazione di passaggio parametri o `SavedStateHandle` per garantire la conservazione dello stato alla morte del processo.
- **Semplificazione StateFlow:** Abbattimento delle catene infinite di `combine` con più di 5 argomenti (e tuple anonime) usando classi wrapper e Use Cases per le computazioni pesanti (es. `DetectMoleCollisionUseCase`).

### Fase 4: Compose UI & Edge-to-Edge Experience (Blocco 4)
- **MainActivity.kt:** Applicazione di `enableEdgeToEdge()` all'avvio e gestione Padding Insets nelle Scaffold root.
- **BodyMapScreen.kt (LOD):** Implementazione Level-of-Detail. By-pass delle `key()` compose loop con uso esclusivo della Canvas nativa per la vista massiva (1000+).
- **AutoCameraScreen.kt:** Spostamento di `autoCaptureProgress` all'interno del DrawScope. Esportazione di `calculateLaplacianVariance` in coroutine background (es. `Dispatchers.Default`).
