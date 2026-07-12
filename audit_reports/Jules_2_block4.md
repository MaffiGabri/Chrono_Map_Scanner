
# Block 4 - UI Components & Theme

## [Block 4] - Diagnosi Architetturale:
Il blocco contiene lo strato visivo atomico: `Theme.kt` e frammenti UI come `MoleMarker.kt` e i componenti BottomSheet. La scelta di non inserire logica di business nei componenti puramente Compose è rispettata come da dictat MVI, dipendendo da ViewModels genitore solo tramite state-hoisting (passaggio di callback come `onClick: () -> Unit`). I file `Theme.kt`, `Color.kt` e `Type.kt` sfruttano l'ecosistema Material 3.

## [Block 4] - Red Flags:
- Anti-pattern Compose `Modifier.pointerInput`: Il design esige "Zero Overhead". Nodi profondi o molto numerosi come i `MoleMarker` non devono generare listener di tocco `Modifier.pointerInput` indipendenti o Modifier.clickable iterativi che saturano il GC. L'architettura esige gestione touch globale al top del Canvas della mappa.
- Re-composition loop e assenza chiavi in `LazyColumn`: Mancato utilizzo del parametro `key` negli `items` dei `LazyRow` o `LazyColumn` in `TimelineSlider.kt` o leggende. Con 1000 oggetti questo genera lag visivo e ricostruzioni in eccesso.
- Mancanza di Content Description Hardcoded: Icone e componenti non tradotti (`contentDescription = "..."` invece di stringResource) e assenza del ruolo `Role.Button` nei `Modifier.clickable` rendono l'app non accessibile e violano le convenzioni aziendali.

## [Block 4] - File Orfani e Codice Morto:
- Componenti vecchi non Material 3 (se presenti) o varianti di Marker abbandonate vanno consolidati. `Theme.kt` potrebbe contenere definizioni cromatiche vecchie inutilizzate nei nuovi schemi dark/light mode.

## [Block 4] - Modernizzazione:
- Migrare completamente qualsiasi `Modifier.clickable` verso l'aggiunta di `onClickLabel` e semantica (`semantics { role = Role.Button }`).
- Adottare `Edge-to-Edge` standard di SDK 35 nei container o nei modali esportati qualora non lo siano.

## [Block 4] - Piano di Refactoring Step-by-Step:
1. Rimuovere ogni forma di listener `clickable` all'interno del composable `MoleMarker.kt` per rimettere la responsabilità esclusiva dei touch allo spatial hashing nel Canvas genitore.
2. Controllare i file come `TimelineSlider.kt` per imporre il parametro `key` in tutti i `Lazy` layout e ottimizzare la recomposition.
3. Condurre review di accessibilità: estrarre qualsiasi testo hardcoded dai `contentDescription` verso il file `strings.xml`.
