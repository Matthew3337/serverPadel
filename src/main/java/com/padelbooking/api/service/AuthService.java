package com.padelbooking.api.service;

import com.padelbooking.api.dto.AuthDTO;
import com.padelbooking.api.exception.BusinessRuleException;
import com.padelbooking.api.model.Utente;
import com.padelbooking.api.repository.UtenteRepository;
import com.padelbooking.api.security.JwtUtil;
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
    private final JwtUtil jwtUtil;

    public AuthService(UtenteRepository utenteRepository,
                        PasswordEncoder passwordEncoder,
                        AuthenticationManager authenticationManager,
                        JwtUtil jwtUtil) {
        this.utenteRepository = utenteRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    public AuthDTO.AuthResponse register(AuthDTO.RegisterRequest request) {
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

        String token = jwtUtil.generateToken(salvato.getId(), salvato.getTelefono(), salvato.getIsAdmin());

        return new AuthDTO.AuthResponse(token, salvato.getId(), salvato.getNome(), salvato.getCognome(), salvato.getIsAdmin());
    }

    public AuthDTO.AuthResponse login(AuthDTO.LoginRequest request) {
        // Se le credenziali non sono valide, l'AuthenticationManager lancia BadCredentialsException,
        // già gestita centralmente dal GlobalExceptionHandler
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getTelefono(), request.getPassword())
        );

        Utente utente = utenteRepository.findByTelefono(request.getTelefono())
                .orElseThrow(() -> new BusinessRuleException("Utente non trovato"));

        String token = jwtUtil.generateToken(utente.getId(), utente.getTelefono(), utente.getIsAdmin());

        return new AuthDTO.AuthResponse(token, utente.getId(), utente.getNome(), utente.getCognome(), utente.getIsAdmin());
    }
}
