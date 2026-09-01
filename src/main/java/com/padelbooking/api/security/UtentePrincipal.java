package com.padelbooking.api.security;

import com.padelbooking.api.model.Utente;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class UtentePrincipal implements UserDetails {

    private final Utente utente;

    public UtentePrincipal(Utente utente) {
        this.utente = utente;
    }

    public Utente getUtente() {
        return utente;
    }

    public Integer getId() {
        return utente.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String ruolo = Boolean.TRUE.equals(utente.getIsAdmin()) ? "ROLE_ADMIN" : "ROLE_USER";
        return List.of(new SimpleGrantedAuthority(ruolo));
    }

    @Override
    public String getPassword() {
        return utente.getPassword();
    }

    @Override
    public String getUsername() {
        return utente.getTelefono();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
