from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user, require_group_member
from app.models import GroupMember, Notification, Settlement, User
from app.schemas import SettlementCreate, SettlementOut

router = APIRouter(prefix="/settlements", tags=["settlements"])


@router.post("", response_model=SettlementOut, status_code=status.HTTP_201_CREATED)
def create_settlement(
    payload: SettlementCreate, db: Session = Depends(get_db), user: User = Depends(get_current_user)
):
    require_group_member(payload.group_id, db, user)

    payee_is_member = (
        db.query(GroupMember)
        .filter(
            GroupMember.group_id == payload.group_id,
            GroupMember.user_id == payload.to_user_id,
            GroupMember.left_at.is_(None),
        )
        .first()
    )
    if payee_is_member is None:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Payee must be a member of this group")
    if payload.to_user_id == user.id:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Cannot settle up with yourself")

    settlement = Settlement(
        group_id=payload.group_id,
        from_user_id=user.id,
        to_user_id=payload.to_user_id,
        amount_minor=payload.amount_minor,
        currency=payload.currency,
        method=payload.method,
        upi_id=payload.upi_id,
        note=payload.note,
    )
    db.add(settlement)

    payee = db.get(User, payload.to_user_id)
    db.add(
        Notification(
            user_id=payload.to_user_id,
            type="settlement_received",
            title="Payment received",
            body=f"{user.name} settled {payload.amount_minor / 100:.2f} {payload.currency} with you",
        )
    )
    db.commit()
    db.refresh(settlement)
    return settlement


@router.get("/groups/{group_id}", response_model=list[SettlementOut])
def list_group_settlements(group_id, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    require_group_member(group_id, db, user)
    return (
        db.query(Settlement)
        .filter(Settlement.group_id == group_id)
        .order_by(Settlement.created_at.desc())
        .all()
    )
