# Fase 1: Mappatura Globale e Analisi del Contesto

## SINTESI INIZIALE

**Stack Tecnologico Dedotto:**
- **Linguaggio:** Kotlin (2.1.0)
- **UI Framework:** Jetpack Compose (BOM 2025.02.00, Material 3)
- **Architettura:** MVI Ibrido con MVVM (ViewModel, StateFlow, Coroutines)
- **Dependency Injection:** Dagger Hilt (2.54) tramite KSP (2.1.0-1.0.29)
- **Database:** Room (2.6.1) con KSP
- **Image Loading:** Coil Compose (2.7.0)
- **Background Work:** WorkManager (2.10.0)
- **Fotocamera:** CameraX (1.4.0)
- **DataStore:** Preferences DataStore (1.1.1)
- **Navigazione:** Navigation Compose (2.8.8), Hilt Navigation Compose (1.2.0)
- **Costruzione & SDK:** Android Gradle Plugin 8.7.2.

**Valutazione architecture.md:**
Il documento delinea un pattern rigoroso finalizzato a preservare le performance in caso di oltre 1000 marker di nei. Esige:
1. Niente calcoli intensivi nel layer Compose. Tutti i mapping su thread secondari (`Dispatchers.Default`) nel `ViewModel`.
2. Spatial Hashing per minimizzare i calcoli O(N) in caso di interazioni touch sulla Body Map.
3. Astensione dall'utilizzo di join costosi (`@Relation`) in query reattive (`Flow`) su centinaia di record. Uso di query piatte con `LEFT JOIN`.
4. Gestione file delegata a `FileRepository` e pulizia asincrona con `WorkManager` per limitare i "storage leak".

## Fase 2: Piano di Audit Iterativo (Lista dei Blocchi)

La totalità del codice rilevante (cartella `app/src/main/java/com/example/chronomapscanner`) è divisa nei seguenti 6 blocchi bilanciati:

**Blocco 1: Domain, Entities & Room (Data Layer Core)**
Contiene le definizioni Room (Dao, Entities, TypeConverters) e i modelli di dominio (DTO, Models).
- SettingsModels.kt, LocalDateSerializer.kt, MoleModels.kt, BackupModels.kt
- MoleDao.kt, BackgroundVariantEntity.kt, Converters.kt, AppDatabaseRoom.kt
- BackgroundCategoryEntity.kt, MoleEntity.kt, BackgroundDao.kt, HistoryEntryEntity.kt

**Blocco 2: Repositories & Workers (Data Layer Logic)**
Contiene l'implementazione del pattern Repository, DataStore e WorkManager per operazioni in background e persistenza file.
- BackupRepository.kt, OfflineMoleRepository.kt, FileRepository.kt, MoleRepository.kt
- BackgroundRepository.kt, DataIntegrityWorker.kt, SettingsRepository.kt
- ImportDatabaseWorker.kt, FileCleanupWorker.kt, ExportDatabaseWorker.kt

**Blocco 3: ViewModels & DI (Presentation Logic & Infrastructure)**
Contiene i ViewModels responsabili della logica MVI/MVVM, trasformazione flussi dati e Dependency Injection.
- DatabaseModule.kt, CoroutineScopesModule.kt, RepositoryModule.kt
- BackgroundSettingsViewModel.kt, BodyMapViewModel.kt, MoleDetailsViewModel.kt
- SettingsViewModel.kt, SplitViewViewModel.kt, ImageEditorViewModel.kt, VariantManagementViewModel.kt

**Blocco 4: UI Components & Theme (Presentation UI - Atoms & Molecules)**
Contiene i componenti visuali (Widgets), dialog e le configurazioni di tema/stile.
- TimelineSlider.kt, VariantManagementBottomSheet.kt, ExportDialog.kt, MoleMarker.kt
- BackgroundSettings.kt, ImportActionDialog.kt, MoleLegend.kt, MoleDetailsComponents.kt
- Theme.kt, Type.kt, Color.kt

**Blocco 5: UI Screens & Navigation (Presentation UI - Organisms & Routing)**
Contiene le macro schermate (Screens), definizioni di State e la configurazione del NavGraph.
- MoleDetailsUiState.kt, MoleDetailsScreen.kt, BodyMapScreen.kt, ChronoMapNavGraph.kt
- Routes.kt, MoleUiModel.kt, SplitViewScreen.kt, BodyImageUtils.kt
- SettingsScreen.kt, SettingsUiState.kt, AutoCameraScreen.kt, ImageEditorScreen.kt
- BodyMapUiState.kt, SplitViewUiState.kt

**Blocco 6: Utils, Main & App (Infrastructure & Support)**
Contiene entry points, configurazione notifiche e utility esterne.
- MainActivity.kt, ChronoMapScannerApplication.kt, LocalizationUtils.kt
- ReportGeneratorWrapper.kt, Seeder.kt, AlgorithmicMoleDetector.kt, ZipUtils.kt
- GlobalReportGenerator.kt, ReminderWorker.kt, ReminderManager.kt
