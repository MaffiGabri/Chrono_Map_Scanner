# Master Audit Base: Chrono Map Scanner

## 1. Executive Summary
L'app possiede una solida architettura di base (MVI Ibrido, Coroutines, Room, KSP, Hilt), concepita per gestire flussi reattivi complessi e mantenere un rigido decoupling. L'implementazione dello *Spatial Hashing* dinamico in `BodyMapViewModel` dimostra che le fondamenta sono pronte per calcoli complessi.
Tuttavia, rispetto al **requisito critico dei 1000+ elementi a 60FPS**, l'implementazione attuale fallisce. Manca un approccio scalabile alla gestione dell'I/O (caricamenti di file in massa) e al rendering UI. Il massiccio utilizzo di `AsyncImage` di Coil in blocchi non-Lazy, la geometria sincrona sul Main Thread e le allocazioni di memoria per-frame nella CameraX causeranno inevitabilmente *OutOfMemoryError* (OOM), lag nei trascinamenti e Battery Drain.

## 2. Top 5 Red Flags Globali
Queste sono le 5 criticità più gravi estratte dai 7 blocchi di audit:

1.  **OOM per File System I/O (`DataIntegrityScanner.kt` - Blocco 2):**
    > [!CAUTION]
    > Lo scanner usa `directory.listFiles()`, allocando in un colpo solo l'intero albero delle directory nell'Heap RAM. Con 10.000+ file (es. 5000 nei + 5000 thumb), scatta il crash per OOM e blocco thread.
2.  **Jank per Rendering Massivo in Compose (`BodyMapScreen.kt` & `MoleMarker.kt` - Blocco 5/6):**
    > [!CAUTION]
    > Il rendering di 1000+ marker foto-visivi genera sincronicamente migliaia di nodi `@Composable` complessi (`AsyncImage` con `crossfade`) in un ciclo aperto e senza culling (no Lazy). Questo distrugge il target dei 60fps.
3.  **Blocco del Main Thread in Geometria (`BodyMapViewModel.kt` - Blocco 4):**
    > [!WARNING]
    > Mentre il tap usa coroutines, `snapMolePosition` esegue pesanti calcoli di collisione (`sqrt`, iterazioni massive) in modo sincrono. Durante il drag, blocca il frame rendering.
4.  **Garbage Collection Churn in CameraX (`AutoCameraScreen.kt` - Blocco 5):**
    > [!WARNING]
    > L'auto-focus alloca un nuovo gigantesco array `IntArray(width * height)` ad ogni singolo frame elaborato (30fps) per il calcolo Laplaciano, scatenando infiniti cicli di Garbage Collection (GC) e surriscaldamento.
5.  **Fake Implementation & Accoppiamento (`SettingsViewModel.kt` e `ReminderManager.kt` - Blocco 3/4):**
    > [!IMPORTANT]
    > Il `SettingsViewModel` usa `delay(1000)` per mockare il backup invece di osservare la reale `WorkInfo`. Il `ReminderManager` è un Singleton (`object`) che abusa di `WorkManager.getInstance(context)`, violando la Dependency Injection e la Clean Architecture.

---

## 3. Piano di Modernizzazione Globale (Macro Obiettivi)
*   **Gestione Dati in Streaming:** Tutto l'I/O pesante si sposterà sulle moderne API NIO.2 (`Files.newDirectoryStream`) o query sincrone ottimizzate per evitare saturazioni di memoria (O(1) footprint).
*   **Object Pooling & Pre-Rendering:** La memoria in processi continui (fotocamera) sarà allocata una tantum (Object Pooling). La UI mappa disegnerà su `Canvas` con comandi hardware nativi delegando al ViewModel il caricamento massivo in cache delle miniature, rimuovendo le `AsyncImage` massicce.
*   **Iniezione Hilt Totale:** Nessun componente di sistema (WorkManager, Notifications) sarà istanziato localmente tramite `Context`. Tutto passerà per i costruttori via Dagger/Hilt.

---

## 4. Roadmap di Refactoring Definitiva

Questa è la sequenza esatta temporale, file per file, con la combinazione di tutti gli output analitici dei blocchi:

### FASE 1: Pulizia Fondamentale e Data Layer (Dai Blocchi 1, 2)
1. **`MainActivity.kt`**: 
   * Aggiungere `androidx.activity.enableEdgeToEdge`. Invocare `enableEdgeToEdge()` prima di `setContent` per supportare il windowing SDK 35+.
   * Rimuovere import orfani e commenti dead-code (`// Invalidate KSP cache`).
2. **`ChronoMapScannerApplication.kt`**:
   * Rimuovere i commenti inutilizzati su `WorkManagerInitializer`.
3. **`MoleDao.kt`**: 
   * Aggiungere una query sincrona per la cancellazione: `@Transaction @Query("SELECT * FROM moles WHERE id = :moleId") suspend fun getMoleByIdWithHistorySync(moleId: String): MoleWithHistory?`
4. **`OfflineMoleRepository.kt`**: 
   * Nel metodo `deleteMole()`, usare `getMoleByIdWithHistorySync(moleId)` al posto di istanziare un Flow e interromperlo con `firstOrNull()`.
5. **`DataIntegrityScanner.kt`**: 
   * Rifattorizzare `cleanupOrphanedFiles` sostituendo `filesDir.listFiles()` con l'iteratore NIO `java.nio.file.Files.newDirectoryStream(filesDir.toPath())` e racchiuderlo in un blocco `.use { ... }` per la chiusura sicura dello stream.

### FASE 2: Dependency Injection e WorkManager (Dai Blocchi 3, 4)
6. **`ReminderManager.kt`**: 
   * Rimuovere la dichiarazione `object`. Trasformarlo in un'interfaccia `ReminderManager` e una classe `@Singleton class ReminderManagerImpl @Inject constructor(private val workManager: WorkManager)`.
   * Rimuovere ogni invocazione di `WorkManager.getInstance(context)`. Rimuovere l'argomento `Context` da `scheduleReminder`.
7. **`AppModule / DatabaseModule` (Setup DI)**: 
   * Aggiungere un `@Provides` per fornire il `WorkManager` qualora `hilt-work` non sia configurato nativamente.
8. **`SettingsViewModel.kt`**: 
   * Iniettare `WorkManager` nel costruttore.
   * Rimuovere `getInstance(context)` e aggiornare `testNotification()`.
   * In `importDatabase` e `exportDatabase`, **eliminare** `delay(1000)` e `onComplete()` simulato. Introdurre la raccolta reattiva del WorkManager: `workManager.getWorkInfoByIdFlow(workRequest.id).collectLatest { ... }` per un feedback UI veritiero.

### FASE 3: Prestazioni Geometria e CameraX (Dai Blocchi 4, 5)
9. **`BodyMapViewModel.kt`**: 
   * Cambiare la firma di `snapMolePosition` in `suspend fun snapMolePosition(...)` e avvolgerne il contenuto geometrico in `withContext(Dispatchers.Default)` per sbloccare il Main Thread.
10. **`AutoCameraScreen.kt` & `AlgorithmicMoleDetector.kt`**: 
    * Implementare l'Object Pooling per la camera: dichiarare un buffer `IntArray` riutilizzabile.
    * Modificare `calculateLaplacianVariance(bitmap, buffer)` in modo che non allochi array ad ogni frame, ma ricicli il buffer (ridimensionandolo solo se necessario).

### FASE 4: Composizione UI Massiva e Componenti (Dai Blocchi 5, 6)
11. **`MoleMarker.kt`**: 
    * Eliminare l'uso forzato di `LocalMinimumInteractiveComponentSize provides Dp.Unspecified` (anti-pattern accessibilità).
    * Spostare la logica da nodo indipendente Compose a funzione helper Canvas `DrawScope.drawMoleMarker(x, y, radius, bitmap, color)`.
12. **`BodyMapScreen.kt`**: 
    * Sostituire interamente il ciclo `forEach { MoleMarker(...) }` nella UI massiva delegandolo alle funzioni `drawBitmap`/`drawCircle` su un livello nativo Canvas per reggere 1000+ marker senza Janks/OOM.
13. **`MoleDetailsComponents.kt` (Ottimizzazione Scroll)**: 
    * Limitare la memoria in `HistoryItem` impostando un `memoryCachePolicy` più rigido in Coil, per prevenire OOM su scroll veloci all'interno delle `LazyColumn`.

### FASE 5: Consolidamento Risorse e Test (Dai Blocchi 1, 7)
14. **`strings.xml`**: 
    * Spostare tutte le stringhe di debug hardcoded individuate ("Scatto in corso...", "Metti a fuoco il neo") da `AutoCameraScreen.kt` alle risorse localizzate.
15. **`architecture.md`**: 
    * Aggiornare il documento eliminando il testo obsoleto "(Piano Futuro)" dalla sezione Fotocamera, riflettendo il fatto che l'AlgorithmicMoleDetector è già implementato.
16. **Test Suites (`SettingsViewModelTest.kt`, ecc.)**: 
    * Adattare i costruttori nei file di test fornendo instanze Mockate del `WorkManager` e del `ReminderManager` iniettato per far compilare i test nuovamente.
