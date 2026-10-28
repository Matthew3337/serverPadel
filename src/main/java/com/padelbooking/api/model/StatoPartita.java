package com.padelbooking.api.model;

// Valori della colonna partita.stato (ENUM in MySQL): i nomi devono coincidere esattamente.
public enum StatoPartita {
    CREATA,
    CONFERMATA,
    ANNULLATA,
    GIOCATA
}
