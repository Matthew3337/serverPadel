package com.padelbooking.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * Il token contiene soltanto l'identità immutabile dell'utente. Non ha claim
     * temporali né dati che possano cambiare al login, quindi per lo stesso telefono
     * viene generata sempre la medesima stringa JWT finché la chiave di firma resta invariata.
     */
    public String generateToken(String telefono) {
        return Jwts.builder()
                .subject(telefono)
                .signWith(getSigningKey())
                .compact();
    }

    public String extractTelefono(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String telefono) {
        String telefonoNelToken = extractTelefono(token);
        return telefonoNelToken.equals(telefono);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
