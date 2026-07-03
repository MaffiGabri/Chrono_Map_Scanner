# Ultimate Refactoring Blueprint: Chrono Map Scanner (SDK 36)

> **Redatto da:** Chief Android Architect & Staff Engineer
> **Obiettivo:** Scaling estremo (1000+ punti immagine), 60fps solidi, zero OOM, e aderenza rigorosa a `ARCHITECTURE.md`.

Questo documento sintetizza, risolve e unifica le valutazioni architettoniche degli agenti precedenti, incrociandole con le direttive ufficiali del progetto (`ARCHITECTURE.md` e `README.md`). L'obiettivo è correggere le violazioni dove il codice non rispetta le specifiche originali di design, ingegnerizzando il sistema per i requisiti reali di performance estrema.

---

## 1. Il Consenso Architetturale (Priorità Assoluta)

Le analisi e la documentazione concordano su vulnerabilità architetturali che causeranno il collasso dell'app in produzione:

*   **OOM Termico per Over-Rendering (Compose + Coil):** L'approccio attuale di instanziare 1000+ nodi `@Composable` (`MoleMarker`) massivi con intercettazione di tocco individuale distrugge l'Heap e vìola la direttiva dell'architettura di gestire il tocco a livello globale.
*   **Daemon Assassino (`DataIntegrityScanner`):** L'implementazione attuale usa un ciclo `while(isActive)` in `Application.onCreate`, in totale violazione del documento di architettura che prescrive un processo asincrono (WorkManager periodico) per l'auto-pulizia.
*   **Choking del Main Thread per Geometria (`snapMolePosition`):** Mentre lo *Spatial Hashing* per il tocco è già architettonicamente previsto, la funzione sincrona `snapMolePosition` blocca il thread UI durante il dragging, mancando di delegare i calcoli pesanti al Worker thread (`Dispatchers.Default`) come richiesto dal pattern MVI del progetto.
*   **Data Layer N+1 (Overhead `@Relation` nei Flussi UI):** L'uso di `@Relation` all'interno di stream reattivi (`Flow`) per alimentare la UI genera un Memory Churn inaccettabile. Come esplicitato nel file `ARCHITECTURE.md`, questo è un anti-pattern vietato.

---

## 2. Risoluzione dei Conflitti e Adattamento al Contesto

Durante l'audit sono emersi consigli in contrasto tra loro o con le linee guida ufficiali del progetto. Ecco le decisioni per il refactoring:

> [!CAUTION]
> **Conflitto 1: Demolizione Totale di `@Relation` vs. Pura Regola Architetturale**
> I report suggerivano di rimuovere `@Relation` *ovunque*. Tuttavia, `ARCHITECTURE.md` è chiaro: `@Relation` è **vietato per flussi UI**, ma **raccomandato per operazioni `suspend` one-off** (es. Export Backup e singola Detail Screen).
> **Soluzione:** Modificheremo `MoleDao` in modo ibrido. La `BodyMapScreen` e i flussi massivi useranno rigorosamente DTO piatti e `LEFT JOIN`. Il modulo Backup manterrà e sfrutterà le `@Relation` per assemblare i JSON in modo pulito ed efficiente in operazioni one-off.

> [!CAUTION]
> **Conflitto 2: Astrazione Funzionale vs. Pre-Calcolo Bruto (MVI)**
> *Clean Code* suggeriva decine di `UseCase` microscopici per frammentare la logica.
> **Soluzione:** L'architettura ufficiale impone che il ViewModel faccia le mappature e non deleghi ciecamente a componenti esterni frammentati se non necessari. Il ViewModel sarà un **Pre-Packager**. Inoltre, per rispettare il vincolo di `ARCHITECTURE.md` sulla sicurezza dei tipi in `combine`, incapsuleremo i flussi reattivi multipli in Tuple/Data Class quando superano i 5 parametri, evitando array non tipizzati (Unchecked cast).

> [!CAUTION]
> **Conflitto 3: Polimorfismo UI vs. Canvas Nativa**
> *Clean Code* consigliava classi `Sealed` per far gestire dinamicamente i marker alla UI Compose.
> **Soluzione:** La UI Compose non deve compiere calcoli. Elimineremo i nodi `MoleMarker` per la massa dei punti. Si utilizzerà un singolo **Canvas puro** (`drawCircle`/`drawImage`). La UI non deciderà varianti, ma itererà su primitivi forniti in `Dispatchers.Default` dal ViewModel. L'intercettazione dei Tap avverrà solo alla radice (Root Canvas) delegando allo Spatial Hashing già presente.

---

## 3. Falsi Positivi Scartati (Over-Engineering Vietato)

I seguenti consigli **NON** saranno implementati perché contraddicono le scelte architetturali di base o sono controproducenti:

1.  **"Implementare lo Spatial Hashing da zero" (Base Report):** Scartato come task nuovo. Lo Spatial Hashing esiste già. Il task reale è riparare le funzioni accessorie (`snapMolePosition`) che attualmente bypassano l'ottimizzazione bloccando il Main Thread.
2.  **"Utilizzare classi Sealed per la risoluzione condizionale in UI":** Scartato. Le varianti verranno risolte nel ViewModel che assegnerà parametri grafici "primitivi" al DTO visivo prima di spingerlo alla UI.
3.  **"Simulare reattività tramite Flow anche per letture massive":** Scartato. Il documento architetturale vieta le query esplosive per le liste massive. Si useranno `suspend` one-shot e un'osservazione puntuale della finestra visualizzata, per evitare che la UI cerchi di re-istanziare i 1000+ marker ad ogni millisecondo.

---

## 4. La Roadmap Definitiva

L'esatta sequenza di esecuzione del refactoring, bilanciata sui report e sulle direttive di `ARCHITECTURE.md` (Target SDK 36, Kotlin Strict):

### FASE 1: Ripristino Conformità Architetturale (Rimozione Crash/OOM)
1.  **Demolizione `DataIntegrityScanner`**: Uccidere il loop `while(isActive)` nel costruttore globale. In base ad `ARCHITECTURE.md`, va sostituito con un `WorkManager` (PeriodicWorkRequest) per la pulizia silenziosa senza bloccare il thread UI.
2.  **Abbattimento Composti UI Massivi (`BodyMapScreen`)**: Distruggere il `forEach { MoleMarker() }`. Passare al rendering massivo su `Canvas` puro. I Tap vengono già intercettati dal Canvas parent, eliminare i `pointerInput` annidati inesistenti o residui.
3.  **LruCache Memory Policy**: Limitare Coil su `AsyncImage` nelle schermate secondarie con `.size()` rigidi e `.crossfade(false)`. Integrare una cache RAM per i rendering sul Canvas primario.
4.  **Off-Load Geometria UI**: Avvolgere il calcolo di `snapMolePosition` in `withContext(Dispatchers.Default)` affinché non interferisca con i 60fps del panning/dragging.

### FASE 2: Turbo-I/O e Sicurezza dei Tipi
5.  **Split delle Query Room**: Nel `MoleDao`, mantenere `@Relation` SOLO per le funzioni `suspend` di Export/Backup e Detail Screen. Modificare `getFlatMolesWithHistory` per usare rigorosamente `LEFT JOIN` e flat DTOs a favore della mappa globale, evitando i prodotti cartesiani reattivi.
6.  **Protezione Combine (Max 5 Flussi)**: Riscrivere le pipe reattive in `BodyMapViewModel`. Laddove si combinano >5 flow, introdurre una *Data Class* intermedia per evitare l'allocazione interna di Kotlin su Array non tipizzati. 
7.  **Serializzazione Binaria delle Date**: (Raccomandata) Aggiornare `Converters.kt` convertendo in database le date ISO in formato binario nativo (Epoch `Long`).

### FASE 3: Clean Architecture Pura (Isolamento)
8.  **Depurazione Dominio**: Trasformare i riferimenti a `Color` (framework dipendente) nei modelli UI in stringhe esadecimali o attributi nativi interi, per garantire testabilità JVM pura.
9.  **Spaghetti State Eradication**: Rimuovere lo storage globale in `SkinHistoryAppState.kt`. Implementare il passaggio parametri o `SavedStateHandle` per le variabili di navigazione transitorie (`movingMoleId`).
10. **Iniezione Corretta e Threading Rigido**: Rimuovere i dispatcher globali `CoroutineScope(Dispatchers.IO)`. Tutte le operazioni di file system nel `FileRepository` e ZipUtils DEVONO usare esplicitamente `withContext(Dispatchers.IO)` per rispettare la conformità IO-Bound. Eliminare ogni Singleton o accesso diretto al WorkManager (deve essere iniettato).

### FASE 4: UI / UX Polish e Refinement Build
11. **Flattening Layout `BackgroundSettings`**: Rimuovere misurazioni infinite appiattendo le `LazyColumn`.
12. **Supporto Moderno (SDK 36)**: Chiamare `enableEdgeToEdge()` e gestire correttamente l'insets padding. Adottare rigorosamente KSP (senza KAPT) per Dagger/Hilt e consolidare le versioni su `libs.versions.toml`. Spostare eventuali stringhe residue in `strings.xml`.
