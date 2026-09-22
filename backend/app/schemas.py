import uuid
from datetime import datetime
from typing import Optional

from pydantic import BaseModel, EmailStr, Field, model_validator


# ---- Auth ----

class RegisterRequest(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    email: Optional[EmailStr] = None
    mobile: Optional[str] = Field(default=None, max_length=20)
    password: str = Field(min_length=8, max_length=128)

    @model_validator(mode="after")
    def require_email_or_mobile(self) -> "RegisterRequest":
        if not self.email and not self.mobile:
            raise ValueError("Provide either an email or a mobile number")
        return self


class LoginRequest(BaseModel):
    identifier: str  # email or mobile
    password: str


class TokenPair(BaseModel):
    access_token: str
    refresh_token: str
    token_type: str = "bearer"


class RefreshRequest(BaseModel):
    refresh_token: str


class UserOut(BaseModel):
    id: uuid.UUID
    name: str
    email: Optional[EmailStr] = None
    mobile: Optional[str] = None
    profile_photo_url: Optional[str] = None
    default_currency: str

    class Config:
        from_attributes = True


# ---- Groups ----

class GroupCreate(BaseModel):
    name: str = Field(min_length=1, max_length=120)
    description: Optional[str] = None
    icon_url: Optional[str] = None
    currency: str = "INR"
    member_ids: list[uuid.UUID] = []


class GroupOut(BaseModel):
    id: uuid.UUID
    name: str
    description: Optional[str]
    icon_url: Optional[str]
    currency: str
    is_archived: bool
    member_count: int

    class Config:
        from_attributes = True


class InvitationOut(BaseModel):
    token: str
    invite_url: str
    share_message: str
    expires_at: datetime


class JoinGroupResponse(BaseModel):
    group: GroupOut
    already_member: bool


# ---- Expenses ----

class ExpenseParticipantIn(BaseModel):
    user_id: uuid.UUID
    amount_minor: Optional[int] = None       # exact
    percentage_bp: Optional[int] = None      # percentage (basis points)
    shares: Optional[int] = None             # shares


class ExpensePaymentIn(BaseModel):
    user_id: uuid.UUID
    amount_minor: int


class ExpenseCreate(BaseModel):
    description: str = Field(min_length=1, max_length=255)
    amount_minor: int = Field(gt=0)
    currency: str = "INR"
    category: str = "other"
    split_type: str  # equal/exact/percentage/shares
    participant_ids: list[uuid.UUID] = []  # for equal split
    participants: list[ExpenseParticipantIn] = []  # for exact/percentage/shares
    payments: list[ExpensePaymentIn]
    receipt_url: Optional[str] = None


class ExpenseOut(BaseModel):
    id: uuid.UUID
    group_id: uuid.UUID
    description: str
    amount_minor: int
    currency: str
    category: str
    split_type: str
    created_by: uuid.UUID
    created_at: datetime
    receipt_url: Optional[str]

    class Config:
        from_attributes = True


class BalanceEntry(BaseModel):
    user_id: uuid.UUID
    user_name: str
    net_minor: int  # positive = owed to them


class SettlementSuggestionOut(BaseModel):
    from_user_id: uuid.UUID
    from_user_name: str
    to_user_id: uuid.UUID
    to_user_name: str
    amount_minor: int


# ---- Settlements ----

class SettlementCreate(BaseModel):
    group_id: uuid.UUID
    to_user_id: uuid.UUID
    amount_minor: int = Field(gt=0)
    currency: str = "INR"
    method: str = "cash"
    upi_id: Optional[str] = None
    note: Optional[str] = None


class SettlementOut(BaseModel):
    id: uuid.UUID
    group_id: uuid.UUID
    from_user_id: uuid.UUID
    to_user_id: uuid.UUID
    amount_minor: int
    currency: str
    method: str
    created_at: datetime

    class Config:
        from_attributes = True


# ---- Friends ----

class FriendRequestCreate(BaseModel):
    to_user_id: uuid.UUID


class FriendRequestOut(BaseModel):
    id: uuid.UUID
    from_user_id: uuid.UUID
    to_user_id: uuid.UUID
    status: str

    class Config:
        from_attributes = True


# ---- Notifications ----

class NotificationOut(BaseModel):
    id: uuid.UUID
    type: str
    title: str
    body: str
    is_read: bool
    created_at: datetime

    class Config:
        from_attributes = True


# ---- Recurring ----

class RecurringExpenseCreate(BaseModel):
    group_id: uuid.UUID
    description: str
    amount_minor: int = Field(gt=0)
    currency: str = "INR"
    category: str = "other"
    split_type: str = "equal"
    frequency: str  # daily/weekly/monthly/yearly
    paid_by: uuid.UUID
    participant_ids: list[uuid.UUID] = []
