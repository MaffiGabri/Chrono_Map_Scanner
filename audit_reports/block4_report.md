# Blocco 4 - Diagnosi Architetturale
Il blocco 4 contiene la navigazione Type-Safe (SDK moderno, ottimo utilizzo di `@Serializable`), parte dei ViewModel (Settings e BackgroundSettings) e UI Components. I componenti Jetpack Compose sono per lo più ben separati. La Navigation supporta Type-Safety come raccomandato nelle recenti versioni di Navigation Compose.

# Blocco 4 - Red Flags
- **Mancata implementazione di UI State in BackgroundSettings**: `BackgroundSettingsViewModel` espone direttamente multipli Flow non combinati in una classe `UiState` consolidata (es. usa `combine` solo per `userSettings` e poi ha `categoriesFlow` ecc.). Questo può portare a race condition nei recomposition (Anti-Pattern UI State).
- **Assenza di gestione Lifecycle-Aware in Navigation**: Navigare tra schermate con un `moleId` stringa passato in Type-Safe arguments carica bene la rotta. Ma bisogna accertarsi che nelle List Compose (se presenti) vi sia l'attributo `key` (come da documentazione app) altrimenti 1000+ mole marker in un Canvas o LazyList causeranno un crollo dei frame (Regola 1 e Memory).

# Blocco 4 - File Orfani e Codice Morto
- Nessun file puramente morto, ma l'enum o la distinzione fissa per `BodySide` che prima citavo è in effetti assente dai Route e UiModel, in favore di una più generale `side: String`. Questo conferma che `BodySide` nel dominio è orfano/obsoleto.

# Blocco 4 - Modernizzazione
- **Consolidamento UI State**: `BackgroundSettingsViewModel` (e potenzialmente `SettingsViewModel`) dovrebbero consolidare l'intero stato della UI in un'unica data class (es. `BackgroundSettingsUiState`) esposta tramite `StateFlow`, invece di multipli flow slegati.
- **Edge-to-Edge**: I componenti UI come le `BottomSheet` (es. VariantManagement) e i Dialog devono supportare e disegnare sotto le system bars.

# Blocco 4 - Piano di Refactoring Step-by-Step
1. Creare `BackgroundSettingsUiState` e aggiornare `BackgroundSettingsViewModel` in modo da esporre un singolo `StateFlow` tramite `combine`.
2. Revisionare i modificatori nei componenti Compose per assicurarsi che i lazy load (se usati nei bottom sheet) abbiano il parametro `key` e gestiscano le `WindowInsets`.
