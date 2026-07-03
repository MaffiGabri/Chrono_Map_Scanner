# Master Audit Base: Chrono Map Scanner

Questo documento rappresenta la sintesi globale e consolidata dell'Audit Architetturale Totale (Blocchi 1-6). Fornisce una panoramica esecutiva dello stato di salute dell'applicazione e definisce la Roadmap Definitiva con tutti i dettagli tecnici step-by-step per raggiungere la stabilità estrema con carichi di 1000+ elementi a 60fps.

## 1. Executive Summary

L'attuale architettura del progetto "Chrono Map Scanner" è funzionalmente valida per dataset piccoli (50-100 elementi), ma **architetturalmente insostenibile per il target di 1000+ elementi storicizzati**. Se rilasciata in questo stato, l'app subirebbe inevitabilmente **OutOfMemoryError (OOM) immediati**, vistosi cali di framerate sotto i 15fps durante lo scrolling/dragging e un severo battery drain in background. La causa principale risiede in un eccesso di affidamento a strati di astrazione (come Room `@Relation` reattive e Coil `AsyncImage` dinamici) senza pre-calcolo e ingegnerizzazione adatta alle moli massive.

## 2. Top 5 Red Flags Globali (Criticità Estreme)

1. **Collasso da Allocazione Composables (`MoleMarker` + Coil) [UI Layer]:**
   Generare 1000 nodi Compose paralleli, ciascuno con una richiesta asincrona di caricamento file crudo (`AsyncImage`), devasta il thread principale e l'Heap (OOM garantito).
2. **Overhead Reattivo del Database (`@Relation` + Flow) [Data Layer]:**
   L'utilizzo delle `@Relation` frammenta la lettura in innumerevoli fetch SQLite paralleli N+1, materializzando copie multiple di oggetti complessi a ogni singola emissione di `Flow`.
3. **Daemon Infinito in Background (`DataIntegrityScanner`) [Core Layer]:**
   Un loop asincrono infinito (`while(isActive)`) lanciato al boot dell'Application (`onCreate`) esegue operazioni di parsing e file system, uccidendo la batteria e il Doze Mode.
4. **Recomposition Globale al Dragging [Canvas Layer]:**
   Le operazioni geometriche (lettura offset e collisioni) avvengono nello strato di Composition, forzando Compose a invalidare l'intera mappa per ogni minimo movimento in pixel del neo, causando frame drop spaventosi.
5. **Leak Memory su Storico Lungo e Layout Nesting [Secondary UI]:**
   Nello storico, Coil carica immagini `File` senza constraint di memoria (`.size()`). Inoltre, liste annidate (`BackgroundSettings`) causano misurazioni infinite nel layout (`IllegalStateException`).

## 3. Piano di Modernizzazione Globale

L'architettura migrerà da un approccio "Lazy & Reactive" a un approccio **"Pre-Packaged & Pure"**:
*   **Database Flattening:** Abbandono degli ORM complessi in Room per query `LEFT JOIN` ultra-piatte, migrando tutte le stringhe di data a interi veloci (`Long`).
*   **Off-loading Background:** Demolizione del loop asincrono infinito. L'os delegherà la manutenzione periodica unicamente tramite `WorkManager`.
*   **ViewModel come Pre-Packager:** I ViewModel calcoleranno lo stato geometrico offline, idratando asincronamente cache di Bitmap L1 (`LruCache`) e isolando la logica di dominio dalla UI.
*   **Pure Canvas UI:** Sparirà l'intero strato Compose dei marker: la mappa disegnerà in modo imperativo puro 1000 elementi su un singolo canvas.

---

## 4. Roadmap di Refactoring Definitiva

Di seguito il consolidamento dettagliato, passo dopo passo, di tutte le azioni tecniche necessarie.

### Fase 1: Data & Storage (Le Fondamenta)
- **`Converters.kt` & Database Migration:** Modificare il mapping di `LocalDate` affinché usi `toEpochDay()` e `ofEpochDay()` (Long), eliminando il `LocalDate.parse(String)`. Preparare la Migration SQL.
- **`MoleDao.kt` (Flat Queries):** Riscrivere `getFlatMolesWithHistory` con subquery e `LEFT JOIN` (per ottenere sempre 1 sola riga per neo col record più recente). Sostituire `@Relation`. Cambiare la firma di `getMoleByIdWithHistory` da `Flow` a `suspend` one-shot. Aggiungere la query ultra-leggera `SELECT imagePath FROM history_entries` per il cleanup orfani, evitando di caricare enormi listati in memoria.
- **`BackupModels.kt`:** Sganciare le classi JSON dal dominio (rimuovere `Mole` e `UserSettings`), introducendo puri DTO (es. `MoleBackupDto`) per immunità ai cambiamenti di versione.
- **`MoleModels.kt`:** Sostituire la ricerca iterativa `BodySide.fromString` (che fa `.find` su loop) con una mappatura diretta O(1) tramite `when`.

### Fase 2: Background, Lifecycle & Setup (Risparmio Energetico)
- **`DataIntegrityScanner.kt`:** Demolire totalmente il file e il suo loop `while(isActive)`.
- **`DataIntegrityWorker.kt` (Nuovo):** Creare un `@HiltWorker` asincrono (`PeriodicWorkRequest`) per il cleanup e la verifica integrità delegato al WorkManager.
- **`ChronoMapScannerApplication.kt`:** Rimuovere il `startScanning()`. Aggiungere inizializzazione di `StrictMode.setThreadPolicy` e `VmPolicy` (sotto `BuildConfig.DEBUG`) per smascherare leak su Main Thread.
- **`MainActivity.kt` & `SettingsViewModel.kt`:** Sostituire il finto `delay(1000)` di importazione db. Connettere la modale alla reale emissione dei `WorkInfo` via WorkManager.
- **`FileRepository.kt` (Bitmap Utils):** Estrarre e centralizzare la complessa manipolazione Bitmap (inSampleSize, matrix rotation EXIF) in un metodo unificato a disposizione sia per il caricamento che per la manutenzione periodica.

### Fase 3: ViewModels & Pre-computazione (Off-Loading)
- **`BodyMapViewModel.kt` (Timeline & LRU):** Eliminare l'allocazione massiva di `cachedTimelineFlow`. Generare lo stato UI limitandosi strettamente ai record validi per la `sampledSelectedDate` visualizzata in quel frame. Introdurre e iniettare una `LruCache<String, ImageBitmap>` popolata asincronamente in thread IO per i thumbnail della timeline in corso.
- **`BodyMapViewModel.kt` (Geometria):** Riscrivere `snapMolePosition` sfruttando primitivi o Grid Spaziali O(1) invece di iterare 1000 oggetti con allocazioni temporanee ad ogni frame di drag.
- **`MoleDetailsViewModel.kt`:** Svuotare `MoleDetailsScreen` estraendo la logica di uscita condizionale (`showEmptyWarningDialog`) nel ViewModel tramite Single Event (`requestExit()`), insieme alle chiamate di rimozione file.
- **`ImageEditorViewModel.kt`:** Agganciare la logica di compressione JPEG duplicata alla nuova utility centralizzata creata in `FileRepository`.
- **`SettingsViewModel.kt`:** Astrarre i task massivi `NonCancellable` (es. `deleteProfile` completo di db e file) spostandoli in layer UseCase dedicati o nel Repository.

### Fase 4: UI Canvas & Reattività Estrema (Pure Canvas Rendering)
- **`MoleMarker.kt`:** Eliminare la view (o mantenerla solo ed esclusivamente per il singolo neo correntemente "draggato").
- **`BodyMapScreen.kt`:** Rimuovere il loop Compose della UI. Disegnare le preview colorate e i contorni dinamicamente all'interno di `Canvas { drawImage() }`, prelevando O(1) l'immagine dalla `LruCache`.
- **`BodyMapScreen.kt` (Defer State):** Isolare le letture reattive di `scale` e `offset` posizionandole rigorosamente all'interno del modificatore `Modifier.graphicsLayer { ... }` o nel blocco di draw, impedendo che lo strato di Layout/Composition venga invalidato durante il Pan&Zoom.

### Fase 5: Secondary UI (Flattening)
- **`BackgroundSettings.kt`:** Eliminare le `LazyColumn` innestate in visibilità animate per rimuovere crash di misurazione infiniti (Flattening layout).
- **`MoleDetailsComponents.kt`:** Aggiornare tutti i tag `AsyncImage` di Coil, applicando hard limits con `.size()` e `.crossfade(false)` per blindare la memoria durante lo scrolling dello storico profondo.
