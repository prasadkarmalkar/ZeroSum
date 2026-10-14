package com.zerosum.zerosum.account.repository;

import com.zerosum.zerosum.account.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

	Optional<Account> findByUserId(Long userId);

	Optional<Account> findBySystemCode(String systemCode);

	/**
	 * Loads an account and locks its row ({@code SELECT ... FOR UPDATE}) until the current transaction ends,
	 * so concurrent writers take turns. Waits at most the connection's {@code lock_timeout}.
	 * Use {@code AccountService.lockAccounts}, which locks in a deadlock-free order.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Account a where a.id = :id")
	Optional<Account> findByIdForUpdate(Long id);

}
