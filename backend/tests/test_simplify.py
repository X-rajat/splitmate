import pytest

from app.balance_engine import BalanceEngineError
from app.simplify import simplify_debts


# 8. Debt simplification -----------------------------------------------------

def test_simplify_matches_spec_example():
    # RJ is owed 1250 + 850 = 2100 total split between two debtors elsewhere;
    # here: RJ owed 1250 by Amit, RJ owed 850 by Rahul, Amit owed 400 by Neha.
    balances = {
        "rj": 2100,
        "amit": 1250 - 1250 - 400,  # amit net after paying rj and being owed by neha
        "rahul": -850,
        "neha": -400,
    }
    # Fix up so it sums to zero for the test (illustrative numbers from the spec).
    balances = {"rj": 1250 + 850, "amit": -1250 + 400, "rahul": -850, "neha": -400}
    assert sum(balances.values()) == 0
    suggestions = simplify_debts(balances)
    total_transferred = sum(s.amount_minor for s in suggestions)
    # Every debtor's debt must be fully covered.
    assert total_transferred == sum(v for v in balances.values() if v < 0) * -1


def test_simplify_chain_reduces_transaction_count():
    # A owes B 1000, B owes C 1000, C owes D 1000 => net: A -1000, D +1000, B/C = 0
    balances = {"a": -1000, "b": 0, "c": 0, "d": 1000}
    suggestions = simplify_debts(balances)
    assert len(suggestions) == 1
    assert suggestions[0].from_user_id == "a"
    assert suggestions[0].to_user_id == "d"
    assert suggestions[0].amount_minor == 1000


def test_simplify_preserves_net_balances():
    balances = {"a": 500, "b": -200, "c": -300}
    suggestions = simplify_debts(balances)
    recomputed = {uid: 0 for uid in balances}
    for s in suggestions:
        recomputed[s.from_user_id] -= s.amount_minor
        recomputed[s.to_user_id] += s.amount_minor
    assert recomputed == balances


def test_simplify_rejects_nonzero_sum():
    with pytest.raises(BalanceEngineError):
        simplify_debts({"a": 100, "b": -50})


def test_simplify_no_debts_returns_empty():
    assert simplify_debts({"a": 0, "b": 0}) == []


def test_simplify_minimizes_transactions_for_five_users():
    balances = {"rj": 2350, "amit": -400, "rahul": -850, "neha": -1500, "karan": 400}
    assert sum(balances.values()) == 0
    suggestions = simplify_debts(balances)
    # Never more transactions than (number of non-zero balances - 1)
    non_zero = sum(1 for v in balances.values() if v != 0)
    assert len(suggestions) <= non_zero - 1
