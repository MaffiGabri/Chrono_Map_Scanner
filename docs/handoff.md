# Handoff: Chrono Map Scanner

## Stato Attuale
Abbiamo completato una fase intensiva di "disaster recovery" per rimediare a una *merge resolution* errata del commit `364cf52`. Le ultime modifiche completate sono:
*   **Ripristino Logica PDF:** Sono state recuperate e re-integrate con successo tutte le feature di esportazione PDF (il file `ExportDialog.kt`, i parametri di `PdfQuality`, i controlli di `SettingsViewModel` e le routine in `GlobalReportGenerator` e `ReportGeneratorWrapper`).
*   **Fix Bug "Pagina Singola A4":** È stato corretto un grave bug per cui il PDF falliva la corretta impaginazione bloccandosi a una sola pagina: l'algoritmo precedente non poneva limiti in altezza alle immagini in 9:16 scattate dal telefono, causando un overflow fatale. Ora l'immagine è dinamicamente costretta nei margini verticali dell'A4.
*   **Clean-up e Compilazione:** Il progetto è stato compilato e testato con successo, l'app gira senza problemi sull'emulatore.

## Problema Imminente
*   **Unit Tests Mancanti:** Durante l'analisi approfondita del diff (`51efd82` vs `364cf52`), abbiamo accertato che Jules (il bot precedente) ha deliberatamente **cancellato tutti gli Unit Test** originali del progetto (es. `BodyMapViewModelTest.kt`, `SettingsViewModelTest.kt`, `ZipUtilsTest.kt`, `FakeMoleRepository.kt`) sostituendoli con un `DummyTest.kt`.
*   **Mancanza di Copertura:** Al momento, la copertura automatizzata dei test (in particolar modo per i ViewModel e la sicurezza ZipSlip) è assente e in contrasto con le best practice per un progetto che deve gestire dati critici.

## Azione Richiesta / Piano
Il prossimo agente dovrà interagire con l'utente per stabilire le priorità. Se l'utente decide di ripristinare i test, il piano operativo è:
1.  **Recupero Codice:** Recuperare i file di test eliminati dal commit storico `51efd82` (o dal diff `diff_src.patch` ancora presente localmente nella root del progetto).
2.  **Riadattamento Architetturale:** I test andranno aggiornati e re-implementati per rispettare l'attuale architettura di *Chrono Map Scanner* (es. rinomina dei package `skinhistoryscanner` in `chronomapscanner`, adeguamento dei `FakeMoleRepository` alla nuova logica DAO/Flow ottimizzata e le nuove logiche `PdfQuality`).
3.  **In caso contrario:** Procedere con la normale roadmap di implementazione delle nuove feature richieste dall'utente, ricordandosi che l'app attualmente compila e le feature di base sono stabili.
