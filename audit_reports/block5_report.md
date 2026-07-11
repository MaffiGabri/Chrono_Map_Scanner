# Blocco 5 - Diagnosi Architetturale
Questo blocco contiene il ViewModel e le View principali. `BodyMapViewModel` utilizza `SavedStateHandle` per preservare lo stato e un Grid (Spatial Hashing) cache per gestire i tap sulle imperfezioni sulla mappa in modo estremamente rapido, anche se la logica della UI mappa è complessa. L'architettura è in linea con le raccomandazioni MVI usando StateFlow e Mutex per thread safety interna.

# Blocco 5 - Red Flags
- **Mancanza di Pagination in UI**: Sebbene le performance del Tap siano eccellenti per via dello Spatial Hashing O(1), se lo stato ui (`BodyMapUiState` o i suoi corrispettivi in `MoleDetailsScreen` e liste) viene popolato prelevando 1000 history via `moleRepository.getMolesWithHistory` il GC si blocca. (Visto nei blocchi precedenti, impatta qui).
- **Hardcoding e Side Effects sulla Map**: Il caricamento nel Canvas di bitmap grandi causa GC Churn (surriscaldamento e battery drain). Non si usano approcci tile-based o vettoriali puri (anche se c'è un `BodyImageUtils`).

# Blocco 5 - File Orfani e Codice Morto
- Il `BodySide` è qui importato e forse usato, confermandosi l'anti-pattern da dismettere rispetto al `Variant` basato su stringhe del database.

# Blocco 5 - Modernizzazione
- **Reattività Canvas**: Mantenere l'ottima logica dello Spatial Hashing in `BodyMapViewModel` ma esternalizzare il calcolo della cache su `Dispatchers.Default` per non bloccare il ViewModel thread, usando `Flow.map`.
- **UI State**: Accertarsi che `MoleDetailsViewModel` adotti `SharingStarted.WhileSubscribed(5000)` costantemente per liberare flussi e risorse quando l'utente mette l'app in background. (Lo fa BodyMap, ma va garantito per i Dettagli dove le immagini pesano).

# Blocco 5 - Piano di Refactoring Step-by-Step
1. Spostare i calcoli complessi del Viewmodel (es. caching Grid per il Tap detection) esplicitamente su `Dispatchers.Default` o `Dispatchers.IO`.
2. Convertire gli import e l'utilizzo di `BodySide` dentro le UI in stringhe `variantId` pure, per supportare illimitate sagome custom senza crash.
3. Riscrivere o ottimizzare i Compose Layout List per supportare `LazyColumn` con chiavi univoche (`item { key = ... }`) quando si mostrano liste di mole/storia.
