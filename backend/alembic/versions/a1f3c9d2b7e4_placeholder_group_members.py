"""placeholder (guest) group members

Revision ID: a1f3c9d2b7e4
Revises: 84613258788f
Create Date: 2026-09-29

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = 'a1f3c9d2b7e4'
down_revision: Union[str, None] = '84613258788f'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    op.add_column(
        "users",
        sa.Column("is_placeholder", sa.Boolean(), nullable=False, server_default=sa.false()),
    )
    op.alter_column("users", "password_hash", existing_type=sa.String(length=255), nullable=True)


def downgrade() -> None:
    op.alter_column("users", "password_hash", existing_type=sa.String(length=255), nullable=False)
    op.drop_column("users", "is_placeholder")
