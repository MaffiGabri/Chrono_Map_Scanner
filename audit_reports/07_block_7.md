# Block 7 - UI Screens - Maps & Details

## Diagnosi Architetturale
Il blocco contiene UI complesse in Jetpack Compose: `BodyMapScreen` e `MoleDetailsScreen`.
Da quello che si può dedurre dalle specifiche lette e parzialmente viste, l'app usa logiche di pop-up per le modifiche (colore, note, eliminazioni).
In `MoleDetailsScreen.kt`, si nota la gestione della logica dei dialog `showEmptyWarningDialog` e l'aggiornamento/salvataggio delle foto in base allo stato in-memory della composizione.

## Red Flags (Criticità e Anti-pattern)
- **UI State in Componenti Componibili (Business Logic in UI):** La cancellazione intelligente di una "Mole Vuota" (ovvero senza note e senza foto) con relativa logica del flag "non ricordarmelo più" (in `MoleDetailsScreen.kt`) delega l'elaborazione del "difetto vuoto" e i trigger di repository al bottone di uscita/backhandler direttamente dalla view. Sarebbe più sicuro gestire questa transizione e le verifiche (if empty -> dialog) nel `ViewModel`, emettendo Side-Effects / UI Intents.
- **`AsyncImage` e Memoria Locale:** L'uso di `AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(File(pendingPhotoPath)))` carica il file su disk in Compose usando Coil senza placeholder esplicito. Coil è ottimizzato, ma in liste l'assenza di limitazioni (come Paging) o chiavi univoche (`items(key= { it.id })`) per le LazyColumn nel dettaglio può creare lag.

## File Orfani e Codice Morto
I file UI sembrano puliti e specifici (nessun orfano notato qui, `BodyImageUtils` è usato dai ViewModels/UI per il matching stringhe corpo/varianti che andrebbe rivisto se si abbandonassero i drawable hardcoded, ma per ora è coerente).

## Modernizzazione
- **MVI per UI Dialog:** Portare lo stato di `showDeleteConfirm`, `showPhotoMenu` e `showEmptyWarningDialog` all'interno dello `StateFlow` del ViewModel, in modo da resistere ai cambiamenti di configurazione (es. rotazione dello schermo).
- **Hardcoded Strings:** in `MoleDetailsScreen.kt` nella logica del `showEmptyWarningDialog` sono presenti stringhe in italiano hardcoded (es. "Difetto vuoto", "Stai uscendo da questo difetto...", "Non ricordarmelo più", "Procedi"). Questo viola la regola delle stringhe centralizzate in `strings.xml` supportate in Italiano e Inglese.

## Piano di Refactoring Step-by-Step (Futuro)
1. Spostare le stringhe hardcoded del `showEmptyWarningDialog` (come "Difetto vuoto", "Procedi") all'interno di `strings.xml` e `values-en/strings.xml`.
2. Estrarre la logica del controllo `discardDraftIfEmpty` nel momento di intercettazione del "Back" nel ViewModel (gestendo il prompt tramite un channel di effetti) in modo che l'UI si limiti a obbedire all'ordine di mostrare l'alert o navigare indietro.
