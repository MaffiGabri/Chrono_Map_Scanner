
# Block 3 - ViewModels & DI

## [Block 3] - Diagnosi Architetturale:
I ViewModels orchestrano il pattern MVI/MVVM esponendo gli Stati UI (es. `BodyMapUiState`) immutabili tramite `StateFlow`. L'uso di Hilt e KSP per il DI nei file Module (`DatabaseModule`, `CoroutineScopesModule`, `RepositoryModule`) è moderno e pulito. L'obiettivo architettonico di mantenere i calcoli intensivi lontano dal layer Compose è implementato tramite `Dispatchers.Default` in questi ViewModels.

## [Block 3] - Red Flags:
- Limite Type-Safety in Kothin Flow: Kothin standard `combine` non supporta il type check statico superati i 5 `Flow`. I ViewModels molto complessi come `BodyMapViewModel` (che combinano filtri, setting, punti, selezioni, varianti di background) rischiano di subire "unchecked cast" a `Array<Any?>` causando crash di runtime se i flussi sono più di cinque.
- Leak nel ciclo di vita `viewModelScope`: Attenzione al `launch` nel `MoleDetailsViewModel` durante le navigazioni all'indietro se l'operazione non è terminata; o salvataggi di note che collidono.
- Calcoli su UI Thread: Bisogna esaminare `BodyMapViewModel.kt` per garantire che lo `Spatial Hashing` (smistamento su griglia dei marker per le interazioni di touch) avvenga effettivamente nel `Dispatchers.Default` come specificato nei requisiti e mai bloccando le pipeline Compose.

## [Block 3] - File Orfani e Codice Morto:
- Nessun modulo Dagger vecchio da rimuovere in quanto usa già KSP e Hilt standardizzato.
- File o classi ViewModel deprecate che appartengono a feature prototipo mai terminate (controllare `ImageEditorViewModel.kt` se la UI per l'editing dell'immagine non è usata o completata).

## [Block 3] - Modernizzazione:
- Utilizzo obbligatorio di tuple logiche (Data Classes) nei `combine` di Flow che superano i 5 flussi per reintrodurre la Type-Safety.
- Sostituire `MutableStateFlow` massivi con approcci in stile `WhileSubscribed(5000)` per preservare la batteria qualora l'app vada in background.

## [Block 3] - Piano di Refactoring Step-by-Step:
1. Refactor in `BodyMapViewModel` e `MoleDetailsViewModel`: raggruppare multipli `Flow` interni in classi Tuple intermedie per evitare il limite di arità (>5) del `combine` standard e risolvere potenziali unsafe casts.
2. Controllare le implementazioni dei filtri in `BodyMapViewModel` assicurandosi che i 1000 marker passino attraverso lo Spatial Hashing nel `Dispatchers.Default`.
3. Verificare i `Hilt Modules` in `di/` per l'inserimento esplicito dei `CoroutineDispatchers` iniettabili facilitando il testing (mocking).
