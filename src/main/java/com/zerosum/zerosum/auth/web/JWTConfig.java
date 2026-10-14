package com.zerosum.zerosum.auth.web;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.*;

import java.io.IOException;
import java.io.InputStream;
import com.nimbusds.jose.jwk.RSAKey;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

@Configuration
@RequiredArgsConstructor
public class JWTConfig {

    private final JWTProperty jwtProperty;

    @Bean
    RSAPublicKey jwtPublicKey() throws IOException {
        try (InputStream in = jwtProperty.publicKey().getInputStream()) {
            return RsaKeyConverters.x509().convert(in);
        }
    }

    @Bean
    RSAPrivateKey jwtPrivateKey() throws IOException {
        try (InputStream in = jwtProperty.privateKey().getInputStream()) {
            return RsaKeyConverters.pkcs8().convert(in);
        }
    }

    /** Signs tokens with the private key. Used by the auth service at login. */
    @Bean
    JwtEncoder jwtEncoder(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        RSAKey rsaKey = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("zerosum-key-1").build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(rsaKey));
        return new NimbusJwtEncoder(jwkSource);
    }

    /** Verifies tokens with the public key: signature, expiry, and issuer. */
    @Bean
    JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtProperty.issuer()));
        return decoder;
    }
}
