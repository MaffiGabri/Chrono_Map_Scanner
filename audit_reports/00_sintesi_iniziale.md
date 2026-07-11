# Sintesi Iniziale

## 1. Stack e Architettura Dedotti
L'applicazione Android **Chrono Map Scanner** adotta uno stack moderno e architetture consolidate:
- **UI:** Jetpack Compose, con Navigation Compose.
- **Architettura:** Clean Architecture, ibrido MVI (Model-View-Intent)/MVVM. StateFlow e UI State per la gestione dello stato reattivo.
- **Dati Locali:** Room (Database SQLite), DataStore Preferences. I modelli non usano `@Relation` per evitare carichi eccessivi di memoria, sfruttando query ottimizzate e DTO piatti con `LEFT JOIN`.
- **Background & Sync:** WorkManager usato pesantemente (`DataIntegrityWorker`, `FileCleanupWorker`, etc.) per mantenere sincronizzati dati Room e file su file system senza bloccare l'UI.
- **Dependency Injection:** Hilt (Dagger-Hilt) con supporto a KSP per la generazione del codice (inclusi Room e Hilt-Worker).
- **Media/File:** CameraX per acquisizione foto. Coil per caricamento asincrono. Elaborazione limitata e IO rigorosamente su `Dispatchers.IO`.

## 2. Valutazione di ARCHITECTURE.md
Il documento definisce linee guida stringenti che sembrano essere ben supportate dai file di configurazione analizzati (come build.gradle e libs.versions.toml). Regole importanti da verificare nel codice:
- Non devono essere istanziate classi framework (WorkManager) direttamente dalla UI.
- Uso di Spatial Hashing per l'ottimizzazione del rendering (rispetto ad algoritmi O(N)).
- Assenza di `@Relation` per query dirette legate alla UI (eccetto backup).
- Mantenimento del worker per integrità dati per prevenire inconsistenze.

## 3. Lista dei Blocchi (Come da indicazioni)
Il codice sarà diviso in 10 blocchi sequenziali, ciascuno contenente i propri file specifici:
1. **Block 1**: App Foundation, DI, & Core Data Models
2. **Block 2**: Database Layer (Room Entities & DAOs)
3. **Block 3**: Repository Layer
4. **Block 4**: Background Work & Data Integrity
5. **Block 5**: ViewModels - Core Mappings & Details
6. **Block 6**: ViewModels - Settings, Images, and Split View
7. **Block 7**: UI Screens - Maps & Details
8. **Block 8**: UI Screens - Settings, Camera, Tools & Navigation
9. **Block 9**: UI Components & Theme
10. **Block 10**: Utilities & XML Resources

Inizio dell'audit iterativo seguendo questo piano.
