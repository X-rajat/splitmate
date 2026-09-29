from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user, require_group_member
from app.models import GroupMember, Notification, Settlement, User
from app.schemas import SettlementCreate, SettlementOut

router = APIRouter(prefix="/settlements", tags=["settlements"])


def _active_member(db: Session, group_id, user_id) -> GroupMember | None:
    return (
        db.query(GroupMember)
        .filter(GroupMember.group_id == group_id, GroupMember.user_id == user_id, GroupMember.left_at.is_(None))
        .first()
    )


@router.post("", response_model=SettlementOut, status_code=status.HTTP_201_CREATED)
def create_settlement(
    payload: SettlementCreate, db: Session = Depends(get_db), user: User = Depends(get_current_user)
):
    caller_membership = require_group_member(payload.group_id, db, user)

    payee_is_member = _active_member(db, payload.group_id, payload.to_user_id)
    if payee_is_member is None:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Payee must be a member of this group")

    payer_id = payload.from_user_id or user.id
    if payer_id == payload.to_user_id:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Cannot settle up with yourself")

    if payer_id != user.id:
        # Recording a settlement on someone else's behalf: only allowed for a
        # placeholder guest (who has no login to do it themselves) or by a group admin.
        payer_membership = _active_member(db, payload.group_id, payer_id)
        if payer_membership is None:
            raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Payer must be a member of this group")
        payer_user = db.get(User, payer_id)
        if not (payer_user and payer_user.is_placeholder) and caller_membership.role != "admin":
            raise HTTPException(
                status.HTTP_403_FORBIDDEN,
                "Only that member, a group admin, or a placeholder guest's settlement can be recorded this way",
            )

    settlement = Settlement(
        group_id=payload.group_id,
        from_user_id=payer_id,
        to_user_id=payload.to_user_id,
        amount_minor=payload.amount_minor,
        currency=payload.currency,
        method=payload.method,
        upi_id=payload.upi_id,
        note=payload.note,
    )
    db.add(settlement)

    db.add(
        Notification(
            user_id=payload.to_user_id,
            type="settlement_received",
            title="Payment received",
            body=f"{user.name} recorded a {payload.amount_minor / 100:.2f} {payload.currency} settlement with you",
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
