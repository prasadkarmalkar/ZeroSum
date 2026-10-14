package com.zerosum.zerosum.ledger.repository;

import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Currency;
import java.util.List;
import java.util.Map;

/**
 * Reads an account's statement with plain SQL, since JPQL has no window functions.
 * <p>
 * The running balance is a window {@code SUM} over <em>all</em> of the account's entries, oldest first,
 * computed in the inner query before the outer query sorts and pages. So every row's balance is right
 * whichever page it lands on, and the newest row's balance equals the account's current balance.
 * <p>
 * Order is {@code created_at}, then entry id: entry ids only grow, so rows created in the same instant
 * still have one fixed order, and pages never overlap or skip rows. The same order is used for the
 * running balance (oldest first) and for display (newest first).
 */
@Repository
@RequiredArgsConstructor
public class StatementQuery {

	private static final String PAGE_SQL = """
			select *
			from (
			    select e.id              as entry_id,
			           t.id              as transaction_id,
			           t.type            as type,
			           t.description     as description,
			           t.created_at      as created_at,
			           e.amount_minor    as amount_minor,
			           e.currency        as currency,
			           sum(e.amount_minor) over (
			               order by t.created_at, e.id
			               rows between unbounded preceding and current row
			           )                 as balance_after_minor
			    from ledger_entries e
			    join ledger_transactions t on t.id = e.transaction_id
			    where e.account_id = :accountId
			) statement
			order by created_at desc, entry_id desc
			limit :limit offset :offset
			""";

	private static final String COUNT_SQL = "select count(*) from ledger_entries where account_id = :accountId";

	private final NamedParameterJdbcTemplate jdbc;

	public List<StatementLine> findPage(Long accountId, int page, int size) {
		return jdbc.query(PAGE_SQL,
				Map.of("accountId", accountId, "limit", size, "offset", (long) page * size),
				StatementQuery::toLine);
	}

	public long count(Long accountId) {
		return jdbc.queryForObject(COUNT_SQL, Map.of("accountId", accountId), Long.class);
	}

	private static StatementLine toLine(ResultSet rs, int rowNum) throws SQLException {
		Currency currency = Currency.getInstance(rs.getString("currency"));
		return new StatementLine(
				rs.getLong("entry_id"),
				rs.getLong("transaction_id"),
				TransactionType.valueOf(rs.getString("type")),
				rs.getString("description"),
				rs.getTimestamp("created_at").toInstant(),
				new Money(rs.getLong("amount_minor"), currency),
				new Money(rs.getLong("balance_after_minor"), currency));
	}

}
