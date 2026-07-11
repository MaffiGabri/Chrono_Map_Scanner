# Block 4 - Background Work & Data Integrity

## Diagnosi Architetturale
Il blocco contiene i Worker di Hilt che garantiscono la pulizia dei dati. `DataIntegrityWorker` è il cuore del sistema di auto-healing, scansiona il DB per storie vuote o senza immagini e cancella i file orfani dal filesystem. `FileCleanupWorker` si occupa di garbage collection a richiesta. Entrambi sfruttano Kotlin Coroutines e delegato l'I/O al `Dispatchers.IO` in modo appropriato. `ReminderWorker` gestisce le notifiche.

## Red Flags (Criticità e Anti-pattern)
- **DataIntegrityWorker Scalability (OOM Risk):** `val allImagePaths = moleDao.getAllActiveImagePathsSync()` carica _tutti_ i percorsi delle immagini in memoria come lista di String in un colpo solo. Se ci sono migliaia di punti con storici complessi, l'aggregazione di un set gigante in memoria potrebbe causare Garbage Collector thrashing o OOM (insieme a `allFiles`). Andrebbe fatto a Chunk o Paging (es: scansionando il DB a blocchi).
- **Hardcoded Notification ID/Icon:** `ReminderWorker` utilizza `android.R.drawable.ic_dialog_info` invece dell'icona dell'app.
- **Logica troppo complessa in Worker:** La ricreazione delle Thumbnail (usata per il ripristino o mancata generazione) all'interno di `DataIntegrityWorker.generateThumbnail` è pesante dal punto di vista computazionale. Scalare su centinaia di file orfani senza thumb fa lavorare la CPU in I/O per un tempo che facilmente supera il timeout limite del WorkManager (10 minuti su OS recenti).

## File Orfani e Codice Morto
Nessun file orfano apparente nel blocco, sebbene la logica ridondante della creazione delle thumb (già presente in `FileRepository`) porti a duplicazioni logiche.

## Modernizzazione
- **Paging DB per Integrity:** Iterare le Active Paths attraverso cursori/room chunks anziché caricare tutti gli stream con `.getAllActiveImagePathsSync()` contemporaneamente.
- **Isolamento CPU in Worker:** Inserire `CoroutineDispatcher.Default` per `generateThumbnail` (che fa rescaling bitmap) separandolo da `Dispatchers.IO` per evitare di saturare i pool di I/O.
- Sostituire le API Worker generiche con `ForegroundInfo` e dichiarazioni asincrone se l'integrità è un task visibile all'utente.

## Piano di Refactoring Step-by-Step (Futuro)
1. Aggiornare `DataIntegrityWorker` per scansionare il DB con offset/limit anziché `getAllActiveImagePathsSync()` se i record superano 1000 unità.
2. Refactoring di `generateThumbnail` per utilizzare l'infrastruttura di Glide/Coil in background se possibile, o estrarlo in un UseCase comune per rimuovere il codice duplicato dal `FileRepository`.
3. Sostituire l'icona di fallback in `ReminderWorker` con un'icona vettoriale di sistema locale o con il drawable dell'app per UX consistenza.
