package com.ai_dlc.workshop.config;

import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Stateless JWT security configuration.
 * ARCHITECTURAL CONSTRAINT: bearer-token only — do NOT add formLogin, session, or CSRF.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http, Environment env) throws Exception {
        http
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            // sameOrigin required for H2 console which renders inside an iframe
            .headers(h -> h.frameOptions(fo -> fo.sameOrigin()))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health", "/actuator/info").permitAll();
                // H2 console, chat UI, and chat API permitted in non-prod only
                if (!env.matchesProfiles("prod")) {
                    auth.requestMatchers("/h2-console/**").permitAll();
                    // Chat page and SSE endpoint are open in dev/test — no JWT required
                    auth.requestMatchers("/chat", "/api/chat/**").permitAll();
                }
                auth.anyRequest().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * JwtDecoder used by the OAuth2 resource server filter.
     *
     * If {@code app.security.jwt.issuer-uri} is set (production), the decoder performs
     * full OIDC issuer validation and enforces the configured audience claim.
     * If not set (dev/test), a symmetric HMAC-256 decoder is created using a dev-only
     * key — no external OIDC server is required.
     *
     * NEVER deploy the dev decoder to production. The JWT_ISSUER_URI env var must be
     * set in every production environment.
     */
    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${app.security.jwt.issuer-uri:}") String issuerUri,
            @Value("${app.security.jwt.audience:}") String audience) {
        if (!issuerUri.isBlank()) {
            NimbusJwtDecoder decoder = (NimbusJwtDecoder) JwtDecoders.fromIssuerLocation(issuerUri);
            if (!audience.isBlank()) {
                OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<>(
                        JwtClaimNames.AUD,
                        aud -> aud instanceof List<?> list && list.contains(audience));
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuerUri), audienceValidator));
            }
            return decoder;
        }
        // Dev/test mode: symmetric key — tokens can be minted locally without an OIDC server.
        // Replace with a real issuer-uri for production.
        byte[] keyBytes = "dev-only-secret-key-not-for-production-32b!".getBytes();
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }
}
