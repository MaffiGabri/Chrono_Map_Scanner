# Audit Master Collection: Chrono Map Scanner

Questo documento raccoglie tutti i report, le specifiche di refactoring dei singoli blocchi e l'analisi Clean Code in un'unica visione d'insieme.

---

# Specifica di Refactoring: Blocco 1 (Data Layer)

## 1. Diagnosi Architetturale
Il Data Layer tenta di astrarre correttamente il filesystem dal database delegando le operazioni di I/O, ma l'implementazione attuale fallisce clamorosamente sui requisiti di scalabilità (1000+ elementi). La logica di "Flattening" adottata nel DAO per alimentare la UI genera prodotti cartesiani, minando le fondamenta della mappa corporea con carichi asincroni sproporzionati. Inoltre, la persistenza e il controllo dell'integrità dei file in background contraddicono le linee guida `ARCHITECTURE.md`: al posto di utilizzare code di sistema affidabili, si è optato per loop infiniti basati su coroutine interne alla RAM, generando un costante battery drain e pesanti "Memory Churn". L'export del DB, infine, usa una fallimentare serializzazione in memoria totale, destinata a causare OOM (Out of Memory) nei dispositivi con molta cronologia fotografica.

## 2. Red Flags
*   **Prodotto Cartesiano nel DAO (Rischio Crash/Lag UI):** In `MoleDao.kt`, la query `getFlatMolesWithHistory` esegue una generica `LEFT JOIN history_entries` senza filtrare l'ultima entry. Un neo con 10 foto storiche verrà clonato 10 volte nel `Flow<List>`. Con 1000 nei e 5 foto ciascuno, la UI riceverà 5000 DTOs. Questo causa un massivo overhead di mappatura su CPU e spreco di memoria (GC Churn).
*   **Loop Infinito e "God Object" (Battery Drain & OOM):** In `DataIntegrityScanner.kt`, un loop infinito `while(isActive)` gira nel `Dispatchers.IO`. Ancora peggio, ad ogni ciclo (es. ogni 5 min), per calcolare i path attivi carica in RAM l'intera relazione gerarchica del database per tutti i profili (`moleDao.getMolesWithHistory`), allocando migliaia di `MoleWithHistory` solo per estrarne delle stringhe.
*   **In-Memory JSON Serialization (OOM Risk):** In `BackupRepository.kt`, la logica di esportazione carica l'intero database in una singola classe e usa `json.encodeToString(databaseDto)`, caricando in heap un file JSON gigante tutto in un solo colpo prima di scriverlo su disco.
*   **Leak Mapping di Dominio:** Nel DTO di Room `variantId` è stato arbitrariamente rinominato in `side` nei mapper di `MoleModels.kt`. Confonde e nasconde il reale intento relazionale.

## 3. File Orfani e Codice Morto
*   `DataIntegrityScanner.kt`: L'intero file rappresenta un anti-pattern strutturale. Il pattern di Singleton con scope globale legato a un `while()` va estirpato. La logica valida deve essere condensata in un `Worker` delegato.

## 4. Modernizzazione
*   `MoleDao.kt` -> **Subquery con Row Numbering/Max:** Aggiornare la left join in modo che associ al neo ESCLUSIVAMENTE la `history_entry` più recente (es. `LEFT JOIN (SELECT mole_id, imagePath, MAX(date) FROM history_entries GROUP BY mole_id)` o l'uso nativo delle Window Functions di SQLite `ROW_NUMBER() OVER (...)`).
*   `DataIntegrityScanner.kt` -> **PeriodicWorkRequest (WorkManager):** Demolire la classe e spostare il ciclo di vita del task nel modulo `WorkManager` (già presente ma sottoutilizzato in `FileCleanupWorker`), garantendo rispetto dei Doze-states di Android e stop automatico.
*   `BackupRepository.kt` -> **JSON Streaming (encodeToStream):** Modificare `createAndWriteExportZip` sostituendo `encodeToString` con `kotlinx.serialization.json.okio.encodeToBufferedSink` o `encodeToStream` nativo di `kotlinx-serialization` su `FileOutputStream` per azzerare l'impatto sulla memoria durante il backup.

## 5. Piano di Refactoring Step-by-Step
1.  **File `MoleDao.kt`:**
    *   Riscrivere la stringa `@Query` di `getFlatMolesWithHistory` introducendo un `GROUP BY h.mole_id` o subquery limitata all'ultimo record storico, per inviare 1 DTO esatto per ogni neo.
    *   Assicurarsi di estrarre `imagePath` come array nativo (`List<String>`) in nuove `@Query` leggere destinate unicamente al controllo integrità dei file fisici, rimuovendo il bisogno di recuperare `MoleWithHistory`.
2.  **File `MoleModels.kt` e Mapper:**
    *   Rinominare le incongruenze semantiche (`side` in `variantId` dove appropriato) per mantenere l'allineamento Strict Type-Safety con le entità.
3.  **File `BackupRepository.kt`:**
    *   Effettuare il refactoring della serializzazione. Aprire il `FileOutputStream` e agganciarlo all'engine JSON per il flusso diretto su disco.
4.  **Sostituzione `DataIntegrityScanner.kt`:**
    *   Cancellare interamente il file `DataIntegrityScanner.kt`.
    *   Spostare e ottimizzare la logica di cancellazione orfani e generazione di Thumbnail (che ora previene il caricamento ad alto livello di tutta la cache in memoria usando query DAO selettive di stringhe) in un nuovo Worker Hilt `DataIntegrityWorker.kt`.
5.  **Aggiornamento Application / DI:**
    *   Rimuovere le instanze di inject in `ChronoMapScannerApplication` che avviavano il `startScanning()`. Programmare il Job tramite `WorkManager` al boot o all'apertura dell'app.

---

# Specifica di Refactoring: Blocco 2 (Workers, Utils, DI, e Notifiche)

## 1. Diagnosi Architetturale
Il blocco responsabile dei processi asincroni (Workers) e dell'inizializzazione dell'app mostra una grave miscelazione di confini architetturali. Nonostante sia stato adottato `WorkManager` per alcune operazioni, i Worker sono stati programmati come estensioni dirette del ViewModel: manipolano direttamente lo stato della UI (tramite `MutableStateFlow` dei Repository) creando rischi letali di blocchi dell'interfaccia se il sistema operativo killa il task. Le operazioni massicce di I/O durante l'importazione bloccano per tempi irragionevoli il database SQLite. Infine, alcuni script esplorativi (come algoritmi sperimentali o logiche di seeding) sporcano l'albero di produzione invece di risiedere in moduli isolati o directory di test.

## 2. Red Flags
*   **State Leak & UI Lock (ImportDatabaseWorker):** Il worker modifica manualmente `settingsRepository.isImporting.value = true` all'inizio e `false` alla fine. Se Android killa il worker per scarsità di risorse, la variabile resterà `true` per sempre, e `MainActivity` mostrerà un overlay di caricamento bloccando l'app definitivamente per l'utente. I worker non devono mai mutare lo stato reattivo UI, ma l'app deve osservare nativamente il `WorkInfo.State` dal WorkManager.
*   **Transazione di Database Estrema (ANR Risk):** In `ImportDatabaseWorker`, il blocco `database.withTransaction { ... }` avvolge non solo gli inserimenti SQL, ma anche pesanti operazioni di lettura, rinominazione e scrittura dei file immagine (`backupRepository.importImageToInternalStorage`) per migliaia di elementi iterati. Un lock di database prolungato su I/O di rete o disco è una garanzia di "Database is Locked" e crash (ANR).
*   **Violazione Dependency Injection (Hardcoded WorkManager):** In `ReminderManager.kt` si istanzia `WorkManager.getInstance(context)` staticamente in un `object`, bypassando totalmente Hilt. Anche `ReminderWorker` manca dell'annotazione `@HiltWorker`. Questo degrada la testabilità e l'omogeneità architetturale.
*   **Violazione Architetturale Boot (Application Class):** La `ChronoMapScannerApplication` esegue nel metodo `onCreate()` la funzione `startScanning()`, lanciando l'esoso e problematico loop infinito di coroutine analizzato nel Blocco 1, paralizzando il MainThread startup finché non finisce il Dispatch.

## 3. File Orfani e Codice Morto
*   `utils/Seeder.kt`: Script hardcoded per iniettare finti nei. Non deve esistere nel target di build Release di produzione.
*   `utils/AlgorithmicMoleDetector.kt`: L'analisi empirica degli istogrammi con array intermedi è un POC (Proof of Concept) non ancora cablato alla UI e rischia di generare memory leak. In base all'`ARCHITECTURE.md`, questo dovrà essere sostituito da ML Kit Object Detection.

## 4. Modernizzazione
*   `MainActivity.kt` & WorkManager -> **WorkManager Flow Observation:** Aggiornare l'osservazione dello stato del processing eliminando la variabile "custom" e sfruttando la nativa `WorkManager.getInstance(context).getWorkInfosByTagFlow(...)`.
*   `ImportDatabaseWorker.kt` -> **Bulk Insert Paginato e I/O Separato:** Rimuovere l'I/O file dal `withTransaction`. Si preparano i dati in memoria/su disco, si creano Liste DTO batch, e si esegue un `insertAll()` massivo limitato esclusivamente al tempo di SQL Injection.
*   `ReminderManager.kt` -> **Hilt Injectable Singleton:** Trasformare l'`object` in una `class ReminderManager @Inject constructor(private val workManager: WorkManager)` e usare Hilt per risolverla.

## 5. Piano di Refactoring Step-by-Step
1.  **Application Class (`ChronoMapScannerApplication.kt`):**
    *   Rimuovere la chiamata a `dataIntegrityScanner.startScanning()`. Sostituirla con la schedulazione persistente (`enqueueUniquePeriodicWork`) del nuovo `DataIntegrityWorker` che creeremo nel Blocco 1.
2.  **Worker Architecture (`ImportDatabaseWorker.kt`):**
    *   Estrarre il loop di copia immagini `importImageToInternalStorage` *fuori* dal blocco `withTransaction`.
    *   Costruire liste batch (`List<MoleEntity>`) in RAM e inviarle ai DAO tramite nuovi metodi `@Insert(onConflict = IGNORE) fun insertMoles(moles: List<MoleEntity>)`.
    *   Eliminare il riferimento a `isImporting.value`.
3.  **Refactoring Hilt Notifiche:**
    *   Convertire `ReminderManager` in classe normale fornita via Dagger Hilt Module.
    *   Annotare `ReminderWorker` con `@HiltWorker` e iniettare i contesti.
4.  **UI Observation (`MainActivity.kt` e `SettingsViewModel`):**
    *   Rimuovere il parametro manuale `processing` in favore dell'ascolto reattivo dei worker taggati (es. "IMPORT_DB_WORK").
5.  **Pulizia R&D:**
    *   Spostare `Seeder.kt` e `AlgorithmicMoleDetector.kt` nella directory `test` o documentarli in `project_tools_and_scripts` per depurare il sorgente di produzione.

---

# Specifica di Refactoring: Blocco 3 (ViewModels, State e Navigation)

## 1. Diagnosi Architetturale
Il livello di presentazione applica diligentemente i principi dell'Unidirectional Data Flow (UDF) sfruttando `StateFlow` e `combine`. Tuttavia, l'intento di "pre-calcolare" lo stato in background è stato mal interpretato nel `BodyMapViewModel`, generando pipeline di calcolo che creano massicce copie in memoria dell'intero set di dati storici, violando palesemente il requisito dei 1000+ elementi e infliggendo letali "GC Churn" (Garbage Collection continue) al dispositivo. 
Inoltre, i limiti architetturali MVI e Clean Architecture sono stati rotti inserendo dipendenze native di Compose all'interno del ViewModel e utilizzando la classe "State" della navigazione come un secchio globale (God Object) per lo scambio di variabili di transizione.

## 2. Red Flags
*   **Memory Leak Esponenziale in Flow Mapping (`BodyMapViewModel.kt`):** La property `cachedTimelineFlow` non filtra i dati, ma itera iterativamente *su tutte le date esistenti*, generando in RAM una gigantesca `Map<LocalDate, List<MoleUiModel>>`. Se l'utente ha 1000 nei e 50 visite storiche (date), questo Flow alloca in continuazione 50.000 oggetti inutili per servire solo la vista di una data singola.
*   **Infezione UI/Compose in ViewModel (`MoleUiModel.kt`):** La Data Class di dominio UI Model importa `androidx.compose.ui.graphics.Color` e `BodyMapViewModel` invoca le conversioni di colore UI internamente (dentro il thread `Dispatchers.Default`). I ViewModel non devono mai conoscere il framework visivo. L'esadecimale (`colorHex`) è l'unico dato legittimo, la composizione del `Color` deve avvenire a valle, nel layer `@Composable`.
*   **State Hoisting Abusivo (Global Variables in `SkinHistoryAppState.kt`):** L'host della navigazione ospita variabili pubbliche come `var movingMoleId` o `var pendingPhotoPath`. Questo rompe l'incapsulamento MVI: i parametri di stato per la fotocamera o per la ricollocazione di un neo dovrebbero viaggiare in Type-Safety tramite le definizioni di Route o essere ritornati in modo sicuro usando il `SavedStateHandle` del `NavBackStackEntry`.
*   **Limite dei 5 flussi in `combine`:** `BodyMapViewModel` utilizza un `combine` multi-stadio estraendo valori in Tuple e Triple, riducendo la type-safety e rendendo la propagazione degli errori molto fragile.

## 3. File Orfani e Codice Morto
*   Nessun file completamente orfano, ma segmenti di codice da piallare: La variabile `val color: Color` dentro `MoleUiModel.kt` va rasa al suolo, obbligando la View a fare il mapping.

## 4. Modernizzazione
*   `BodyMapViewModel` -> **Filtro Singolo, Nessuna Cached Map:** Smantellare `cachedTimelineFlow`. Combinare `allMolesWithHistoryFlow` e `sampledSelectedDate` filtrando al volo unicamente per la data attiva. Il tempo impiegato per un filtering su 1000 iterazioni è inferiore al costo di allocazione della mappa completa, specialmente grazie allo `Spatial Hashing` già presente.
*   `SkinHistoryNavGraph` -> **SavedStateHandle Navigation:** Eliminare le var mutabili in `SkinHistoryAppState`. Implementare l'uso della Navigation Type-Safe (es. aggiungendo opzionalmente `movingMoleId` alla rotta target) o restituire il path della foto scattata passando da `navController.previousBackStackEntry?.savedStateHandle?.set("photo_path", path)`.

## 5. Piano di Refactoring Step-by-Step
1.  **Refactoring Clean Architecture UI (`MoleUiModel.kt`):**
    *   Rimuovere la proprietà `color: Color` e i suoi import da `MoleUiModel`.
    *   In `BodyMapViewModel`, cancellare il mapping `try { Color(parseColor(...)) }`.
2.  **Disinnesco OOM Memory Leak (`BodyMapViewModel.kt`):**
    *   Rimuovere la variabile `cachedTimelineFlow`.
    *   Riscrivere `uiMolesAndCountsFlow` combinando direttamente `allMolesWithHistoryFlow`, `_selectedDate` (e gli altri parametri).
    *   Filtrare la lista dei record storici mantenendo solo l'ultima entry <= `selectedDate` del ciclo corrente. Generare *una sola* lista in uscita.
3.  **Sanitizzazione Routing (`SkinHistoryAppState.kt` & `SkinHistoryNavGraph.kt`):**
    *   Svuotare completamente `SkinHistoryAppState` dalle variabili di transizione.
    *   Adattare i click-listener di navigazione nel `NavGraph` per usare il `SavedStateHandle` per passare "risultati" (come la path della foto o l'istruzione di spostamento neo) tra una schermata e la schermata che la invocava.
4.  **Consolidamento State MVI:**
    *   Estrarre Triple non tipizzate nei flussi `combine` usando Data Class interne semantiche (es. `ViewStateContext`), evitando collassi di type-safety quando si cambiano i tipi sorgente.

---

# Specifica di Refactoring: Blocco 4 (UI Layer: Composables, Edge-to-Edge e UX)

## 1. Diagnosi Architetturale
Il livello dell'interfaccia utente costruita con Jetpack Compose soffre di gravi inefficienze nella gestione del layout. Sebbene i paradigmi di base siano rispettati, l'app cade in alcune trappole letali per la performance quando testata sotto stress (1000+ elementi). La generazione dinamica dei marker anatomici collassa sotto il proprio peso a causa di un abuso dei nodi Compose. Inoltre, l'applicazione non rispetta le moderne direttive Android 15 (SDK 35+) per la gestione Edge-to-Edge dello schermo, lasciando bande di sistema non ottimizzate.

## 2. Red Flags
*   **FPS Drop Catastrofico (BoxWithConstraints + Nodi Massivi):** In `BodyMapScreen`, viene eseguito un ciclo `forEach` per instanziare centinaia o migliaia di Composables `MoleMarker` (che contengono immagini asincrone, ombre, clip) in sovrapposizione al Canvas. Costruire 1000+ nodi UI interattivi contemporaneamente su un layout che permette il pan/zoom farà crollare il frame rate da 60fps a valori insostenibili e riempirà la RAM. Un numero così alto di nodi deve essere demandato a disegni nativi in fase di *draw* (`Canvas`), e i Composable instanziati solo se il neo è attivamente esaminato o sufficientemente ingrandito.
*   **Assenza Edge-to-Edge (SDK 35+ Constraint):** Nel file `MainActivity.kt`, il blocco `onCreate` non invoca la funzione `enableEdgeToEdge()`. Con Android 15, questo rende l'app obsoleta graficamente. La UI non reagisce ai `WindowInsets` per disegnare fluidamente dietro le System Bars.
*   **Recomposition Leak (AutoCameraScreen):** L'animazione di pre-scatto modifica uno stato fluttuante (`autoCaptureProgress`) scatenando un loop di ricomposizioni a 60hz dell'intero `AutoCameraScreen`. Poiché il `Canvas` legge questo valore dallo scope del padre, l'intero albero UI del fragment si ridisegna, causando battery drain e lag nella preview.
*   **Calcoli Matematici su Thread (Laplacian Variance):** All'interno dell'Analyzer di `AutoCameraScreen`, il calcolo della varianza per l'autofocus analizza i pixel in modo rudimentale. Non è gestito come un Job dedicato in coroutine isolata o tramite script/render script, ma blocca pesantemente la pipeline d'immagine, minacciando il sync dell'Analyzer.

## 3. File Orfani e Codice Morto
* Nessun file è orfano, ma l'implementazione in `MainActivity.kt` è scheletrica e carente.

## 4. Modernizzazione
*   **Edge-to-Edge in MainActivity:** Aggiungere `enableEdgeToEdge()` prima di `setContent` in `MainActivity.kt`. Assicurarsi che lo `Scaffold` o il genitore `Surface` in `ChronoMapScannerApp` distribuiscano correttamente i pad tramite Modifier e Insets.
*   **Culling Architetturale (BodyMapScreen):** Implementare un sistema di rendering "Level of Detail" (LOD). Se la scala del Canvas non supera una certa soglia e l'app deve mostrare 1000 nei, questi verranno disegnati *esclusivamente* dal `Canvas` con primitive (`drawCircle`, `drawImage`). I `MoleMarker` (che sono nodi Compose a pieno titolo) esisteranno solo per i pochi nei visibili o selezionati localmente, aggirando il limite del compose layout traversal.
*   **Deferring State Reads (Canvas Animations):** Correggere `AutoCameraScreen` leggendo i float d'animazione direttamente dentro la lambda del `Canvas` (o usando classi deleganti/Lambda), affinché l'invalidation loop coinvolga solo il blocco di *Draw* e non l'intero albero di composizione.

## 5. Piano di Refactoring Step-by-Step
1.  **Impostazione Edge-to-Edge:**
    *   Aprire `MainActivity.kt` e aggiungere la chiamata a `enableEdgeToEdge()`.
    *   Aggiornare `SkinHistoryNavGraph` e gli schermi root per assorbire `Modifier.windowInsetsPadding()`.
2.  **Risanamento FPS Rendering Mappa:**
    *   In `BodyMapScreen.kt`, sfoltire il blocco `forEach` che istanzia i `MoleMarker`. Introdurre una logica per la quale, se `previewSize == PreviewSize.COLORED_DOT`, l'intero blocco Composable viene skippato a favore del `drawCircle` nativo. 
3.  **Ottimizzazione Ricomposizione (Camera):**
    *   Nel file `AutoCameraScreen.kt`, raggruppare i `Canvas` e gli stati fluttuanti all'interno di micro-componenti o usare blocchi lambda `{ progress }` per ritardare la lettura dello stato alla fase di rendering.
4.  **Messa in Sicurezza del Calcolo Pixel:**
    *   Ottimizzare il Loop di `calculateLaplacianVariance` in `AutoCameraScreen` o assicurarne l'esecuzione in uno scope coroutine parallelo senza bloccare i frame in arrivo della telecamera.

---

# Specifica di Refactoring: Blocco 5 (Gradle, Manifest e Configurazioni)

## 1. Diagnosi Architetturale
Il blocco di configurazione del progetto è nel complesso eccellente per quanto riguarda la modernità: l'applicazione adotta le specifiche target per SDK 36, Kotlin 2.1.0, KSP al posto di KAPT e il BOM di Compose del 2025. Inoltre, gestisce intelligentemente i permessi: utilizzando lo Storage Access Framework (`CreateDocument` / `OpenDocument`) per i backup, non sono state aggiunte le deprecate `READ_EXTERNAL_STORAGE`, aumentando la privacy dell'utente e la pulizia del manifest. Tuttavia, il Version Catalog (`libs.versions.toml`) è applicato a metà, lasciando numerose dipendenze hardcoded nei file Gradle, il che mina la scalabilità e la manutenzione a lungo termine.

## 2. Red Flags
*   **Version Catalog Incompleto (Hardcoded Dependencies):** Il file `app/build.gradle.kts` contiene diverse dipendenze iniettate letteralmente invece di usare il Version Catalog (`libs.versions.toml`). Questo è un anti-pattern per i progetti moderni, in quanto le dipendenze rischiano di sfuggire ai controlli di aggiornamento centralizzati o causare conflitti di versione.
    *   `implementation("androidx.appcompat:appcompat:1.6.1")`
    *   `implementation("androidx.exifinterface:exifinterface:1.3.7")`
    *   `implementation("androidx.hilt:hilt-work:1.2.0")`
    *   `ksp("androidx.hilt:hilt-compiler:1.2.0")`
    *   `implementation("sh.calvin.reorderable:reorderable:2.3.2")`
    *   Più le librerie di test (`mockk`, `turbine`).

## 3. File Orfani e Codice Morto
*   Nessun file inutile rilevato nei configuratori. L'approccio `allowBackup="false"` nel manifest è lodevole per un'app che tratta file e logiche sanitarie sensibili, impedendo che i file finiscano nel cloud non criptato di Google Drive.

## 4. Modernizzazione
*   **Centralizzazione Version Catalog:** Tutte le stringhe di dipendenza attualmente "nude" in `build.gradle.kts` devono essere formattate all'interno del file `libs.versions.toml` (es. creando una section `[versions] mockk = "1.13.10"` e la rispettiva definition in `[libraries]`). Successivamente, il file build.gradle andrà aggiornato usando la notazione type-safe `libs.xyz`.

## 5. Piano di Refactoring Step-by-Step
1.  **Refactoring del Version Catalog:**
    *   Aprire `gradle/libs.versions.toml`.
    *   Aggiungere le versioni per `appcompat`, `exifinterface`, `hilt-work`, `reorderable`, `mockk`, e `turbine`.
    *   Aggiungere i bundle associati in `[libraries]`.
2.  **Sostituzione nel build.gradle:**
    *   Rimpiazzare le stringhe hardcoded in `app/build.gradle.kts` con i rispettivi `libs.nome.dipendenza`.
3.  **Approvvigionamento Insets Manifest:**
    *   Nessuna azione strettamente necessaria sul `AndroidManifest.xml`, poiché `adjustResize` reagirà automaticamente se nel Blocco 4 configureremo correttamente `enableEdgeToEdge` e `imePadding()` nella root dell'Activity.

---

# Clean Code & SOLID Red Flags

In qualità di Software Craftsman, l'analisi del progetto rivela che, sebbene l'app adotti librerie moderne (Compose, Flow, Hilt), l'architettura sottostante è carente dal punto di vista dei principi SOLID e della Clean Architecture. Il codice è "funzionante", ma non è testabile in isolamento né manutenibile a lungo termine senza un alto rischio di regressioni.

Ecco le violazioni architetturali profonde identificate:

## 1. Assenza del Layer "Use Case" (Violazione SRP e Fat ViewModels)
I ViewModel (in particolare `BodyMapViewModel` e `MoleDetailsViewModel`) si sono trasformati in "God Classes". Svolgono troppe responsabilità:
- Contattano direttamente i repository multipli.
- Eseguono logica di calcolo spaziale (Spatial Hashing per la collisione dei tap).
- Calcolano l'algoritmo di espansione della timeline storica.
**Impatto:** I ViewModel sono impossibili da testare in isolamento senza mockare intere catene di repository e framework Android. 
**Soluzione Clean:** La logica di business pura deve essere estratta in classi `UseCase` indipendenti dal framework (es. `CalculateTimelineUseCase`, `DetectMoleCollisionUseCase`), iniettate nel ViewModel. Il ViewModel dovrebbe limitarsi a ricevere i dati e mapparli nell'UiState.

## 2. Inquinamento del Framework UI nel Dominio (Violazione Clean Architecture)
Le classi di modello (come `MoleUiModel`) e i ViewModel importano e istanziano primitivi del framework visivo (`androidx.compose.ui.graphics.Color`).
**Impatto:** Il layer di presentazione/dominio è accoppiato a Jetpack Compose. Non è possibile eseguire Unit Test puri sulla JVM senza importare le librerie Android/Compose, rallentando la CI/CD e violando la regola d'oro della Clean Architecture (il centro non deve conoscere i dettagli periferici).
**Soluzione Clean:** I modelli UI devono esportare tipi primitivi puri (es. `String` per l'hex code `"#FF0000"`). La conversione in `Color` deve avvenire esclusivamente al momento del rendering (nei `@Composable`).

## 3. Global State e Violazione dell'Incapsulamento (State Hoisting Abusivo)
Il file `SkinHistoryAppState.kt` viene utilizzato come un contenitore globale di variabili (`var movingMoleId`, `var pendingPhotoPath`) condivise tra schermi scollegati.
**Impatto:** L'accoppiamento tra le rotte di navigazione è invisibile e implicito (Spaghetti State). Uno schermo può modificare una variabile che corrompe lo stato di un altro, rendendo il flusso di navigazione imprevedibile e infrangendo i principi dell'Unidirectional Data Flow (UDF).
**Soluzione Clean:** Usare le potenzialità di Compose Navigation Type-Safe per passare parametri direttamente nelle rotte, oppure sfruttare il `SavedStateHandle` per restituire risultati in modo isolato all'Entry precedente.

## 4. Leak del Data Layer verso la Presentation (Violazione Dependency Inversion)
L'uso di DTO complessi di Room (es. `@Relation` in `MoleWithHistory`) che vengono restituiti direttamente e manipolati dai ViewModel.
**Impatto:** Se cambia lo schema del database (Room), il ViewModel si rompe a cascata. Il ViewModel non dovrebbe sapere nulla di come i dati sono strutturati in SQLite.
**Soluzione Clean:** Il `MoleRepository` deve fungere da vero confine (Boundary). Deve eseguire le query necessarie, assemblare gli oggetti e restituire dei puri Domain Models Kotlin (`Mole`, `HistoryEntry`) slegati da annotazioni Room.

## 5. Dependency Injection Non Testabile (Hardcoded Dispatchers)
Nel progetto sono presenti scope asincroni hardcoded (es. `CoroutineScope(Dispatchers.IO)` o `Dispatchers.Default` chiamati direttamente nei ViewModel o nei repository).
**Impatto:** Questo rende impossibile lo scambio dei Dispatcher durante gli Unit Test. Per testare i ViewModel o gli Use Case in modo deterministico usando il `TestDispatcher`, l'esecuzione fallirà o causerà flaky tests (test instabili).
**Soluzione Clean:** Qualsiasi `CoroutineDispatcher` o `CoroutineScope` deve essere iniettato tramite Hilt usando Qualifier specifici (es. `@IoDispatcher`, `@ApplicationScope`), permettendo di rimpiazzarli con un `StandardTestDispatcher` in fase di test.

## 6. Logica Condizionale in UI (Violazione OCP - Open/Closed Principle)
Il `BodyMapScreen` e altri composable sono farciti di controlli `if (variant.isBuiltIn)` o `when (previewSize)`. 
**Impatto:** Ogni volta che si aggiunge un nuovo tipo di "Vista" o variante anatomica, è necessario modificare file UI complessi, aumentando il rischio di introdurre bug.
**Soluzione Clean:** La risoluzione di questi stati deve avvenire a monte. Il ViewModel dovrebbe mappare la variante direttamente in un identificatore visivo standardizzato o usare il polimorfismo (tramite classi *Sealed*) in modo che la UI si limiti a "disegnare" l'interfaccia prescritta dal modello polimorfico senza processarne la logica condizionale.

---

# Master Audit Clean: Analisi Architetturale Globale "Chrono Map Scanner"

## Executive Summary
L'applicazione "Chrono Map Scanner" maschera profonde fragilità strutturali dietro l'adozione di un moderno tech stack (Compose, Coroutines, Room). La severa ispezione su tutto l'albero del codice ha evidenziato che l'attuale architettura **non scalerebbe in alcun modo al requisito critico di 1000+ elementi con storico multi-foto**.
Esiste un diffuso problema di "Over-computation & Over-rendering": le pipeline del database restituiscono prodotti cartesiani enormi (`@Relation`), i ViewModel manipolano i flussi clonando oggetti in RAM in mappe temporali gigantesche (`cachedTimelineFlow`) e il motore grafico prova a istanziare contemporaneamente migliaia di nodi interattivi Compose per i marker, strozzando la GPU durante lo zoom.
**Verdetto:** Il refactoring deve sradicare l'accoppiamento tra strati e invertire la logica di calcolo: elaborazioni asincrone su richiesta e pre-filtrate a monte (O(1)), e rendering passivo basato sul Level of Detail (LOD).

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

## Piano di Modernizzazione Globale

1. **UDF e Separazione MVI Puro:** Svincolare la navigazione e i dati tramite Type-Safe Routes o `SavedStateHandle`. Disaccoppiare del tutto gli import UI dal Livello Dominio.
2. **Single Responsibility (Use Cases):** I ViewModel obesi (es. Spatial hashing e pipeline temporali in `BodyMapViewModel`) verranno svuotati trasferendo l'elaborazione pesante ad `UseCase` iniettati.
3. **Ottimizzazione Grafica (Culling & Deferring):** Se il `BodyMapScreen` si trova a bassi livelli di zoom (`PreviewSize.COLORED_DOT`), i `MoleMarker` non esisteranno come entità Compose. Tutto sarà disegnato con primitive Canvas (`drawCircle`). Nelle animazioni di sistema, il *read* degli stati fluttuanti verrà relegato rigorosamente all'interno del delegato `DrawScope`.
4. **Edge-to-Edge & Gradle Compliance:** Riconoscimento completo delle barre di sistema (SDK 35) tramite `enableEdgeToEdge()` e migrazione totale delle dipendenze hardcoded (`appcompat`, `mockk`, `turbine`) verso `libs.versions.toml`.

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
