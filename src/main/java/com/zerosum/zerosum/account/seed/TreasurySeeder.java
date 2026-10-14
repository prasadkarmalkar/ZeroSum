package com.zerosum.zerosum.account.seed;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.account.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * Ensures the TREASURY system account exists, on every startup.
 * <p>
 * Safe to run repeatedly: an existing treasury, and its balance, is left untouched.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TreasurySeeder implements ApplicationRunner {

	private final AccountRepository accountRepository;
	private final AccountService accountService;

	@Override
	public void run(ApplicationArguments args) {
		seed();
	}

	public void seed() {
		if (accountRepository.findBySystemCode(Account.TREASURY).isPresent()) {
			return;
		}
		try {
			accountService.openSystemAccount(Account.TREASURY);
		} catch (DataIntegrityViolationException e) {
			// Another instance created it at the same moment (uk_accounts_system_code); that's the outcome we wanted
			log.info("Treasury account was created concurrently; nothing to do");
		}
	}

}
