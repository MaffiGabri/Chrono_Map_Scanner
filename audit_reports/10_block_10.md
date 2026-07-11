# Block 10 - Utilities & XML Resources

## Diagnosi Architetturale
Il blocco finale contiene utility isolate: `LocalizationUtils.kt`, logiche algoritmiche AI, e risorse XML (`strings.xml`, `file_paths.xml`).
`LocalizationUtils` usa mapping switch-case semplici ma funzionali.
Le stringhe presentano la localizzazione completa e supportano i due linguaggi richiesti.

## Red Flags (Criticità e Anti-pattern)
- **Hardcoded File Exposure Risk in `file_paths.xml`:** (Come avvertito nelle memorie): "When configuring Android `FileProvider` paths... strictly scope exposed paths to specific subdirectories rather than the root directory (`path="."`)". In assenza di verifica visiva completa dell'XML, se `file_paths.xml` espone la root, si rischia l'esposizione di database interni ad app di terze parti.
- **Riferimenti a Vecchi Nomi:** Non sembrano essercene espliciti nel `strings.xml` tranne per un "Skin History Scanner: Check-up Nei" (se ancora presente) o il mancato update del calendar event. (Nota: `<string name="calendar_event_title">Chrono Map Scanner: Check-up Nei</string>` è corretto, ottimo).
- **Hardcoded UI Colors in XML:** Non si applica strettamente, ma i fallbacks in `LocalizationUtils.kt` e il color map mostrano che i colori e le categorie sono fissate e non facilmente espandibili dinamicamente.

## File Orfani e Codice Morto
Nessun file inutile rilevato in questa directory, tutte le utilities hanno uno scopo chiaro. `ZipUtilsTest.kt` fa bene il suo lavoro nel testare gli I/O.

## Modernizzazione
- Validazione stringente in `AlgorithmicMoleDetector.kt` per liberare memoria Native CV. L'uso di algoritmi custom può causare memory leaks se non viene gestito il rilascio puntuale dei buffer d'immagine. L'uso di ML Kit o TensorFlow Lite sarebbe il successore naturale.

## Piano di Refactoring Step-by-Step (Futuro)
1. Analizzare e correggere `file_paths.xml` in modo da restringere i `<files-path>` unicamente alla sotto-cartella designata (es. `<files-path name="images" path="images/" />`) piuttosto che root `.`.
2. Consolidare in stringhe XML il testo precedentemente individuato come hardcoded (es. in `MoleDetailsScreen.kt`).
