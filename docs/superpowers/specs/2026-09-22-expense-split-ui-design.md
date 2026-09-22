# Expense splitting UI — design spec

Date: 2026-09-22
Status: Approved (sub-project 1 of 3 — see "Related work" at the end)

## Context

The FastAPI backend already fully implements flexible expense splitting:
`equal` / `exact` / `percentage` / `shares` split types, multiple payers per
expense (`payments: list[ExpensePaymentIn]`), and free-text expense
categories. All validation (sums must match, percentages must total 100%,
shares must be positive, etc.) lives in `app/balance_engine.py` and is
enforced server-side.

None of this is exposed in the Android app. `AddExpenseScreen.kt` today:
- Only supports equal split.
- Has the user paste in raw UUIDs for "paid by" (single payer only) and
  "split between" as a comma-separated text field.
- Has a category dropdown (`EXPENSE_CATEGORIES` — 11 plain-text categories,
  no icons).

This spec covers bringing the Android UI up to what the backend already
supports, plus icon-based categories. It does not add any new backend
splitting capability — only a small new read endpoint to list group members
by name (currently there's no way for the client to know a group's member
names, only a `member_count` integer).

## Goals

1. Category picker with icons, replacing the plain-text dropdown.
2. Real member picker (names, not pasted UUIDs) for both payer and
   participant selection, sourced from a new backend endpoint.
3. Support all four split types the backend already accepts: equal,
   unequal (exact amounts), percentage, shares — via a segmented control.
4. Support multiple payers ("someone else paid, someone else is adding"
   splits the payment across payers) via a "Split the payment" toggle.
5. Live client-side validation that mirrors backend rules (sums must
   match) so errors surface before a round trip, not after a 422.

## Non-goals

- No new backend split-type math — `balance_engine.py` is unchanged.
- No general UI/UX redesign of other screens (separate sub-project).
- No group invite / add-by-phone changes (separate sub-project).
- No custom/user-defined categories — the fixed icon set below is enough
  for this pass; extending it later is a small, isolated follow-up.

## Backend changes

### New endpoint: `GET /groups/{group_id}/members`

- Auth: requires group membership (reuse `require_group_member`).
- Returns `list[GroupMemberOut]` where:
  ```python
  class GroupMemberOut(BaseModel):
      user_id: uuid.UUID
      name: str
      email: Optional[str] = None
      mobile: Optional[str] = None
  ```
- Implementation: join `GroupMember` (active, `left_at IS NULL`) → `User`,
  ordered by `User.name`.
- File: `backend/app/routers/groups.py` (new route), `backend/app/schemas.py`
  (new `GroupMemberOut`).
- No migration needed — read-only, no schema change.

## Android changes

### Category picker

- Fixed list mapping category key → (label, emoji/icon):
  `food` 🍔, `drinks` 🍹, `bills` 🧾, `groceries` 🛒, `fuel` ⛽,
  `travel` ✈️, `rent` 🏠, `entertainment` 🎬, `shopping` 🛍️,
  `utilities` 💡, `other` 📎.
  (This supersedes the current `EXPENSE_CATEGORIES` list — `drinks` and
  `fuel` are new, `hotel`/`transport` folded into `travel`.)
- UI: horizontal `LazyRow` of `FilterChip`s with the emoji + label; selected
  chip highlighted. Replaces the `ExposedDropdownMenuBox`.
- New small file: `CategoryPicker.kt` (composable + the category list/icon
  map), so it's reusable if categories show up elsewhere later (e.g. a
  future expense-list filter).

### Member data

- `GroupRepository` gets a `getMembers(groupId): List<GroupMemberDto>`
  calling the new endpoint (no local caching needed — small list, fetched
  fresh each time `AddExpenseScreen` opens).
- New `GroupMemberDto(user_id, name, email, mobile)` in `Dtos.kt`, new
  `ApiService.getGroupMembers(groupId)`.

### Payer selection

- Default mode: single payer, chip-select from the member list (radio
  behaviour — exactly one selected).
- "Split the payment" switch: when on, multi-select payers; each gets an
  amount input. A running total ("₹X of ₹Y allocated") is shown; Save is
  disabled until the payer amounts sum exactly to the expense total —
  mirrors the backend's `paid_total != owed_total` check in
  `compute_expense_net`.
- Maps to `payments: List<ExpensePaymentIn>` — one entry in single-payer
  mode, N entries in split-payment mode.

### Split-type selection ("split between")

`SingleChoiceSegmentedButtonRow` with 4 options: Equal / Unequal / % / Shares
(`split_type` values: `equal` / `exact` / `percentage` / `shares`).

All four start from the same participant multi-select (chip list of group
members, all selected by default when the screen opens).

- **Equal**: no extra input. Preview per-person amount using the existing
  `BalanceCalculator.equalSplit` (already implements the same
  largest-remainder logic as the backend) so the user sees the actual
  split before saving. Maps to `participant_ids`.
- **Unequal (exact)**: one amount field per selected participant. Live
  "remaining to allocate" = total − sum(entered). Save disabled until
  remaining == 0. Maps to `participants: [{user_id, amount_minor}]`.
- **Percentage**: one percentage field per selected participant (accepts
  up to 2 decimal places, stored as basis points ×100). Live sum shown vs
  100.00%; Save disabled until it matches exactly. Maps to
  `participants: [{user_id, percentage_bp}]`.
- **Shares**: one integer stepper per selected participant, default 1
  share each. Live preview of the resulting per-person amount (client-side
  largest-remainder computation, same algorithm as
  `_largest_remainder_distribute`, added as a small pure function next to
  `BalanceCalculator.equalSplit` so all four types can show a live preview
  without a round trip). Maps to `participants: [{user_id, shares}]`.

### ViewModel

`AddExpenseViewModel` is rewritten (not patched — the current one only
knows equal split):
- Loads members on init.
- Holds richer state: `payers: List<PayerEntry>`, `splitType`,
  `participants: List<ParticipantEntry>` (entry shape depends on
  `splitType`: amount / percentage / shares).
- `canSubmit` derived state encodes all the live-validation rules above
  (payer sum matches total, participant allocation matches total/100%,
  at least one participant selected).
- `submit()` builds the same `ExpenseCreateRequest` shape the backend
  already accepts — no backend contract changes beyond the new
  members-list endpoint.

### Files touched

- Backend: `app/routers/groups.py`, `app/schemas.py`.
- Android:
  - `AddExpenseScreen.kt` — rewritten.
  - `AddExpenseViewModel.kt` — rewritten.
  - `CategoryPicker.kt` — new.
  - `Dtos.kt` — add `GroupMemberDto`.
  - `ApiService.kt` — add `getGroupMembers`.
  - `GroupRepository.kt` — add `getMembers`.

## Error handling

- Network/API errors on submit surface the backend's error message
  directly (it already returns clear 422 messages like "Percentages must
  sum to 100.00%").
- Client-side validation (sum checks) is a UX nicety to catch mistakes
  before submit, not a replacement for backend validation — the backend
  remains the source of truth, matching the existing offline-save-then-
  sync behavior (`savedOffline` state) elsewhere in the app.

## Testing

- Backend: a small pytest for `GET /groups/{group_id}/members` (returns
  correct members, 403/404 for non-members) — follow the existing test
  patterns in `backend/tests/`.
- Android: manual verification on-device for each of the 4 split types
  plus single/multi-payer, since the project has no Android UI test
  harness set up yet (out of scope to add one here).

## Related work (not in this spec)

Two more sub-projects were identified in the same conversation, deferred
in favor of this one:
2. Group invites / add member by phone-number lookup.
3. General UI/UX visual redesign across all screens.

Each should get its own brainstorming pass and spec when picked up.
