# Specifica di Refactoring Completa (Tutti i Blocchi)

---

# Specifica di Refactoring: Blocco 1 (Root, Application, MainActivity, Manifest, Build)

## 1. Diagnosi Architetturale
Il blocco principale del progetto risulta solido per quanto riguarda il setup di base (Hilt, KSP, Room, WorkManager integrati correttamente nel `build.gradle.kts` e Manifest). L'entry point `MainActivity` funge esclusivamente da host per Compose tramite `NavHost`, delegando responsabilmente lo stato al NavGraph, in linea con l'architettura MVI desiderata. Tuttavia, il blocco mostra segni di incuria (debito tecnico "cosmetico") con la presenza di pesanti residui di codice di prototipazione, importazioni orfane e la mancata adozione di standard nativi moderni per il windowing su Android 15+ (SDK 35/36).

## 2. Red Flags
* **Mancanza di Edge-to-Edge nativo (Violazione Regola 3):** L'app punta al `targetSdk = 36` ma la `MainActivity` non invoca `enableEdgeToEdge()` prima di `setContent`. Sulle versioni moderne di Android, la gestione dei WindowInsets e del layout full-screen è obbligatoria e il suo mancato rispetto causa rendering errato (bande nere) sui nuovi device.
* **Avvio Diretto di Coroutine nell'Application:** Chiamare `dataIntegrityScanner.startScanning()` direttamente in `SkinHistoryScannerApplication.onCreate()` è al limite dell'accoppiamento. Fortunatamente lo scanner usa un proprio `appScope` (verificato nei blocchi successivi), ma sporca il ciclo di vita dell'Application class. È passabile, ma non elegantissimo.

## 3. File Orfani e Codice Morto
* **`MainActivity.kt`**: È letteralmente invaso da importazioni orfane e non utilizzate che degradano i tempi di KSP e la pulizia del file (es. `android.Manifest`, `android.os.Build`, `java.io.File`, `androidx.lifecycle.lifecycleScope`, `kotlinx.coroutines.launch`, `kotlinx.serialization.json.Json`, `android.net.Uri`, `com.example.skinhistoryscanner.utils.Seeder`). È presente anche un inutile commento `// Invalidate KSP cache`.
* **`SkinHistoryScannerApplication.kt`**: Contiene un enorme e inutile blocco di testo commentato (righe 25-28) che descrive speculazioni sul funzionamento del `WorkManagerInitializer`. Codice totalmente morto.

## 4. Modernizzazione
* **Edge-to-Edge**: Adozione imperativa di `enableEdgeToEdge()` in `MainActivity` per adeguare la UI agli standard SDK 35+.
* **Discrepanza con `architecture.md` (Verità Architetturale):** La sezione 5 di `architecture.md` dichiara: *"Rilevamento Automatico con Fotocamera (Piano Futuro) - Questa feature non è ancora attiva..."*. L'ispezione al codice dimostra che la `AutoCameraScreen` è stata completata, implementata e usa attivamente l'oggetto `AlgorithmicMoleDetector`. **Il codice è più moderno del documento.** Il file markdown dovrà essere aggiornato per sanare l'incongruenza.

## 5. Piano di Refactoring Step-by-Step
1. **Modifica `MainActivity.kt`**: 
   * Aggiungere l'import `androidx.activity.enableEdgeToEdge`.
   * Invocare `enableEdgeToEdge()` come prima istruzione in `onCreate()` prima di `setContent`.
   * Eliminare tutte le importazioni orfane non utilizzate.
   * Rimuovere il commento dead-code `// Invalidate KSP cache`.
2. **Modifica `SkinHistoryScannerApplication.kt`**:
   * Cancellare il lungo e inutile commento multi-riga sul `WorkManagerInitializer` nel metodo `onCreate()`.
3. **Modifica `architecture.md`**:
   * Aggiornare la **Sezione 5** eliminando il tag "(Piano Futuro)" e la frase "Questa feature non è ancora attiva", allineando il documento alla reale esistenza e funzionamento dell'algoritmo visivo implementato.

---

# Specifica di Refactoring: Blocco 2 (Data Layer, Room, Scanners)

## 1. Diagnosi Architetturale
Il blocco 2 gestisce il cuore persistente dell'app (Room Database, Repositories e Scanner in background). Il design pattern è solido: le entità sono separate dai modelli di dominio, il mapping avviene asincronamente e la distruzione dei file fisici è disaccoppiata tramite un `FileRepository`. L'ottimizzazione del DAO per la visualizzazione massiva è conforme alle linee guida. Tuttavia, l'algoritmo dello scanner d'integrità presenta un rischio latente di inefficienza e memoria sotto carico estremo.

## 2. Red Flags
* **Rischio OOM (Scalabilità Estrema - Violazione Regola 1):** In `DataIntegrityScanner.kt`, il metodo `cleanupOrphanedFiles` utilizza `filesDir.listFiles()`. Questo approccio legacy di `java.io.File` alloca istantaneamente un array in memoria contenente tutte le reference ai file. Se l'utente raggiunge i 5000+ nei storicizzati (con 5000 foto e 5000 thumbnail associate, totale 10.000+ file), chiamare `listFiles()` genera un massiccio "GC Churn" e può scatenare un OutOfMemoryError sui dispositivi più vecchi. 
* **Esecuzione Flow in DAO:** Nel `OfflineMoleRepository.deleteMole`, viene chiamato `moleDao.getMoleByIdWithHistory(moleId).firstOrNull()`. Poiché la query DAO restituisce un `Flow`, Room attiva i trigger SQLite, crea la stream, emette il primo valore e poi la stream viene interrotta da `firstOrNull()`. Questo è un overkill per un semplice recupero sincrono prima di una cancellazione. 

## 3. File Orfani e Codice Morto
* In `MoleDao.kt`, sono presenti import non strettamente necessari, ma il file risulta generalmente pulito. 
* Non si rilevano grossi frammenti di codice morto, ma un eccesso di log string (`Log.w`, `Log.d`) in `DataIntegrityScanner.kt` potrebbe ingombrare il logcat inutilmente in produzione se non filtrato.

## 4. Modernizzazione
* **Directory Streaming (NIO):** Abbandonare `File.listFiles()` in favore del moderno `java.nio.file.Files.newDirectoryStream()`. Questa API moderna permette di iterare la cartella dei file in modo *lazy* (pigro), caricando un file alla volta in RAM. Garantisce stabilità anche con 100.000 file nella cartella interna.
* **Recupero Sincrono DAO:** Introdurre in `MoleDao` una funzione `suspend fun getMoleByIdWithHistorySync(moleId: String): MoleWithHistory?` per i recuperi one-shot (es. cancellazione), evitando di istanziare macchinari reattivi (Flow) per un'operazione imperativa.

## 5. Piano di Refactoring Step-by-Step
1. **Modifica `MoleDao.kt`**:
   * Aggiungere: `@Transaction @Query("SELECT * FROM moles WHERE id = :moleId") suspend fun getMoleByIdWithHistorySync(moleId: String): MoleWithHistory?`
2. **Modifica `OfflineMoleRepository.kt`**:
   * Nel metodo `deleteMole()`, sostituire `getMoleByIdWithHistory(moleId).firstOrNull()` con il nuovo e più performante `getMoleByIdWithHistorySync(moleId)`.
3. **Modifica `DataIntegrityScanner.kt`**:
   * Sostituire il blocco in `cleanupOrphanedFiles` che fa `val allFiles = filesDir.listFiles() ?: return` con un iteratore basato su `java.nio.file.Files.newDirectoryStream(filesDir.toPath())`. 
   * Iterare lo stream avvolgendolo in un blocco `.use { stream -> ... }` per garantire il rilascio sicuro delle risorse, preservando i `delay()` per non bloccare il context di background.

---

# Specifica di Refactoring: Blocco 3 (DI, Utilities, Notifiche)

## 1. Diagnosi Architetturale
Il blocco 3 gestisce i moduli di Dependency Injection, le funzioni di compressione (ZipUtils) e il setup dei reminder in background. Nel complesso, il blocco eccelle nella gestione dei file I/O: la classe `ZipUtils` adotta un approccio `stream-based` (prevenendo OOM durante il backup di giga di foto) e implementa correttamente difese di sicurezza contro l'attacco "Zip Slip". Tuttavia, la gestione delle notifiche presenta un serio problema di accoppiamento che viola le linee guida architetturali.

## 2. Red Flags
* **Accoppiamento Stretto e Violazione DI (Violazione Regola 2):** `ReminderManager.kt` è implementato come un Singleton `object` statico di Kotlin che istanzia direttamente il framework con `WorkManager.getInstance(context)`. Questo approccio viola l'inversione delle dipendenze, lega la logica al framework in modo hardcoded, rende la classe impossibile da testare via Mocking, e costringe chi la usa (probabilmente i ViewModel) a passargli un `Context`, contravvenendo alle regole di Clean Architecture.

## 3. File Orfani e Codice Morto
* Nessun file orfano rilevato. I moduli di Hilt (`DatabaseModule.kt`, `CoroutineScopesModule.kt`) e l'utility Zip sono estremamente coesi e focalizzati.

## 4. Modernizzazione
* **Dependency Injection Pura per i Service:** L'`object ReminderManager` deve essere trasformato in una classe standard gestita da Hilt (es. `@Singleton class ReminderManager @Inject constructor(...)`). Il `WorkManager` non va ottenuto tramite metodo statico `getInstance`, ma iniettato direttamente nel costruttore dal grafo di Hilt per massimizzare la testabilità.

## 5. Piano di Refactoring Step-by-Step
1. **Modifica `ReminderManager.kt`**:
   * Rimuovere la dichiarazione `object` e convertirla in una classe `@Singleton class ReminderManagerImpl @Inject constructor(private val workManager: WorkManager) : ReminderManager`.
   * Estrarre un'interfaccia base `ReminderManager` per il decoupling.
   * Rimuovere l'uso statico di `WorkManager.getInstance(context)`.
   * Adattare la firma del metodo `scheduleReminder` in modo che non richieda più il passaggio manuale del `Context` (dato che il `WorkManager` iniettato ha già il suo contesto).
2. **Creazione Modulo DI (Opzionale/Integrazione)**:
   * Aggiungere un `@Provides fun provideWorkManager(@ApplicationContext context: Context): WorkManager` nel modulo di apposito, qualora non fosse già fornito da `androidx.hilt:hilt-work`.

---

# Specifica di Refactoring: Blocco 4 (ViewModels e State Management)

## 1. Diagnosi Architetturale
Il blocco 4 è il nucleo dell'architettura reattiva (MVI). I ViewModels operano una magistrale gestione dei flussi concorrenti: i `combine` sono stati sapientemente nidificati in data class (`MapContextData`, `UserSettings`) per aggirare il limite di 5 argomenti di Kotlin Flow, preservando la Type-Safety totale (come richiesto in `architecture.md`). L'uso del `Dispatchers.Default` per le operazioni pesanti è diffuso. Purtroppo, sono state riscontrate due violazioni critiche relative alle performance UI e all'inversione delle dipendenze.

## 2. Red Flags
* **Main Thread Blocking (Violazione Regola 2):** Nel `BodyMapViewModel`, il metodo `findMoleAtTap` è correttamente protetto da `suspend fun ... withContext(Dispatchers.Default)`. Tuttavia, la funzione gemella `snapMolePosition` esegue calcoli geometrici massivi (ricerca di collisioni tramite distanza Euclidea e radici quadrate `sqrt`, reiterata fino a 10 tentativi per 1000+ nei) in modo del tutto sincrono. Se invocata da un gesto di drag sulla UI di Compose, questa funzione bloccherà il Main Thread sgretolando il target dei 60fps.
* **Fake Implementation & Coupling (Violazione Regola 2 e 4):** Nel `SettingsViewModel`, l'avvio dei worker di import/export chiama esplicitamente `WorkManager.getInstance(context)`. Oltre alla violazione della DI, il tracciamento della fine del processo è un mock ("fake"): utilizza `delay(1000)` simulando artificialmente il completamento del backup invece di osservare la `WorkInfo` del WorkManager. 

## 3. File Orfani e Codice Morto
* `SettingsViewModel.kt`: Contiene commenti di debito tecnico (`// In a real app we would observe the WorkInfo...`) che certificano la presenza di un workaround. Non sono presenti file o funzioni interamente orfane, ma il codice di simulazione del delay è da considerarsi "codice morto/placeholder" da sostituire.

## 4. Modernizzazione
* **Geometria Asincrona:** Tutte le funzioni che iterano array di UI Models per calcoli complessi (come `snapMolePosition`) devono essere convertite in `suspend fun` incapsulate in `withContext(Dispatchers.Default)`.
* **Flows reattivi per WorkManager:** Invece di usare un callback `onComplete` e un `delay(1000)`, il ViewModel deve interrogare `workManager.getWorkInfoByIdFlow(workRequest.id)` e reagire quando lo stato diventa `SUCCEEDED` o `FAILED`, per garantire un feedback UI reale.
* **Dependency Injection:** Il `SettingsViewModel` deve iniettare `WorkManager` direttamente nel costruttore tramite Hilt, rimuovendo le dipendenze dal `Context` per istanziarlo localmente.

## 5. Piano di Refactoring Step-by-Step
1. **Modifica `BodyMapViewModel.kt`**:
   * Cambiare la firma: `suspend fun snapMolePosition(...)`
   * Avvolgere il corpo del metodo in `withContext(Dispatchers.Default) { ... }`.
2. **Modifica `SettingsViewModel.kt`**:
   * Aggiungere `private val workManager: WorkManager` nel costruttore `@Inject`.
   * Rimuovere `WorkManager.getInstance(context)`.
   * Nei metodi `importDatabase` ed `exportDatabase`, rimuovere il blocco `delay(1000)` e l'uso fittizio di `onComplete()`.
   * Osservare il WorkRequest tramite `workManager.getWorkInfoByIdFlow(workRequest.id).collectLatest { info -> if(info.state.isFinished) { _isProcessing.value = false; onComplete() } }` all'interno del `viewModelScope`.
3. **Modifica `SettingsViewModel.kt` (Minor)**:
   * Aggiornare `testNotification()` per utilizzare il `workManager` iniettato anziché chiamare `getInstance`.

---

# Specifica di Refactoring: Blocco 5 (UI Screens & Compose)

## 1. Diagnosi Architetturale
Il Blocco 5 racchiude la UI principale scritta in Jetpack Compose (`BodyMapScreen`, `AutoCameraScreen`, `MoleDetailsScreen`). Nel complesso, la reattività è implementata correttamente tramite state hoisting e `UiState` pre-calcolati. Tuttavia, sono emersi colli di bottiglia critici, specificamente per il target di 1000+ nei (Regola 1) e nella gestione della memoria in real-time nella fotocamera (Regola 2).

## 2. Red Flags e Anti-Pattern Critici
* **OOM & Compose Jank (Violazione Regola 1):** In `BodyMapScreen`, quando la modalità di visualizzazione (`previewSize`) richiede di mostrare le thumbnail fotografiche (`IMAGE_SMALL` o `IMAGE_LARGE`), il codice utilizza un ciclo `state.moles.forEach { MoleMarker(...) }`. `MoleMarker` invoca a sua volta `AsyncImage` di Coil. Caricare 1000+ componenti `@Composable` `AsyncImage` in memoria simultaneamente, fuori da un layout *Lazy* (e peraltro tutti a schermo), causerà OutOfMemoryError immediati o crolli dei frame rate ben al di sotto dei 60fps. La UI di Compose non è adatta al rendering simultaneo di migliaia di nodi complessi.
* **GC Churn in Camera Analyzer (Violazione Regola 2):** In `AutoCameraScreen`, la funzione `calculateLaplacianVariance` (usata per misurare il focus dell'immagine) viene chiamata su ogni frame analizzato (30fps). All'interno di questa funzione viene allocato un nuovo array: `val pixels = IntArray(width * height)`. Allocare array massivi ad ogni frame causa un rapido esaurimento della memoria giovane (Young Generation) e innesca cicli continui di Garbage Collection, bloccando il Main Thread e causando surriscaldamento del dispositivo.
* **Logica Geometrica Duplicata (Violazione Regola 4):** Nel `BodyMapScreen`, la conversione dei tocchi da coordinate schermo a coordinate logiche `(internalX, internalY)` avviene direttamente all'interno della view tramite calcoli matematici hard-coded.

## 3. File Orfani e Codice Morto
* In `AutoCameraScreen.kt`, alcune stringhe di stato ("Scatto in corso...", "Metti a fuoco il neo") sono hardcoded, bypassando il sistema di localizzazione. 
* Non sono presenti file UI completamente inutilizzati.

## 4. Modernizzazione e Ottimizzazione
* **Pre-rendering su Canvas:** Per supportare 1000+ nei in modalità anteprima fotografica, bisogna evitare di creare migliaia di `MoleMarker` (Composables). La soluzione ottimale è far calcolare al `BodyMapViewModel` un'unica `ImageBitmap` (o mappa di `ImageBitmap` in miniatura in cache limitata) e passarle alla UI, affinché `BodyMapScreen` esegua il rendering tramite un semplice `drawBitmap` puro (O(N) rapido) nel blocco `Canvas`. Coil `AsyncImage` deve essere riservato unicamente a viste singole o elenchi paginati (es. `LazyColumn`).
* **Object Pooling per la Camera:** Il buffer `IntArray` per il calcolo del Laplaciano deve essere pre-allocato e memorizzato/iniettato come instanza riutilizzabile (`Object Pool`), riciclandolo ad ogni frame.

## 5. Piano di Refactoring Step-by-Step
1. **Modifica `AutoCameraScreen.kt`**:
   * Dichiarare un `private var reusablePixelsBuffer: IntArray? = null` a livello di classe/oggetto o all'interno di uno state persistente di `AlgorithmicMoleDetector`.
   * Aggiornare `calculateLaplacianVariance(bitmap, buffer)` per accettare e riutilizzare il buffer, ridimensionandolo solo se le dimensioni della bitmap cambiano (es. `if (buffer == null || buffer.size < expectedSize) buffer = IntArray(expectedSize)`).
   * Spostare le stringhe hardcoded nel `strings.xml`.
2. **Modifica `BodyMapScreen.kt` e architettura di Rendering**:
   * Eliminare il loop `forEach` di `MoleMarker` per i punti visivi massivi.
   * Modificare il `Canvas` per disegnare direttamente le immagini miniatura (`ImageBitmap`) caricate asincronamente dal ViewModel. (Il ViewModel esporrà un layer di thumbnail pre-cacheate via NIO).
3. **Refactoring di Layout & State**:
   * Assicurarsi che `MoleDetailsScreen` e le sue liste rimangano interamente su `LazyColumn` (già presente e corretto).

---

# Specifica di Refactoring: Blocco 6 (UI Components)

## 1. Diagnosi Architetturale
Il Blocco 6 contiene la libreria di componenti UI riutilizzabili (`MoleMarker`, `MoleLegend`, `TimelineSlider`, `MoleDetailsComponents`, `VariantManagementBottomSheet`). I componenti seguono correttamente i principi di "State Hoisting" e architettura unidirezionale di Compose, ma ereditano le criticità di performance sollevate nel Blocco 5.

## 2. Red Flags e Anti-Pattern Critici
* **OOM Causato da `MoleMarker` (Violazione Regola 1):** Il componente `MoleMarker` è il principale responsabile del collo di bottiglia individuato nel Blocco 5. Ciascuna istanza di questo componente invoca `AsyncImage` di Coil, impostando un ridimensionamento fisso a `size(150)` e `crossfade(true)`. Poiché la mappa principale invoca questo componente 1000+ volte simultaneamente fuori da un contesto Lazy, la natura "pesante" di questo nodo UI è un anti-pattern architetturale gravissimo. Un componente "Marker" inteso per il rendering massivo in una mappa non dovrebbe mai essere un nodo `@Composable` con un gestore asincrono interno per le immagini, bensì un pre-render su Canvas.
* **Bypass dell'Accessibilità (Violazione Regola 2 e 3):** `MoleMarker` forza esplicitamente un override dell'accessibilità di sistema per ridurre la dimensione interattiva: `LocalMinimumInteractiveComponentSize provides Dp.Unspecified`. Questo nasconde un problema di design: se il touch target è troppo piccolo per l'accessibilità, bypassare il controllo di sistema è un anti-pattern. La gestione dei tap massicci deve essere delegata a un handler `pointerInput` centralizzato sulla griglia, non forzando il ridimensionamento dei componenti singoli.

## 3. File Orfani e Codice Morto
* I file `DateHeader.kt`, `HistoryItem.kt` e `MoleSummaryHeader.kt` non sono più file indipendenti, ma sono stati uniti/spostati all'interno di `MoleDetailsComponents.kt`.

## 4. Modernizzazione e Ottimizzazione
* **Deprecazione e Riscossa di `MoleMarker`:** Il componente `MoleMarker` in quanto nodo `@Composable` indipendente deve essere marcato come obsoleto per l'uso "massivo". Deve essere sostituito da una funzione puramente di disegno (es. estensione su `DrawScope` o metodo delegato al `Canvas` in `BodyMapScreen`) che riceva le `ImageBitmap` pre-cacheate dal `BodyMapViewModel`. `MoleMarker` potrà sopravvivere solo se utilizzato come indicatore UI isolato.
* **Consolidamento dei Moduli:** I dettagli interni dei singoli componenti in `MoleDetailsComponents.kt` sono eccellenti (es. uso coerente di material design, gradienti sfumati sulle foto). Devono essere mantenuti.

## 5. Piano di Refactoring Step-by-Step
1. **Riformattazione Architetturale per `MoleMarker`**:
   * Scrivere una nuova classe o funzione helper `DrawScope.drawMoleMarker(x, y, radius, bitmap, color)` ottimizzata che utilizzi API native di canvas (`drawBitmap`, `drawCircle`) in modo deterministico e O(1).
   * Rimuovere l'invocazione di `MoleMarker` dal loop composable in `BodyMapScreen` e rimpiazzarla con il renderer Canvas.
   * Eliminare il bypass `LocalMinimumInteractiveComponentSize` qualora `MoleMarker` dovesse rimanere per scopi marginali, o eliminarlo del tutto.
2. **Ottimizzazione Coil in UI Components**:
   * Negli altri componenti come `MoleDetailsComponents.kt` (es. `HistoryItem`), limitare e ottimizzare l'uso della memoria di Coil aggiungendo un placeholder leggero o usando `memoryCachePolicy` rigorosi per evitare OOM durante lo scroll molto veloce della `LazyColumn`.

---

# Specifica di Refactoring: Blocco 7 (Risorse XML e Test Suite)

## 1. Diagnosi Architetturale
Il blocco finale copre i file di risorsa XML (`strings.xml`, `themes.xml`) e le Unit Test Suite (`BodyMapViewModelTest.kt`, `SettingsViewModelTest.kt`, `MoleDaoTest.kt`). 

## 2. Red Flags e Anti-Pattern Critici
* **Nessuna violazione bloccante (Regola 2):** L'infrastruttura di test utilizza correttamente le librerie moderne (`Turbine` per i Flow, `MockK`, `kotlinx.coroutines.test`). L'uso di `advanceTimeBy(5001)` nei test dimostra una profonda consapevolezza di come funziona l'operatore `stateIn(SharingStarted.WhileSubscribed(5000))` di Coroutines.
* **Debito Tecnico Minore (Regola 4):** I file di traduzione (`strings.xml`) mancano di alcune stringhe identificate nel Blocco 5 ("Scatto in corso...", "Metti a fuoco il neo" in `AutoCameraScreen.kt`). 

## 3. File Orfani e Codice Morto
* Non sono state rilevate risorse XML duplicate o inutilizzate di rilevanza. I test coprono con precisione i comportamenti di business e le query Room fondamentali.

## 4. Modernizzazione e Ottimizzazione
* **Integrazione XML:** Centralizzare le stringhe cablate individuate in precedenza direttamente in `strings.xml` per completare il supporto multi-lingua nativo.
* **Test Coroutines MVI:** Consolidare l'inizializzazione del dispatcher di test.

## 5. Piano di Refactoring Step-by-Step
1. Spostamento delle stringhe di debug/stato di CameraX e WorkManager all'interno di `strings.xml` per uniformità.
2. Manutenzione ordinaria dei test in concomitanza col refactoring dei blocchi precedenti (es. quando modificheremo `SettingsViewModel` per WorkManager, i test dovranno essere adattati).
