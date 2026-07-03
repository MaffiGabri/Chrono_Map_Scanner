# Ultimate Refactoring Blueprint: Chrono Map Scanner

Questo documento contiene il piano di refactoring definitivo e unificato per garantire estrema scalabilità (1000+ punti), mantenimento dei 60fps e stabilità della memoria.

## FASE 1: Core Data, Memory Leak e Background
1. **Rifacimento `DataIntegrityScanner`**: Eliminare il loop `while(isActive)` invocato globalmente in `Application`. Implementare al suo posto un `@HiltWorker class DataIntegrityWorker` schedulato periodicamente, progettato per eseguire una singola passata sequenziale.
2. **Risoluzione Prodotto Cartesiano e Ottimizzazione DAO**:
   - Riscrivere `getFlatMolesWithHistory` con una subquery di raggruppamento per ottenere esclusivamente l'ultima data disponibile (esattamente 1 record per neo).
   - Introdurre la query `getAllActiveImagePathsSync` (che restituisce `List<String>`) per alimentare il worker di integrità evitando di istanziare inutilmente i Domain Model.
   - Mantenere l'uso di `@Relation` **solo** per operazioni `suspend` one-shot (Export Backup e Detail Screen), utilizzando rigorosamente `LEFT JOIN` e DTO piatti per tutti i flussi massivi.
3. **Serializzazione Binaria Date**: Modificare `Converters.kt` per memorizzare nel database le date in formato `Long` (Epoch Days) anziché `String` (ISO8601). Richiederà una migration di Room.

## FASE 2: Prevenzione OOM e Rendering UI (Canvas)
1. **Canvas Purism (`BodyMapScreen`)**: Rimuovere completamente l'istanziazione di nodi Compose massivi (`forEach { MoleMarker() }`). Disegnare i 1000+ thumbnail imperativamente tramite `DrawScope.drawImage()` / `drawCircle()` su un singolo Canvas root.
2. **Caching L1 Immagini e Ban di Coil**: Eliminare l'uso di `AsyncImage` all'interno della mappa principale. Alimentare il Canvas tramite una `LruCache<String, ImageBitmap>` residente nel `BodyMapViewModel`. Nelle schermate secondarie, restringere le chiamate Coil con `.size()` fissi e `.crossfade(false)`.
3. **Distruzione `cachedTimelineFlow`**: Eliminare dal `BodyMapViewModel` l'intero pre-calcolo temporale che teneva in RAM l'intera storia (`Map<LocalDate, List<MoleUiModel>>`). Incrociare lo stream di dati esclusivamente con la `selectedDate` corrente (calcolo O(N) dinamico).
4. **Defer State Reads**: In `BodyMapScreen`, garantire che la lettura dello stato reattivo (es. `scale` e `offset` del pan/zoom) avvenga esclusivamente all'interno del modificatore `graphicsLayer { ... }` o nel blocco di draw, impedendo la ricomposizione dell'intero albero durante il drag.
5. **Ottimizzazione Geometria Zero-Allocation**: Mantenere l'algoritmo di Spatial Hashing 10x10 esistente, ma confinare il calcolo di collisione (`snapMolePosition`) all'interno di `withContext(Dispatchers.Default)`. Riscrivere il loop interno per rimuovere totalmente allocazioni boxate (`Pair`, `Float` non primitivi).

## FASE 3: Clean Architecture, MVI e Sicurezza Tipi
1. **Sradicamento State Globale (`SkinHistoryAppState.kt`)**: Rimuovere le variabili di transizione globali (es. `movingMoleId`). Passare questi parametri in modo type-safe o appoggiarsi a `SavedStateHandle`.
2. **Depurazione Dominio**: Rimuovere le dipendenze al framework UI (`androidx.compose.*`, `Color`) da `MoleUiModel.kt` e dal ViewModel. Utilizzare stringhe esadecimali per rappresentare i colori.
3. **Protezione dei Flussi `combine`**: All'interno del ViewModel, laddove si debbano combinare più di 5 flussi, introdurre una Data Class intermedia (Tuple) per evitare la perdita di type-safety dovuta all'uso interno di array non tipizzati in Kotlin.
4. **Disaccoppiamento e Streaming Backup**: 
   - Creare entità `MoleBackupDto` specifiche per l'export e indipendenti dal dominio UI.
   - Effettuare la serializzazione JSON utilizzando flussi I/O streammati (`encodeToBufferedSink`) per abbattere l'uso della RAM.
   - Forzare l'utilizzo di `withContext(Dispatchers.IO)` in `FileRepository` e `ZipUtils`.

## FASE 4: Modernizzazione ed Ecosistema (Target SDK 36)
1. **Edge-to-Edge**: Abilitare `enableEdgeToEdge()` in `MainActivity.kt` per il pieno supporto ad Android 15/16.
2. **Dependency Injection Stretta**: Eliminare Singleton `object` statici (es. `ReminderManager.kt`) e l'uso di `WorkManager.getInstance()`, sostituendoli con un'architettura 100% `@Inject` nativa tramite Hilt. Sostituire KAPT con KSP.
3. **Version Catalog**: Centralizzare le dipendenze spostandole da `build.gradle.kts` a `libs.versions.toml`.
4. **Flattening UI**: Risolvere eventuali loop di layout o misurazioni infinite appiattendo gerarchie annidate come `LazyColumn` in `BackgroundSettings`.
