package com.padelbooking.api.service;

import com.padelbooking.api.dto.PartitaDTO;
import com.padelbooking.api.repository.UtenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Ricerche sugli utenti che non riguardano una partita già esistente (R11)
@Service
public class UtenteService {

    private final UtenteRepository utenteRepository;

    public UtenteService(UtenteRepository utenteRepository) {
        this.utenteRepository = utenteRepository;
    }

    // ============================================
    // Utenti invitabili alla prenotazione
    // ============================================
    // R11: gli inviti si scelgono prima che la partita esista, quindi l'elenco è quello di
    // GET /api/partite/{id}/invitabili senza il filtro sui posti della partita: gli utenti non admin,
    // escluso chi chiama, che sarà il creatore. Stessa ricerca e stesso ordine (cognome, nome, telefono).
    @Transactional(readOnly = true)
    public List<PartitaDTO.InvitabileResponse> getInvitabili(String cerca, String telefonoRichiedente) {
        return utenteRepository.findNonAdmin(PartitaService.patternRicerca(cerca)).stream()
                .filter(u -> !u.getTelefono().equals(telefonoRichiedente))
                .map(u -> new PartitaDTO.InvitabileResponse(u.getTelefono(), u.getNome(), u.getCognome(),
                        u.getLivello()))
                .toList();
    }
}
