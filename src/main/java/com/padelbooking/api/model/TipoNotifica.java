package com.padelbooking.api.model;

// Valori della colonna notifica_push.tipo (ENUM in MySQL): i nomi devono coincidere esattamente.
public enum TipoNotifica {
    INVITO,
    RICHIESTA_CONFERMA,
    ANNULLAMENTO,
    PROMEMORIA,
    ADMIN_CREAZIONE,
    ADMIN_ANNULLAMENTO,
    ADMIN_CONFERMA,
    ADMIN_INCOMPLETA
}
