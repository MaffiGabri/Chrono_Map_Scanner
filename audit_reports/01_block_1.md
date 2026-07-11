# Block 1 - App Foundation, DI, & Core Data Models

## Diagnosi Architetturale
Il blocco contiene l'infrastruttura fondamentale (Application, Activity, Modelli di Dominio).
In `ChronoMapScannerApplication.kt`, c'è un worker di integrità schedulato ogni 15 minuti tramite API native di WorkManager nell'`onCreate()`, pur utilizzando un'architettura Hilt.
In `MainActivity.kt`, notiamo un disallineamento e anti-pattern rispetto alle best practices per un'app scalabile, con potenziale sovrapposizione UI/logica. La presenza dell'istanza DB in Activity e il mix di navigation direttamente nella composizione principale sollevano questioni di separazione e pulizia.
I `MoleModels` presentano un mapping da Room (POJO) a dominio, usando `@Immutable`, ma i metodi toDomain possono istanziare liste di grandi dimensioni (se non paginato, rischia OOM sui 1000+ punti).

## Red Flags (Criticità e Anti-pattern)
- **WorkManager Inizializzazione:** In `ChronoMapScannerApplication.kt` viene schedulato il `DataIntegrityWorker` in modo sincrono e immediato nell'`onCreate` con WorkManager (utilizzando API dirette che bypassano i pattern moderni Hilt/App Startup o le policy di inizializzazione lazy).
- **MainActivity - Database Injection:** In `MainActivity.kt`, l'iniezione del `database: AppDatabaseRoom` è un enorme anti-pattern (layer Framework iniettato direttamente nella UI, violando la regola di decupling imposta in ARCHITECTURE.md).
- **MainActivity - Side Effects e Stato Globale:** `MainActivity.kt` fa troppo. Richiama e gestisce dialoghi di importazione e processa gli stati, creando accoppiamento stretto e un layer UI non puramente dichiarativo. `SnackbarHostState` e logica nav andrebbero separati.
- **MoleModels (Scalabilità/OOM):** `Mole.toDomain` mappa intere collezioni di `HistoryEntryEntity` (con stringhe, path) in liste. Se un punto ha uno storico vastissimo (e considerati 1000+ punti), si rischiano allocazioni giganti in heap durante i mapping in main memory se non strettamente paginati.

## File Orfani e Codice Morto
- Il file `CustomTestRunner.kt` o test rule dispatcher (indicato in lista) devono essere verificati per effettivo utilizzo.
- `MainActivity.kt`: L'injection del DB `lateinit var database: AppDatabaseRoom` non viene mai usato, è codice morto o residuo da test temporanei. Deve essere rimosso in modo tassativo.

## Modernizzazione
- **Inizializzazione App:** Sostituire l'uso diretto di `WorkManager.getInstance()` nell'`onCreate()` con l'uso della libreria `androidx.startup` oppure spostando la schedulazione iniziale del worker nei repository o nel launcher Viewmodel per non ritardare il boot time.
- **MainActivity UI:** Delegare interamente l'UI state e l'hosting (dialoghi, loading overlay, navigation setup) a un `AppState` class o un navigation component dedicato, sfruttando pattern Edge-to-Edge integrati moderni per SDK 35/36.
- **Data Models:** Modificare le strutture affinché per le collezioni storiche supportino Paging3 o lazy iterators invece di mappare massivamente le Entity a Domain se la lista superasse certe dimensioni, pur mantenendo `MoleMapItem` piatto che è già un'ottima mossa architetturale.

## Piano di Refactoring Step-by-Step (Futuro)
1. Rimuovere l'iniezione di `AppDatabaseRoom` da `MainActivity.kt`.
2. Estrarre la logica di visualizzazione dello `showImportDialog` e progress overlay in una componente o Route a livello alto, svincolandola dalla MainActivity.
3. Migrare l'inizializzazione del `DataIntegrityWorker` dalla Application in un modulo `Startup` dedicato o gestirlo nel momento di avvio logico dall'App ViewModel per evitare cold-start delay.
4. Introdurre Paging3 o cursor pagination per il retrieve dello storico nei modelli di dominio `MoleModels.kt` anziché caricare array interi.
