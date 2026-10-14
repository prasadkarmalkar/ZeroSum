package com.zerosum.zerosum.account.listener;

import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.common.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Opens an account for every newly registered user.
 * <p>
 * A plain {@code @EventListener} on purpose, not {@code @TransactionalEventListener}: it runs
 * synchronously inside the registration transaction, so if opening the account fails, the user
 * is rolled back too and a user without an account can never exist.
 */
@Component
@RequiredArgsConstructor
public class UserRegisteredListener {

	private final AccountService accountService;

	@EventListener
	public void onUserRegistered(UserRegisteredEvent event) {
		accountService.openUserAccount(event.userId());
	}

}
