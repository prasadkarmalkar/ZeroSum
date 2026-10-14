package com.zerosum.zerosum.ledger.repository;

import com.zerosum.zerosum.ledger.domain.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

	Optional<IdempotencyKey> findByUserIdAndKey(Long userId, String key);

}
