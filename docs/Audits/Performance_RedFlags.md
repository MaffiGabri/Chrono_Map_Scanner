# Performance_RedFlags

> Analisi estrema sulle prestazioni (Performance Engineering) dell'architettura attuale. L'obiettivo è individuare ogni causa di lag, stutter (drop frame sotto i 16ms), Battery Drain e Out Of Memory per garantire reattività glaciale e a 60fps con dataset >1000 record.

## 1. Battery Killers & CPU Hogs (Gravità Massima)

*   **Daemon Infinito di Integrità (`DataIntegrityScanner`):**
    *   **Criticità:** Lanciato in `Application.onCreate()`, questo job esegue un loop `while(isActive)` a livello globale interrogando Room e file system. Anche se contiene dei `delay()`, mantiene l'app costantemente "in allerta", impattando i context switch della CPU, consumando cicli IO e impedendo al dispositivo di entrare correttamente in Doze mode profondo.
    *   **Extreme Fix:** Demolizione totale della classe. Sostituire con `WorkManager` (tramite `PeriodicWorkRequest`) schedulato per girare solo di notte, in ricarica o su rete Wi-Fi non a consumo (`Constraints.Builder()`).

## 2. Heap Bloat & OOM (Out Of Memory) Risks

*   **Collasso da Allocazione Composables (`MoleMarker` + Coil):**
    *   **Criticità:** L'UI genera 1000 Composable `MoleMarker` simultanei. Ognuno innesca una richiesta Coil concorrente per leggere e idratare un file immagine dal disco su 1000 nodi separati. Questo è il peggior anti-pattern immaginabile in Compose. Il runtime esaurirà l'Heap JVM e frammenterà la memoria, provocando Garbage Collection prolungate o un crash `OutOfMemoryError` in pochi millisecondi al caricamento.
    *   **Extreme Fix:** 
        1. Distruzione totale dei nodi Compose per il rendering massivo.
        2. Istituzione di una Memory Cache L1 pre-calcolata rigorosamente dimensionata: `LruCache<String, ImageBitmap>`.
        3. Il ViewModel in IO-thread pre-calcola/estrapola solo i sample in bassa risoluzione e idrata la cache. Il Canvas principale si limita a mappare (in tempo costante O(1)) gli id alle Bitmap pre-pronte.

*   **Storico Dettagli (Image Decode Leak):**
    *   **Criticità:** Il `MoleDetailsScreen` innesca il caricamento asincrono (`AsyncImage`) passando direttamente istanze crude di `File` alle miniature dello storico, senza vincoli espliciti sulle dimensioni massime decodificate in RAM.
    *   **Extreme Fix:** Hard-limit della decodifica tramite i constraint `.size()` di Coil e abilitazione esplicita del `crossfade(false)` in contesti di Lazy scroll, per minimizzare l'overhead dell'animazione su liste molto dense.

## 3. Database & I/O Bottlenecks

*   **Il "Relationship" Overhead di Room (`@Relation`):**
    *   **Criticità:** Il DAO sfrutta annotazioni `@Relation` nidificate per ricostruire il grafo tra Moles e Storico. Sotto il cofano, questo emette query SQLite N+1. All'interno di un flusso reattivo (`Flow`), ogni lieve modifica al DB attiva un effetto a cascata che interroga il disco ripetutamente, distruggendo l'I/O.
    *   **Extreme Fix:** Eliminare la pigrizia architetturale. Creare query piatte `LEFT JOIN` altamente indicizzate (sui campi ID e Timestamp) mappandole in POJO/DTO singoli (Plain Old Java Objects).

*   **String Parsing nel DB (`LocalDate`):**
    *   **Criticità:** Il `Converters.kt` mappa un campo cruciale come la Data usando stringhe ISO-8601 serializzate. Con >1000+ entry per neo, il parser JVM è costretto ad allocare e scartare migliaia di oggetti `String` e `LocalDate` in tempo reale per ogni query.
    *   **Extreme Fix:** Conversione binaria obbligatoria. Convertire tutti i timestamp in `Long` (Epoch Days/Milli). Trasferimento 1:1 dalla CPU al Disco senza allocazioni intermedie di memoria O(1).

## 4. UI Thrashing & Render Lag (Budget 16ms/frame)

*   **Recomposition globale al trascinamento (Dragging):**
    *   **Criticità:** Il calcolo geometrico (`calculateMolePosition` e `snapMolePosition`) attraversa e legge lo stato su tutto il set reattivo. Spostare 1 neo costringe Compose a invalidare e ri-disegnare tutto il grafo UI per capire quali "bounds" siano stati inficiati, scendendo sistematicamente sotto i 60fps.
    *   **Extreme Fix:** "Defer State Reads". Le variabili spaziali globali (pan, offset, zoom) e le coordinate del neo in movimento vanno lette *esclusivamente* dentro la lambda del `drawScope` o tramite `.graphicsLayer { ... }`. Così facendo, Jetpack Compose salterà la fase algoritmica (Composition) e geometrica (Layout) passando istantaneamente alla scheda video (Render phase).

*   **Calcolo Costoso On-Main-Thread:**
    *   **Criticità:** Pre-valutare le `MoleUiModel` (includendo parsing delle dipendenze per 30 date in `TimelineSlider`) causa ricalcoli sincroni pesanti all'inizio dello scope UI.
    *   **Extreme Fix:** Elaborazione off-loaded totale. I ViewModel devono agire da "Pre-Packager". Tutte le trasformazioni/mapping del dominio (Da `Mole` a `MoleUiModel` con relative `Color` parsing) si eseguono nel `Dispatchers.Default` (Worker thread) ed emesse tramite un `StateFlow` congelato e in sola lettura. La UI deve essere "tonta" e non processare nulla, limitandosi a stampare dati perfetti e finiti.

## 5. LazyColumn Nesting Crash
*   **Misurazione Infinita:**
    *   **Criticità:** `BackgroundSettings` annida logiche scrollabili della stessa direzione. Questo provoca, ad un certo livello di nesting, un'eccezione `IllegalStateException` legata al limite infinito dell'altezza.
    *   **Extreme Fix:** Flattening totale dei nodi Lazy. Usare blocchi dinamici iterativi (es. singola LazyColumn con header/items mischiati logici) anziché widget impilati.
