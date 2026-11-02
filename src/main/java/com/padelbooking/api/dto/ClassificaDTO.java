package com.padelbooking.api.dto;

public class ClassificaDTO {

    // Una riga della classifica
    public static class Response {
        // Chi ha gli stessi punti ha la stessa posizione: 1, 2, 2, 4
        private Integer posizione;
        private String telefono;
        private String nome;
        private String cognome;
        private Integer punteggio;
        // True solo sulla riga di chi chiama
        private Boolean utenteCorrente;

        public Response() {
        }

        public Response(Integer posizione, String telefono, String nome, String cognome,
                        Integer punteggio, Boolean utenteCorrente) {
            this.posizione = posizione;
            this.telefono = telefono;
            this.nome = nome;
            this.cognome = cognome;
            this.punteggio = punteggio;
            this.utenteCorrente = utenteCorrente;
        }

        public Integer getPosizione() {
            return posizione;
        }

        public void setPosizione(Integer posizione) {
            this.posizione = posizione;
        }

        public String getTelefono() {
            return telefono;
        }

        public void setTelefono(String telefono) {
            this.telefono = telefono;
        }

        public String getNome() {
            return nome;
        }

        public void setNome(String nome) {
            this.nome = nome;
        }

        public String getCognome() {
            return cognome;
        }

        public void setCognome(String cognome) {
            this.cognome = cognome;
        }

        public Integer getPunteggio() {
            return punteggio;
        }

        public void setPunteggio(Integer punteggio) {
            this.punteggio = punteggio;
        }

        public Boolean getUtenteCorrente() {
            return utenteCorrente;
        }

        public void setUtenteCorrente(Boolean utenteCorrente) {
            this.utenteCorrente = utenteCorrente;
        }
    }
}
