package com.padelbooking.api.exception;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessRule(BusinessRuleException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Telefono o password non corretti");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String messaggio = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .orElse("Dati non validi");
        return buildResponse(HttpStatus.BAD_REQUEST, messaggio);
    }

    // Body non leggibile: JSON malformato oppure un valore che non si converte nel tipo del campo
    // (es. data non in formato AAAA-MM-GG). Senza questo handler finirebbe nel 500 generico.
    // Nel messaggio indichiamo solo il nome del campo, mai il messaggio interno di Jackson.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBodyNonLeggibile(HttpMessageNotReadableException ex) {
        String campo = nomeCampoJson(ex.getCause());
        String messaggio = campo != null
                ? "Valore non valido per il campo " + campo
                        + " (date nel formato AAAA-MM-GG, orari nel formato HH:MM o HH:MM:SS)"
                : "Corpo della richiesta non valido: controlla che sia un JSON corretto";
        return buildResponse(HttpStatus.BAD_REQUEST, messaggio);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleParametroMancante(MissingServletRequestParameterException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Parametro obbligatorio mancante: " + ex.getParameterName());
    }

    // Parametro di query o di path non convertibile (es. idCampo=abc, data=2026-13-45)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTipoParametroErrato(MethodArgumentTypeMismatchException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Valore non valido per il parametro " + ex.getName());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno: " + ex.getMessage());
    }

    // Percorso del campo che Jackson non è riuscito a leggere (es. "dataPrenotazione"),
    // null se il problema è la sintassi del JSON e non un campo specifico
    private String nomeCampoJson(Throwable causa) {
        if (!(causa instanceof JsonMappingException mappingEx) || mappingEx.getPath().isEmpty()) {
            return null;
        }
        String percorso = mappingEx.getPath().stream()
                .map(rif -> rif.getFieldName() != null ? rif.getFieldName() : "[" + rif.getIndex() + "]")
                .collect(Collectors.joining("."));
        return percorso.isEmpty() ? null : percorso;
    }

    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("errore", message);
        return ResponseEntity.status(status).body(body);
    }
}
