# Clean Code & SOLID Red Flags

In qualità di Software Craftsman, l'analisi del progetto rivela che, sebbene l'app adotti librerie moderne (Compose, Flow, Hilt), l'architettura sottostante è carente dal punto di vista dei principi SOLID e della Clean Architecture. Il codice è "funzionante", ma non è testabile in isolamento né manutenibile a lungo termine senza un alto rischio di regressioni.

Ecco le violazioni architetturali profonde identificate:

## 1. Assenza del Layer "Use Case" (Violazione SRP e Fat ViewModels)
I ViewModel (in particolare `BodyMapViewModel` e `MoleDetailsViewModel`) si sono trasformati in "God Classes". Svolgono troppe responsabilità:
- Contattano direttamente i repository multipli.
- Eseguono logica di calcolo spaziale (Spatial Hashing per la collisione dei tap).
- Calcolano l'algoritmo di espansione della timeline storica.
**Impatto:** I ViewModel sono impossibili da testare in isolamento senza mockare intere catene di repository e framework Android. 
**Soluzione Clean:** La logica di business pura deve essere estratta in classi `UseCase` indipendenti dal framework (es. `CalculateTimelineUseCase`, `DetectMoleCollisionUseCase`), iniettate nel ViewModel. Il ViewModel dovrebbe limitarsi a ricevere i dati e mapparli nell'UiState.

## 2. Inquinamento del Framework UI nel Dominio (Violazione Clean Architecture)
Le classi di modello (come `MoleUiModel`) e i ViewModel importano e istanziano primitivi del framework visivo (`androidx.compose.ui.graphics.Color`).
**Impatto:** Il layer di presentazione/dominio è accoppiato a Jetpack Compose. Non è possibile eseguire Unit Test puri sulla JVM senza importare le librerie Android/Compose, rallentando la CI/CD e violando la regola d'oro della Clean Architecture (il centro non deve conoscere i dettagli periferici).
**Soluzione Clean:** I modelli UI devono esportare tipi primitivi puri (es. `String` per l'hex code `"#FF0000"`). La conversione in `Color` deve avvenire esclusivamente al momento del rendering (nei `@Composable`).

## 3. Global State e Violazione dell'Incapsulamento (State Hoisting Abusivo)
Il file `SkinHistoryAppState.kt` viene utilizzato come un contenitore globale di variabili (`var movingMoleId`, `var pendingPhotoPath`) condivise tra schermi scollegati.
**Impatto:** L'accoppiamento tra le rotte di navigazione è invisibile e implicito (Spaghetti State). Uno schermo può modificare una variabile che corrompe lo stato di un altro, rendendo il flusso di navigazione imprevedibile e infrangendo i principi dell'Unidirectional Data Flow (UDF).
**Soluzione Clean:** Usare le potenzialità di Compose Navigation Type-Safe per passare parametri direttamente nelle rotte, oppure sfruttare il `SavedStateHandle` per restituire risultati in modo isolato all'Entry precedente.

## 4. Leak del Data Layer verso la Presentation (Violazione Dependency Inversion)
L'uso di DTO complessi di Room (es. `@Relation` in `MoleWithHistory`) che vengono restituiti direttamente e manipolati dai ViewModel.
**Impatto:** Se cambia lo schema del database (Room), il ViewModel si rompe a cascata. Il ViewModel non dovrebbe sapere nulla di come i dati sono strutturati in SQLite.
**Soluzione Clean:** Il `MoleRepository` deve fungere da vero confine (Boundary). Deve eseguire le query necessarie, assemblare gli oggetti e restituire dei puri Domain Models Kotlin (`Mole`, `HistoryEntry`) slegati da annotazioni Room.

## 5. Dependency Injection Non Testabile (Hardcoded Dispatchers)
Nel progetto sono presenti scope asincroni hardcoded (es. `CoroutineScope(Dispatchers.IO)` o `Dispatchers.Default` chiamati direttamente nei ViewModel o nei repository).
**Impatto:** Questo rende impossibile lo scambio dei Dispatcher durante gli Unit Test. Per testare i ViewModel o gli Use Case in modo deterministico usando il `TestDispatcher`, l'esecuzione fallirà o causerà flaky tests (test instabili).
**Soluzione Clean:** Qualsiasi `CoroutineDispatcher` o `CoroutineScope` deve essere iniettato tramite Hilt usando Qualifier specifici (es. `@IoDispatcher`, `@ApplicationScope`), permettendo di rimpiazzarli con un `StandardTestDispatcher` in fase di test.

## 6. Logica Condizionale in UI (Violazione OCP - Open/Closed Principle)
Il `BodyMapScreen` e altri composable sono farciti di controlli `if (variant.isBuiltIn)` o `when (previewSize)`. 
**Impatto:** Ogni volta che si aggiunge un nuovo tipo di "Vista" o variante anatomica, è necessario modificare file UI complessi, aumentando il rischio di introdurre bug.
**Soluzione Clean:** La risoluzione di questi stati deve avvenire a monte. Il ViewModel dovrebbe mappare la variante direttamente in un identificatore visivo standardizzato o usare il polimorfismo (tramite classi *Sealed*) in modo che la UI si limiti a "disegnare" l'interfaccia prescritta dal modello polimorfico senza processarne la logica condizionale.
