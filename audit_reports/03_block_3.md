# Block 3 - Repository Layer

## Diagnosi Architetturale
Questo blocco espone le operazioni verso Room e FileSystem tramite Hilt. I Repository delegano correttamente il lavoro sui thread I/O (`Dispatchers.IO`) e integrano WorkManager per compiti lenti (come `FileCleanupWorker` chiamato in `scheduleFileDeletion`). `BackupRepository` si occupa dell'esportazione/importazione JSON e file ZIP.

## Red Flags (Criticità e Anti-pattern)
- **Hardcoded Legacy Name in Backup:** In `BackupRepository.kt`, il file esportato o importato nello zip è cablato come `"Skin History Scanner_db.json"`. La memoria di istruzione vieta l'uso di "Skin History Scanner", il cui nome canonico deve essere "Chrono Map Scanner".
- **Memoria in Backup/ZIP:** `BackupRepository.createAndWriteExportZip` deserializza/serializza il DB internamente in una stringa di testo e la scrive (`json.encodeToString(databaseDto)`). Se il databaseDto contiene >1000 oggetti con storie lunghe, l'intera serializzazione tenuta in memoria heap (`jsonContent`) prima della scrittura in file causerà un OutOfMemory (OOM) error.

## File Orfani e Codice Morto
- Il file `OfflineMoleRepository.kt` pare essere una variante di `MoleRepository.kt` ma nel repository pattern (non letto nel dettaglio qua ma elencato tra i file del blocco) se fa duplicazione con `MoleRepository` andrà deprecato, ma non abbiamo trovato evidenze di `OfflineMoleRepository` nei cat. Assumiamo `MoleRepository` sia il primario.
- `FakeMoleRepository.kt` e `FakeSettingsRepository.kt` contengono errori noti (da memory), sono stub per test e vanno ignorati per l'audit a meno che non interferiscano col build di produzione.

## Modernizzazione
- **Streaming JSON Serialization:** Nel `BackupRepository`, sostituire `json.encodeToString(databaseDto)` con kotlinx.serialization streaming (`encodeToStream` da `kotlinx-serialization-json-io` se disponibile) oppure Jackson Streaming per gestire export massivi senza far esplodere la memoria.
- **Rinominare DUMP DB:** Aggiornare `"Skin History Scanner_db.json"` a `"Chrono Map Scanner_db.json"`.

## Piano di Refactoring Step-by-Step (Futuro)
1. Modificare in `BackupRepository.kt` il nome `"Skin History Scanner_db.json"` in `"Chrono Map Scanner_db.json"`. Eseguire check e avvisi per la backward compatibility in import (es. provare a leggere prima il vecchio file se il nuovo non esiste per non rompere backup storici).
2. Ottimizzare la serializzazione JSON in `BackupRepository` per scrivere sul `File.outputStream()` in modo chunkato o bufferizzato, invece di mantenere l'enorme stringa `jsonContent` interamente in memoria.
