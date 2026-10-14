package com.zerosum.zerosum.concurrency;

import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.service.AdminSeeder;
import com.zerosum.zerosum.auth.service.AuthService;
import com.zerosum.zerosum.auth.web.RegisterRequest;
import com.zerosum.zerosum.auth.web.User;
import com.zerosum.zerosum.ledger.service.DepositService;
import com.zerosum.zerosum.ledger.web.DepositRequest;
import org.springframework.boot.test.context.TestComponent;

import java.util.UUID;

/**
 * Creates users and funds them the way production does: an admin deposit out of TREASURY,
 * through the real {@link DepositService}.
 */
@TestComponent
public class TestUsers {

	private final AuthService authService;
	private final DepositService depositService;
	private final AdminSeeder adminSeeder;
	private final UserRepository userRepository;

	public TestUsers(AuthService authService, DepositService depositService, AdminSeeder adminSeeder,
			UserRepository userRepository) {
		this.authService = authService;
		this.depositService = depositService;
		this.adminSeeder = adminSeeder;
		this.userRepository = userRepository;
	}

	/** Registers a user and, if the amount is positive, deposits that much into their account. */
	public User createFunded(String email, String amount) {
		User user = authService.register(new RegisterRequest(email, "s3cret-Passw0rd"));
		if (!amount.matches("0+(\\.0+)?")) {
			depositService.deposit(admin().getId(), "fund-" + UUID.randomUUID(),
					new DepositRequest(email, amount, "Test funding"));
		}
		return user;
	}

	private User admin() {
		adminSeeder.seed(); // no-op when the admin already exists
		return userRepository.findByEmail("admin@zerosum.test").orElseThrow();
	}

}
