package com.padelbooking.api.service;

import com.padelbooking.api.dto.ClassificaDTO;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.UtenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ClassificaService {

    private final UtenteRepository utenteRepository;

    public ClassificaService(UtenteRepository utenteRepository) {
        this.utenteRepository = utenteRepository;
    }

    // ============================================
    // Classifica
    // ============================================
    // UC_GCL_1, RF_GCL_1–2, RNF_P_1: legge il punteggio memorizzato su ogni utente, senza rielaborare lo
    // storico. Gli admin non sono in elenco, quindi per loro nessuna riga risulta utenteCorrente.
    @Transactional(readOnly = true)
    public List<ClassificaDTO.Response> getClassifica(String telefonoRichiedente) {
        List<Utente> utenti = utenteRepository.findClassifica();
        List<ClassificaDTO.Response> classifica = new ArrayList<>();
        int posizione = 0;
        Integer punteggioPrecedente = null;
        for (int i = 0; i < utenti.size(); i++) {
            Utente utente = utenti.get(i);
            // A parità di punti la posizione resta quella del primo, poi si salta: 1, 2, 2, 4
            if (!utente.getPunteggio().equals(punteggioPrecedente)) {
                posizione = i + 1;
                punteggioPrecedente = utente.getPunteggio();
            }
            classifica.add(new ClassificaDTO.Response(posizione, utente.getTelefono(), utente.getNome(),
                    utente.getCognome(), utente.getPunteggio(), utente.getTelefono().equals(telefonoRichiedente)));
        }
        return classifica;
    }
}
