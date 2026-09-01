package com.padelbooking.api.security;

import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.UtenteRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class UtenteDetailsService implements UserDetailsService {

    private final UtenteRepository utenteRepository;

    public UtenteDetailsService(UtenteRepository utenteRepository) {
        this.utenteRepository = utenteRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String telefono) throws UsernameNotFoundException {
        Utente utente = utenteRepository.findByTelefono(telefono)
                .orElseThrow(() -> new UsernameNotFoundException("Utente non trovato: " + telefono));
        return new UtentePrincipal(utente);
    }
}
