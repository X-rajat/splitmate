"""
Debt simplification: given each user's net balance (positive = owed money,
negative = owes money) within a group, produce a minimal set of payer -> payee
transfers that resolves all balances to zero, without changing anyone's net
position.

Algorithm: greedy max-pair matching. Sort debtors and creditors by magnitude;
repeatedly settle the largest debtor against the largest creditor. This does not
guarantee the theoretical minimum number of transactions in every case (that is
NP-hard in general), but it is a well known, fast, correct approximation used by
most expense-splitting apps and is optimal for the common cases tested here.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List

from app.balance_engine import BalanceEngineError


@dataclass(frozen=True)
class SettlementSuggestion:
    from_user_id: str
    to_user_id: str
    amount_minor: int


def simplify_debts(net_balances: Dict[str, int]) -> List[SettlementSuggestion]:
    """net_balances: user_id -> net minor units (positive = is owed, negative = owes)."""
    if sum(net_balances.values()) != 0:
        raise BalanceEngineError(
            "Net balances must sum to zero to be simplifiable "
            f"(got {sum(net_balances.values())})"
        )

    creditors = [[uid, amt] for uid, amt in net_balances.items() if amt > 0]
    debtors = [[uid, -amt] for uid, amt in net_balances.items() if amt < 0]

    # Deterministic ordering: largest first, tie-broken by user_id for stable output.
    creditors.sort(key=lambda x: (-x[1], x[0]))
    debtors.sort(key=lambda x: (-x[1], x[0]))

    suggestions: List[SettlementSuggestion] = []
    ci, di = 0, 0
    while ci < len(creditors) and di < len(debtors):
        creditor_id, credit_amt = creditors[ci]
        debtor_id, debt_amt = debtors[di]
        transfer = min(credit_amt, debt_amt)
        if transfer > 0:
            suggestions.append(SettlementSuggestion(debtor_id, creditor_id, transfer))
        creditors[ci][1] -= transfer
        debtors[di][1] -= transfer
        if creditors[ci][1] == 0:
            ci += 1
        if debtors[di][1] == 0:
            di += 1
    return suggestions
