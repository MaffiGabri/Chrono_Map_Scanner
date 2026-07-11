# Block 2 - Database Layer (Room Entities & DAOs)

## Diagnosi Architetturale
Il blocco contiene lo schema del database (Room), i DAO e il repository per i setting locali tramite DataStore.
Lo schema prevede entità snelle (es. `MoleEntity` e `HistoryEntryEntity`) legate da un vincolo `ON DELETE CASCADE`.
Il `MoleDao.kt` include diverse query complesse che fanno uso di `Flow` reattivo o chiamate `suspend`. Particolarmente valida, in chiave scalabilità, è l'assenza di `@Relation` nelle query usate costantemente per la UI; si utilizza la `LEFT JOIN` (in `getMolesAtDate`) che mappa tutto in un Data Transfer Object leggero (`MoleMapDto`), allineato alle best practices previste.

## Red Flags (Criticità e Anti-pattern)
- **OOM Risk in `getMolesWithHistory`**: La query annotata con `@Transaction` e `@Relation` (dentro `MoleWithHistory`) che seleziona *tutti* i record per profilo carica in RAM intere liste di mole e intere storie. In un profilo con >1000 elementi e 5-10 foto di storico per elemento, richiamare `getMolesWithHistory` blocca la UI, sfonda l'heap (OOM). Va usata solo per operazioni batch/export a chunk, mai per mostrare i dati nella UI.
- **Indici Mancanti o Lenti**: L'indice su `HistoryEntryEntity` `date` (`idx_history_date`) e su `mole_id` c'è, ma le subquery come `(SELECT MAX(date) FROM history_entries WHERE mole_id = m.id AND date <= :targetDate)` potrebbero beneficiare di un indice composto o un desc ordering, specie col crescere del volume.
- **Sincronismo Rischioso:** Diverse chiamate come `suspend fun getAllProfileNamesSync(): List<String>` sono safe se usate con moderazione, ma `getMolesByVariantsSync` potrebbe riportare liste massive senza PagingSource.

## File Orfani e Codice Morto
- La maggior parte dei DAO metodi sono probabilmente in uso, ma query multiple che fanno la stessa cosa (es. `getMolesWithHistory` e le sue variazioni `getMoleByIdWithHistory`) dovrebbero essere deprecate per l'utilizzo da parte dei ViewModel, favorendo quelle limitate o paginate.
- Nel `SettingsRepository`, c'è un campo `isImporting` (`MutableStateFlow(false)`) che viene esposto pubblicamente ma non sembra far parte in modo strutturato dello stato memorizzato (è in un repository di preferenze, non è un setting serializzato su disco ma uno stato transient).

## Modernizzazione
- **Room Paging 3:** `MoleDao` dovrebbe supportare `PagingSource<Int, MoleEntity>` per query massive, in modo da integrarsi nativamente con `LazyColumn` di Compose per schermate di elenco se presenti.
- **DataStore e Struttura Transient:** Rimuovere `isImporting` da `SettingsRepository` (che deve gestire unicamente lo storage IO dei settings) e spostarlo in uno UseCase dedicato o in un domain store.

## Piano di Refactoring Step-by-Step (Futuro)
1. Rinominare o disabilitare l'uso di `getMolesWithHistory` per view standard; assicurarsi che venga usata solo in Background Work per Export e Backup.
2. Aggiungere il supporto alla paginazione su Room (`PagingSource`) per eventuali elenchi storici e punti mappa, per garantire i 1000+ support.
3. Rivedere `idx_history_date` in `HistoryEntryEntity` in `Index(value = ["mole_id", "date"], unique = false)` per ottimizzare la sotto-query in `getMolesAtDate`.
4. Spostare logiche di stato in-memory (`isImporting`) fuori da `SettingsRepository`.
