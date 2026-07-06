# Ultimate Refactoring Blueprint: Nei Map (1000+ Scalability)

In qualità di Chief Android Architect, ho analizzato i report di audit forniti (`Audit Jules.md` e `Report final.md`) e le direttive del progetto. L'obiettivo primario è inequivocabile: garantire i 60fps con oltre 1000 punti immagine (nessun OOM, nessun lag) e risolvere i crash di integrità referenziale legati ai profili. 

Di seguito la sintesi direttiva e la roadmap sequenziale.

## 1. Il Consenso Architetturale (Priorità Assoluta)
Entrambi gli audit concordano su vulnerabilità critiche che minacciano la sopravvivenza stessa dell'app sotto stress:

*   **Violazione Letale del Canvas (OOM & Jank):** Eseguire I/O (richiesta di thumbnail) o parsing di stringhe esadecimali all'interno del `DrawScope` di Compose distrugge il budget di 16ms del frame. Anche l'uso di `clipPath` su 1000 elementi satura la GPU. **Consenso:** La UI deve essere passiva al 100%. Il ViewModel deve pre-calcolare `ImageBitmap` (via Coil in background) e i `Color`, fornendoli già pronti al render.
*   **Collision Detection $O(N)$:** La funzione `snapMolePosition` esegue una ricerca lineare pesante per risolvere le collisioni, inaccettabile per 1000+ marker. **Consenso:** Deve adottare lo *Spatial Hashing* a griglia già in uso per il tap detection.
*   **GC Churn della Fotocamera:** L'algoritmo di rilevazione nei (`AlgorithmicMoleDetector`) istanzia giganteschi `Bitmap` ad ad ogni frame, per poi ritagliarli. **Consenso:** L'algoritmo deve operare direttamente sul buffer YUV per abbattere l'allocazione a zero, o essere sostituito da RenderScript/TFLite.
*   **Database Bottleneck:** La query `getMolesAtDate` diventerà un collo di bottiglia senza indici composti. 

## 2. Risoluzione dei Conflitti
Dove i report divergevano, ecco le decisioni architetturali definitive per Android 15+:

*   **Crash su "Keep/Discard Moles" & Integrità Referenziale:**
    *   *Jules* suggerisce di usare transazioni SQL manuali prima del flush su disco.
    *   *Final Report* suggerisce di normalizzare il DB introducendo una `ProfileEntity` e usare strict `@ForeignKey(ON UPDATE CASCADE, ON DELETE CASCADE)`.
    *   **Decisione:** **Vince il Final Report.** L'approccio di Jules è error-prone. Introdurre chiavi esterne native delega la cancellazione/aggiornamento a cascata a SQLite (in C++), garantendo velocità $O(1)$ e consistenza atomica. Questo renderà inutile molta della logica manuale del Worker.
*   **Gestione Eliminazione File e Background Work:**
    *   *Jules* vuole lanciare un WorkManager on-off per ogni eliminazione di file.
    *   *Final Report* evidenzia che il `DataIntegrityWorker` va in Out of Memory caricando 1000+ elementi in un colpo solo e drena la batteria girando ogni 15 minuti.
    *   **Decisione:** **Approccio Ibrido.** Useremo WorkManager one-off per le cancellazioni esplicite lanciate dall'utente (per pulizia istantanea), **ma** riformatteremo il `DataIntegrityWorker` impostandolo su base settimanale (7 giorni) con `RequiresCharging` e `RequiresDeviceIdle`. Inoltre, sarà obbligatorio l'uso di `LIMIT` e `OFFSET` per chunkizzare la lettura dal DB (max 50 nei alla volta) evitando l'OOM.
*   **Caching Immagini UI:**
    *   *Jules* suggerisce `drawWithCache` in Compose.
    *   *Final Report* suggerisce di uccidere la logica `LruCache` custom e demandare tutto a Coil pre-caricando i `BitmapShader`.
    *   **Decisione:** **Vince il Final Report.** Il caching a livello Compose non salva dalle chiamate I/O iniziali bloccanti. Coil è lo standard industriale: il ViewModel userà Coil per fetchare e applicare il `BitmapShader`, passando un oggetto `DrawScope`-ready a Compose.

## 3. Falsi Positivi Scartati (Over-engineering)
Le seguenti indicazioni sono state ignorate per mantenere il codice performante e snello:

*   **Scartato: Utilizzo del `QuadTree` (Jules).** Suggerire un QuadTree per le collisioni 2D è over-engineering e introduce costose allocazioni per i nodi dell'albero. L'attuale *Grid Spatial Hashing* (array/liste piatte) consigliato dal Final Report è molto più memory-friendly ed efficiente per il nostro use case.
*   **Scartato: Database Views (Jules).** Sostituire le query con *Room Database Views* aggiunge un layer di rigidità in caso di refactoring futuro. L'approccio con *Window Functions* e *Indici Composti* (Final Report) è più che sufficiente per le query SQL moderne.

---

## 4. La Roadmap Definitiva (Step-by-Step)
Questa è la sequenza esatta di esecuzione. Le priorità sono invertite rispetto al tipico refactoring: prima si spengono gli incendi (Crash/OOM/Jank), poi si pulisce.

### Fase 1: Sopravvivenza (Anti-OOM & Frame Lock)
1.  **Canvas Rendering $O(1)$ (`BodyMapScreen.kt` & `BodyMapViewModel.kt`)**
    *   Rimuovere la funzione `getThumbnail` (IO Proxying) dal `DrawScope`.
    *   Il ViewModel deve caricare le thumbnail tramite Coil (in background) e wrapparle in un `BitmapShader` dentro al `MoleUiModel`.
    *   Sostituire `clipPath` con una singola `drawCircle` (usando lo Shader).
    *   Implementare il **Viewport Culling**: non disegnare elementi le cui coordinate non intersecano il rect visibile dello zoom.
2.  **Worker Battery Drain & OOM (`DataIntegrityWorker.kt`)**
    *   Spostare la frequenza da 15 minuti a 7 giorni (`SkinHistoryScannerApplication.kt`), aggiungendo constraint `RequiresDeviceIdle` e `RequiresCharging`.
    *   Implementare query impaginate con `LIMIT 50` / `OFFSET` nel DAO per estrarre i record in batch, distruggendo il rischio di Heap Overflow.
3.  **Camera GC Churn (`AutoCameraScreen.kt` & `AlgorithmicMoleDetector.kt`)**
    *   Modificare l'ImageAnalyzer per estrarre solo il rect di cropping centrale direttamente dal piano YUV del frame, azzerando le allocazioni massive di Bitmap per ogni frame a 30fps.

### Fase 2: Scalabilità Geometrica (1000+ Markers)
4.  **Matematica $O(1)$ (`BodyMapViewModel.kt`)**
    *   Riscrivere `snapMolePosition`. Eliminare la ricerca euclidea lineare $O(N)$ iterativa.
    *   Innestare la logica di *Spatial Hashing* (già usata nel tap detection) per risolvere gli offset in tempo costante calcolando le distanze solo con gli elementi nell'intorno del bucket (vicini 3x3).

### Fase 3: Fondamenta Dati & Integrità (Normalizzazione DB)
5.  **Profile Entity & Cascades (`Room Database`)**
    *   Creare `ProfileEntity.kt`.
    *   Modificare `MoleEntity` e `BackgroundCategoryEntity`: sostituire il campo raw `profileName` con `profileId` provvisto di `@ForeignKey(ON UPDATE CASCADE, ON DELETE CASCADE)`. 
    *   Questo risolve nativamente il crash del "Keep/Discard" senza bisogno di transazioni manuali o logiche a strascico nel Worker.
6.  **Indici e Subqueries (`MoleDao.kt`)**
    *   Aggiungere un `@Index(value = ["mole_id", "date"])` su `HistoryEntryEntity`.
    *   Ottimizzare `getMolesAtDate` per usare le Window Functions SQLite invece delle subquery aggregate, per azzerare la latenza sul filtraggio storico di 1000+ marker.

### Fase 4: Pulizia Architetturale (Clean up)
7.  **Compilazione KSP (`build.gradle.kts`)**
    *   Rimuovere l'obsoleto `kotlin-kapt` e migrare le ultime dipendenze Hilt su `ksp` puro per allineare il codice a `ARCHITECTURE.md`.
8.  **Dead Code & Pre-Calcoli UI**
    *   Rimuovere l'orfano `MoleMarker.kt`.
    *   Spostare `Color.parseColor` e la logica temporale (`LocalDate.now()`) dai Composable (es. `DateHeader.kt`) e farle processare dal ViewModel, che fornirà alla UI dati terminali.
