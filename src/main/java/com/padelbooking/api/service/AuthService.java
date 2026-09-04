package com.padelbooking.api.service;

import com.padelbooking.api.dto.AuthDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.UtenteRepository;
import com.padelbooking.api.security.UtentePrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UtenteRepository utenteRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    public AuthService(UtenteRepository utenteRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager) {
        this.utenteRepository = utenteRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
    }

    public AuthDTO.UtenteResponse register(AuthDTO.RegisterRequest request) {
        if (utenteRepository.existsByTelefono(request.getTelefono())) {
            throw new BusinessRuleException("Esiste già un utente registrato con questo numero di telefono");
        }

        Utente utente = new Utente();
        utente.setTelefono(request.getTelefono());
        utente.setNome(request.getNome());
        utente.setCognome(request.getCognome());
        utente.setPassword(passwordEncoder.encode(request.getPassword()));
        utente.setDataNascita(request.getDataNascita());
        utente.setIsAdmin(false); // un utente non può auto-promuoversi admin in fase di registrazione

        Utente salvato = utenteRepository.save(utente);

        return new AuthDTO.UtenteResponse(salvato.getId(), salvato.getTelefono(), salvato.getNome(),
                salvato.getCognome(), salvato.getDataNascita(), salvato.getIsAdmin());
    }

    public AuthDTO.AuthResponse login(AuthDTO.LoginRequest request) {
        // Se le credenziali non sono valide, l'AuthenticationManager lancia BadCredentialsException,
        // già gestita centralmente dal GlobalExceptionHandler
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getTelefono(), request.getPassword())
        );

        Utente utente = utenteRepository.findByTelefono(request.getTelefono())
                .orElseThrow(() -> new BusinessRuleException("Utente non trovato"));

        return new AuthDTO.AuthResponse(utente.getId(), utente.getNome(), utente.getCognome(),
                utente.getTelefono(), utente.getDataNascita(),
                utente.getIsAdmin(), utente.getLivello());
    }
}
