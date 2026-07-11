# Block 5 - ViewModels - Core Mappings & Details

## Diagnosi Architetturale
I ViewModel esaminati (`BodyMapViewModel`, `MoleDetailsViewModel`, `VariantManagementViewModel`) usano Hilt, combinazioni complesse di `StateFlow` tramite `combine` e `flatMapLatest` con il `SharingStarted.WhileSubscribed(5000)`.
Il pattern generale dell'UI State è moderno. Tuttavia, `BodyMapViewModel` (pur avendone visto solo estratti limitati dai log e dall'architettura generale, e concentrandoci su `MoleDetailsViewModel`) espone una `Mole` con tutto lo storico.

## Red Flags (Criticità e Anti-pattern)
- **Combinazioni OOM Limit (`combine` arity):** In `MoleDetailsViewModel`, viene usato `combine` con 5 flussi. Se il numero di flussi aumenta (es. per nuovi settings), Kotlin limita `combine` nativo a 5 parametri; l'aggiunta di un 6° porterà a un array di `Any?` e fallimenti runtime se non gestito con Tuple o overload appositi, come documentato anche in `.jules/bolt.md` e `ARCHITECTURE.md`.
- **Eager History Loading in ViewModel:** `moleRepository.getMoleByIdWithHistory(moleId)` viene caricato e mappato per la UI. Se lo storico di un neo contiene 200 foto, tenerle tutte in RAM dentro `MoleDetailsUiState` degrada le performance. Servirebbe limitare o paginare questa singola chiamata per la view di dettaglio (almeno il numero di thumbnail da decodificare).

## File Orfani e Codice Morto
Nessun file orfano tra i ViewModel principali analizzati in questo blocco. I test (`BodyMapViewModelTest.kt`) sono noti per avere warning di compatibilità che l'audit ignorerà come da memory.

## Modernizzazione
- **State Merging Scale:** Sostituire l'uso diretto di `combine` multipli >4 con data classes intermedie (es. combinare prima i setting dell'utente e poi combinarli con il mole stream) per garantire la type safety e prevenire crash se il team estende la UI.
- **Paging / Limit per History:** Se `MoleDetails` mostra una UI, non ha bisogno dei campi "notes" giganti di tutta la cronologia subito. Caricare solo l'HistoryEntry più recente e caricare on-demand il resto.

## Piano di Refactoring Step-by-Step (Futuro)
1. In `MoleDetailsViewModel`, rifattorizzare `combine` in due passaggi se si devono aggiungere nuovi StateFlow (raggruppando `settingsRepository.colorSettings`, `gender`, `bodyType` in un flow `UserSettingsFlow`).
2. Sostituire `moleRepository.getMoleByIdWithHistory` con due query separate se lo storico è massivo: una per i dettagli `Mole` e un `Flow<PagingData<HistoryEntry>>` per lo storico.
