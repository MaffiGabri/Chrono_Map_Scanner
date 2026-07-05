# Handoff: Chrono Map Scanner

## Stato Attuale
- Abbiamo integrato la libreria `com.vanniktech:android-image-cropper` per permettere il ritaglio delle immagini profilo.
- Abbiamo rifattorizzato le schermate UI rimuovendo "Profilo Corpo" e integrandolo all'interno della gestione degli Sfondi (modello "Persona").
- Abbiamo risolto un bug nel modello "Persona" per cui l'immagine del corpo (sesso/peso) non si aggiornava dinamicamente al variare delle preferenze dell'utente. Abbiamo fixato il parsing del nome della variante in `BodyImageUtils.kt` per supportare suffissi custom (es. `FRONT_myprofile`).
- Abbiamo risolto un problema in `MoleDetailsComponents.kt` dove la minimappa risultava trasparente nei modelli "Persona", integrando il fallback `getBodyImageRes` quando `imagePath` è nullo.
- Le stringhe della UI sono state internazionalizzate correttamente (inglese/italiano).

## Problema Imminente / Task in Sospeso
- **Sistema di Avvisi al Cambio di Modello di Sfondo (Discard/Keep Alert):** Questa è la feature principale in sospeso. L'utente ha chiesto: quando si sceglie un nuovo sfondo (modello Personalizzato) nella schermata BackgroundSettings (o BodyMapScreen), bisogna mostrare un alert: "Stai cambiando lo sfondo. Cosa vuoi che succeda ai difetti? - Eliminali - Mantienili". Se l'utente sceglie "Mantienili", l'app crasha attualmente secondo i log precedenti, e bisogna mostrare un secondo alert "Questa azione è irreversibile. -Ok -Annulla".
- **Migrazione Sfondi Legacy:** Occorre migrare le varianti personalizzate esistenti nella nuova categoria "Personalizzato".

## Azione Richiesta / Piano per il Prossimo Agente
1. **Analizzare il sistema di avvisi Discard/Keep:** Individuare in `BackgroundSettings.kt` o `BodyMapViewModel` dove avviene il cambio di sfondo.
2. **Implementare il Dialog di Conferma a due step:**
   - Step 1: "Stai cambiando lo sfondo. Cosa vuoi che succeda ai difetti? (Eliminali / Mantienili)"
   - Step 2 (se "Mantienili"): "Questa azione è irreversibile. (Ok / Annulla)"
3. **Risolvere il crash relativo al "Mantienili":** L'utente segnalava un crash quando tentava di conservare i difetti al cambio di variante. Questo crash probabilmente deriva da una discrepanza negli ID o in chiamate di database sospese (forse una `Foreign Key constraint violation` o un problema di concorrenza con i Job di Room). Sarà fondamentale leggere attentamente Logcat per scovare e fissare l'eccezione al momento del cambio sfondo.

