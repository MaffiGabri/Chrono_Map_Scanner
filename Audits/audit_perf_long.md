# Specifica di Refactoring - Blocco 1 (Data Domain, Entities & DAO)

## 1. Diagnosi Architetturale
Il layer dei dati presenta una buona struttura di base con separazione netta tramite DTO (Domain vs Room Entities), ma è minato da gravi inefficienze sulle prestazioni in fase di materializzazione dei dati dal DB. L'uso di `String` per le date all'interno del database, unito a query Room non ottimizzate per le relazioni 1-a-N nel flusso piatto, rende impossibile il mantenimento dei 60fps con 1000+ elementi, generando pesanti "GC Churn" e duplicazioni di payload verso la UI. Inoltre, il layer di Backup mischia modelli di dominio con DTO puri, violando il principio di version-agnosticism.

## 2. Red Flags
*   **Memory/GC Churn in Room Converters (CRITICO):** Il file `Converters.kt` mappa `LocalDate` in `String` usando `LocalDate.parse()`. In una lista di 1000+ elementi, materializzare gli oggetti Room invoca `.parse()` migliaia di volte ad ogni frame aggiornato, causando massicce allocazioni di stringhe e CPU overhead.
*   **Moltiplicazione Cartesiana in `getFlatMolesWithHistory` (CRITICO):** La query `LEFT JOIN history_entries` in `MoleDao` non filtra l'ultimo evento. Se un neo ha 5 foto storiche, la query ritorna 5 righe clonate per quel singolo neo. Con 1000 nei con 3 foto ciascuno, la UI riceverà 3000 `MoleMapDto`, disegnando i marker uno sopra l'altro e saturando la memoria.
*   **Violazione Architetturale `@Relation` Reattiva:** `MoleDao.getMoleByIdWithHistory` restituisce un `Flow<MoleWithHistory?>` (con `@Transaction`). Il documento `ARCHITECTURE.md` vieta esplicitamente i `Flow` reattivi con `@Relation` perché costringono Room a ricalcolare pesanti join in memoria a ogni minima variazione.
*   **Leak di Astrazione nel Backup:** `AppDatabaseDto` (in `BackupModels.kt`) usa le classi di dominio `Mole` e `UserSettings`. Se l'app evolve e la data class di dominio cambia, i backup storici dell'utente non potranno più essere deserializzati in JSON.

## 3. File Orfani e Codice Morto
*   **Nessun file completamente orfano.** Tuttavia, la funzione `BodySide.fromString` in `MoleModels.kt` usa una ricerca iterativa allocativa `.find { it.name.equals... }` in un loop. Deve essere de-allocata e trasformata in un lookup diretto O(1).

## 4. Modernizzazione
*   **Epoch Days Over Strings:** Sostituire le `String` ISO8601 in Room con interi `Long` (Epoch Days). Questo abbatte le allocazioni a zero e rende l'ordinamento in SQL nativo ed efficiente.
*   **Window Functions / Subqueries SQLite:** La query per i DTO piatti deve usare una subquery che garantisca un solo record per neo (quello più recente).
*   **Isolamento Serlialization:** Sganciare `BackupModels` dal dominio introducendo `MoleBackupDto` e `HistoryBackupDto`.
*   **Flow vs Suspend:** Modificare le chiamate one-off di Room per usare `suspend` al posto di `Flow`.

## 5. Piano di Refactoring Step-by-Step
1. **File: `Converters.kt` & `AppDatabaseRoom.kt`**
    *   Modificare `Converters` affinché `toLocalDate` e `fromLocalDate` lavorino su `Long?` (usando `LocalDate.toEpochDay()` e `LocalDate.ofEpochDay()`).
    *   Incrementare la versione del DB in `AppDatabaseRoom` a 5 e preparare la Migration SQL `ALTER TABLE` per convertire le stringhe esistenti in interi.
2. **File: `MoleDao.kt`**
    *   Riscrivere la query `getFlatMolesWithHistory`: 
        ```sql
        SELECT m.id, m.x, m.y, m.variantId, m.color, h.date as historyDate, h.imagePath 
        FROM moles m 
        LEFT JOIN (
            SELECT mole_id, MAX(date) as date, imagePath 
            FROM history_entries 
            GROUP BY mole_id
        ) h ON m.id = h.mole_id 
        WHERE m.profileName = :profile
        ```
    *   Cambiare la firma di `getMoleByIdWithHistory` da `Flow<MoleWithHistory?>` a `suspend fun getMoleByIdWithHistory(moleId: String): MoleWithHistory?`.
3. **File: `BackupModels.kt`**
    *   Rimuovere le importazioni di `Mole` e `UserSettings`.
    *   Aggiungere `MoleBackupDto` e `HistoryEntryBackupDto` esclusive per il file JSON di backup.
    *   Aggiornare `AppDatabaseDto` per usare le nuove classi.
4. **File: `MoleModels.kt`**
    *   Riscrivere `BodySide.fromString` usando un semplice `when (value.uppercase()) { "FRONT" -> FRONT; "BACK" -> BACK; else -> FRONT }`.
# Specifica di Refactoring - Blocco 2 (Repositories, Workers & Background Ops)

## 1. Diagnosi Architetturale
Il layer dei repository separa bene i flussi dati (tramite coroutines `Dispatchers.IO`/`Default`) e i Worker delegati al Backup implementano correttamente il caricamento asincrono e le notifiche. Tuttavia, l'implementazione del sistema di integrità e pulizia dati viola drasticamente le linee guida di Android, introducendo un rischio fatale per l'autonomia della batteria e stabilità dell'app.

## 2. Red Flags
*   **Loop Infinito per lo Scanner di Integrità (CRITICO):** Il file `DataIntegrityScanner.kt` lancia un loop infinito (`while (isActive)`) all'interno di un `CoroutineScope` legato all'application. Questo è un anti-pattern estremo: tiene l'app costantemente "sveglia" consumando batteria, e verrà inesorabilmente ucciso dal sistema operativo (Doze Mode). Il documento `ARCHITECTURE.md` richiedeva l'uso di un **WorkManager periodico**.
*   **Rischio OOM nel Garbage Collector dei File (CRITICO):** Durante il `cleanupOrphanedFiles` (in `DataIntegrityScanner`), l'algoritmo raccoglie gli `activePaths` caricando in RAM l'intera gerarchia dei dati di **tutti i profili** tramite `moleDao.getMolesWithHistory(prof)`. Con 1000+ nei e storico associato, l'istanziazione contemporanea di migliaia di oggetti `MoleWithHistory` saturerà rapidamente la RAM generando un `OutOfMemoryError`.
*   **Duplicazione Logica Bitmap:** La logica complessa e rischiosa per la generazione e rotazione EXIF delle thumbnail (gestione `BitmapFactory`, calcolo aspect ratio, `Matrix` rotation) è hard-coded sia in `FileRepository` che in `DataIntegrityScanner`.

## 3. File Orfani e Codice Morto
*   La classe `DataIntegrityScanner.kt` così com'è va considerata architetturalmente morta e va completamente convertita in un componente WorkManager. La logica ridondante di parsing delle Bitmap va rimossa in favore di un singola utility centralizzata.

## 4. Modernizzazione
*   **Da Singleton Coroutine a WorkManager:** La logica di scansione deve migrare verso un `DataIntegrityWorker` asincrono.
*   **Query DAO Ultra-Leggere per Whitelist:** Sostituire l'ingestione totale degli oggetti Room con una query SQL dedicata che estragga esclusivamente e unicamente la colonna `imagePath` (una `List<String>`), evitando qualsiasi mapping allocativo nel layer di dominio.

## 5. Piano di Refactoring Step-by-Step
1. **File: `DataIntegrityWorker.kt` (Nuovo) / Ristrutturazione Scanner**
    *   Trasformare `DataIntegrityScanner.kt` in un `@HiltWorker class DataIntegrityWorker : CoroutineWorker`.
    *   Rimuovere l'endless loop (`while (isActive)`) e il `delay`. Il worker dovrà eseguire una singola passata sequenziale (scansione + cleanup) e poi restituire `Result.success()`.
2. **File: `SettingsRepository.kt` & WorkManager Enqueue**
    *   Rimuovere le logiche di avvio manuale legate a `startScanning()`.
    *   Creare un metodo in `SettingsRepository` o `MainActivity` che osservi `scannerIntervalMin` e scheduli (tramite `enqueueUniquePeriodicWork`) il nuovo `DataIntegrityWorker`.
3. **File: `MoleDao.kt` (Dal Blocco 1)**
    *   Aggiungere una query pura: `@Query("SELECT imagePath FROM history_entries WHERE imagePath IS NOT NULL") fun getAllActiveImagePathsSync(): List<String>`. Questo preverrà l'OOM durante l'allestimento della whitelist per i file orfani.
4. **File: `FileRepository.kt` e Condivisione Bitmap**
    *   Estrarre la logica di calcolo `inSampleSize`, rotazione EXIF e salvataggio thumbnail in un metodo pubblico (es. `suspend fun generateThumbnail(originalPath: String, destPath: String)`) all'interno di `FileRepository` o di un `ImageUtils`. Farvi affidamento sia dal Worker che da `saveImageFromUri`.
# Specifica di Refactoring - Blocco 3 (ViewModels & UI Models)

## 1. Diagnosi Architetturale
I ViewModel seguono correttamente le linee guida MVI/MVVM: espongono `StateFlow` tramite `combine`, utilizzano `SharingStarted.WhileSubscribed(5000)` e spostano le query pesanti su thread background. Tuttavia, la gestione delle aggregazioni per liste estese è architetturalmente ingenua e inadatta ai volumi richiesti (1000+ imperfezioni), rendendo lo strato UI instabile sotto sforzo.

## 2. Red Flags & Violazioni

*   **Esplosione Allocativa su `BodyMapViewModel.kt` (CRITICO):**
    *   Il flusso `cachedTimelineFlow` converte l'intero dataset storico in una mappa pre-calcolata `Map<LocalDate, List<MoleUiModel>>`. Se ci sono 30 snapshot temporali e 1000 nei, il ViewModel instanzia contemporaneamente 30.000 oggetti `MoleUiModel`, eseguendo in loop `parseColor` e lookup di stringhe. Questo triggererà il Garbage Collector ripetutamente, distruggendo il budget di 16ms/frame per mantenere i 60fps, e portando quasi certamente a OOM.
*   **Logica O(N) Sincrona su Movimenti Trascinamento (Performance):**
    *   In `snapMolePosition()`, l'algoritmo ripercorre iterativamente la lista UI (con N=1000) **ogni frame** in cui l'utente trascina un neo, eseguendo calcoli vettoriali (distanze quadrate) allocando `Pair` e `Float` scatolari temporanei. Questo renderà il drag-and-drop a scatti.
*   **Logica di Business Spuria nei ViewModels:**
    *   `MoleDetailsViewModel` orchestra manualmente l'eliminazione dei file quando l'utente modifica l'immagine associata (`fileRepository.scheduleFileDeletion`).
    *   `SettingsViewModel` lancia coroutine `NonCancellable` in `applicationScope` per orchestrare l'eliminazione a cascata di un intero profilo (Record DB + File su disco).

## 3. Ottimizzazione Pre-calcolo e O(1) Budget
*   **Calcolo Temporale Lazy:** La timeline non deve più pre-calcolare tutte le date. `uiMolesAndCountsFlow` deve derivare lo stato direttamente incrociando lo storico piatto di Room con la SOLA `selectedDate` corrente, instanziando oggetti UI Model strettamente necessari per la vista attuale.
*   **Zero-Allocation Drag:** I calcoli per le collisioni geometriche devono evitare la creazione di oggetti nel loop interno.

## 4. Modernizzazione
*   Introdurre l'astrazione di **UseCases (Interactors)** per le orchestrazioni complesse. Ad esempio, la cancellazione di un profilo deve essere delegata a un `DeleteProfileUseCase` che coordini DAO e disco, liberando `SettingsViewModel` da queste responsabilità.
*   Far confluire la generazione della Thumbnail nell'`ImageEditorViewModel` verso la stessa funzione unica e centralizzata auspicata per il Blocco 2, eliminando l'algoritmo in-place per la compressione JPEG.

## 5. Piano di Refactoring Step-by-Step
1. **File: `BodyMapViewModel.kt` (Refactor Timeline)**
    *   Eliminare del tutto `cachedTimelineFlow`.
    *   Spostare la logica reattiva su `uiMolesAndCountsFlow` combinando direttamente l'`allMolesWithHistoryFlow` con `sampledSelectedDate` (e i colori filtrati). Si mapperanno in `MoleUiModel` solo le entità valide per la data specifica scelta.
2. **File: `BodyMapViewModel.kt` (Refactor Geometrico)**
    *   Riscrivere `snapMolePosition` per utilizzare strutture dati primitive o una logica QuadTree/Grid, ottimizzando l'iterazione sulle distanze. Rimuovere l'allocazione massiva di `Pair` internamente.
3. **File: `MoleDetailsViewModel.kt` & `SettingsViewModel.kt` (Estrazione Business Logic)**
    *   Delegare le logiche "fat" (es. deleteProfile con pulizia a cascata e pulizia immagini obsolete durante update storia) a domain UseCases o all'OfflineMoleRepository, limitando il ViewModel a fare da ponte tra UI ed eventi.
4. **File: `ImageEditorViewModel.kt` (Condivisione Bitmap Utils)**
    *   Sostituire la logica duplicata di scaling/compressione della miniatura in `cropAndSaveImage` facendola invocare la funzione helper che verrà creata in `FileRepository`/`ImageUtils`.
# Specifica di Refactoring - Blocco 4 (BodyMap UI Layer & Rendering)

## 1. Diagnosi Architetturale
Il layer UI per la BodyMap utilizza un approccio misto: un `Canvas` per le fondamenta (puntini base) sovrapposto a uno strato di nodi Compose generati dinamicamente (`MoleMarker`) per le interazioni e le miniature. Questo layer riceve lo stato dal `BodyMapViewModel` e applica trasformazioni (`graphicsLayer`) per il pan e lo zoom.

## 2. Red Flags & Violazioni

*   **Esplosione dei Nodi Compose e OOM di Coil (CRITICO):**
    *   `BodyMapScreen.kt` esegue un loop su `state.moles` e, se la vista non è a puntini, genera un Composable `MoleMarker` per **ogni neo**. Con 1000+ elementi, questo si traduce in 1000 nodi Compose paralleli.
    *   All'interno di ogni `MoleMarker`, un'istanza separata di Coil (`AsyncImage`) prova a caricare in memoria file immagine dal disco concorrentemente. Questo comporterà un picco devastante di allocazione RAM, OOM crash garantito e un crollo totale del framerate (UI Tree Thrashing).
*   **Gestione Gestures e Ricomposizione Costosa:**
    *   Durante il trascinamento (`isMoving`), l'intero stato delle coordinate cambia frame-by-frame, forzando Compose a ricalcolare i bounds di layout di tutti i 1000+ nodi `MoleMarker` adiacenti anziché limitarsi a un ridisegno veloce.

## 3. Ottimizzazione Pre-calcolo e O(1) Budget
*   **Architettura Pure Canvas:** Nessun Composable (eccetto quello in drag) deve essere generato per renderizzare i nei statici. Tutte le miniature devono essere stampate a schermo tramite chiamate imperative nel `drawScope` interno al `Canvas` (`drawImage(imageBitmap)`), bypassando completamente la Composition e il Layout phase.
*   **LRU Image Cache:** L'ingestione indiscriminata di Coil va dismessa. Il layer UI deve poggiare su una cache ristretta (`LruCache` per `ImageBitmap`) pre-gestita dal ViewModel o da un ImageLoader, assicurando che non venga mai superato il budget di RAM a prescindere dal numero di Thumbnail sulla mappa.

## 4. Modernizzazione
*   **Defer State Reads:** Le variabili di stato di pan/zoom (`scale`, `offset`) devono essere lette unicamente all'interno della lambda del `graphicsLayer` o della fase `Draw`, isolando la logica per prevenire ricomposizioni superflue (come indicato dalle best practice di Compose per le performance estreme).

## 5. Piano di Refactoring Step-by-Step
1. **File: `MoleMarker.kt` (Abolizione)**
    *   Rimuovere questo componente (o mantenerlo unicamente come UI overlay esclusivo per l'unico neo attualmente trascinato/selezionato).
2. **File: `BodyMapScreen.kt` (Pure Canvas Rendering)**
    *   Eliminare il loop `state.moles.forEach { MoleMarker(...) }` dalla `Box`.
    *   Spostare l'intera logica di disegno delle thumbnail e dei bordi dentro l'ambiente `Canvas { ... }`.
    *   Ottimizzare la lettura di `scale` e `offset` spostandole esclusivamente nelle primitive compatibili con `DrawScope`.
3. **File: `BodyMapViewModel.kt` (Supporto LRU Cache)**
    *   Aggiungere una struttura dati controllata (es. `LruCache<String, ImageBitmap>`) per caricare proattivamente su thread IO le miniature dei nei della data attiva, fornendo le `ImageBitmap` pronte per il prelievo O(1) da parte del `Canvas`.
# Specifica di Refactoring - Blocco 5 (Secondary UIs)

## 1. Diagnosi Architetturale
Il Blocco 5 gestisce interfacce secondarie (Dettaglio, Settings, Gestione Sfondi e Image Editor) usando Jetpack Compose. In generale l'uso di Layout, Scaffold e Canvas (nell'editor) rispetta le convenzioni di Jetpack Compose. 

## 2. Red Flags & Violazioni

*   **LazyColumn Nidificate in `BackgroundSettings.kt` (CRITICO):**
    *   L'interfaccia per la gestione delle categorie e sfondi contiene una `LazyColumn` annidata all'interno di un'altra `LazyColumn` (tramite `AnimatedVisibility`). Anche se mitigato dall'uso di un `heightIn(max = 300.dp)`, la nidificazione di componenti scrollabili nella stessa direzione è un conclamato anti-pattern di Compose che mina le performance di layout e le gesture di scroll, rischiando frame-drop su liste piene.
*   **Leak di Orchestrazione UI in `MoleDetailsScreen.kt`:**
    *   L'intercettazione del tasto Back (`BackHandler`) e la logica per mostrare l'avviso di "Difetto Vuoto" (`showEmptyWarningDialog`, `warnOnEmptyMoleDeletion`, controllo sull'assenza di `history`) ingombrano il Composable. Questa logica comporta decisioni di business (eliminazione preventiva di un neo) che non dovrebbero risiedere nel layer puramente visivo.

## 3. Ottimizzazione e Sicurezza
*   **Gestione Immagini in `MoleDetailsScreen`:** Nelle liste cronologiche dello storico, assicurarsi che Coil (tramite `AsyncImage`) sia esplicitamente configurato per caricare solo miniature/downsampled (es. `.size()`) piuttosto che il file `File` crudo, per impedire che l'espansione di uno storico di 100+ visite sature il pool di RAM della libreria di image loading.

## 4. Modernizzazione
*   Rimuovere le `LazyColumn` innestate in `BackgroundSettings`, preferendo un flattening della lista o l'espansione delle singole varianti come item dinamici (tramite `items` / `item`) del costrutto genitore.
*   Estrarre il flusso decisionale di uscita e cancellazione silenziosa dal `MoleDetailsScreen` spostandolo in un `onBackRequest` gestito attivamente dal `MoleDetailsViewModel`.

## 5. Piano di Refactoring Step-by-Step
1. **File: `BackgroundSettings.kt` (Flattening Layout)**
    *   Sostituire la `LazyColumn` esterna con un container standard (`Column` + `verticalScroll`), o in alternativa gestire le categorie e le loro relative varianti espanse "spianandole" (`flatten`) a livello logico in modo che esista una sola `LazyColumn` per tutto.
2. **File: `MoleDetailsScreen.kt` (Pulizia Logica Condizionale)**
    *   Rimuovere tutto lo spaccato `showEmptyWarningDialog` e le condizioni interne. Invocare solo una funzione `viewModel.requestExit()` e lasciare che il ViewModel comunichi alla UI se mostrare un alert o navigare via tramite eventi (Single Event Pattern).
3. **File: `MoleDetailsComponents.kt` & Liste (Ottimizzazione Coil)**
    *   Nelle righe che mostrano la timeline o i bottoni, applicare configurazioni restrittive in Coil `ImageRequest.Builder` limitando la larghezza/altezza.
# Specifica di Refactoring - Blocco 6 (Core Setup, DI & Config)

## 1. Diagnosi Architetturale
Il blocco di configurazione principale definisce correttamente lo stack tecnologico (Compose, Hilt, Room, CameraX, WorkManager) e centralizza le dipendenze in `build.gradle.kts`. La configurazione di `WorkManager` con Hilt (`HiltWorkerFactory`) è impostata correttamente nell'Application class.

## 2. Red Flags & Violazioni

*   **Avvio Asincrono Illegale nell'Application Class (CRITICO):**
    *   Nel file `SkinHistoryScannerApplication.kt`, il metodo `onCreate()` invoca `dataIntegrityScanner.startScanning()`. Poiché il design attuale dello scanner (vedi Blocco 2) è un loop infinito, l'avvio globale di questa coroutine lega permanentemente cicli CPU pesanti al processo vitale dell'applicazione, causando battery drain e probabili ANR se lo scope dell'app viene terminato malamente.
*   **Finto Sincronismo WorkManager in `MainActivity`:**
    *   L'importazione del database blocca la UI tramite uno stato `processing` che, anziché reagire allo stato reale del `WorkManager` (`WorkInfo.State`), si affida a un `delay(1000)` fittizio nel ViewModel prima di chiudere il dialogo. Questo comporta UX ingannevole per moli di dati grandi (es. database con 1000 record storici e foto in ZIP) che richiederanno svariati secondi.

## 3. Ottimizzazione e Sicurezza
*   **Memoria e StrictMode:** Mancano policy di controllo per le operazioni bloccanti. In un'app che rischia OOM e I/O pesante, l'inizializzazione dell'app dovrebbe iniettare le regole di `StrictMode` per i log su thread violation (in modalità Debug), rilevando preventivamente accessi a disco errati sul Main Thread.

## 4. Modernizzazione
*   Rimuovere qualsiasi dipendenza da `DataIntegrityScanner` (il Singleton) dalla classe `Application`. L'avvio dei worker periodici (`DataIntegrityWorker`) deve essere dichiarato e schedulato in modo dichiarativo e idempotente (es. tramite un `BootReceiver` o un'inizializzazione app-startup che chiama `WorkManager.enqueueUniquePeriodicWork`).
*   Aggiornare l'Activity o il layer di navigazione per osservare reattivamente i `WorkInfo` del WorkManager tramite `Flow` o `LiveData`, rimuovendo i timer fittizi.

## 5. Piano di Refactoring Step-by-Step
1. **File: `SkinHistoryScannerApplication.kt` (Pulizia Avvio)**
    *   Rimuovere l'iniezione (`@Inject`) di `DataIntegrityScanner` e la chiamata `startScanning()`.
    *   (Opzionale) Introdurre `StrictMode.setThreadPolicy` e `VmPolicy` sotto check `BuildConfig.DEBUG` per rintracciare i memory leak e gli IO bloccanti.
2. **File: `MainActivity.kt` & `SettingsViewModel.kt` (Osservazione Reale)**
    *   Connettere lo stato `isProcessing` e la chiusura della modale di importazione all'osservazione esplicita del `WorkManager` per i tag associati a Import/Export, garantendo un feedback affidabile all'utente.
3. **File: `build.gradle.kts`**
    *   Ripulire eventuali dipendenze inutilizzate (il blocco include già tutte le librerie necessarie). Non sono richieste modifiche radicali, l'infrastruttura di base è solida.
# Performance_RedFlags

> Analisi estrema sulle prestazioni (Performance Engineering) dell'architettura attuale. L'obiettivo è individuare ogni causa di lag, stutter (drop frame sotto i 16ms), Battery Drain e Out Of Memory per garantire reattività glaciale e a 60fps con dataset >1000 record.

## 1. Battery Killers & CPU Hogs (Gravità Massima)

*   **Daemon Infinito di Integrità (`DataIntegrityScanner`):**
    *   **Criticità:** Lanciato in `Application.onCreate()`, questo job esegue un loop `while(isActive)` a livello globale interrogando Room e file system. Anche se contiene dei `delay()`, mantiene l'app costantemente "in allerta", impattando i context switch della CPU, consumando cicli IO e impedendo al dispositivo di entrare correttamente in Doze mode profondo.
    *   **Extreme Fix:** Demolizione totale della classe. Sostituire con `WorkManager` (tramite `PeriodicWorkRequest`) schedulato per girare solo di notte, in ricarica o su rete Wi-Fi non a consumo (`Constraints.Builder()`).

## 2. Heap Bloat & OOM (Out Of Memory) Risks

*   **Collasso da Allocazione Composables (`MoleMarker` + Coil):**
    *   **Criticità:** L'UI genera 1000 Composable `MoleMarker` simultanei. Ognuno innesca una richiesta Coil concorrente per leggere e idratare un file immagine dal disco su 1000 nodi separati. Questo è il peggior anti-pattern immaginabile in Compose. Il runtime esaurirà l'Heap JVM e frammenterà la memoria, provocando Garbage Collection prolungate o un crash `OutOfMemoryError` in pochi millisecondi al caricamento.
    *   **Extreme Fix:** 
        1. Distruzione totale dei nodi Compose per il rendering massivo.
        2. Istituzione di una Memory Cache L1 pre-calcolata rigorosamente dimensionata: `LruCache<String, ImageBitmap>`.
        3. Il ViewModel in IO-thread pre-calcola/estrapola solo i sample in bassa risoluzione e idrata la cache. Il Canvas principale si limita a mappare (in tempo costante O(1)) gli id alle Bitmap pre-pronte.

*   **Storico Dettagli (Image Decode Leak):**
    *   **Criticità:** Il `MoleDetailsScreen` innesca il caricamento asincrono (`AsyncImage`) passando direttamente istanze crude di `File` alle miniature dello storico, senza vincoli espliciti sulle dimensioni massime decodificate in RAM.
    *   **Extreme Fix:** Hard-limit della decodifica tramite i constraint `.size()` di Coil e abilitazione esplicita del `crossfade(false)` in contesti di Lazy scroll, per minimizzare l'overhead dell'animazione su liste molto dense.

## 3. Database & I/O Bottlenecks

*   **Il "Relationship" Overhead di Room (`@Relation`):**
    *   **Criticità:** Il DAO sfrutta annotazioni `@Relation` nidificate per ricostruire il grafo tra Moles e Storico. Sotto il cofano, questo emette query SQLite N+1. All'interno di un flusso reattivo (`Flow`), ogni lieve modifica al DB attiva un effetto a cascata che interroga il disco ripetutamente, distruggendo l'I/O.
    *   **Extreme Fix:** Eliminare la pigrizia architetturale. Creare query piatte `LEFT JOIN` altamente indicizzate (sui campi ID e Timestamp) mappandole in POJO/DTO singoli (Plain Old Java Objects).

*   **String Parsing nel DB (`LocalDate`):**
    *   **Criticità:** Il `Converters.kt` mappa un campo cruciale come la Data usando stringhe ISO-8601 serializzate. Con >1000+ entry per neo, il parser JVM è costretto ad allocare e scartare migliaia di oggetti `String` e `LocalDate` in tempo reale per ogni query.
    *   **Extreme Fix:** Conversione binaria obbligatoria. Convertire tutti i timestamp in `Long` (Epoch Days/Milli). Trasferimento 1:1 dalla CPU al Disco senza allocazioni intermedie di memoria O(1).

## 4. UI Thrashing & Render Lag (Budget 16ms/frame)

*   **Recomposition globale al trascinamento (Dragging):**
    *   **Criticità:** Il calcolo geometrico (`calculateMolePosition` e `snapMolePosition`) attraversa e legge lo stato su tutto il set reattivo. Spostare 1 neo costringe Compose a invalidare e ri-disegnare tutto il grafo UI per capire quali "bounds" siano stati inficiati, scendendo sistematicamente sotto i 60fps.
    *   **Extreme Fix:** "Defer State Reads". Le variabili spaziali globali (pan, offset, zoom) e le coordinate del neo in movimento vanno lette *esclusivamente* dentro la lambda del `drawScope` o tramite `.graphicsLayer { ... }`. Così facendo, Jetpack Compose salterà la fase algoritmica (Composition) e geometrica (Layout) passando istantaneamente alla scheda video (Render phase).

*   **Calcolo Costoso On-Main-Thread:**
    *   **Criticità:** Pre-valutare le `MoleUiModel` (includendo parsing delle dipendenze per 30 date in `TimelineSlider`) causa ricalcoli sincroni pesanti all'inizio dello scope UI.
    *   **Extreme Fix:** Elaborazione off-loaded totale. I ViewModel devono agire da "Pre-Packager". Tutte le trasformazioni/mapping del dominio (Da `Mole` a `MoleUiModel` con relative `Color` parsing) si eseguono nel `Dispatchers.Default` (Worker thread) ed emesse tramite un `StateFlow` congelato e in sola lettura. La UI deve essere "tonta" e non processare nulla, limitandosi a stampare dati perfetti e finiti.

## 5. LazyColumn Nesting Crash
*   **Misurazione Infinita:**
    *   **Criticità:** `BackgroundSettings` annida logiche scrollabili della stessa direzione. Questo provoca, ad un certo livello di nesting, un'eccezione `IllegalStateException` legata al limite infinito dell'altezza.
    *   **Extreme Fix:** Flattening totale dei nodi Lazy. Usare blocchi dinamici iterativi (es. singola LazyColumn con header/items mischiati logici) anziché widget impilati.
