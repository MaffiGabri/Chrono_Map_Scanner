# Block 6 - ViewModels - Settings, Images, and Split View

## Diagnosi Architetturale
Il blocco contiene ViewModel per feature accessorie (Settings, Editor di Immagini e SplitView).
`ImageEditorViewModel` carica e manipola bitmap in memoria in un background thread e le riduce (`inSampleSize`), gestendo bene la chiusura dei file e il riciclo (`recycle()`), ma anche qua duplica la logica per la miniatura.
`SettingsViewModel` gestisce Profile e le preferenze locali interfacciandosi con DataStore, delegando un lavoro massivo (GlobalReport) su background coroutine.
`SplitViewViewModel` filtra `Mole` per ottenere solo le storie con immagini.

## Red Flags (Criticità e Anti-pattern)
- **Image Editor Thumbnail Duplication:** Ancora una volta (come in `FileRepository` e `DataIntegrityWorker`), la generazione della thumbnail è copiata-incollata in `ImageEditorViewModel.cropAndSaveImage`. Questo viola il principio DRY e aumenta il rischio di bug isolati.
- **`SplitViewViewModel` Eager Loading OOM:** Anche in `SplitViewViewModel` viene richiamato `moleRepository.getMoleByIdWithHistory(moleId)` per mappare *in memoria* tutta la History con photoHistory prima di filtrare. Se i punti hanno 200 storici, questo mapping appesantisce il main model e il garbage collector senza motivo se non per lo Split View che magari mostra solo 2 foto alla volta. Va eseguita una filter query lato DB.

## File Orfani e Codice Morto
Non sembrano esserci test orfani diretti, ma `BackgroundSettingsViewModel` non è stato ispezionato riga per riga se non le sue dichiarazioni, poiché il volume logico era focalizzato sugli editor. I metodi `decodeSampledBitmapFromFile` in `ImageEditor` sono candidati per l'estrazione in utility condivisa.

## Modernizzazione
- **Image Loading:** Sebbene l'ImageEditor necessiti del Bitmap crudo per croppare, l'affidamento a factory native potrebbe essere bypassato usando Coil per i caricamenti asincroni, oppure estraendo `decodeSampledBitmapFromFile` in un `ImageUtils` testabile.
- **SplitView Database Filter:** Sostituire `getMoleByIdWithHistory` con un `getHistoryWithImagesForMole(moleId: String)` a livello Room Dao (eseguita con un Flow) che scarichi dal DBMS solo i record che contengono foto e senza tirare su l'intera Entità Neo, ottimizzando CPU e Heap.

## Piano di Refactoring Step-by-Step (Futuro)
1. Estrarre il codice di scaling / thumbnail creation in un `ImageUtils.kt` (singleton o Hilt Injectable) e farlo usare da `DataIntegrityWorker`, `FileRepository` e `ImageEditorViewModel`.
2. Sostituire la query in `SplitViewViewModel` con un metodo dedicato del Dao che restituisca nativamente solo le `HistoryEntry` ordinate con `imagePath IS NOT NULL`, anziché filtrare l'intera mole collection.
