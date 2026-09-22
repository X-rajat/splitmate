import uuid
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import get_db
from app.deps import get_current_user, require_group_admin, require_group_member
from app.models import Group, GroupMember, Invitation, User
from app.schemas import GroupCreate, GroupOut, InvitationOut, JoinGroupResponse
from app.security import generate_invitation_token

router = APIRouter(prefix="/groups", tags=["groups"])
settings = get_settings()


def _group_out(db: Session, group: Group) -> GroupOut:
    member_count = (
        db.query(GroupMember)
        .filter(GroupMember.group_id == group.id, GroupMember.left_at.is_(None))
        .count()
    )
    return GroupOut(
        id=group.id,
        name=group.name,
        description=group.description,
        icon_url=group.icon_url,
        currency=group.currency,
        is_archived=group.is_archived,
        member_count=member_count,
    )


@router.get("", response_model=list[GroupOut])
def list_groups(db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    group_ids = [
        m.group_id
        for m in db.query(GroupMember).filter(
            GroupMember.user_id == user.id, GroupMember.left_at.is_(None)
        )
    ]
    groups = db.query(Group).filter(Group.id.in_(group_ids)).all()
    return [_group_out(db, g) for g in groups]


@router.post("", response_model=GroupOut, status_code=status.HTTP_201_CREATED)
def create_group(payload: GroupCreate, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    if payload.currency not in settings.supported_currencies:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Unsupported currency")

    group = Group(
        name=payload.name,
        description=payload.description,
        icon_url=payload.icon_url,
        currency=payload.currency,
        created_by=user.id,
    )
    db.add(group)
    db.flush()

    db.add(GroupMember(group_id=group.id, user_id=user.id, role="admin"))
    for member_id in payload.member_ids:
        if member_id == user.id:
            continue
        member = db.get(User, member_id)
        if member is None:
            continue
        db.add(GroupMember(group_id=group.id, user_id=member_id, role="member"))

    db.commit()
    db.refresh(group)
    return _group_out(db, group)


@router.get("/{group_id}", response_model=GroupOut)
def get_group(group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    require_group_member(group_id, db, user)
    group = db.get(Group, group_id)
    return _group_out(db, group)


@router.post("/{group_id}/members", status_code=status.HTTP_201_CREATED)
def add_member(
    group_id: uuid.UUID,
    member_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    require_group_admin(group_id, db, user)
    existing = (
        db.query(GroupMember)
        .filter(GroupMember.group_id == group_id, GroupMember.user_id == member_id)
        .first()
    )
    if existing and existing.left_at is None:
        raise HTTPException(status.HTTP_409_CONFLICT, "Already a member")
    if existing:
        existing.left_at = None
    else:
        db.add(GroupMember(group_id=group_id, user_id=member_id, role="member"))
    db.commit()
    return {"status": "added"}


@router.delete("/{group_id}/members/{member_id}")
def remove_member(
    group_id: uuid.UUID,
    member_id: uuid.UUID,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    require_group_admin(group_id, db, user)
    membership = (
        db.query(GroupMember)
        .filter(GroupMember.group_id == group_id, GroupMember.user_id == member_id)
        .first()
    )
    if membership is None or membership.left_at is not None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Member not found")
    membership.left_at = datetime.now(timezone.utc)
    db.commit()
    return {"status": "removed"}


@router.post("/{group_id}/invite", response_model=InvitationOut, status_code=status.HTTP_201_CREATED)
def create_invitation(
    group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)
):
    require_group_member(group_id, db, user)
    group = db.get(Group, group_id)

    token = generate_invitation_token()
    while db.query(Invitation).filter(Invitation.token == token).first() is not None:
        token = generate_invitation_token()

    invitation = Invitation(
        group_id=group_id,
        token=token,
        created_by=user.id,
        expires_at=datetime.now(timezone.utc) + timedelta(days=settings.invitation_token_expire_days),
    )
    db.add(invitation)
    db.commit()

    invite_url = f"{settings.invite_base_url}/{token}"
    return InvitationOut(
        token=token,
        invite_url=invite_url,
        share_message=(
            f'Join my "{group.name}" expense group on SplitMate.\n\nJoin here:\n{invite_url}'
        ),
        expires_at=invitation.expires_at,
    )


@router.post("/{group_id}/invite/disable")
def disable_invitations(
    group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)
):
    require_group_admin(group_id, db, user)
    db.query(Invitation).filter(Invitation.group_id == group_id).update({"is_disabled": True})
    db.commit()
    return {"status": "disabled"}


@router.post("/join/{token}", response_model=JoinGroupResponse)
def join_group(token: str, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    invitation = db.query(Invitation).filter(Invitation.token == token).first()
    if invitation is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Invalid invitation link")
    if invitation.is_disabled:
        raise HTTPException(status.HTTP_410_GONE, "This invitation has been disabled")
    if invitation.expires_at < datetime.now(timezone.utc):
        raise HTTPException(status.HTTP_410_GONE, "This invitation has expired")
    if invitation.max_uses is not None and invitation.use_count >= invitation.max_uses:
        raise HTTPException(status.HTTP_410_GONE, "This invitation has reached its usage limit")

    group = db.get(Group, invitation.group_id)
    if group is None or group.is_archived:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Group not found")

    existing = (
        db.query(GroupMember)
        .filter(GroupMember.group_id == group.id, GroupMember.user_id == user.id)
        .first()
    )
    already_member = existing is not None and existing.left_at is None
    if existing and existing.left_at is not None:
        existing.left_at = None
    elif existing is None:
        db.add(GroupMember(group_id=group.id, user_id=user.id, role="member"))
        invitation.use_count += 1

    db.commit()
    return JoinGroupResponse(group=_group_out(db, group), already_member=already_member)


@router.patch("/{group_id}")
def update_group(
    group_id: uuid.UUID,
    name: str | None = None,
    icon_url: str | None = None,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    require_group_admin(group_id, db, user)
    group = db.get(Group, group_id)
    if name:
        group.name = name
    if icon_url:
        group.icon_url = icon_url
    db.commit()
    return _group_out(db, group)


@router.post("/{group_id}/archive")
def archive_group(group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    require_group_admin(group_id, db, user)
    group = db.get(Group, group_id)
    group.is_archived = True
    db.commit()
    return {"status": "archived"}


@router.delete("/{group_id}")
def delete_group(group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    require_group_admin(group_id, db, user)
    group = db.get(Group, group_id)
    db.delete(group)
    db.commit()
    return {"status": "deleted"}
