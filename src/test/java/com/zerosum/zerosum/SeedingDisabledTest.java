package com.zerosum.zerosum;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.account.seed.TreasurySeeder;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.service.AdminSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** With zerosum.admin.enabled=false no admin is created, but TREASURY is always seeded. */
@SpringBootTest(properties = "zerosum.admin.enabled=false")
@ActiveProfiles("test")
class SeedingDisabledTest {

	@Autowired
	private AdminSeeder adminSeeder;

	@Autowired
	private TreasurySeeder treasurySeeder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Test
	void doesNotSeedAdminWhenDisabled() {
		accountRepository.deleteAll();
		userRepository.deleteAll();

		adminSeeder.seed();
		treasurySeeder.seed();

		assertThat(userRepository.count()).isZero();
		assertThat(accountRepository.findBySystemCode(Account.TREASURY)).isPresent();
	}

}
