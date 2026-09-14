package com.padelbooking.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(Integer userId, String telefono, boolean isAdmin, Integer tokenVersion) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(telefono)
                .claim("userId", userId)
                .claim("isAdmin", isAdmin)
                .claim("tokenVersion", tokenVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public String extractTelefono(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Integer extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("userId", Integer.class);
    }

    public Integer extractTokenVersion(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("tokenVersion", Integer.class);
    }

    // Il token è valido solo se: il telefono corrisponde, non è scaduto (limite tecnico,
    // vedi jwt.expiration-ms) e la tokenVersion al suo interno coincide con quella
    // attualmente salvata sull'utente. Quest'ultimo controllo è il vero meccanismo di
    // invalidazione: ad ogni login la tokenVersion dell'utente viene incrementata, quindi
    // tutti i token emessi in precedenza (con la vecchia versione) smettono di essere validi.
    public boolean isTokenValid(String token, String telefono, Integer tokenVersionAttesa) {
        String telefonoNelToken = extractTelefono(token);
        Integer tokenVersionNelToken = extractTokenVersion(token);

        return telefonoNelToken.equals(telefono)
                && tokenVersionAttesa.equals(tokenVersionNelToken)
                && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
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
