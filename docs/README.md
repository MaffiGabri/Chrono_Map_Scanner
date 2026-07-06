# Chrono Map Scanner - Mole Tracker App

Chrono Map Scanner è un'applicazione Android progettata per mappare, tracciare e monitorare l'evoluzione nel tempo di nei e imperfezioni della pelle. Questo documento fornisce una panoramica generale del progetto.

## 🎯 Obiettivo del Progetto
L'obiettivo di Chrono Map Scanner è fornire un'interfaccia intuitiva e robusta per la mappatura visiva sul corpo umano di un elevato numero di nei e imperfezioni (se ne prevedono 1000+). Gli utenti possono salvare foto scattate nel tempo per ogni singola imperfezione, permettendo un confronto storico e visivo utile per la prevenzione dermatologica.

## 👥 A chi è rivolta questa documentazione?
1. **Utente/Proprietario:** Questa applicazione è pensata per essere facile e intuitiva senza bisogno di manuali complessi.
2. **AI Gemini (Sviluppatore):** Le logiche architetturali, i flussi dei dati e i dettagli sul refactoring e sviluppo futuro sono documentati in `ARCHITECTURE.md`. Questo garantisce che l'AI mantenga sempre il contesto tecnico del progetto in ogni futura sessione di sviluppo.

## 🚀 Funzionalità Principali

*   **Gestione Multi-Profilo Isolato:** Crea profili differenti per mantenere le mappe dei nei separate. I sistemi di backup gestiscono cloni e profili in "sandbox" fisiche (UUID univoci), rendendo impossibile corrompere le foto originali se si modifica un backup duplicato.
*   **Body Map Interattiva (Ultra Performante):** Posiziona i marker su un modello 2D scalabile e interattivo (zoomabile e trascinabile). La mappa è ottimizzata tramite un motore di *Spatial Hashing* personalizzato, in grado di gestire senza alcun rallentamento (zero lag) oltre 1000 nei su schermo simultaneamente.
*   **Architettura Sicura (Memory Leak Prevention):** Un gestore background si occupa di fare "pulizia profonda" sul dispositivo: eliminare un neo o un profilo svuota automaticamente non solo il database, ma disintegra asincronamente ogni foto associata dallo storage interno per impedire l'accumulo di file spazzatura.
*   **Gestione Temporale Estesa:** Naviga indietro nel tempo per vedere come era la mappatura in date specifiche; le date a cui la timeline fa snap includono il primo record di un neo e ogni foto successiva.
*   **Split View (Confronto):** Confronta visivamente la foto passata di un neo con una più recente per notare cambiamenti di forma, dimensione o colore.
*   **Import/Export Database Avanzato:** Sistema robusto di estrazione completa che garantisce l'assenza di dati orfani in modalità sovrascrittura. I dati personali (foto e database JSON) vengono compressi in ZIP condivisibili.
*   **Rilevamento Automatico:** Modulo sperimentale per la fotocamera che rileva in automatico la presenza di un neo utilizzando un'analisi algoritmica dei contrasti (Otsu's thresholding) e scatta quando le condizioni ideali vengono rispettate.

## 🛠️ Stack Tecnologico (Per Sviluppatori)
*   **Linguaggio:** Kotlin (Target SDK 36)
*   **Interfaccia Utente:** Jetpack Compose (Material Design 3)
*   **Database e Dati Locali:** Room Database, DataStore (Preferences)
*   **Dependency Injection:** Dagger-Hilt (tramite KSP per prestazioni di build ottimali)
*   **Immagini:** Coil per il caricamento, CameraX per l'acquisizione.
*   **Architettura:** MVI/MVVM Rigorosa (Logiche I/O e WorkManager isolate dalla UI)

---
*Per le specifiche tecniche e l'architettura interna, vai su `ARCHITECTURE.md`.*

*   **Personalizzazione del Modello Corporeo e Varianti:** Aggiunta la possibilit� di personalizzare sesso e corporatura con aggiornamento live della sagoma. Supporto al ritaglio intelligente (cropping) delle immagini profilo tramite ndroid-image-cropper.
