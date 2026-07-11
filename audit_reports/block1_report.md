# Blocco 1 - Diagnosi Architetturale
Il blocco 1 contiene il bootstrap dell'applicazione, la configurazione della DI (Hilt) e i modelli di dominio. Il livello di astrazione è buono: ci sono modelli di dominio separati (es. `Mole`, `HistoryEntry`) con mappers da/verso le entity di Room.
Tuttavia, `Seeder.kt` è problematico. Esegue operazioni pesanti di I/O (copia di file) in modo molto naif, e genera finti nei per test. Inoltre, `ChronoMapScannerApplication.kt` non sfrutta Hilt Worker in modo ottimale (sebbene importato in gradle) o Configuration Provider se necessario.

# Blocco 1 - Red Flags
- **`Seeder.kt` - OOM Risk / I/O Blocking:** Il metodo `seedFakeData` copia centinaia di volte un'immagine fisica generando fino a 300+ file immagine su storage interno bloccando a lungo anche se in `Dispatchers.IO`. Nessuna gestione corretta delle bitmap o compressione. Oltretutto genera thumbnail "fittizie" semplicemente copiando l'immagine grande, sprecando disco.
- **Mappers inefficaci per grandi moli di dati:** Mappare in memoria `MoleWithHistory` (in `MoleModels.kt`) in un modello di dominio `Mole` con una lista nidificata di `HistoryEntry` può portare a Out-Of-Memory quando si caricano "1000+ punti di imperfezione", specialmente se queste liste storiche si popolano.

# Blocco 1 - File Orfani e Codice Morto
- **`Seeder.kt`**: Sembra un utility di debug/test rimasta in produzione. Non è scalabile e viola le best practice di storage. Se serve solo per debug, va isolato in `src/debug` o rimosso.
- La classe `BodySide` in `BackupModels.kt` o `MoleModels.kt` è in conflitto con la gestione dinamica delle varianti implementata dai Background, che usano Stringhe (variantId). Va riconsiderata o deprecata se i layer inferiori usano String in modo generico (cosa che in effetti fanno: `side: String` in `Mole`).

# Blocco 1 - Modernizzazione
- **Hilt Configuration**: `ChronoMapScannerApplication` (che non ho potuto leggere per intero nel dump, ma presumo base) dovrebbe implementare `Configuration.Provider` per il WorkManager se si usano Worker Hilt.
- **Mappers & Paginazione**: Le conversioni di massa da DB a Domain (`toDomain`) dovrebbero usare tipi Paged (Paging3) o Flow lazy, piuttosto che liste in memory.

# Blocco 1 - Piano di Refactoring Step-by-Step
1. Spostare `Seeder.kt` fuori dai sorgenti di produzione (es. `src/debug`) o rimuoverlo completamente.
2. Controllare le dipendenze di `BodySide` nei modelli. Eliminare l'enum rigido (FRONT, BACK) in favore delle Varianti dinamiche (String).
3. Modificare i mappers in `MoleModels.kt` affinché non mappino forzatamente l'intera history di tutti i nei insieme, ma supportino la paginazione, disaccoppiando il dominio `Mole` "leggero" dalla sua `History`.
