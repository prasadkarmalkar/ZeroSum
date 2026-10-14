package com.zerosum.zerosum.account.repository;

import com.zerosum.zerosum.account.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

	Optional<Account> findByUserId(Long userId);

	Optional<Account> findBySystemCode(String systemCode);

}
