package ru.spbstu.hsai.auth;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {
    private final String apiKey;
    private final Object principal;

    public ApiKeyAuthenticationToken(String apiKey) {
        super(null);
        this.apiKey = apiKey;
        this.principal = apiKey;
        setAuthenticated(false); // Важно: сначала не аутентифицирован
    }

    public ApiKeyAuthenticationToken(String apiKey, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.apiKey = apiKey;
        this.principal = apiKey;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return apiKey;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}