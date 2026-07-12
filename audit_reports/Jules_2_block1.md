
# Block 1 - Domain & Entities & Room

## [Block 1] - Diagnosi Architetturale:
Il blocco definisce il cuore del Data Layer. Le Entities Room e i modelli di dominio sono ben separati. La struttura relazionale 1-a-molti tra `MoleEntity` e `HistoryEntryEntity` è standard, tuttavia la presenza di join nidificate o caricamento reattivo non ottimizzato (Flow con @Relation) va monitorata per prevenire cali di performance. I TypeConverters sono implementati correttamente (UUID, LocalDate, Enum).

## [Block 1] - Red Flags:
- Nel file `MoleDao.kt`, verificare se `getAllMolesWithHistory` o metodi simili restituiscono un `Flow<List<MoleWithHistory>>` (che usa `@Relation`). Questo è un anti-pattern critico per 1000+ elementi, causa OOM o GC Churn severo. L'architettura esige un approccio "piatto" con `LEFT JOIN` per flussi reattivi.
- Possibili query non paginate: Mancano direttive Paging3. Se tutte le cronologie o le liste dei nei vengono caricate in memoria per la serializzazione o la mappatura, ciò violerà la regola di scalabilità per target massivi.

## [Block 1] - File Orfani e Codice Morto:
- Attualmente non risultano orfani evidenti. Tutti i file contribuiscono allo schema Room o alle policy di backup/serializzazione. Tuttavia, si dovrebbe riconsiderare qualsiasi DTO generato solo per UI superflue e non più utilizzate se ce ne sono (da verificare nei prossimi blocchi).

## [Block 1] - Modernizzazione:
- Implementare `PagingSource` in `MoleDao.kt` per le liste (es. lista di tutti i nei) anziché esporre `Flow<List<T>>` completi.
- Se le date sono convertite iterativamente, spostarsi verso implementazioni basate su interi o stringhe ottimizzate a livello SQLite se i filtri di data diventano query frequenti.

## [Block 1] - Piano di Refactoring Step-by-Step:
1. Analizzare e refactoring di `MoleDao.kt`: sostituire qualsiasi `Flow<List<MoleWithHistory>>` con query piatte tramite `LEFT JOIN` (`getFlatMolesWithHistory`) che mappano su POJO piatti per l'UI.
2. Riservare i caricamenti tramite `@Relation` esclusivamente a query `suspend` one-shot (per backup e visualizzazione singola del dettaglio neo).
3. Introdurre `Paging 3` nel DAO per scalare oltre i 1000 marker nelle visualizzazioni a lista qualora esistano.
