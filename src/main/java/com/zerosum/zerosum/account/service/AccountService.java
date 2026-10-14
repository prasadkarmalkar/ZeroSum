package com.zerosum.zerosum.account.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.common.ZerosumProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountRepository accountRepository;
	private final ZerosumProperties properties;

	/**
	 * Opens a user's account in the project currency, with a zero balance.
	 * <p>
	 * Joins the caller's transaction if there is one (as during registration), so the account is
	 * saved or rolled back together with the user.
	 */
	@Transactional
	public Account openUserAccount(Long userId) {
		Account account = accountRepository.saveAndFlush(Account.forUser(userId, properties.currency()));
		log.info("Opened account {} for user {}", account.getId(), userId);
		return account;
	}

	/** Opens a system account, e.g. {@link Account#TREASURY}, in the project currency with a zero balance. */
	@Transactional
	public Account openSystemAccount(String systemCode) {
		Account account = accountRepository.saveAndFlush(Account.system(systemCode, properties.currency()));
		log.info("Opened system account {} ({})", account.getId(), systemCode);
		return account;
	}

}
