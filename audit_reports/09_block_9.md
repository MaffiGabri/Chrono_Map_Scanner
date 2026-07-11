# Block 9 - UI Components & Theme

## Diagnosi Architetturale
Questo blocco analizza l'interfaccia architetturale fine: i componenti isolati (`MoleLegend`, `MoleDetailsComponents`, `MoleMarker`).
L'uso dei colori tramite parsing hex string-to-int (`android.graphics.Color.parseColor`) è racchiuso all'interno dei componenti Compose (es: `Color(android.graphics.Color.parseColor(setting.hex))`), il che significa che in caso di errore hex invalido il blocco try/catch fallback esegue correttamente, ma farlo dentro i `@Composable` costantemente in ricomposizione suona leggermente inelegante.
La `MoleLegend` e il `HistoryItem` sono compatti e supportano bene la localizzazione, facendo uso corretto di `stringResource`.
In `HistoryItem`, l'immagine caricata è nuovamente un `AsyncImage` di Coil.

## Red Flags (Criticità e Anti-pattern)
- **Accessibility (A11y) Violations:**
  - Nel `HistoryItem`, le icone `Icons.Default.AutoAwesome`, `Icons.Default.Image` e `Icons.AutoMirrored.Filled.Notes` hanno `contentDescription = null`. Come specificato nelle memorie, "always provide meaningful contentDescriptions... Only use null if purely decorative AND accompanied by descriptive text." In questo caso la label testuale è presente accanto, ma in `MoleLegend` i bottoni e i cerchi di colore non hanno content description per lo screen reader.
  - La Row cliccabile in `HistoryItem` ha `.clickable { onClick() }` ma manca di proper semantic roles (`role = Role.Button`) e `onClickLabel`, compromettendo l'uso con TalkBack.
- **Color Parsing in Compose:** Parse continuo di costanti hex string in `Color` all'interno dei modifier (es in `MoleLegend`). Sarebbe più corretto farlo una volta sola nel ViewModel o come extension property pre-calcolata del `ColorSetting`.

## File Orfani e Codice Morto
Nessun file orfano tra i componenti UI analizzati in questo step. Il pattern di estrazione dei componenti in file dedicati è pulito e manutenibile.

## Modernizzazione
- Sostituire le primitive di interazione `.clickable` con quelle semanticamente complete o avvolgerle in `Surface(onClick = ...)` per abilitare out-of-the-box i ripple effects e l'accessibility completa per gli screen readers.
- Creare una val locale `ColorSetting.composeColor: Color` in un dominio Mapper per evitare il parsing della stringa `Color(Color.parseColor(hex))` all'interno della view gerarchica (risparmio di microsecondi ma buona norma architetturale contro i GC spikes in LazyLists).

## Piano di Refactoring Step-by-Step (Futuro)
1. Aggiungere le Semantic properties (`role`, `onClickLabel`) a tutte le Row modificate con `clickable` nei componenti (incluso `MoleLegend` pointer interactions).
2. Spostare il parsing Hex-to-Color in una utility Kotlin memorizzata (es. un `@Stable` class o una extension con cache) in modo da non eseguire `android.graphics.Color.parseColor(setting.hex)` ad ogni layout frame di `MoleLegend` o `MoleDetailsComponents`.
