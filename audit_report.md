# REPORT DI AUDIT ARCHITETTURALE: SKIN HISTORY SCANNER

## SINTESI INIZIALE

**Stack Tecnologico Dedotto:**
- **Linguaggio:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Architettura:** Ibrido MVI/MVVM
- **Database Local:** Room Database (KSP)
- **Preferences:** DataStore (Preferences)
- **Dependency Injection:** Dagger-Hilt (KAPT/KSP misti in Gradle)
- **Background & Scheduling:** WorkManager
- **Immagini:** Coil, CameraX, Vanniktech Image Cropper
- **Targeting:** Min SDK 26, Target SDK 36

**Valutazione di architecture.md:**
Il file `ARCHITECTURE.md` stabilisce direttive eccellenti (Spatial Hashing, MVI ibrido, divieto di @Relation verso la UI per non intasare la RAM, KSP puro e type-safety su Flow).
*Discrepanze trovate:*
Il codice attuale usa ancora `kotlin-kapt` in `build.gradle.kts` in aperta violazione con la direttiva "L'app compila tramite KSP e ha eliminato KAPT" del documento. Il documento va conservato, ma il codice va allineato ad esso. Il codice presenta logiche pesanti (Otsu threshold in `AlgorithmicMoleDetector.kt`) che non sono coperte dalle direttive. Il crash menzionato in `handoff.md` (Discard/Keep Alert) rappresenta una violazione FK in Room non tracciata adeguatamente.

**Lista dei Blocchi Definiti per l'Audit (Fase 2):**
1. **Blocco 1:** Configurazione, DI & Core Application
2. **Blocco 2:** Local Storage (Room Database & Entities)
3. **Blocco 3:** Domain, DataStore & Repositories
4. **Blocco 4:** ViewModels & State Management
5. **Blocco 5:** Presentation - Screens & Navigation
6. **Blocco 6:** Presentation - Componenti UI & Theme
7. **Blocco 7:** Background Work, Utils & Notifications

---

## SPECIFICA DI REFACTORING (Blocco per Blocco)

### Blocco 1: Configurazione, DI & Core Application
*(build.gradle.kts, libs.versions.toml, SkinHistoryScannerApplication, di/)*

- **Diagnosi Architetturale:** Il progetto usa un approccio moderno con Version Catalogs. Tuttavia, viola palesemente le sue stesse direttive (`ARCHITECTURE.md`) mantenendo il plugin `kotlin-kapt`.
- **Red Flags:**
  - Presenza di `id("kotlin-kapt")` e `kapt(libs.hilt.compiler)` in `build.gradle.kts`. Questo degrada drasticamente i tempi di build.
  - Nessuna configurazione esplicita per R8/ProGuard in release per ottimizzare la scalabilità dei 1000+ marker in memoria.
- **File Orfani e Codice Morto:**
  - Configurazione `kapt` obsoleta.
- **Modernizzazione:**
  - **SDK 35/36:** Transizione COMPLETA di Hilt e Hilt-Work a KSP come dettato dal documento architetturale.
- **Piano di Refactoring Step-by-Step:**
  1. Rimuovere `id("kotlin-kapt")` da `build.gradle.kts`.
  2. Sostituire `kapt(libs.hilt.compiler)` con `ksp(libs.hilt.compiler)`.
  3. Sostituire `kapt("androidx.hilt:hilt-compiler:1.2.0")` con `ksp("androidx.hilt:hilt-compiler:1.2.0")`.

---

### Blocco 2: Local Storage (Room Database & Entities)
*(data/local/room/*)*

- **Diagnosi Architetturale:** Allineato in parte con le direttive: usa `MoleMapDto` per evitare @Relation di massa. Tuttavia, presenta logiche obsolete nelle migration e possibili colli di bottiglia su update di massa.
- **Red Flags:**
  - **SCALABILITÀ (1000+):** `MoleDao.getMolesAtDate` usa una complessa query SQL nidificata per estrarre la singola HistoryEntry corretta. Anche se mappa su DTO, ricalcolare questo per 1000+ nei per ogni tap sulla UI potrebbe causare latenza.
  - L'assenza di gestione transazionale per l'aggiornamento di `variantId` causa il crash documentato nell'handoff in fase di *Keep Moles*. L'aggiornamento di centinaia di chiavi esterne richiede `DEFERRED` constraints o transazioni isolate se il target non è ancora flushato su disco.
- **File Orfani e Codice Morto:**
  - Migrazioni Room legacy scritte a mano (`MIGRATION_1_2`, ecc.) potrebbero essere convertite in `AutoMigration` per maggior pulizia se lo schema fosse gestito via file.
- **Modernizzazione:**
  - Creare *Database Views* pre-compilate se la vista BodyMap diventa troppo esigente.
- **Piano di Refactoring Step-by-Step:**
  1. Revisionare `MoleDao.updateMolesVariant` assicurandosi che venga eseguita all'interno di una transazione e solo se il `newVariantId` esiste già validamente in `background_variants` (risoluzione crash HandOff).
  2. Ottimizzare le query Flow introducendo un index composito `[mole_id, date]` su `history_entries`.

---

### Blocco 3: Domain, DataStore & Repositories
*(data/domain/*, data/local/datastore/*, data/repository/*)*

- **Diagnosi Architetturale:** Repository pattern implementato decentemente. Il `DataStore` è impiegato ma forse sovra-usato per logiche transitorie.
- **Red Flags:**
  - Cancellazioni asincrone su `FileRepository`: `OfflineMoleRepository` delega la rimozione file a `FileRepository.scheduleFileDeletion`. Se l'app si chiude, i file rimangono orfani.
  - La logica del *Keep/Discard* per il cambio variante sfondo richiede intervento proprio al livello Repository per essere atomica e safe.
- **File Orfani e Codice Morto:** Nessun codice morto evidente in questa fase.
- **Modernizzazione:**
  - Utilizzare WorkManager in modo stringente per TUTTE le operazioni di pulizia file come da indicazioni `ARCHITECTURE.md` (Background Integrity Scanner).
- **Piano di Refactoring Step-by-Step:**
  1. Modificare `OfflineMoleRepository.deleteMole` e simli affinché avviino un WorkManager robusto per la cancellazione file passandogli i path in array, invece di usare coroutines asincrone non tracciate che possono morire.

---

### Blocco 4: ViewModels & State Management
*(ui/viewmodels/*, UiStates)*

- **Diagnosi Architetturale:** Rispetto impeccabile per le direttive del limite `combine` a 5 elementi (es. uso di tuple `Triple` in `BodyMapViewModel.kt`). L'architettura dello stato è monolitica ma coerente con MVI.
- **Red Flags:**
  - **SCALABILITÀ (1000+ CRITICO):** In `BodyMapViewModel.kt`, il metodo `snapMolePosition` esegue cicli CPU pesantissimi $O(N)$ ricalcolando distanze trigonometriche con collisioni, più volte, per centinaia di nei! Questo è un anti-pattern puro in netta violazione con le richieste di scalabilità estreme! Questo algoritmo iterativo blocca il thread in modo inaccettabile se l'utente sposta rapidamente i neo.
  - Nel `BackgroundSettingsViewModel.kt` la migrazione `switchCategory` assume che la migrazione avvenga prima che la UI scatti, generando race conditions e crash di FK.
- **File Orfani e Codice Morto:** Nessuno.
- **Modernizzazione:**
  - Introdurre strutture dati spaziali (`QuadTree`) per `snapMolePosition` come si fa per `findMoleAtTap` (che usa Spatial Hashing).
- **Piano di Refactoring Step-by-Step:**
  1. Riscrivere `snapMolePosition` applicando la stessa logica di Spatial Hashing in griglia usata in `findMoleAtTap` per limitare i check di collisione a un numero irrisorio (O(1) o vicini costanti), risolvendo le performance su 1000+ marker.
  2. Implementare la logica UI a 2 step (Alert Discard/Keep e Alert Irreversibile) nel `BackgroundSettingsViewModel` delegando allo Screen l'apertura tramite State.

---

### Blocco 5: Presentation - Screens & Navigation
*(ui/*Screen.kt, Navigation)*

- **Diagnosi Architetturale:** Composizione massiva nel layer UI con Canvas che disegna tutto. Ben distaccato dalla logica (come richiesto).
- **Red Flags:**
  - **SCALABILITÀ (1000+):** `BodyMapScreen` esegue decine di operazioni Compose `drawCircle` in `DrawScope`. Compose gestisce bene i canvas primitivi, ma per 1000+ elementi su un Canvas animabile potrebbe balbettare.
  - La navigation passa primitive pesanti nel SavedStateHandle invece di sfruttare a pieno Kotlinx Serialization supportato da Navigation Compose.
- **File Orfani e Codice Morto:** Componenti Inline enormi da estrarre.
- **Modernizzazione:**
  - Sostituire il drawScope base con `drawWithCache` (se non già presente) per ottimizzare il caching delle istanze `Path` o colori derivati.
- **Piano di Refactoring Step-by-Step:**
  1. Implementare i Dialog necessari per il bug fix "Discard/Keep" descritto nell'Handoff all'interno dei nodi appropriati della Screen di Setting Sfondi.
  2. Modificare il rendering del Canvas implementando meccaniche di LOD (Level of Detail) o consolidando i draw call se il limite di marker eccede una certa soglia.

---

### Blocco 6: Presentation - Componenti UI & Theme
*(ui/components/*, theme/*)*

- **Diagnosi Architetturale:** Pulito.
- **Red Flags:**
  - `ColorPickerOverlay` ricalcola esadecimali a ogni frame con `android.graphics.Color.parseColor` all'interno della lambda Compose. Un memory leak minimo ma costante.
- **File Orfani e Codice Morto:** Immagini PNG nei `res` potrebbero essere WebP.
- **Modernizzazione:** Pre-calcolare i `Color` Compose in uno StateFlow.
- **Piano di Refactoring Step-by-Step:**
  1. Spostare le funzioni di parsing Color hex all'interno della mappatura dello stateFlow del ViewModel in modo che Compose riceva l'oggetto `Color` già risolto.

---

### Blocco 7: Background Work, Utils & Notifications
*(data/local/workers/*, utils/*, notifications/*)*

- **Diagnosi Architetturale:** L'implementazione di algoritmi di calcolo raw è presente.
- **Red Flags:**
  - **CRITICO:** `AlgorithmicMoleDetector.kt` cicla iterativamente i pixel di una Bitmap! Un ANR (Application Not Responding) è garantito su foto Hi-Res di SDK 35. Assolutamente inaccettabile come prassi moderna.
- **File Orfani e Codice Morto:**
  - Python scripts sparsi per directory esterne dimostrano che c'è un modello YoloV8 ma l'app Android non lo integra nativamente!
- **Modernizzazione:**
  - L'intera classe `AlgorithmicMoleDetector` va etichettata per la sostituzione futura con TFLite o rimossa se non essenziale al MVP.
- **Piano di Refactoring Step-by-Step:**
  1. Se non strettamente necessario, eliminare `AlgorithmicMoleDetector` e le relative dipendenze legacy, o sostituire il loop di pixel mapping con `RenderScript` o API Vulkan se si intende mantenere la CV base.
