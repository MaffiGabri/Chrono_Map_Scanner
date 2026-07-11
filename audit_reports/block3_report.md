# Blocco 3 - Diagnosi Architetturale
Il blocco 3 gestisce logiche di business pesanti.
`OfflineMoleRepository` aggrega chiamate DAO (es. risoluzione percorsi).
`AlgorithmicMoleDetector` esegue thresholding in tempo reale su Bitmap.
`GlobalReportGenerator` genera file PDF usando `PdfDocument` con calcoli grafici intensivi.

# Blocco 3 - Red Flags
- **`OfflineMoleRepository.getMolesWithHistory`**: Ripete l'anti-pattern del DAO. Preleva l'intera history dal DB in un sol colpo usando coroutines. Rischio Out-Of-Memory altissimo per il limite di 1000+ punti.
- **`AlgorithmicMoleDetector.detectInCenter`**: La conversione RGB -> Luminance è implementata in software con calcoli float eseguiti per ogni pixel dell'array, iterando su array in memoria. Su frame continui provenienti da CameraX provocherà drain di batteria e surriscaldamento, oltre ad allocation GC per `IntArray` ad ogni frame.
- **`GlobalReportGenerator.generateGlobalPdf`**: Disegna immagini sul canvas del PDF (`PdfDocument`). L'oggetto PDF in Android richiede molta memoria e se deve includere (in base ai loop più sotto) centinaia di bitmap originali per le history senza forte downscaling/compressione, esaurirà in un colpo la heap disponibile (Limite RAM per-process su Android è tipicamente ~256MB - 512MB).

# Blocco 3 - File Orfani e Codice Morto
- Nessun file puramente "orfano" ma logiche troppo arcaiche. Il `GlobalReportGenerator` ha bisogno di un refactor drastico se deve stampare 1000 imperfezioni in un PDF.

# Blocco 3 - Modernizzazione
- **CameraX Image Analysis**: `AlgorithmicMoleDetector` dovrebbe sfruttare `ImageProxy` di CameraX direttamente, usando il piano `Y` del formato YUV_420_888 che è GIÀ una mappa di luminanza grayscale, saltando l'allocazione di bitmap e i calcoli RGB->Lum. (E' indicato per SDK moderni).
- **PDF e PDF-Generator**: Per generare PDF immensi, andrebbe valutato un streaming (es. creare pagine, scriverle e flussarle su disco in stream) e implementato downsampling severo (es. caricare le immagini history a `100x100` max invece di usare le dimensioni originali del crop cropper).

# Blocco 3 - Piano di Refactoring Step-by-Step
1. Modificare `OfflineMoleRepository` per allinearsi alle query DAO limitate o paginate descritte nel Blocco 2.
2. Risolvere l'algoritmo di Detection: Riscrivere l'analisi dell'immagine di `AlgorithmicMoleDetector` perché riceva un `ImageProxy` e usi il piano di luma (Y) al posto delle operazioni manuali RGB.
3. Inserire vincoli restrittivi o memory-pool al caricamento di Bitmap all'interno di `GlobalReportGenerator`.
