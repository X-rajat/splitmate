import pytest

from app.balance_engine import (
    BalanceEngineError,
    Payment,
    SplitType,
    aggregate_net_balances,
    compute_equal_split,
    compute_exact_split,
    compute_expense_net,
    compute_percentage_split,
    compute_shares_split,
    compute_split,
    settlement_as_net,
)


# 1. Equal split -------------------------------------------------------------

def test_equal_split_divides_evenly():
    result = compute_equal_split(4800_00, ["rj", "amit", "rahul", "neha"])
    assert result == {"rj": 1200_00, "amit": 1200_00, "rahul": 1200_00, "neha": 1200_00}
    assert sum(result.values()) == 4800_00


def test_equal_split_with_remainder_sums_exactly():
    # 10 rupees / 3 people = 3.33... ; must still sum exactly, remainder goes to first.
    result = compute_equal_split(1000, ["a", "b", "c"])
    assert sum(result.values()) == 1000
    assert sorted(result.values()) == [333, 333, 334]


def test_equal_split_requires_participants():
    with pytest.raises(BalanceEngineError):
        compute_equal_split(1000, [])


# 2. Unequal / exact split ----------------------------------------------------

def test_exact_split_matches_total():
    result = compute_exact_split(4800_00, {"rj": 1500_00, "amit": 1000_00, "rahul": 1000_00, "neha": 1300_00})
    assert sum(result.values()) == 4800_00


def test_exact_split_rejects_mismatched_total():
    with pytest.raises(BalanceEngineError):
        compute_exact_split(4800_00, {"rj": 1000_00, "amit": 1000_00})


def test_exact_split_rejects_negative_amounts():
    with pytest.raises(BalanceEngineError):
        compute_exact_split(100, {"a": -50, "b": 150})


# 3. Percentage split ----------------------------------------------------------

def test_percentage_split_basic():
    # 30/20/20/30
    result = compute_percentage_split(4800_00, {"rj": 3000, "amit": 2000, "rahul": 2000, "neha": 3000})
    assert sum(result.values()) == 4800_00
    assert result["rj"] == result["neha"]
    assert result["amit"] == result["rahul"]


def test_percentage_split_must_sum_to_100():
    with pytest.raises(BalanceEngineError):
        compute_percentage_split(1000, {"a": 5000, "b": 4000})


def test_percentage_split_rounding_sums_exactly():
    # 3-way split at 33.33/33.33/33.34 style percentages against an odd total.
    result = compute_percentage_split(10001, {"a": 3334, "b": 3333, "c": 3333})
    assert sum(result.values()) == 10001


# 4. Shares split ---------------------------------------------------------------

def test_shares_split_basic():
    result = compute_shares_split(400_00, {"rj": 2, "amit": 1, "rahul": 1})
    assert sum(result.values()) == 400_00
    assert result["rj"] == 200_00
    assert result["amit"] == 100_00
    assert result["rahul"] == 100_00


def test_shares_split_rejects_non_positive_shares():
    with pytest.raises(BalanceEngineError):
        compute_shares_split(1000, {"a": 0, "b": 1})


def test_compute_split_dispatch():
    result = compute_split(SplitType.EQUAL, 900, participant_ids=["a", "b", "c"])
    assert sum(result.values()) == 900


# 5. Multiple payers ------------------------------------------------------------

def test_multiple_payers_net_balance():
    # Dinner = 10000; RJ paid 6000, Amit paid 4000. Split equally among rj, amit.
    payments = [Payment("rj", 6000), Payment("amit", 4000)]
    liabilities = compute_equal_split(10000, ["rj", "amit"])
    net = compute_expense_net(payments, liabilities)
    assert net.net_by_user["rj"] == 6000 - 5000
    assert net.net_by_user["amit"] == 4000 - 5000


def test_expense_net_rejects_payment_liability_mismatch():
    payments = [Payment("rj", 1000)]
    liabilities = {"rj": 500, "amit": 400}
    with pytest.raises(BalanceEngineError):
        compute_expense_net(payments, liabilities)


def test_single_payer_three_participants_matches_spec_example():
    # RJ pays 3000 for 3 participants (rj, amit, rahul) equal split.
    payments = [Payment("rj", 3000)]
    liabilities = compute_equal_split(3000, ["rj", "amit", "rahul"])
    net = compute_expense_net(payments, liabilities)
    assert net.net_by_user["rj"] == 3000 - 1000
    assert net.net_by_user["amit"] == -1000
    assert net.net_by_user["rahul"] == -1000


# 6. Multiple expenses aggregation ------------------------------------------------

def test_multiple_expenses_aggregate_correctly():
    e1 = compute_expense_net([Payment("rj", 1000)], compute_equal_split(1000, ["rj", "amit"])).net_by_user
    e2 = compute_expense_net([Payment("amit", 2000)], compute_equal_split(2000, ["rj", "amit"])).net_by_user
    totals = aggregate_net_balances([e1, e2])
    # e1: rj paid 1000, split equal -> rj +500, amit -500
    # e2: amit paid 2000, split equal -> rj -1000, amit +1000
    assert totals["rj"] == -500
    assert totals["amit"] == 500


# 7. Settlement --------------------------------------------------------------------

def test_settlement_moves_balance_correctly():
    net = settlement_as_net("amit", "rj", 1250)
    assert net == {"amit": 1250, "rj": -1250}
    # Applying it to an existing owed balance should zero it out.
    starting = {"rj": 1250, "amit": -1250}
    combined = aggregate_net_balances([starting, net])
    assert combined["rj"] == 0
    assert combined["amit"] == 0


def test_settlement_rejects_non_positive_amount():
    with pytest.raises(BalanceEngineError):
        settlement_as_net("a", "b", 0)


# 9. Partial settlement --------------------------------------------------------------

def test_partial_settlement_reduces_but_does_not_zero_balance():
    starting = {"rj": 1250, "amit": -1250}
    partial = settlement_as_net("amit", "rj", 500)
    combined = aggregate_net_balances([starting, partial])
    assert combined["rj"] == 750
    assert combined["amit"] == -750


# 14. Currency / cross validation -----------------------------------------------------

def test_zero_total_expense_is_valid_with_zero_splits():
    result = compute_equal_split(0, ["a", "b"])
    assert result == {"a": 0, "b": 0}


# 15. Decimal / rounding handling -------------------------------------------------------

def test_rounding_never_loses_or_gains_a_paisa():
    for total in range(1, 1000):
        for n in (2, 3, 4, 7):
            ids = [f"u{i}" for i in range(n)]
            result = compute_equal_split(total, ids)
            assert sum(result.values()) == total
