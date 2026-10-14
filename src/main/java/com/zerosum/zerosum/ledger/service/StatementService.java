package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.common.web.PageResponse;
import com.zerosum.zerosum.ledger.repository.StatementLine;
import com.zerosum.zerosum.ledger.repository.StatementQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatementService {

	public static final int DEFAULT_PAGE_SIZE = 20;
	public static final int MAX_PAGE_SIZE = 100;

	private final AccountService accountService;
	private final StatementQuery statementQuery;

	/**
	 * One page of the user's account statement, newest first.
	 * <p>
	 * Read-only and repeatable-read, so the page and the total count come from the same snapshot even if a
	 * transfer commits in between.
	 *
	 * @param size capped at {@link #MAX_PAGE_SIZE}; the response reports the size actually used
	 * @throws AccountNotFoundException if the user has no account
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public PageResponse<StatementLine> statementFor(Long userId, int page, int size) {
		Long accountId = accountService.getForUser(userId).getId();
		int cappedSize = Math.min(size, MAX_PAGE_SIZE);
		return PageResponse.of(
				statementQuery.findPage(accountId, page, cappedSize),
				page,
				cappedSize,
				statementQuery.count(accountId));
	}

}
