# Report Final - Audit & Refactoring Spec Blocks

Questo report unifica le specifiche di refactoring dettagliate per tutti i 6 blocchi del progetto "Nei Map".

---

# Specifica di Refactoring: Blocco 1 (Data & Entity Layer)

## 1. Diagnosi Architetturale
Il layer dei dati si fonda su Room Database e definisce le tabelle per nei, storia clinica e varianti di background. L'impostazione generale rispetta la regola d'oro descritta nell'`ARCHITECTURE.md`: l'uso di query piatte via `LEFT JOIN` (`getMolesAtDate`) per alimentare la mappa, prevenendo il GC Churn. 
Tuttavia, emergono debolezze strutturali critiche per la scalabilità a lungo termine e per l'integrità referenziale, in particolare l'assenza di un'entità per i Profili.

## 2. Red Flags
*   **Assenza di un `ProfileEntity` (Stringly Typed):** I profili utente non esistono come tabelle, ma sono semplici stringhe (`profileName`) duplicate in `MoleEntity` e `BackgroundCategoryEntity`. L'aggiornamento/rinominazione è fragile (`renameProfile` e `renameProfileInCategories`) e l'assenza di `ForeignKey` con `ON UPDATE CASCADE` espone a inconsistenze.
*   **Type mismatch implicito su `MoleEntity.variantId`:** Nelle conversioni di dominio (es. `MoleEntity.toDomain()`), il campo `variantId` viene mappato forzatamente a `side` (stringa "FRONT" o "BACK"). Questo crea ambiguità semantica: un id di variante non è necessariamente un lato anatomico.
*   **Mancanza di indici composti critici:** In `MoleDao.kt`, la query `getMolesAtDate` esegue una subquery su `history_entries` filtrando per `mole_id` e `date`. Nonostante ci siano indici singoli, un indice composto `[mole_id, date]` su `HistoryEntryEntity` renderebbe la query SQL $O(\log N)$ pura anche con centinaia di migliaia di entry.
*   **Assenza di Migrations:** `AppDatabaseRoom.kt` ha `version = 5` ma non sono definite o esportate le policy di migrazione visibili.

## 3. File Orfani e Codice Morto
*   **Nessun file orfano immediato.** Tuttavia, `BodySide` in `MoleModels.kt` è fortemente accoppiato a "front" e "back", un retaggio hardcoded in contrasto con la dinamicità suggerita da `BackgroundVariantEntity`.

## 4. Modernizzazione
*   **Database Normalization:** Creare la tabella `ProfileEntity` (id, name, avatar) e trasformare `profileName` in `profileId` nelle tabelle figlie con `ForeignKey(ON UPDATE CASCADE, ON DELETE CASCADE)`.
*   **Ottimizzazione Subquery SQL:** Utilizzare le Window Functions (`ROW_NUMBER() OVER (PARTITION BY mole_id ORDER BY date DESC)`) disponibili in SQLite 3.25+ per sostituire la pesantissima subquery `MAX(date)` nella funzione `getMolesAtDate`, garantendo fluidità a 60FPS per 1000+ marker in una sola iterazione del DB.

## 5. Piano di Refactoring Step-by-Step
1.  **[NEW] `ProfileEntity.kt`:** Creare l'entità per il profilo.
2.  **[MODIFY] `MoleEntity.kt` & `BackgroundCategoryEntity.kt`:** Sostituire `profileName: String` con `profileId: String` e aggiungere la clausola `@ForeignKey(entity = ProfileEntity::class)`.
3.  **[MODIFY] `HistoryEntryEntity.kt`:** Modificare gli `@Index` per creare un indice composto `Index(value = ["mole_id", "date"], unique = true)`.
4.  **[MODIFY] `MoleDao.kt`:** 
    *   Sostituire la subquery in `getMolesAtDate` implementando una query con Window Functions o appoggiandosi rigorosamente al nuovo indice.
    *   Rimuovere i metodi manuali `renameProfile`, delegando la coerenza alle foreign keys (Cascade).
5.  **[MODIFY] `MoleModels.kt`:** Separare la semantica di `variantId` e `side`. `MoleEntity` deve esplicitare a quale variante appartiene il marker, senza confliggere con vecchi Enum hardcoded.

---

# Specifica di Refactoring: Blocco 2 (Repositories & Workers)

## 1. Diagnosi Architetturale
Il layer dedicato alla gestione I/O, ai Worker in background e all'astrazione dei dati (Repository) impiega in modo pulito gli standard moderni (WorkManager, Coroutines, DataStore). Tuttavia, si evince una discrepanza gravissima tra l'obiettivo di scalabilità a 1000+ marker e l'implementazione pratica del `DataIntegrityWorker`, che viola le limitazioni di memoria e tenta di simulare logicamente vincoli relazionali che andrebbero delegati al Database (SQLite).

## 2. Red Flags
*   **Out Of Memory (OOM) Fatale nel Worker:** In `DataIntegrityWorker`, l'istruzione `val moles = moleDao.getMolesWithHistory(profile)` materializza sincronicamente *tutti* i nei e *tutta* la cronologia del profilo in una singola lista RAM. Con 1000+ nei (ciascuno con foto multiple), l'allocazione massiva saturerà l'heap, eludendo il presunto throttling implementato con `delay(delayBetweenMolesMs)`.
*   **Integrità Referenziale Emulata in RAM:** Il worker controlla esplicitamente se `validVariantIds.contains(mole.mole.variantId)`. Qualora mancasse, elimina il neo. Questo è un anti-pattern catastrofico: la cancellazione a cascata deve avvenire istantaneamente e transazionalmente tramite `@ForeignKey(ON DELETE CASCADE)` nel DB, senza aspettare un worker asincrono.
*   **Problema Query N+1 su DataStore:** Nel ciclo `cleanupOrphanedFiles`, viene chiamato `settingsRepository.getProfileImageForProfile(prof).first()` all'interno di un `for`. Leggere dal DataStore in loop sequenziale rallenta inutilmente il thread I/O.
*   **Esportazione Zip a Rischio:** `BackupRepository.createAndWriteExportZip` raccoglie tutti i `path` stringa e cerca di comprimerli. Se profili diversi condividessero un'immagine (es. default), o ci fossero inconsistenze nei nomi, la libreria Zip potrebbe lanciare eccezioni di file duplicato.

## 3. File Orfani e Codice Morto
*   Metà della logica di `DataIntegrityWorker.kt` (check di orfanotrofio per profili e varianti inesistenti) diventerà **codice ridondante (morto)** non appena il refactoring del Blocco 1 (normalizzazione tabelle Room con Foreign Keys) entrerà a regime.

## 4. Modernizzazione
*   **Paginazione / Chunking Batch:** L'accesso al DB per controlli in background massivi deve essere tassativamente impaginato tramite costrutti `LIMIT` e `OFFSET` in Room, elaborando, ad esempio, blocchi da 50 entità per volta, permettendo al Garbage Collector di recuperare memoria tra i delay.
*   **Isolamento I/O per i File:** Sfruttare appieno la potenza di `java.nio.file` (se SDK 26+) per la lista massiva e rapida dei file invece della vecchia API `File.listFiles()`.
*   **Delegate-to-SQL:** Trasferire l'obbligo di controllo integrità al DB. Il worker dovrà occuparsi *unicamente* di sincronizzare lo storage fisico (File System) con le verità incise su Room, non di aggiustare le discrepanze relazionali.

## 5. Piano di Refactoring Step-by-Step
1.  **[MODIFY] `MoleDao.kt` (Blocco 1/2):**
    *   Aggiungere una query paginata: `suspend fun getMolesWithHistoryPaged(profile: String, limit: Int, offset: Int): List<MoleWithHistory>`.
2.  **[MODIFY] `DataIntegrityWorker.kt`:**
    *   **Rimozione:** Eliminare totalmente la logica manuale di controllo delle varianti (`!validVariantIds.contains(...)`), demandandola al DB.
    *   **Implementazione Chunking:** Sostituire il caricamento totale con un loop `while (true)` che estrae batch da 50 nei con offset progressivo.
    *   **Ottimizzazione Loop:** Estrarre la lettura di `profileImage` e altri Settings fuori dal loop dei profili.
3.  **[MODIFY] `SettingsRepository.kt`:**
    *   Esporre un flusso/metodo combinato per restituire un dizionario `Map<String, String?>` (Profilo -> ImagePath) per abbattere l'N+1 del Worker.
4.  **[MODIFY] `BackupRepository.kt`:**
    *   Implementare un controllo stringente di univocità all'interno di `createAndWriteExportZip`, assicurando che la mappa di file da zippare non contenga chiavi duplicate prima dello stream.

---

# Specifica di Refactoring: Blocco 3 (ViewModels & DI)

## 1. Diagnosi Architetturale
I ViewModels (in particolare `BodyMapViewModel` e `SettingsViewModel`) orchestrano una notevole quantità di logica reattiva basata su Flow, rispettando in larga parte le direttive di `ARCHITECTURE.md` (uso di Tuple intermedie per evitare cast unchecked con >5 Flow e limitazione della logica sul Main Thread). La genialità architettonica dello *Spatial Hashing* per il rilevamento dei tocchi ($O(1)$) è implementata e thread-safe. Purtroppo, l'infrastruttura frana clamorosamente sulla gestione del rendering delle immagini e sulle allocazioni di string parsing, introducendo GC churn massiccio.

## 2. Red Flags
*   **Thread Exhaustion (Caricamento Thumbnails):** La funzione `getThumbnail(path)` in `BodyMapViewModel` lancia una nuova coroutine `Dispatchers.IO` per **ogni cache-miss**. Essendo presumibilmente richiamata dai componenti Compose per ogni marker visibile, quando ci sono 1000 nei su schermo verranno lanciate ~1000 coroutines simultanee al primo frame, causando Thread Starvation, picchi termici e potenziale ANR. È un gravissimo anti-pattern. Coil esiste appositamente per risolvere questo problema con pooling e richieste cancellabili.
*   **Pre-calcolo on-the-fly Inefficiente:** All'interno di `uiMolesAndCountsFlow`, per ogni marker (1000+ iterazioni a ogni emissione), l'app esegue la pesantissima `android.graphics.Color.parseColor(hexColorString)` allocando nuovi oggetti `Color` Compose. Questo genera un Garbage Collection Churn letale (minimo 1000 string-parsing e oggetti a ogni tocco/selezione colore).
*   **Collision Detection Lineare:** Mentre il tap detection usa lo Spatial Hashing, il metodo `snapMolePosition` esegue una ricerca lineare $O(N)$ in un loop `while (attempts < 10)`. Con 1000 elementi e scenari densi, equivale a ~10.000 iterazioni non necessarie, sprecando budget CPU.
*   **StateFlow Anti-pattern in SettingsViewModel:** Il metodo `updateProfileInfo` muta la dipendenza attiva ma aspetta implicitamente che le coroutines lo risolvano. Non fatale ma fragile.

## 3. File Orfani e Codice Morto
*   **Logica custom di caricamento Immagini:** Il blocco `thumbnailCache` (LruCache) e la mappa `loadingThumbnails` in `BodyMapViewModel` è codice fondamentalmente "morto" o altamente deprecato: deve essere completamente eradicato in favore di Coil (come peraltro suggerito da `ARCHITECTURE.md`).

## 4. Modernizzazione
*   **Demandare a Coil (UI):** `BodyMapViewModel` non deve mai gestire Bitmap native né LruCache. Deve restituire stringhe di path o `Uri`. I marker UI in Compose useranno `AsyncImage` di Coil, che gestisce internamente thread pool, caching L2 (memoria) e L3 (disco) senza impattare il ViewModel.
*   **Caching dei Colori:** Sostituire il parsing dinamico delle stringhe esadecimali tramite una mappa di conversione statica pre-compilata, dato che l'app offre un set finito di colori (`ColorSetting`), riducendo la complessità di calcolo a $O(1)$ memory-lookup.
*   **Spatial Hashing Universale:** Espandere l'utilizzo della griglia spaziale (già presente per i tap) per risolvere le collisioni in `snapMolePosition`.

## 5. Piano di Refactoring Step-by-Step
1.  **[MODIFY] `BodyMapViewModel.kt`:**
    *   Cancellare interamente `thumbnailCache`, `loadingThumbnails`, `_cacheUpdateTrigger` e `getThumbnail()`.
    *   Ottimizzare `uiMolesAndCountsFlow`: Mappare preventivamente i colori esadecimali agli oggetti `Color` Compose usando un dizionario `Map<String, Color>`. Sostituire `Color.parseColor` con una lookup veloce.
    *   Applicare lo *Spatial Hashing* anche alla logica interna di `snapMolePosition`.
2.  **[MODIFY] `BodyMapUiState.kt` / `MoleUiModel.kt` (Blocco 4):**
    *   Assicurarsi che la UI riceva esclusivamente i `path` delle thumbnail per delegarli asincronamente a Coil nel layer Compose.

---

# Specifica di Refactoring: Blocco 4 (UI Compose & States)

## 1. Diagnosi Architetturale
Il layer UI utilizza adeguatamente Jetpack Compose, con l'uso corretto di `graphicsLayer` in `BodyMapScreen` per scaricare le trasformazioni di pan/zoom sulla GPU, evitando ricomposizioni inutili. Tuttavia, l'implementazione pratica del rendering della mappa viola clamorosamente i vincoli prestazionali dei 60fps, introducendo operazioni bloccanti (I/O proxying) all'interno del ciclo di disegno (DrawScope) e allocazioni massicce continue in background (Camera).

## 2. Red Flags
*   **Violazione Letale del DrawScope (Side-Effects in Canvas):** In `BodyMapScreen.kt`, all'interno del loop del `Canvas` (che deve essere puro, sincrono e istantaneo), viene effettuata la chiamata `getThumbnail(mole.latestPhotoPath)`. Questa funzione istruiva il ViewModel a scatenare coroutines di I/O per il caricamento da disco. Scatenare operazioni I/O o side-effects dalla fase di disegno causa un disastroso collo di bottiglia e frame-drop massicci (Jank) quando si renderizzano decine/centinaia di marker.
*   **Saturazione GPU da `clipPath`:** Eseguire 1000 operazioni `clipPath` all'interno del Canvas per mascherare circolarmente i Bitmap distrugge il budget di 16ms del frame. `clipPath` rompe il batching delle draw call hardware su vecchie versioni e rallenta enormemente la pipeline di rendering 2D se abusato in loop ampi.
*   **Assenza Totale di Culling:** Il `Canvas` disegna brutalmente tutti i 1000 nei anche se l'utente ha ingrandito (zoom) una zona infinitesimale dello schermo (viewport). La CPU elabora geometria inutile fuori schermo.
*   **Allocazioni di Memoria a 30fps (AutoCameraScreen):** Nel blocco `ImageAnalysis`, la conversione `imageProxy.toBitmap()` traduce l'intero frame YUV in un enorme Bitmap RGBA ad ogni step analizzato. Questo causa una congestione tremenda del Garbage Collector (GC Churn), riscaldando il dispositivo (termal throttling) prima ancora di scattare la foto.

## 3. File Orfani e Codice Morto
*   La funzione `getThumbnail` passata come parametro lambda alla Screen diventa concettualmente obsoleta alla luce della direttiva di "Pre-calcolo obbligatorio".

## 4. Modernizzazione
*   **Passività Assoluta della UI:** La UI deve ricevere un `MoleUiModel` in cui i tipi pesanti (`Color` e `ImageBitmap`) sono **già pre-calcolati** e materializzati in memoria dal ViewModel (che se ne occuperà in background thread). Il Canvas dovrà fare null'altro che accedere alla proprietà ed eseguire una draw-call $O(1)$ immediata.
*   **Rendering O(1) con BitmapShader:** Per arrotondare le immagini, invece del distruttivo `clipPath`, i `Bitmap` andranno wrappati (durante il pre-calcolo) in un `ImageShader`/`BitmapShader`, consentendo di usare una singola `drawCircle` che l'hardware disegnerà alla massima velocità.
*   **Cropping Selettivo (Camera):** `AutoCameraScreen` deve estrarre/convertire solo la porzione centrale (`cropRect`) dei pixel YUV richiesti dall'algoritmo ML, non l'intero frame, riducendo l'allocazione memoria del 90%.

## 5. Piano di Refactoring Step-by-Step
1.  **[MODIFY] `MoleUiModel.kt`:**
    *   Aggiungere una proprietà `val thumbnailBitmap: ImageBitmap?` che conterrà l'asset pronto all'uso, scaricando la UI dall'onere del fetch.
2.  **[MODIFY] `BodyMapScreen.kt`:**
    *   Rimuovere la lambda `getThumbnail`.
    *   Rimuovere `clipPath`. Usare direttamente `thumbnailBitmap` fornito dal modello, se presente, oppure un fallback vettoriale.
    *   Aggiungere logica di **Viewport Culling** base: calcolare il rettangolo visibile (basato su `scale` e `offset`) ed eludere il disegno dei nei con coordinate escluse dal bounding box.
3.  **[MODIFY] `BodyMapViewModel.kt` (Blocco 3 - Aggiornamento Collegato):**
    *   Il flusso reattivo asincrono deve popolare `thumbnailBitmap` leggendolo via Coil (usando `ImageLoader.execute` o cache) sul thread I/O *prima* di emettere lo stato.
4.  **[MODIFY] `AutoCameraScreen.kt`:**
    *   Alterare l'`ImageAnalysis.Analyzer` affinché utilizzi un `RenderScript` o le API `YuvToRgbConverter` moderne per tagliare (crop) e convertire unicamente l'area centrale d'interesse 200x200, abbattendo l'allocazione di Bitmap colossali.

---

# Specifica di Refactoring: Blocco 5 (UI Components & Navigazione)

## 1. Diagnosi Architetturale
I componenti atomici della UI e la navigazione (NavGraph) sono nel complesso ben strutturati, adottando i moderni standard di Compose (Type-safe Navigation, `collectAsStateWithLifecycle`, Coil per i caricamenti asincroni tramite `AsyncImage`). Tuttavia, permangono delle scorie di sviluppo e infrazioni della regola di passività assoluta della UI, specialmente per quanto riguarda i calcoli eseguiti in tempo di ricomposizione.

## 2. Red Flags & Anti-Pattern
*   **Logica di Dominio e Temporale nella UI (`DateHeader.kt`):** Il componente `DateHeader` istanzia `LocalDate.now()` e calcola il delta dei giorni tramite `ChronoUnit.DAYS.between` direttamente nel blocco Composable. Questo comporta due problemi:
    1.  Testabilità nulla: il componente è accoppiato al clock di sistema.
    2.  Violazione della Passività: queste elaborazioni (es. determinare la stringa "Ieri" o "Oggi") spettano esclusivamente al ViewModel/Mapper, che deve fornire una semplice stringa già formattata.
*   **Parsing On-the-fly (`MoleLegend.kt`):** Il colore dei nei viene decodificato tramite `android.graphics.Color.parseColor(setting.hex)` direttamente nel Composable. Sebbene veloce, infrange la direttiva di "pre-calcolo": i tipi complessi UI (come `androidx.compose.ui.graphics.Color`) devono provenire già mappati dal ViewModel, al fine di evitare overhead o eccezioni a runtime (in caso di hex malformato) durante il rendering del frame.
*   **Codice Morto (Zombie Code):** Il file `MoleMarker.kt` è importato in `BodyMapScreen` ma non viene **mai istanziato**. Si tratta chiaramente di un residuo di un'architettura passata (quando i marker erano forse N Composable separati prima di passare al `Canvas` unificato). Genera confusione e deve essere eliminato.
*   **Mancata Estrazione delle Risorse:** All'interno di `BackgroundSettings.kt` e `VariantManagementBottomSheet.kt` sono presenti numerose stringhe hardcoded (es. "Modello Sfondo", "Pagine di Sfondo", "Gestisci Varianti") invece di usare `stringResource(R.string.*)`. Questo blocca la localizzazione dell'app.

## 3. Punti di Forza (Mantenere)
*   **Navigazione Type-Safe:** L'uso corretto di kotlinx.serialization per le Rotte (`BodyMapRoute`, ecc.) garantisce sicurezza al compile-time, un ottimo pattern.
*   **Uso di Coil (`AsyncImage`):** L'utilizzo intensivo di `AsyncImage` all'interno di `HistoryItem` e `BackgroundSettings` permette un eccellente decoupling dalla logica asincrona e un'ottima gestione della cache, in netto contrasto con i problemi presenti in `BodyMapScreen`.
*   **Mini-Viewport Canvas:** Il componente `MoleSummaryHeader` implementa correttamente un rendering $O(1)$ utilizzando un `Canvas` per sovrapporre il mirino sulla mappa traslata. È un pattern estremamente efficiente.

## 4. Piano di Refactoring Step-by-Step
1.  **[DELETE] `MoleMarker.kt`:**
    *   Eliminare fisicamente il file e rimuovere l'import inutilizzato da `BodyMapScreen.kt`.
2.  **[MODIFY] `MoleLegend.kt`:**
    *   Il parametro `colorSettings` (proveniente dal ViewModel) deve essere pre-calcolato in un nuovo modello `ColorSettingUiModel` che esponga direttamente un `val composeColor: Color`, eliminando la necessità del blocco `try/catch` con `parseColor` nel Composable.
3.  **[MODIFY] `MoleDetailsComponents.kt` (`DateHeader`):**
    *   Rimuovere le dipendenze al tempo (`LocalDate.now()`) dal componente `DateHeader`. Il componente deve ricevere semplicemente due parametri testuali: `val dateText: String` (es. "12 Maggio 2024") e `val relativeTimeText: String` (es. "Oggi"). Il ViewModel si occuperà di questa formattazione.
4.  **[MODIFY] Componenti Minori (`VariantManagementBottomSheet`, `BackgroundSettings`):**
    *   (Opzionale ma raccomandato) Spostare tutte le stringhe di interfaccia hardcoded nel file `strings.xml` per la localizzazione.

---

# Specifica di Refactoring: Blocco 6 (Root, Utils & Application)

## 1. Diagnosi Architetturale
Il blocco finale delinea il cablaggio dell'applicazione. Sono presenti ottime pratiche di sicurezza (es. la protezione "Zip Slip" in `ZipUtils.kt` è da manuale). Tuttavia, a livello di configurazione globale e logica "Core", ci sono gravissime problematiche di gestione energetica (Battery Drain) e di allocazione della memoria.

## 2. Red Flags & Anti-Pattern Letali
*   **Battery Drain Ingiustificabile (`SkinHistoryScannerApplication.kt`):** Il `DataIntegrityWorker` viene schedulato con una periodicità di 15 minuti (`15, TimeUnit.MINUTES`). Considerato che nel Blocco 2 abbiamo già appurato come questo Worker esegua un fetch totale ($O(N)$) di tutto il DB e iteri su migliaia di file per verificarne l'integrità, eseguirlo in background ogni 15 minuti è un suicidio prestazionale. L'app verrà segnalata da Android Vitals e disattivata dal Doze Mode.
*   **Allocazione Spazzatura Continua (`AlgorithmicMoleDetector.kt`):** L'algoritmo rileva un neo analizzando l'istogramma del crop centrale. Pur essendo ben ingegnerizzato (Otsu Thresholding), l'interfaccia obbliga la UI a passargli un gigantesco oggetto `Bitmap`. Allocare un Bitmap RGBA 1080x1920 a 30fps per estrarne solo un quadrato 300x300, per poi scartarlo 30 volte al secondo, distrugge la heap memory (causando surriscaldamento da GC Churn).
*   **Dipendenze Inutilizzate (`MainActivity.kt`):** La classe inietta `AppDatabaseRoom` (`@Inject lateinit var database`) ma non la usa mai. In Hilt/Dagger, iniettare un singleton a livello di Activity quando non serve comporta un ritardo nel tempo di avvio dell'Activity (TTR - Time to Render) e viola il principio di incapsulamento.

## 3. Punti di Forza
*   **Sicurezza Archivi (`ZipUtils.kt`):** La prevenzione del path traversal (Zip Slip Vulnerability) è un tocco di classe (Senior level).
*   **Gestione Ambientale (`AndroidManifest.xml`):** L'uso corretto di `android:allowBackup="false"` evita saturazioni ingiustificate dello spazio Drive dell'utente per via delle foto pesanti.

## 4. Piano di Refactoring Step-by-Step
1.  **[MODIFY] `SkinHistoryScannerApplication.kt` (Ottimizzazione Background):**
    *   Rimuovere la schedulazione periodica fissa a 15 minuti.
    *   Spostare il `DataIntegrityWorker` su base settimanale (7 giorni) e agganciarlo a vincoli rigorosi di sistema (Constraints) tramite `Constraints.Builder()`:
        *   `setRequiresCharging(true)`
        *   `setRequiresDeviceIdle(true)`
2.  **[MODIFY] `AlgorithmicMoleDetector.kt` (Zero-Allocation ML):**
    *   L'algoritmo non deve più accettare un `Bitmap` ma un buffer YUV nativo (es. il piano di luminanza `ImageProxy.planes[0].buffer`) o, in alternativa, gestire le API CameraX per l'analisi nativa in YUV. Questo permetterà di estrarre i pixel del crop centrale senza alcuna allocazione per frame, abbattendo la latenza e stabilizzando i 60fps/30fps termici.
3.  **[MODIFY] `MainActivity.kt`:**
    *   Rimuovere `@Inject lateinit var database` e i relativi import inutilizzati per pulire il lifecycle.
