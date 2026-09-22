import json
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user, require_group_member
from app.models import Expense, ExpenseParticipant, ExpensePayment, RecurringExpense, User
from app.balance_engine import compute_equal_split
from app.schemas import RecurringExpenseCreate

router = APIRouter(prefix="/recurring-expenses", tags=["recurring"])

_FREQUENCY_DELTA = {
    "daily": timedelta(days=1),
    "weekly": timedelta(weeks=1),
    "monthly": timedelta(days=30),
    "yearly": timedelta(days=365),
}


@router.post("", status_code=status.HTTP_201_CREATED)
def create_recurring_expense(
    payload: RecurringExpenseCreate, db: Session = Depends(get_db), user: User = Depends(get_current_user)
):
    require_group_member(payload.group_id, db, user)
    if payload.frequency not in _FREQUENCY_DELTA:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Invalid frequency")

    recurring = RecurringExpense(
        group_id=payload.group_id,
        description=payload.description,
        amount_minor=payload.amount_minor,
        currency=payload.currency,
        category=payload.category,
        split_type=payload.split_type,
        split_config=json.dumps({"participant_ids": [str(i) for i in payload.participant_ids]}),
        frequency=payload.frequency,
        paid_by=payload.paid_by,
        next_run_at=datetime.now(timezone.utc) + _FREQUENCY_DELTA[payload.frequency],
    )
    db.add(recurring)
    db.commit()
    db.refresh(recurring)
    return {"id": recurring.id, "next_run_at": recurring.next_run_at}


def run_due_recurring_expenses(db: Session) -> int:
    """Backend scheduler entry point (invoked by a cron/beat job). Materializes any
    recurring expense whose next_run_at has passed into a real Expense, then advances
    next_run_at. Returns the number of expenses created.
    """
    now = datetime.now(timezone.utc)
    due = db.query(RecurringExpense).filter(
        RecurringExpense.is_active.is_(True), RecurringExpense.next_run_at <= now
    ).all()
    created = 0
    for recurring in due:
        config = json.loads(recurring.split_config)
        participant_ids = config.get("participant_ids", [])
        if not participant_ids:
            continue
        liabilities = compute_equal_split(recurring.amount_minor, participant_ids)

        expense = Expense(
            group_id=recurring.group_id,
            description=recurring.description,
            amount_minor=recurring.amount_minor,
            currency=recurring.currency,
            category=recurring.category,
            split_type="equal",
            created_by=recurring.paid_by,
            recurring_expense_id=recurring.id,
        )
        db.add(expense)
        db.flush()
        for uid, amount in liabilities.items():
            db.add(ExpenseParticipant(expense_id=expense.id, user_id=uid, liability_minor=amount))
        db.add(ExpensePayment(expense_id=expense.id, user_id=recurring.paid_by, amount_minor=recurring.amount_minor))

        recurring.next_run_at = recurring.next_run_at + _FREQUENCY_DELTA[recurring.frequency]
        created += 1
    db.commit()
    return created
