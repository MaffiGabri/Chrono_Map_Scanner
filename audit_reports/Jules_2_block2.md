
# Block 2 - Repositories & Workers

## [Block 2] - Diagnosi Architetturale:
Questo layer gestisce la logica di business asincrona, la persistenza e il ponte verso WorkManager. I worker per l'import/export zip e per la pulizia dei file (`FileCleanupWorker`) dimostrano una robusta separazione degli intenti, esternalizzando l'I/O intensivo e limitando storage leak in base a `architecture.md`.

## [Block 2] - Red Flags:
- Sincronia Bloccante: In `ExportDatabaseWorker` o `ImportDatabaseWorker`, le operazioni di zip/unzip su I/O file devono rigorosamente essere avvolte in `withContext(Dispatchers.IO)`. Manipolazioni di 1000+ immagini causano ANR se non esplicitate.
- WorkManager Anti-patterns: `DataIntegrityWorker` e `FileCleanupWorker` potrebbero subire timeout se le query Room per confrontare le foto fisiche e il DB non sono ottimizzate con `suspend` e caricano in memoria enormi batch.

## [Block 2] - File Orfani e Codice Morto:
- Verificare se vecchie implementazioni dirette nei repository per il salvataggio dei file non sono state rimosse a favore dell'uso di `FileRepository`. I metodi duplicati devono essere considerati codice orfano.

## [Block 2] - Modernizzazione:
- Implementare `Expedited Jobs` nel WorkManager (SDK 31+) per i backup forzati se l'app rischia di chiudersi, garantendo che i dati non vadano persi.
- Iniettare `CoroutineDispatcher` (es. `@IoDispatcher`) via Hilt al posto di usare classi statiche o hardcodate come `Dispatchers.IO` nei costruttori per facilitare i testing rigorosi.

## [Block 2] - Piano di Refactoring Step-by-Step:
1. Revisionare `ExportDatabaseWorker` e `ImportDatabaseWorker` per accertare che ogni lettura/scrittura sincrona chiami `withContext(Dispatchers.IO)`.
2. Analizzare `OfflineMoleRepository.kt` per assicurarsi che i job di eliminazione fisica asincrona innescati dalle cancellazioni dei record passino a `FileCleanupWorker` correttamente.
3. Introdurre i test su `SettingsRepository.kt` assicurando che le preferenze (es. `warnOnEmptyMoleDeletion`) siano reattive e non blocchino mai il thread UI alla prima lettura con `.first()`.
