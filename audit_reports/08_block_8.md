# Block 8 - UI Screens - Settings, Camera, Tools & Navigation

## Diagnosi Architetturale
Questi file contengono la navigazione (`Routes.kt`) modernamente implementata con Kotlin Serialization per Compose Navigation, una feature eccellente. `SettingsScreen.kt` gestisce un numero elevato di dialoghi e state interni, un po' farraginoso ma funzionante, tuttavia include un residuo di conflitto git evidentissimo che distrugge la struttura sintattica (in particolare in `AboutTab`).
`AutoCameraScreen` (letto parzialmente dalle memorie/struttura) interagisce con `CameraX` e logiche sperimentali AI.

## Red Flags (Criticità e Anti-pattern)
- **Conflitto GIT Irrisolto:** Nel file `SettingsScreen.kt` è presente un macro blocco di conflitto Git (`<<<<<<< HEAD`, `=======`, `>>>>>>> edd0a09`) non risolto. Questo causa la mancata compilazione del progetto intero se non viene sistemato.
- **State Hoisting in Settings:** `SettingsScreen` definisce svariati `MutableState` locali (es: `showAddProfile`, `showProfileOptions`). Per un'app robusta e testabile, andrebbero hoistati nel `SettingsViewModel`.
- **Navigazione con Oggetti di Dominio:** I `Routes` passano path assoluti stringa in `ImageEditorRoute(val imagePath: String)`. In Android Navigation Compose 2.8.x, passare URL/path complessi come stringhe native nel bundle serializzato può causare parsing error sui backslash. Andrebbero encodati in Base64 o passati via ViewModel condiviso/State.

## File Orfani e Codice Morto
Il codice scartato dal merge conflict in `SettingsScreen.kt` è essenzialmente codice "morto in attesa di morte", da ripulire urgentemente (il blocco HEAD/edd0a09).

## Modernizzazione
- **Uri Encoding in Navigation:** In `ImageEditorRoute`, prevedere la sanificazione degli URI prima della serializzazione o usare `SavedStateHandle` per passare parametri grossi anziché affidarsi alla route url-like.
- **Risoluzione Hardcoded Text:** Controllare sempre che tutte le stringhe di copyright e link nel "Tempio" dell'About Tab rispettino i requirements di UX minimalista e stringhe tradotte.

## Piano di Refactoring Step-by-Step (Futuro)
1. Risolvere *immediatamente* il conflitto Git in `SettingsScreen.kt`, fondendo la UI o scegliendo l'HEAD corretto, poiché rompe il compiler.
2. In `Routes.kt` e relativi chiamanti, inserire `URLEncoder.encode` sui percorsi file, o delegare il passaggio di ID complessi alla sessione state del ViewModel associato a `SavedStateHandle`.
