"""
Core financial calculation engine for SplitMate.

All monetary values are integers in minor units (e.g. paise for INR, cents for USD).
Never use floats for money. Rounding is deterministic: any remainder left over after
an equal/percentage/share split is distributed one minor-unit-at-a-time, in order,
to the first participants (largest-remainder method) so that:

    sum(splits) == total_amount   (always, exactly)
"""
from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from enum import Enum
from typing import Dict, List, Sequence


class SplitType(str, Enum):
    EQUAL = "equal"
    EXACT = "exact"
    PERCENTAGE = "percentage"
    SHARES = "shares"


class BalanceEngineError(ValueError):
    """Raised when an expense/split cannot be reconciled to an exact total."""


@dataclass(frozen=True)
class Payment:
    user_id: str
    amount_minor: int  # amount this user contributed (paid) toward the expense


@dataclass(frozen=True)
class Participant:
    user_id: str
    # For EXACT: amount_minor is the liability directly.
    # For PERCENTAGE: amount_minor field is unused; use `percentage_bp` (basis points, 1% = 100bp).
    # For SHARES: amount_minor field is unused; use `shares`.
    amount_minor: int = 0
    percentage_bp: int = 0
    shares: int = 0


def _largest_remainder_distribute(total: int, weights: Sequence[int]) -> List[int]:
    """Distribute `total` minor units across len(weights) buckets proportional to
    `weights`, guaranteeing the parts sum exactly to `total` (largest-remainder method).
    """
    n = len(weights)
    if n == 0:
        if total != 0:
            raise BalanceEngineError("Cannot distribute a non-zero amount across zero participants")
        return []
    weight_sum = sum(weights)
    if weight_sum <= 0:
        raise BalanceEngineError("Split weights must sum to a positive number")

    raw = [Decimal(total) * Decimal(w) / Decimal(weight_sum) for w in weights]
    floors = [int(r.to_integral_value(rounding="ROUND_FLOOR")) for r in raw]
    remainder = total - sum(floors)
    remainders = sorted(
        range(n), key=lambda i: (raw[i] - floors[i]), reverse=True
    )
    result = floors[:]
    for i in range(remainder):
        result[remainders[i % n]] += 1
    return result


def compute_equal_split(total_minor: int, participant_ids: Sequence[str]) -> Dict[str, int]:
    if not participant_ids:
        raise BalanceEngineError("Equal split requires at least one participant")
    amounts = _largest_remainder_distribute(total_minor, [1] * len(participant_ids))
    return dict(zip(participant_ids, amounts))


def compute_exact_split(total_minor: int, exact_amounts: Dict[str, int]) -> Dict[str, int]:
    if not exact_amounts:
        raise BalanceEngineError("Exact split requires at least one participant")
    given_total = sum(exact_amounts.values())
    if given_total != total_minor:
        raise BalanceEngineError(
            f"Exact split amounts ({given_total}) do not sum to the expense total ({total_minor})"
        )
    if any(v < 0 for v in exact_amounts.values()):
        raise BalanceEngineError("Exact split amounts cannot be negative")
    return dict(exact_amounts)


def compute_percentage_split(total_minor: int, percentages_bp: Dict[str, int]) -> Dict[str, int]:
    if not percentages_bp:
        raise BalanceEngineError("Percentage split requires at least one participant")
    total_bp = sum(percentages_bp.values())
    if total_bp != 10000:
        raise BalanceEngineError(
            f"Percentages must sum to 100.00% (10000 bp), got {total_bp / 100:.2f}%"
        )
    if any(v < 0 for v in percentages_bp.values()):
        raise BalanceEngineError("Percentages cannot be negative")
    ids = list(percentages_bp.keys())
    weights = [percentages_bp[i] for i in ids]
    amounts = _largest_remainder_distribute(total_minor, weights)
    return dict(zip(ids, amounts))


def compute_shares_split(total_minor: int, shares: Dict[str, int]) -> Dict[str, int]:
    if not shares:
        raise BalanceEngineError("Shares split requires at least one participant")
    if any(s <= 0 for s in shares.values()):
        raise BalanceEngineError("Shares must be positive integers")
    ids = list(shares.keys())
    weights = [shares[i] for i in ids]
    amounts = _largest_remainder_distribute(total_minor, weights)
    return dict(zip(ids, amounts))


def compute_split(
    split_type: SplitType,
    total_minor: int,
    *,
    participant_ids: Sequence[str] | None = None,
    exact_amounts: Dict[str, int] | None = None,
    percentages_bp: Dict[str, int] | None = None,
    shares: Dict[str, int] | None = None,
) -> Dict[str, int]:
    if split_type == SplitType.EQUAL:
        return compute_equal_split(total_minor, participant_ids or [])
    if split_type == SplitType.EXACT:
        return compute_exact_split(total_minor, exact_amounts or {})
    if split_type == SplitType.PERCENTAGE:
        return compute_percentage_split(total_minor, percentages_bp or {})
    if split_type == SplitType.SHARES:
        return compute_shares_split(total_minor, shares or {})
    raise BalanceEngineError(f"Unknown split type: {split_type}")


@dataclass(frozen=True)
class ExpenseNetResult:
    """Net contribution (paid - owed) per user for a single expense. Positive = is owed money."""
    net_by_user: Dict[str, int]


def compute_expense_net(
    payments: Sequence[Payment], liabilities: Dict[str, int]
) -> ExpenseNetResult:
    """Given who paid what and who owes what for one expense, compute each user's
    net position for that expense: positive means the group owes them, negative
    means they owe the group.
    """
    paid_total = sum(p.amount_minor for p in payments)
    owed_total = sum(liabilities.values())
    if paid_total != owed_total:
        raise BalanceEngineError(
            f"Sum of payments ({paid_total}) must equal sum of liabilities ({owed_total})"
        )
    net: Dict[str, int] = defaultdict(int)
    for p in payments:
        net[p.user_id] += p.amount_minor
    for user_id, liability in liabilities.items():
        net[user_id] -= liability
    return ExpenseNetResult(net_by_user=dict(net))


def aggregate_net_balances(expense_nets: Sequence[Dict[str, int]]) -> Dict[str, int]:
    """Sum net positions across many expenses (and settlements, represented the same way)
    into one running balance per user. Positive = user is owed money overall.
    """
    total: Dict[str, int] = defaultdict(int)
    for net in expense_nets:
        for user_id, amount in net.items():
            total[user_id] += amount
    return dict(total)


def settlement_as_net(payer_id: str, payee_id: str, amount_minor: int) -> Dict[str, int]:
    """A settlement payment reduces the payer's debt and reduces the payee's credit,
    i.e. it moves the balance the same direction an equivalent expense payment would.
    """
    if amount_minor <= 0:
        raise BalanceEngineError("Settlement amount must be positive")
    return {payer_id: amount_minor, payee_id: -amount_minor}
