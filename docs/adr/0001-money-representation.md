# ADR 0001: Money is `long` minor units inside, decimal strings in the API

- **Status:** Accepted

## Context

A ledger must never lose a fraction of a paisa. Floating point (`double`) can't represent 0.10 exactly,
so it is out. That leaves two exact options for the domain, and a separate question for the JSON API,
where many clients (JavaScript above all) parse every JSON number as a 64-bit float.

## Decision

**Inside the application**, an amount is a `Money(long amountMinor, Currency currency)` record: a whole
number of the currency's minor units (paise for INR). It is stored the same way, as a `BIGINT` column.
Arithmetic uses `Math.addExact` and friends, so an overflow throws instead of silently wrapping.
Operations between two currencies throw. There is one configured currency (`zerosum.currency`).

**In the API**, amounts are **decimal strings** at the currency's scale, both ways:
`"amount": "12.50"`, `"balance": "1050.00"`. `Money.parse` and `Money.toDecimalString` convert.
Requests are validated as a positive decimal with at most 2 places.

## Consequences

- No rounding anywhere: every amount is an exact integer, and `equals` just works.
- `BIGINT` columns are simple to sum, index and check (e.g. `balance_minor >= 0`).
- JSON strings can't be misread as floats, and they're still human-readable. Clients that do arithmetic
  must parse them with a decimal type, not a float.
- `BigDecimal` would also be exact, but brings scale handling (`1.0` is not `equals` to `1.00`) and slower
  arithmetic, for no benefit with a single fixed-scale currency.
- The request rule "at most 2 decimal places" matches INR. A currency with a different scale would need
  that validation changed too.
