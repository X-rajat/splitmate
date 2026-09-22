import csv
import io
import uuid
from collections import defaultdict

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user, require_group_member
from app.models import Expense, Settlement, User

router = APIRouter(prefix="/groups/{group_id}", tags=["analytics"])


@router.get("/analytics")
def group_analytics(group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    require_group_member(group_id, db, user)
    expenses = db.query(Expense).filter(Expense.group_id == group_id, Expense.is_deleted.is_(False)).all()

    total_spending = sum(e.amount_minor for e in expenses)
    by_category: dict[str, int] = defaultdict(int)
    by_member: dict[str, int] = defaultdict(int)
    by_month: dict[str, int] = defaultdict(int)

    for e in expenses:
        by_category[e.category] += e.amount_minor
        by_member[str(e.created_by)] += e.amount_minor
        by_month[e.created_at.strftime("%Y-%m")] += e.amount_minor

    largest = sorted(expenses, key=lambda e: e.amount_minor, reverse=True)[:5]

    return {
        "total_spending_minor": total_spending,
        "spending_by_category": by_category,
        "spending_by_member": by_member,
        "spending_by_month": by_month,
        "largest_expenses": [
            {"id": e.id, "description": e.description, "amount_minor": e.amount_minor} for e in largest
        ],
    }


@router.get("/export.csv")
def export_csv(group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    require_group_member(group_id, db, user)
    expenses = db.query(Expense).filter(Expense.group_id == group_id, Expense.is_deleted.is_(False)).all()
    settlements = db.query(Settlement).filter(Settlement.group_id == group_id).all()

    buffer = io.StringIO()
    writer = csv.writer(buffer)
    writer.writerow(["type", "date", "description", "amount_minor", "currency", "category", "from_or_paid_by"])
    for e in expenses:
        writer.writerow(["expense", e.created_at.isoformat(), e.description, e.amount_minor, e.currency, e.category, e.created_by])
    for s in settlements:
        writer.writerow(["settlement", s.created_at.isoformat(), "settlement", s.amount_minor, s.currency, "-", s.from_user_id])

    buffer.seek(0)
    return StreamingResponse(
        iter([buffer.getvalue()]),
        media_type="text/csv",
        headers={"Content-Disposition": f"attachment; filename=group_{group_id}_export.csv"},
    )
