
# Block 5 - UI Screens & Navigation

## [Block 5] - Diagnosi Architetturale:
Le Screen Compose (`BodyMapScreen.kt`, `MoleDetailsScreen.kt`, `SettingsScreen.kt`) implementano la vista principale. Sono correttamente disaccoppiate in file separati e abbinate a data class UiState immutabili (`BodyMapUiState.kt`). La navigazione con `ChronoMapNavGraph.kt` sfrutta Hilt-Navigation-Compose per iniettare e formare scope corretti. Esistono screen prototipali come `AutoCameraScreen.kt`.

## [Block 5] - Red Flags:
- Logica di Business in Compose: Esaminando i file come `BodyImageUtils.kt` o lo screen della mappa, eventuali calcoli geometrici, parsing di nomi file, o filtri applicati a liste enormi `1000+` direttamente nelle funzioni annotative `@Composable` violano gravemente il dictat architetturale (MVI ibrido). Compose riceve payload "puri e visivi".
- Caricamento di Immagini Bloccanti: Bisogna usare rigorosamente `AsyncImage` (Coil Compose) nei dettagli per caricare `Uri` string. Verifiche esplicite `File(path).exists()` all'interno delle pipeline composable forzano I/O sul main thread.

## [Block 5] - File Orfani e Codice Morto:
- Lo screen `ImageEditorScreen.kt` o lo screen sperimentale `AutoCameraScreen.kt` qualora falliti, disconnessi o dipendenti da vecchie librerie. Occorre segregarli e valutarne l'integrazione con ML-Kit nel futuro.
- `SplitViewScreen.kt` se la visualizzazione a pannelli laterali si dimostrasse ridondante o non aggiornata coi requisiti recenti M3.

## [Block 5] - Modernizzazione:
- Adottare le `Type-Safe Routes` (Sealed Data Classes / Serialization) di Compose Navigation 2.8+ invece di costanti e URL string-based `route = "screen/{id}"` in `Routes.kt`.
- Aggiornare CameraX analyzer in `AutoCameraScreen` sfruttando estensioni coroutines più moderne al posto di executors base se applicabile, ed esaminare migrazione a ML Kit.

## [Block 5] - Piano di Refactoring Step-by-Step:
1. Migrare interamente `ChronoMapNavGraph.kt` e `Routes.kt` alle rotte Strongly-Typed introdotte nella Navigation Compose >2.8.
2. Spostare tutta la logica condizionale complessa (come il ricalcolo degli sfondi) fuori da `BodyImageUtils.kt` o chiamate `@Composable` per trasferirla nei ViewModels corrispondenti, mantenendo state-hoisting puro.
3. Rimuovere letture di I/O sincrone da Coil in `MoleDetailsScreen`, affidando la validazione dei path a passaggi precedenti in `FileRepository`.
