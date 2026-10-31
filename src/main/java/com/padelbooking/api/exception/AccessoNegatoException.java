package com.padelbooking.api.exception;

// Operazione vietata al ruolo dell'utente autenticato (403), per esempio l'admin che prova a entrare in una partita.
// Diversa da AccessDeniedException di Spring Security, che scatta prima del controller per le regole di SecurityConfig.
public class AccessoNegatoException extends RuntimeException {
    public AccessoNegatoException(String message) {
        super(message);
    }
}
