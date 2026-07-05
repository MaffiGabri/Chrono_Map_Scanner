# Architettura e Linee Guida di Sviluppo (Per AI Gemini)

> **NOTA IMPORTANTE (Living Documentation):** Questo documento è in continua evoluzione e riflette l'effettiva implementazione (state of the art as-is) del codice, non una specifica rigida calata dall'alto. L'implementazione reale nel codice è l'UNICA "Single Source of Truth" (SSOT). Eventuali funzionalità future o sperimentali sono chiaramente etichettate come tali.


Questo documento serve a te, AI Gemini, per mantenere il contesto architetturale di `Skin History Scanner` in sessioni future. Descrive le convenzioni, l'architettura dei dati e il design pattern adottato, in particolare riguardo alle performance richieste per gestire 1000 nei tra fronte e retro.

## 1. Pattern Architetturale e UI
L'app utilizza un pattern **MVI (Model-View-Intent) ibrido con MVVM**:
*   **StateFlow e UI State:** Ogni schermata (es. `BodyMapScreen`, `MoleDetailsScreen`) ha una data class `*UiState` dedicata che incapsula interamente lo stato. La vista Compose osserva lo stato tramite `collectAsStateWithLifecycle()`.
*   **Gestione degli Eventi:** La UI non compie calcoli né gestisce logiche di business. Intercetta gli eventi dell'utente e li delega al `ViewModel` (Intents).
*   **Rimozione Calcoli dalla UI:** *Nessun loop intensivo* deve mai essere inserito in funzioni `@Composable` (es. ordinamento liste, conversioni di colori esadecimali, parsing di nomi file). Tutte le mappature avvengono su thread secondari (`Dispatchers.Default`) all'interno del ViewModel, in modo che la UI riceva payloads puramente visivi pronti per il rendering.
*   **Gestione Touch Globale (Zero Overhead):** Nodi figli complessi (es. `MoleMarker`) non istanziano `pointerInput` per catturare i click. I tap vengono catturati globalmente a livello radice (Canvas parent) e delegati alla matematica del ViewModel.

## 2. Gestione Dati e Performance (Target: 1000 Nei con foto)
L'app è esposta a un forte rischio di "GC Churn" e frame-drop a causa dell'alta densità di elementi su schermo.

### Room Database e Reattività
*   Il database SQLite/Room è costituito da due entità principali collegate da una relazione 1-A-Molti:
    1.  `MoleEntity`: Dati geografici (x, y) e metadati statici del neo.
    2.  `HistoryEntryEntity`: Gli eventi storici legati al neo (date, percorsi file immagine, note).
*   **Anti-Pattern Evitato:** Non restituire mai dal DAO una `Flow<List<MoleWithHistory>>` per l'intera collezione dei 1000 nei. Questo forzerebbe Room a ricalcolare un'enorme join in memoria tramite `@Relation` ogni volta che una qualsiasi foto viene aggiunta. L'uso di `@Relation` è vietato per alimentare la UI.
*   **Soluzione Adottata (UI vs Backup):** La `BodyMapScreen` deve utilizzare DTO 'piatti' e `LEFT JOIN` (es. `getFlatMolesWithHistory`) esposti tramite `Flow`. Al contrario, l'uso di `@Relation` (caricamento entità complete nidificate) è **ammesso e raccomandato ESCLUSIVAMENTE** per query `suspend` one-off (non reattive), come l'esportazione di Backup JSON o il caricamento di un singolo neo in `MoleDetailsScreen`.

### I/O su Disco e Immagini
*   **Coil e File paths:** Coil gestisce il caricamento asincrono. Passa a Coil percorsi stringa o Uri; non forzare l'UI a fare controlli espliciti come `File(path).exists()`.
*   Tutti i controlli su file system vanno delegati a un `FileRepository` dedicato iniettato nei ViewModels ed eseguito nel context `Dispatchers.IO`.
*   **Storage Leak Prevention:** Ogni eliminazione dal Database (singolo neo, cronologia, intero profilo) delega la distruzione fisica delle foto `.jpg` associate a un sistema `WorkManager` (via `OfflineMoleRepository`), prevenendo saturazione del disco e garantendo che file orfani vengano asincronamente spazzati via.

### Integrità Dati e Pulizia in Background (Scanner)
*   **Background Integrity Scanner:** È prevista una logica di auto-pulizia del database per evitare che entità non più valide (es. nei rimasti senza storico o foto) rimangano "appese". Questo processo è configurabile (frequenza e delay) per ottimizzare il consumo di batteria, delegando il compito a un processo asincrono (es. WorkManager periodico) che controlla eventuali mismatch tra record Room e file fisici.

### Ciclo di vita dei Nei Vuoti (Safety Interceptor)
*   **Prevenzione Eliminazione Silente:** Per evitare frustrazione utente, l'app intercetta sempre le azioni distruttive silenti (es. uscire da un neo appena creato senza salvare foto/note, oppure cancellare l'ultimo elemento storico di un neo esistente). L'architettura impone di avvisare l'utente (`AlertDialog` condizionato dallo stato `warnOnEmptyMoleDeletion`) prima di consentire un'azione che causerà la rimozione dell'entità genitore a causa delle policy di pulizia automatica.

## 3. Modulo Import/Export (Backup)
L'architettura prevede la possibilità di estrarre interamente i dati dell'utente per scopi di backup o condivisione sicura.
*   **Implementazione:** Gestito tramite `ExportDatabaseWorker` e `ImportDatabaseWorker` (WorkManager).
*   **Specifica Formato:** Il backup consiste in un file archivio `.zip` che contiene:
    1.  Un file `database.json` indipendente dallo schema (version-agnostic) che descrive impostazioni, nei e cronologia.
    2.  La directory interna delle foto dell'app contenente sia le foto in alta risoluzione che le thumbnail.
*   **Workflow:** Durante l'import, i worker analizzano il JSON e utilizzano Room per effettuare l'upsert (inserimento o sovrascrittura) dei record.
    *   **Modalità OVERWRITE:** I dati correnti del profilo attivo vengono sovrascritti.
    *   **Modalità NEW_PROFILE:** L'app genera nuovi UUID casuali per i nei e la cronologia, creando un clone perfetto del backup come profilo separato, senza interferire con i profili già esistenti sulla stessa istanza SQLite. Inoltre, i file fisici estratti vengono **rinominati istantaneamente utilizzando i nuovi UUID** (`img_<uuid>.jpg`), isolando per sempre il destino dei cloni da quello degli originali.
    *   **Modalità OVERWRITE:** L'importazione ripulisce fisicamente e in anticipo l'intero database locale del profilo attivo prima di sovrascriverlo, prevenendo il deposito di dati orfani.

## 4. Ottimizzazioni Geometriche e Touch (Spatial Hashing)
*   Per gestire 1000+ marker fluidamente, il calcolo della hitbox di tocco abbandona l'iterazione classica $O(N)$ o i bounding box standard di Compose.
*   **Spatial Hashing su Griglia:** Il `BodyMapViewModel` smista proattivamente i nei in background all'interno di una griglia spaziale (es. 10x10 buckets). Quando avviene il tocco, la logica matematica localizza il secchio (bucket) intercettato ed analizza **esclusivamente i suoi vicini 3x3** (gestendo i bordi in modo perfetto).
*   Questo abbatte la complessità di calcolo dei tap a massimo ~9 elementi valutati con pura Euclidea su Dispatchers.Default, garantendo frame lock a 60FPS.

## 5. Rilevamento Automatico con Fotocamera (Sperimentale)
*   È predisposta l'esistenza di `AutoCameraScreen` che sfrutta CameraX con l'Analyzer (ImageAnalysis).
*   **Design attuale:** Utilizza un approccio algoritmico basato sull'istogramma in scala di grigi e sul thresholding di Otsu (`AlgorithmicMoleDetector`) per analizzare un ritaglio centrale dell'immagine e scattare in presenza di forte contrasto.
*   *Nota per il futuro:* L'implementazione di un modello ML Kit (es. object detection on-device) è considerata un obiettivo futuro non ancora integrato.

## 6. Organizzazione del Workspace
All'esterno della cartella principale dell'app Android (`android-app/`) è presente la directory **`project_tools_and_scripts/`**.
Questa cartella contiene script accessori (es. Python per test algoritmici su OpenCV, file YAML, backup di prova, dump di chat). **Questi file fanno parte dell'ecosistema di ricerca e sviluppo del progetto, ma non sono intrinsecamente legati all'app Android.** La loro presenza fuori dal root Android previene l'inquinamento del Build System (Gradle/KSP) pur mantenendoli accessibili per le future evoluzioni (es. addestramento ML).

## 7. Clean Architecture & Strict Type-Safety (Build System)
L'app compila tramite **KSP (Kotlin Symbol Processing)** e ha eliminato KAPT. Per mantenere la build pulita (zero-warning) bisogna rispettare rigorosamente:
*   **Decoupling UI/Framework:** La UI Compose (es. `SettingsScreen`) **non deve mai** istanziare nativamente classi del framework Android come `WorkManager` o eseguire intenti diretti. I trigger fisici avvengono passando callback lambdas (es. `onTestNotification: () -> Unit`) che risalgono l'albero Compose fino a scaricarsi nel `ViewModel` competente.
*   **IO Bound Threading:** Operazioni di lettura/scrittura sincrone (come quelle esposte in `ZipUtils`) devono essere tassativamente funzioni `suspend` protette da `withContext(Dispatchers.IO)`. Questo per prevenire ANR (Application Not Responding) ed eludere l'ingerenza accidentale dell'Main Thread.
*   **Sicurezza dei Tipi nei Flow (`combine`):** La funzione `combine` standard di Kotlin mantiene la Type-Safety completa solo per un massimo di **5 Flow**. Sforando tale limite si incorre in un fallback ad Array non tipizzato (`Array<Any?>`) con conseguenti `Unchecked cast`. Per combinare >5 flussi, bisogna obbligatoriamente raggrupparli logicamente in *Tuple* (o *Data Classes* intermedie) in modo da rispettare il limite dell'API e garantire il controllo dei tipi al compilatore.

## 6. Sfondi, Gestione Profili e Data Integrity
- **Sfondi e Sagome (Persona)**: L'app gestisce i modelli base (Sesso/Corporatura) dinamicamente. Non usa file fisici ma le risorse drawable interne. Il calcolo della variante attiva avviene in `BodyImageUtils.kt` usando il fallback su base string matching (es. FRONT_myprofile).
- **DataIntegrityWorker**: Esiste un worker in background che scansiona periodicamente le discrepanze, pulendo i db da: varianti orfane, storie vuote senza foto o note, eliminazione foto non in db. E' fondamentale preservare questa funzionalit� per le performance.
