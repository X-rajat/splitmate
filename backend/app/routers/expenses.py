import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.balance_engine import (
    BalanceEngineError,
    Payment,
    SplitType,
    aggregate_net_balances,
    compute_expense_net,
    compute_split,
)
from app.database import get_db
from app.deps import get_current_user, require_group_member
from app.models import (
    Expense,
    ExpenseParticipant,
    ExpensePayment,
    GroupMember,
    Notification,
    Settlement,
    User,
)
from app.schemas import (
    BalanceEntry,
    ExpenseCreate,
    ExpenseOut,
    SettlementSuggestionOut,
)
from app.simplify import simplify_debts

router = APIRouter(prefix="/groups/{group_id}/expenses", tags=["expenses"])


def _build_liabilities(payload: ExpenseCreate) -> dict[uuid.UUID, int]:
    split_type = SplitType(payload.split_type)
    if split_type == SplitType.EQUAL:
        if not payload.participant_ids:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "participant_ids required for equal split")
        return compute_split(split_type, payload.amount_minor, participant_ids=payload.participant_ids)

    if not payload.participants:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "participants required for this split type")

    try:
        if split_type == SplitType.EXACT:
            exact = {p.user_id: p.amount_minor for p in payload.participants}
            if any(v is None for v in exact.values()):
                raise BalanceEngineError("amount_minor required for every participant in exact split")
            return compute_split(split_type, payload.amount_minor, exact_amounts=exact)
        if split_type == SplitType.PERCENTAGE:
            pct = {p.user_id: p.percentage_bp for p in payload.participants}
            if any(v is None for v in pct.values()):
                raise BalanceEngineError("percentage_bp required for every participant")
            return compute_split(split_type, payload.amount_minor, percentages_bp=pct)
        if split_type == SplitType.SHARES:
            shares = {p.user_id: p.shares for p in payload.participants}
            if any(v is None for v in shares.values()):
                raise BalanceEngineError("shares required for every participant")
            return compute_split(split_type, payload.amount_minor, shares=shares)
    except BalanceEngineError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, str(exc)) from exc

    raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Unknown split_type")


@router.post("", response_model=ExpenseOut, status_code=status.HTTP_201_CREATED)
def create_expense(
    group_id: uuid.UUID,
    payload: ExpenseCreate,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    require_group_member(group_id, db, user)

    liabilities = _build_liabilities(payload)

    # IDOR guard: every participant/payer named in the request must actually be an
    # active member of THIS group.
    member_ids = {
        m.user_id
        for m in db.query(GroupMember).filter(
            GroupMember.group_id == group_id, GroupMember.left_at.is_(None)
        )
    }
    all_named = set(liabilities.keys()) | {p.user_id for p in payload.payments}
    if not all_named.issubset(member_ids):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "All participants/payers must be group members")

    try:
        compute_expense_net(
            [Payment(str(p.user_id), p.amount_minor) for p in payload.payments],
            {str(k): v for k, v in liabilities.items()},
        )
    except BalanceEngineError as exc:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, str(exc)) from exc

    expense = Expense(
        group_id=group_id,
        description=payload.description,
        amount_minor=payload.amount_minor,
        currency=payload.currency,
        category=payload.category,
        split_type=payload.split_type,
        receipt_url=payload.receipt_url,
        created_by=user.id,
    )
    db.add(expense)
    db.flush()

    for uid, amount in liabilities.items():
        db.add(ExpenseParticipant(expense_id=expense.id, user_id=uid, liability_minor=amount))
    for p in payload.payments:
        db.add(ExpensePayment(expense_id=expense.id, user_id=p.user_id, amount_minor=p.amount_minor))

    for member_id in member_ids:
        if member_id == user.id:
            continue
        db.add(
            Notification(
                user_id=member_id,
                type="expense_added",
                title="New expense",
                body=f"{user.name} added \"{payload.description}\" ({payload.amount_minor / 100:.2f} {payload.currency})",
            )
        )

    db.commit()
    db.refresh(expense)
    return expense


@router.get("", response_model=list[ExpenseOut])
def list_expenses(
    group_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
    category: str | None = None,
    member_id: uuid.UUID | None = None,
):
    require_group_member(group_id, db, user)
    query = db.query(Expense).filter(Expense.group_id == group_id, Expense.is_deleted.is_(False))
    if category:
        query = query.filter(Expense.category == category)
    if member_id:
        query = query.join(ExpenseParticipant).filter(ExpenseParticipant.user_id == member_id)
    return query.order_by(Expense.created_at.desc()).all()


@router.delete("/{expense_id}")
def delete_expense(
    group_id: uuid.UUID,
    expense_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    require_group_member(group_id, db, user)
    expense = db.get(Expense, expense_id)
    if expense is None or expense.group_id != group_id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Expense not found")
    expense.is_deleted = True
    db.commit()
    return {"status": "deleted"}


def _group_net_balances(db: Session, group_id: uuid.UUID) -> dict[uuid.UUID, int]:
    nets: list[dict[str, int]] = []
    expenses = db.query(Expense).filter(Expense.group_id == group_id, Expense.is_deleted.is_(False)).all()
    for expense in expenses:
        payments = [Payment(str(p.user_id), p.amount_minor) for p in expense.payments]
        liabilities = {str(p.user_id): p.liability_minor for p in expense.participants}
        nets.append(compute_expense_net(payments, liabilities).net_by_user)

    settlements = db.query(Settlement).filter(Settlement.group_id == group_id).all()
    for s in settlements:
        nets.append({str(s.from_user_id): s.amount_minor, str(s.to_user_id): -s.amount_minor})

    totals = aggregate_net_balances(nets)
    return {uuid.UUID(k): v for k, v in totals.items()}


balances_router = APIRouter(prefix="/groups/{group_id}", tags=["balances"])


@balances_router.get("/balances", response_model=list[BalanceEntry])
def get_balances(group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    require_group_member(group_id, db, user)
    totals = _group_net_balances(db, group_id)
    result = []
    for uid, amount in totals.items():
        member = db.get(User, uid)
        if member is None:
            continue
        result.append(BalanceEntry(user_id=uid, user_name=member.name, net_minor=amount))
    return result


@balances_router.get("/settlements/suggested", response_model=list[SettlementSuggestionOut])
def get_suggested_settlements(
    group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)
):
    require_group_member(group_id, db, user)
    totals = _group_net_balances(db, group_id)
    non_zero = {str(k): v for k, v in totals.items() if v != 0}
    if not non_zero:
        return []
    suggestions = simplify_debts(non_zero)
    users_cache: dict[str, User] = {}

    def name(uid: str) -> str:
        if uid not in users_cache:
            users_cache[uid] = db.get(User, uuid.UUID(uid))
        return users_cache[uid].name if users_cache[uid] else "Unknown"

    return [
        SettlementSuggestionOut(
            from_user_id=uuid.UUID(s.from_user_id),
            from_user_name=name(s.from_user_id),
            to_user_id=uuid.UUID(s.to_user_id),
            to_user_name=name(s.to_user_id),
            amount_minor=s.amount_minor,
        )
        for s in suggestions
    ]
