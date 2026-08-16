"""add shorts_limit_cycles and hud_appearance

Revision ID: fd8a365d772d
Revises: 657ba9f4d4f8
Create Date: 2026-08-16 12:00:00.000000

Scope (reviewed — Shorts Control backend domain ONLY):
  1. NEW table `shorts_limit_cycles` — the durable 24-hour enforcement
     cycle. Single-active-cycle guard: unique (user_id, is_active) where
     `is_active` is True for the current window and NULL for historical
     windows (MySQL treats NULLs as distinct, so many historical cycles can
     coexist while at most one row per user is active). device_id is stored
     for future multi-device support but is NOT part of the uniqueness —
     the approved single-device development reality keeps the active cycle
     per-user, and a broad global unique constraint would wrongly break
     future multi-device work.
  2. `shorts_settings.hud_appearance` — the persisted Shorts HUD appearance
     preference (BRAIN / LIVE_COUNTER / SHORTSCAP), default 'BRAIN'. This is
     the spec-mandated HUD preference field on the existing Shorts settings
     table (no new appearance table).

No unrelated tables are touched; no data is dropped.
"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision: str = 'fd8a365d772d'
down_revision: Union[str, Sequence[str], None] = '657ba9f4d4f8'
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    """Upgrade schema — add the Shorts Control domain tables/columns."""
    # HUD appearance preference on the existing Shorts settings row.
    op.add_column(
        'shorts_settings',
        sa.Column('hud_appearance', sa.String(length=50), server_default='BRAIN', nullable=False),
    )

    # The durable 24-hour limit cycle table.
    op.create_table(
        'shorts_limit_cycles',
        sa.Column('id', sa.BigInteger(), autoincrement=True, nullable=False),
        sa.Column('user_id', sa.BigInteger(), nullable=False),
        sa.Column('device_id', sa.BigInteger(), nullable=True),
        sa.Column('limit_count', sa.Integer(), nullable=False),
        sa.Column('current_count', sa.Integer(), nullable=False),
        sa.Column('cycle_started_at', sa.DateTime(), nullable=False),
        sa.Column('cycle_expires_at', sa.DateTime(), nullable=False),
        sa.Column('status', sa.String(length=20), nullable=False),
        sa.Column('warning_triggered', sa.Boolean(), nullable=False),
        sa.Column('limit_reached', sa.Boolean(), nullable=False),
        sa.Column('is_active', sa.Boolean(), nullable=True),
        sa.Column('created_at', sa.DateTime(), nullable=False, server_default=sa.func.now()),
        sa.Column('updated_at', sa.DateTime(), nullable=False, server_default=sa.func.now()),
        sa.ForeignKeyConstraint(['device_id'], ['devices.id'], ondelete='SET NULL'),
        sa.ForeignKeyConstraint(['user_id'], ['users.id'], ondelete='CASCADE'),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('user_id', 'is_active', name='uq_shorts_limit_cycles_user_active'),
    )
    op.create_index('ix_shorts_limit_cycles_user_id', 'shorts_limit_cycles', ['user_id'])


def downgrade() -> None:
    """Downgrade schema."""
    op.drop_index('ix_shorts_limit_cycles_user_id', table_name='shorts_limit_cycles')
    op.drop_table('shorts_limit_cycles')
    op.drop_column('shorts_settings', 'hud_appearance')
