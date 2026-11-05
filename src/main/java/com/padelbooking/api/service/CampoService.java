package com.padelbooking.api.service;

import com.padelbooking.api.dto.CampoDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.repository.CampoRepository;
import com.padelbooking.api.repository.PartitaRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CampoService {

    // Chiave esterna partita.id_campo
    private static final String VINCOLO_PARTITA_CAMPO = "fk_partita_campo";

    private static final String MESSAGGIO_CAMPO_CON_PARTITE = "Il campo ha partite collegate e non può essere eliminato";

    private final CampoRepository campoRepository;
    private final PartitaRepository partitaRepository;

    public CampoService(CampoRepository campoRepository, PartitaRepository partitaRepository) {
        this.campoRepository = campoRepository;
        this.partitaRepository = partitaRepository;
    }

    public List<CampoDTO.Response> getAll() {
        return campoRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public CampoDTO.Response getById(Integer id) {
        Campo campo = trovaCampoOLancia(id);
        return toResponse(campo);
    }

    public CampoDTO.Response create(CampoDTO.Request request) {
        Campo campo = new Campo();
        campo.setNome(request.getNome());
        campo.setAlCoperto(request.getAlCoperto());
        campo.setOraApertura(request.getOraApertura());
        campo.setOraChiusura(request.getOraChiusura());

        Campo salvato = campoRepository.save(campo);
        return toResponse(salvato);
    }

    public CampoDTO.Response update(Integer id, CampoDTO.Request request) {
        Campo campo = trovaCampoOLancia(id);
        campo.setNome(request.getNome());
        campo.setAlCoperto(request.getAlCoperto());
        campo.setOraApertura(request.getOraApertura());
        campo.setOraChiusura(request.getOraChiusura());

        Campo salvato = campoRepository.save(campo);
        return toResponse(salvato);
    }

    // RF_GCA_3: qualunque partita collegata, anche annullata o passata, blocca l'eliminazione per la chiave esterna
    @Transactional
    public void delete(Integer id) {
        Campo campo = trovaCampoOLancia(id);
        if (partitaRepository.existsByCampoId(id)) {
            throw new BusinessRuleException(MESSAGGIO_CAMPO_CON_PARTITE);
        }
        try {
            campoRepository.delete(campo);
            // Flush esplicito: una partita creata tra il controllo e la cancellazione fa scattare la chiave
            // esterna, e al commit l'errore arriverebbe fuori da qui e diventerebbe un 500
            campoRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            if (PartitaService.violaVincolo(ex, VINCOLO_PARTITA_CAMPO)) {
                throw new BusinessRuleException(MESSAGGIO_CAMPO_CON_PARTITE);
            }
            throw ex;
        }
    }

    // Usato anche da PrenotazioneService, per questo resta pubblico e restituisce l'Entity
    public Campo trovaCampoOLancia(Integer id) {
        return campoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Campo non trovato con id " + id));
    }

    private CampoDTO.Response toResponse(Campo campo) {
        return new CampoDTO.Response(
                campo.getId(),
                campo.getNome(),
                campo.getAlCoperto(),
                campo.getOraApertura(),
                campo.getOraChiusura()
        );
    }
}
