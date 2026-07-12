
# Block 6 - Utils, Main & Notifications

## [Block 6] - Diagnosi Architetturale:
Questa sezione incorpora l'Application class principale (`ChronoMapScannerApplication.kt`), l'activity Compose e le utilities globali. Le notifiche usano `ReminderManager.kt` e `ReminderWorker.kt` (WorkManager base). Utils per zipping e seeding sono raggruppati qui per convenienza.

## [Block 6] - Red Flags:
- ZipUtils.kt I/O bloccante: Il file è ad alto rischio di ANR. Anche se in questo contesto si applica, bisogna accertare visivamente che i file `unzip` e `zip` non esportino calcoli fuori dal context `Dispatchers.IO` o senza `suspend`.
- Inizializzazioni massive nella MainActivity o nell'Application: Creare o seedare file/database su thread sincroni nel blocco `onCreate` può causare crash d'avvio o timeout di Android in base alle direttive "Zero Tolleranza Anti-Pattern".
- Naming Convention App: Controllare hardcoded obsolete strings se la risorsa è localizzata. L'app DEVE usare `ChronoMap Scanner` come nome canonico (mai MoleTracker).

## [Block 6] - File Orfani e Codice Morto:
- `ReportGeneratorWrapper.kt` e `GlobalReportGenerator.kt` potrebbero essere script ad-hoc obsoleti per export AI non più necessari e dovrebbero essere verificati e confinati in `project_tools_and_scripts/` se sono di natura per il developer (o per script esterni) e non funzionali all'app utente.

## [Block 6] - Modernizzazione:
- Activity Edge-To-Edge di default su SDK >34 con le chiamate standard nel lifecycle in `MainActivity.kt`.
- Aggiornare canali notifiche per le permission esplicite (Android 13/14) se mancano in `ReminderManager.kt`.

## [Block 6] - Piano di Refactoring Step-by-Step:
1. Revisione `ZipUtils.kt`: Aggiungere modificatori `suspend` a tutte le utilità pubbliche e avvolgere la logica File I/O in block `withContext(Dispatchers.IO)`.
2. Segregazione Utilities Esterne: Analizzare la dipendenza da `GlobalReportGenerator.kt` e spostarla fuori dal build target Android se serve solo all'ambiente di ricerca o allo sviluppatore.
3. Ottimizzare le funzioni in `ChronoMapScannerApplication.kt` delegando a inizializzatori Hilt asincroni per garantire boot rapidi.
