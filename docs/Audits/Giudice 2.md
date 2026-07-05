# Ultimate Refactoring Blueprint: "Nei Map" (Target SDK 35, 1000+ Scalability)

Questo documento rappresenta la sintesi definitiva e il piano di attacco architetturale derivato dall'analisi incrociata dei tre report di audit (Base, Clean, Perf). L'obiettivo primario è garantire il mantenimento dei 60fps e la stabilità della memoria (no OOM) con carichi estremi (>1000 elementi).

## 1. Il Consenso Architetturale (Priorità Assoluta)

Tutti i report concordano su una serie di vulnerabilità critiche (Red Flags) che minacciano direttamente la stabilità dell'applicazione e violano i requisiti di scalabilità:

*   **OOM da Over-Rendering in Compose (CRITICO):** La generazione simultanea di 1000+ nodi `@Composable` (`MoleMarker`) in `BodyMapScreen`, ciascuno con una propria istanza di caricamento asincrono via Coil (`AsyncImage`), è la causa primaria di OutOfMemoryError e frame drop. Compose non può gestire migliaia di nodi interattivi fuori da un layout `Lazy`.
*   **Battery Drain e Thread Blocking (CRITICO):** L'avvio di un loop infinito (`while(isActive)`) in `DataIntegrityScanner` direttamente dall'Application Class distrugge l'autonomia del dispositivo, viola le policy Doze di Android e causa pesanti "GC Churn".
*   **Esplosione RAM da Pre-calcolo Temporale (CRITICO):** Il flusso `cachedTimelineFlow` in `BodyMapViewModel` che tenta di mantenere in memoria una mappa totale `Map<LocalDate, List<MoleUiModel>>` per tutte le date storiche simultaneamente garantisce l'esaurimento dell'Heap.
*   **Prodotto Cartesiano nel DAO (CRITICO):** L'utilizzo di una `LEFT JOIN` piatta non filtrata in `getFlatMolesWithHistory` causa una moltiplicazione esponenziale dei DTO restituiti se un neo ha molte foto storiche, saturando la pipeline reattiva.
*   **Inquinamento Architetturale e UDF (GRAVE):** Il dominio è inquinato da framework UI (es. `Color` importato nei ViewModel). Lo stato globale in `SkinHistoryAppState` bypassa la type-safety della navigazione. I calcoli geometrici sincroni durante il drag ricalcolano i bound di Compose bloccando il thread.
*   **Debito Tecnico Moderno (GRAVE):** Mancanza di `enableEdgeToEdge()` per Android 15 (SDK 35+) e parsing inefficiente delle date (`String` vs `Long`) nel database.

## 2. Risoluzione dei Conflitti e Scelte Architetturali

Durante l'analisi sono emerse alcune sovrapposizioni e discrepanze tra i report. Ecco le decisioni prese in qualità di Chief Architect:

*   **Conflitto 1: Gestione dell'Integrità dei File (OOM I/O)**
    *   *Dilemma:* Il report Base suggerisce l'uso di `Files.newDirectoryStream` (NIO) per scorrere i file orfani pigramente. I report Clean/Perf suggeriscono query DAO `SELECT imagePath` per evitare di caricare le `@Relation` in RAM.
    *   *Decisione:* **Approccio Ibrido Sinergico.** La scansione userà `Files.newDirectoryStream` per non saturare la memoria con l'albero della directory locale, e incrocerà i file con una speciale query Room ultra-leggera che restituisce **solo** una `List<String>` (`getAllActiveImagePathsSync`), evitando del tutto l'istanziazione dei Domain Models.
*   **Conflitto 2: Refactoring del Backup JSON**
    *   *Dilemma:* Il report Clean suggerisce di streammare l'export JSON direttamente su disco per evitare OOM. Il report Perf suggerisce di scollegare i DTO del backup dai modelli di dominio attuali (`Mole`).
    *   *Decisione:* **Entrambe le soluzioni.** Si implementeranno `MoleBackupDto` (version-agnostic) disaccoppiati dal dominio corrente. La serializzazione avverrà tassativamente tramite *I/O Streaming* (`encodeToBufferedSink`) per annullare il footprint in RAM durante backup massicci di migliaia di elementi.
*   **Conflitto 3: Algoritmo Geometrico di Collisione**
    *   *Dilemma:* Il report Perf suggerisce di implementare logiche QuadTree/Grid per il trascinamento. Tuttavia, `ARCHITECTURE.md` dichiara che esiste già un "motore di Spatial Hashing personalizzato" con bucket 10x10.
    *   *Decisione:* **Vince ARCHITECTURE.md (Ottimizzazione Micro).** Non si scriverà un nuovo QuadTree. Si manterrà l'attuale Spatial Hashing 10x10, ma si elimineranno brutalmente tutte le allocazioni scatolari (`Pair`, `Float` non primitivi) all'interno del loop per garantire un calcolo *zero-allocation* in fase di drag.

## 3. Falsi Positivi Scartati (No Over-Engineering)

Per proteggere il budget di performance (16ms/frame) e concentrarci sulla scalabilità brutale, le seguenti proposte dei report vengono scartate o de-prioritizzate:

*   **SCARTATO - Uso estremo di UseCases per il Layer Visivo:** Il report Clean Code suggerisce di estrarre `CalculateTimelineUseCase` e `DetectMoleCollisionUseCase`. Poiché lo Spatial Hashing e la timeline operano nel core rendering loop asincrono del ViewModel, introdurre un'eccessiva frammentazione in classi astratte potrebbe causare micro-overhead. Manterremo queste logiche all'interno di delegate private del ViewModel o Interactor strettamente accoppiati, riservando gli `UseCase` classici per operazioni esterne (es. `DeleteProfileUseCase`).
*   **SCARTATO - Rimozione del Bypass dell'Accessibilità in `MoleMarker`:** Il report Base voleva rimuovere `LocalMinimumInteractiveComponentSize provides Dp.Unspecified`. Poiché distruggeremo completamente il `MoleMarker` come nodo Compose in favore del puro `Canvas` draw, questa disputa diventa irrilevante.
*   **SCARTATO - Deprecazione Totale del `while(isActive)` a prescindere dal contesto:** Mentre il loop globale nell'App è un crimine, se un loop del genere è confinato in un `Worker` periodico (entro i limiti temporali di esecuzione), è accettabile. Lo elimineremo comunque preferendo un run sequenziale singolo per `DataIntegrityWorker`.

---

## 4. La Roadmap Definitiva (Step-by-Step)

Questo è l'ordine esatto e inalterabile in cui le modifiche dovranno essere implementate. Le emergenze architetturali (crash e battery drain) vengono prima della pulizia del codice.

### FASE 1: Estirpazione dei Memory Leak e Battery Drain (Core Data)
1.  **Disintegrare `DataIntegrityScanner`:** Rimuovere il Singleton globale e la sua invocazione in `SkinHistoryScannerApplication.kt`. Trasformarlo in un `@HiltWorker class DataIntegrityWorker` da schedulare periodicamente.
2.  **Risolvere Prodotto Cartesiano Room:** In `MoleDao.kt`, riscrivere la `@Query` `getFlatMolesWithHistory` usando una subquery di raggruppamento per prendere solo l'ultima data, restituendo esattamente 1 record per neo.
3.  **Ottimizzare DAO Whitelist:** Creare in `MoleDao.kt` la query `@Query("SELECT imagePath FROM history_entries WHERE imagePath IS NOT NULL")` per alimentare il Worker di pulizia senza allocare entità complesse.
4.  **Codifica Binaria Date:** Modificare `Converters.kt` per trasformare il DB da `String` ISO8601 a interi nativi `Long` (Epoch Days). *Richiede Migration Room.*

### FASE 2: Prevenzione OOM e 60FPS UI Lock (Presentation)
1.  **Distruzione `cachedTimelineFlow`:** Svuotare `BodyMapViewModel.kt` della mappa pre-calcolata globale. Il ViewModel incrocerà lo stream piatto (corretto in Fase 1) *esclusivamente* con la singola `selectedDate` richiesta (O(N) rapido), emettendo solo lo strettissimo necessario.
2.  **Canvas Purism (Culling & Rendering):** In `BodyMapScreen.kt`, sradicare l'uso massivo di `@Composable MoleMarker` all'interno dei cicli `forEach`. Il rendering di 1000+ thumbnail avverrà puramente in modo imperativo tramite `DrawScope.drawImage()` nel Canvas.
3.  **L1 Thumbnail Cache:** Introdurre in `BodyMapViewModel` una `LruCache<String, ImageBitmap>` per alimentare il Canvas, bandendo i caricamenti asincroni `AsyncImage` (Coil) incontrollati della Mappa.
4.  **Defer State Reads:** In `BodyMapScreen`, garantire che la lettura di `scale` e `offset` avvenga solo all'interno del modificatore `graphicsLayer { ... }` o nel blocco di draw, impedendo l'invalidazione della Composizione durante i trascinamenti.

### FASE 3: Pulizia Architetturale (UDF & Clean Domain)
1.  **Disinfettare il Dominio:** Rimuovere ogni import `androidx.compose.*` da `MoleUiModel.kt` e dal ViewModel. Mappare i colori come primitive `String` esadecimali.
2.  **Sradicare il God Object `SkinHistoryAppState.kt`:** Svuotare questa classe dalle variabili di transizione ibride (`movingMoleId`, ecc.) e incanalare la logica nello standard `SavedStateHandle` o rotte type-safe.
3.  **Refactoring Backup (I/O & DTO):** Creare i `MoleBackupDto` indipendenti e modificare l'esportazione per usare flussi I/O (Streaming JSON) anziché gigantesche allocazioni in RAM di singole stringhe.

### FASE 4: Modernizzazione (Fit & Finish)
1.  **Edge-to-Edge:** Implementare `enableEdgeToEdge()` in `MainActivity.kt` (SDK 35 Compliant).
2.  **Version Catalog:** Migrare le dipendenze hardcoded da `build.gradle.kts` a `libs.versions.toml`.
3.  **Dependency Injection Pura:** In `ReminderManager.kt`, abbandonare il Singleton `object` e `WorkManager.getInstance()` in favore di un'iniezione di dipendenze nativa tramite Hilt (`@Inject`).
