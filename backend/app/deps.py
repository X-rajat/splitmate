import time
import uuid
from collections import defaultdict, deque

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from sqlalchemy.orm import Session

from app.config import get_settings
from app.database import get_db
from app.models import Group, GroupMember, User
from app.security import decode_token

settings = get_settings()
bearer_scheme = HTTPBearer()

# In-memory sliding-window rate limiter (per-IP). Swappable for a Redis-backed
# implementation in production; kept simple and dependency-free here.
_request_log: dict[str, deque] = defaultdict(deque)


def rate_limit(request: Request) -> None:
    client_ip = request.client.host if request.client else "unknown"
    now = time.monotonic()
    window = 60.0
    log = _request_log[client_ip]
    while log and now - log[0] > window:
        log.popleft()
    if len(log) >= settings.rate_limit_per_minute:
        raise HTTPException(status.HTTP_429_TOO_MANY_REQUESTS, "Rate limit exceeded")
    log.append(now)


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme),
    db: Session = Depends(get_db),
) -> User:
    try:
        payload = decode_token(credentials.credentials)
    except ValueError as exc:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid or expired token") from exc
    if payload.get("type") != "access":
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "Invalid token type")
    user = db.get(User, uuid.UUID(payload["sub"]))
    if user is None or not user.is_active:
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, "User not found or inactive")
    return user


def require_group_member(group_id: uuid.UUID, db: Session, user: User) -> GroupMember:
    """Authorization gate: a user may only touch a group's resources if they are an
    active member of it. This is what prevents IDOR — changing a group_id in a
    request never grants access to a group the caller isn't in.
    """
    membership = (
        db.query(GroupMember)
        .filter(
            GroupMember.group_id == group_id,
            GroupMember.user_id == user.id,
            GroupMember.left_at.is_(None),
        )
        .first()
    )
    if membership is None:
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Not a member of this group")
    group = db.get(Group, group_id)
    if group is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Group not found")
    return membership


def require_group_admin(group_id: uuid.UUID, db: Session, user: User) -> GroupMember:
    membership = require_group_member(group_id, db, user)
    if membership.role != "admin":
        raise HTTPException(status.HTTP_403_FORBIDDEN, "Admin privileges required")
    return membership
