package com.zerosum.zerosum.auth.service;

import com.zerosum.zerosum.auth.exception.EmailAlreadyRegisteredException;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.web.Role;
import com.zerosum.zerosum.auth.web.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the admin user from {@code zerosum.admin.*} exists, on every startup.
 * <p>
 * Safe to run repeatedly: an existing user with that email is left exactly as it is
 * (its password and role are never overwritten).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {

	private final AdminSeedProperties properties;
	private final UserRepository userRepository;
	private final AuthService authService;

	@Override
	public void run(ApplicationArguments args) {
		seed();
	}

	public void seed() {
		if (!properties.enabled()) {
			log.info("Admin seeding is disabled (zerosum.admin.enabled=false)");
			return;
		}
		if (isBlank(properties.email()) || isBlank(properties.password())) {
			throw new IllegalStateException(
					"zerosum.admin.email and zerosum.admin.password must be set when zerosum.admin.enabled=true");
		}

		String email = User.normalizeEmail(properties.email());
		userRepository.findByEmail(email).ifPresentOrElse(
				existing -> {
					if (existing.getRole() != Role.ADMIN) {
						log.warn("Admin email {} belongs to user {} with role {}; not changing it",
								email, existing.getId(), existing.getRole());
					}
				},
				() -> create(email));
	}

	private void create(String email) {
		try {
			User admin = authService.createAdmin(email, properties.password());
			log.info("Seeded admin user {}", admin.getId());
		} catch (EmailAlreadyRegisteredException e) {
			// Another instance seeded it at the same moment; that's the outcome we wanted
			log.info("Admin user was created concurrently; nothing to do");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
