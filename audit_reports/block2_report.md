# Blocco 2 - Diagnosi Architetturale
Il blocco 2 gestisce la persistenza con Room. Le query SQL sono pulite, e l'approccio reattivo con `Flow` è corretto per la UI. C'è un'ottima separazione tramite i Worker (Export/Import/Cleanup).
Tuttavia, emergono seri problemi di scalabilità per "1000+ punti".

# Blocco 2 - Red Flags
- **`MoleDao.getMolesWithHistory(profile)` (CRITICO)**: Ritorna in memoria una `List<MoleWithHistory>`. Con 1000 nei e 10 entry di history l'una, sono 10.000 righe estratte dal DB interamente caricate sulla heap. OOM garantito.
- **Assenza di Pagination**: Nessuna query espone `PagingSource` di Room. Tutte le liste ritornate da DAO non sono paginate.
- **Relazioni Room Avide**: `MoleWithHistory` carica eager l'intera collection `@Relation`. Quando si naviga, se si richiede il neo o la lista, si scarica l'intera storia. Per 1000 imperfezioni, la memoria collassa.

# Blocco 2 - File Orfani e Codice Morto
- Le query sincrone come `getAllProfileNamesSync` non sono ideali ma accettabili in worker. Vanno esaminate le chiamate per verificare che non avvengano su thread principali (Room blocca di default ma in alcuni punti potrebbe dare problemi).

# Blocco 2 - Modernizzazione
- **Paging3 Integration**: L'app deve implementare `PagingSource` in Room e ritornare `PagingData<T>` o caricare lazy l'history.
- **Proiezione Ottimizzata**: Nella vista mappa, serve solo l'ULTIMO snapshot di un neo, non tutta la history. Attualmente `getMolesAtDate` lo fa parzialmente tramite una query SQL (uso di `LEFT JOIN` e subquery su `MAX(date)`). Ma la query SQL su DB SQLite con subquery per ogni riga in caso di 1000+ mole può essere inefficiente senza indici mirati su `(mole_id, date)`. Bisogna verificare gli indici.

# Blocco 2 - Piano di Refactoring Step-by-Step
1. Modificare `MoleEntity` o le annotation di Room per aggiungere un Indice composito su `(mole_id, date)` in `HistoryEntryEntity` per velocizzare le query `getMolesAtDate`.
2. Convertire o rimuovere le query Room eager come `getMolesWithHistory(profile)` in favore di chiamate lazy o basate su un intervallo di data.
3. Introdurre `androidx.paging.PagingSource` per la lista dettagliata dei nei, se presente nella UI.
