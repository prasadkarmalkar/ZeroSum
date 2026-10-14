package com.zerosum.zerosum.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	private static final String[] PUBLIC_ENDPOINTS = {
			"/swagger-ui.html",
			"/swagger-ui/**",
			"/v3/api-docs/**",
			"/actuator/health",
			"/actuator/health/**"
	};

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				// Token-based API with no browser forms, so CSRF protection and sessions are not needed
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(PUBLIC_ENDPOINTS).permitAll()
						// Only registration is public; the rest of /auth/** stays protected
						.requestMatchers(HttpMethod.POST, "/auth/register").permitAll()
						.anyRequest().authenticated());
		return http.build();
	}

	/** Encodes passwords with BCrypt (default strength 10); hashes look like {@code $2a$10$...}. */
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
