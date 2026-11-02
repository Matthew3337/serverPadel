package com.padelbooking.api.exception;

// Dati della richiesta non validi secondo una regola che si controlla nel service, perché dipende
// dalla partita o perché il messaggio del contratto non deve avere il prefisso del campo: dà un 400
public class RichiestaNonValidaException extends RuntimeException {
    public RichiestaNonValidaException(String message) {
        super(message);
    }
}
