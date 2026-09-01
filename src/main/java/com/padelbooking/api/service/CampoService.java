package com.padelbooking.api.service;

import com.padelbooking.api.dto.CampoDTO;
import com.padelbooking.api.exception.ResourceNotFoundException;
import com.padelbooking.api.model.Campo;
import com.padelbooking.api.repository.CampoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CampoService {

    private final CampoRepository campoRepository;

    public CampoService(CampoRepository campoRepository) {
        this.campoRepository = campoRepository;
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

    public void delete(Integer id) {
        Campo campo = trovaCampoOLancia(id);
        campoRepository.delete(campo);
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
