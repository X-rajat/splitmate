import uuid

from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy import or_
from sqlalchemy.orm import Session

from app.database import get_db
from app.deps import get_current_user
from app.models import FriendRequest, Friendship, Notification, User
from app.schemas import FriendRequestCreate, FriendRequestOut, UserOut

router = APIRouter(prefix="/friends", tags=["friends"])


@router.get("/search", response_model=list[UserOut])
def search_users(q: str, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    if len(q) < 2:
        return []
    return (
        db.query(User)
        .filter(or_(User.email.ilike(f"%{q}%"), User.name.ilike(f"%{q}%")))
        .filter(User.id != user.id)
        .limit(20)
        .all()
    )


@router.post("/requests", response_model=FriendRequestOut, status_code=status.HTTP_201_CREATED)
def send_request(
    payload: FriendRequestCreate, db: Session = Depends(get_db), user: User = Depends(get_current_user)
):
    if payload.to_user_id == user.id:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Cannot friend yourself")
    target = db.get(User, payload.to_user_id)
    if target is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "User not found")

    existing = (
        db.query(FriendRequest)
        .filter(
            FriendRequest.from_user_id == user.id,
            FriendRequest.to_user_id == payload.to_user_id,
            FriendRequest.status == "pending",
        )
        .first()
    )
    if existing:
        raise HTTPException(status.HTTP_409_CONFLICT, "Request already sent")

    request = FriendRequest(from_user_id=user.id, to_user_id=payload.to_user_id)
    db.add(request)
    db.add(
        Notification(
            user_id=payload.to_user_id,
            type="friend_request",
            title="New friend request",
            body=f"{user.name} wants to add you as a friend",
        )
    )
    db.commit()
    db.refresh(request)
    return request


@router.post("/requests/{request_id}/respond", response_model=FriendRequestOut)
def respond_request(
    request_id: uuid.UUID,
    accept: bool,
    db: Session = Depends(get_db),
    user: User = Depends(get_current_user),
):
    request = db.get(FriendRequest, request_id)
    if request is None or request.to_user_id != user.id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Request not found")
    if request.status != "pending":
        raise HTTPException(status.HTTP_409_CONFLICT, "Request already handled")

    request.status = "accepted" if accept else "rejected"
    if accept:
        db.add(Friendship(user_id=request.from_user_id, friend_id=request.to_user_id))
        db.add(Friendship(user_id=request.to_user_id, friend_id=request.from_user_id))
    db.commit()
    db.refresh(request)
    return request


@router.get("", response_model=list[UserOut])
def list_friends(db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    friend_ids = [f.friend_id for f in db.query(Friendship).filter(Friendship.user_id == user.id)]
    return db.query(User).filter(User.id.in_(friend_ids)).all()
