# Expense Splitting UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a user pick real group members (not paste UUIDs), choose a category with an icon, select one or more payers, and split an expense equally / unequally / by percentage / by shares — all four split types the backend already computes and validates.

**Architecture:** One small new read-only backend endpoint (`GET /groups/{group_id}/members`) supplies real names for pickers. All new split-building/validation logic is a pure Kotlin object (`ExpensePayloadBuilder`) with JUnit tests, mirroring the existing `BalanceCalculator` pattern. The ViewModel and Compose UI are rewritten on top of that pure logic — no new backend split math.

**Tech Stack:** FastAPI + SQLAlchemy (backend, unchanged patterns), Kotlin + Jetpack Compose + Hilt + Retrofit (Android, unchanged patterns), JUnit4 for pure-Kotlin unit tests.

**Spec:** `docs/superpowers/specs/2026-09-22-expense-split-ui-design.md`

## Global Constraints

- All monetary values are `Long`/`int` minor units (paise) — never `Double`/`float` for money, matching the existing codebase rule (see `balance_engine.py` docstring and `BalanceCalculator` kdoc).
- Split-type strings sent to the backend must be exactly `"equal"`, `"exact"`, `"percentage"`, `"shares"` (matches `SplitType` enum in `backend/app/balance_engine.py`).
- No backend split-math changes — `backend/app/balance_engine.py` is untouched.
- Category keys: `food`, `drinks`, `bills`, `groceries`, `fuel`, `travel`, `rent`, `entertainment`, `shopping`, `utilities`, `other` (supersedes the old `EXPENSE_CATEGORIES` list).
- New Android pure-logic code goes under `app/splitmate/domain/expense/` (parallel to the existing `app/splitmate/domain/balance/` package).

---

### Task 1: Backend — list group members endpoint

**Files:**
- Modify: `backend/app/schemas.py` (add `GroupMemberOut`)
- Modify: `backend/app/routers/groups.py` (add `GET /{group_id}/members`)

**Interfaces:**
- Produces: `GET /groups/{group_id}/members` → `list[GroupMemberOut]`, each
  `{user_id: UUID, name: str, email: str | null, mobile: str | null}`,
  ordered by name. 403 if caller isn't an active member of the group
  (reuses `require_group_member`, same as every other group route).

- [ ] **Step 1: Add `GroupMemberOut` schema**

In `backend/app/schemas.py`, add right after `GroupOut`:

```python
class GroupMemberOut(BaseModel):
    user_id: uuid.UUID
    name: str
    email: Optional[EmailStr] = None
    mobile: Optional[str] = None

    class Config:
        from_attributes = True
```

- [ ] **Step 2: Add the route**

In `backend/app/routers/groups.py`, add `GroupMemberOut` to the `from app.schemas import ...` line, and add this route right after `get_group`:

```python
@router.get("/{group_id}/members", response_model=list[GroupMemberOut])
def list_members(group_id: uuid.UUID, db: Session = Depends(get_db), user: User = Depends(get_current_user)):
    require_group_member(group_id, db, user)
    rows = (
        db.query(User)
        .join(GroupMember, GroupMember.user_id == User.id)
        .filter(GroupMember.group_id == group_id, GroupMember.left_at.is_(None))
        .order_by(User.name)
        .all()
    )
    return [GroupMemberOut(user_id=u.id, name=u.name, email=u.email, mobile=u.mobile) for u in rows]
```

- [ ] **Step 3: Rebuild and restart the local backend**

```bash
cd /Users/rajat/splitmate && docker compose build backend && docker compose up -d backend
```

- [ ] **Step 4: Verify manually with curl**

The project has no HTTP-level pytest harness yet (only pure-function unit
tests exist in `backend/tests/`, see `test_balance_engine.py`) — adding one
is out of scope for this plan, so verify this endpoint the same way every
other endpoint in this project has been verified this session: a live curl
call against the running local stack.

```bash
# Register a user and a group, then list members, e.g.:
TOKEN=$(curl -s -X POST http://localhost:8000/auth/register -H "Content-Type: application/json" \
  -d '{"name":"Plan Test User","email":"plan-test@example.com","password":"testpass123"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

GROUP_ID=$(curl -s -X POST http://localhost:8000/groups -H "Content-Type: application/json" -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Plan Test Group","currency":"INR"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['id'])")

curl -s http://localhost:8000/groups/$GROUP_ID/members -H "Authorization: Bearer $TOKEN"
```

Expected: a JSON array with one object — `user_id`, `name: "Plan Test
User"`, `email: "plan-test@example.com"`, `mobile: null`.

- [ ] **Step 5: Commit**

```bash
cd /Users/rajat/splitmate
git add backend/app/schemas.py backend/app/routers/groups.py
git commit -m "backend: add GET /groups/{id}/members endpoint"
```

---

### Task 2: Android — data layer for group members

**Files:**
- Modify: `android/app/src/main/java/app/splitmate/data/remote/dto/Dtos.kt` (add `GroupMemberDto`)
- Modify: `android/app/src/main/java/app/splitmate/data/remote/ApiService.kt` (add `getGroupMembers`)
- Modify: `android/app/src/main/java/app/splitmate/data/repository/GroupRepository.kt` (add `getMembers`)

**Interfaces:**
- Consumes: nothing new (uses the existing `ApiService`/Retrofit setup).
- Produces: `GroupRepository.getMembers(groupId: String): List<GroupMemberDto>`,
  where `GroupMemberDto(user_id: String, name: String, email: String?, mobile: String?)`.
  Task 5 (ViewModel) depends on this exact signature.

- [ ] **Step 1: Add the DTO**

In `Dtos.kt`, add near `UserDto`:

```kotlin
data class GroupMemberDto(
    val user_id: String,
    val name: String,
    val email: String?,
    val mobile: String?,
)
```

- [ ] **Step 2: Add the Retrofit call**

In `ApiService.kt`, add after `getGroup`:

```kotlin
    @GET("groups/{id}/members")
    suspend fun getGroupMembers(@Path("id") id: String): List<GroupMemberDto>
```

- [ ] **Step 3: Add the repository method**

In `GroupRepository.kt`, add import `app.splitmate.data.remote.dto.GroupMemberDto` and this method:

```kotlin
    suspend fun getMembers(groupId: String): List<GroupMemberDto> = api.getGroupMembers(groupId)
```

- [ ] **Step 4: Verify it compiles**

```bash
cd /Users/rajat/splitmate/android && ./gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
cd /Users/rajat/splitmate
git add android/app/src/main/java/app/splitmate/data/remote/dto/Dtos.kt \
        android/app/src/main/java/app/splitmate/data/remote/ApiService.kt \
        android/app/src/main/java/app/splitmate/data/repository/GroupRepository.kt
git commit -m "android: add GroupRepository.getMembers"
```

---

### Task 3: Android — category picker

**Files:**
- Create: `android/app/src/main/java/app/splitmate/ui/screens/expense/CategoryPicker.kt`

**Interfaces:**
- Produces: `val EXPENSE_CATEGORIES: List<ExpenseCategoryOption>` (replaces
  the old `EXPENSE_CATEGORIES: List<String>` in `AddExpenseViewModel.kt` —
  Task 5 removes the old one), and
  `@Composable fun CategoryPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier)`.
  Task 6 (screen) consumes both.

- [ ] **Step 1: Create the file**

```kotlin
package app.splitmate.ui.screens.expense

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ExpenseCategoryOption(val key: String, val label: String, val emoji: String)

val EXPENSE_CATEGORIES = listOf(
    ExpenseCategoryOption("food", "Food", "🍔"),
    ExpenseCategoryOption("drinks", "Drinks", "🍹"),
    ExpenseCategoryOption("bills", "Bills", "🧾"),
    ExpenseCategoryOption("groceries", "Groceries", "🛒"),
    ExpenseCategoryOption("fuel", "Fuel", "⛽"),
    ExpenseCategoryOption("travel", "Travel", "✈️"),
    ExpenseCategoryOption("rent", "Rent", "🏠"),
    ExpenseCategoryOption("entertainment", "Entertainment", "🎬"),
    ExpenseCategoryOption("shopping", "Shopping", "🛍️"),
    ExpenseCategoryOption("utilities", "Utilities", "💡"),
    ExpenseCategoryOption("other", "Other", "📎"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPicker(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier) {
        items(EXPENSE_CATEGORIES, key = { it.key }) { option ->
            FilterChip(
                selected = option.key == selected,
                onClick = { onSelect(option.key) },
                label = { Text("${option.emoji} ${option.label}") },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /Users/rajat/splitmate/android && ./gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
cd /Users/rajat/splitmate
git add android/app/src/main/java/app/splitmate/ui/screens/expense/CategoryPicker.kt
git commit -m "android: add icon-based category picker"
```

---

### Task 4: Android — pure split-payload logic (TDD)

**Files:**
- Create: `android/app/src/main/java/app/splitmate/domain/expense/ExpensePayloadBuilder.kt`
- Test: `android/app/src/test/java/app/splitmate/domain/expense/ExpensePayloadBuilderTest.kt`

**Interfaces:**
- Consumes: `app.splitmate.domain.balance.BalanceCalculator` (`equalSplit`,
  `exactSplit`, `percentageSplit`, `sharesSplit`) and
  `app.splitmate.domain.balance.BalanceEngineException` — both already
  exist, unchanged.
- Produces: `SplitMode` enum, `PayerInput`, `ParticipantInput`,
  `ExpensePayload` data classes, and
  `ExpensePayloadBuilder.build(amountMinor, splitMode, includedParticipantIds, exactAmounts, percentagesBp, shares, payments): ExpensePayload`
  (throws `BalanceEngineException` on any invalid input). Task 5
  (ViewModel) depends on these exact names and the exception type.

- [ ] **Step 1: Write the failing tests**

Create `android/app/src/test/java/app/splitmate/domain/expense/ExpensePayloadBuilderTest.kt`:

```kotlin
package app.splitmate.domain.expense

import app.splitmate.domain.balance.BalanceEngineException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ExpensePayloadBuilderTest {

    private val singlePayer = listOf(PayerInput("rj", 100_00))

    @Test
    fun `equal split builds participant_ids and no participants list`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.EQUAL,
            includedParticipantIds = listOf("rj", "amit"),
            payments = singlePayer,
        )
        assertEquals("equal", payload.splitType)
        assertEquals(listOf("rj", "amit"), payload.participantIds)
        assertEquals(emptyList<ParticipantInput>(), payload.participants)
        assertEquals(singlePayer, payload.payments)
    }

    @Test
    fun `exact split builds participants with amount_minor`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.EXACT,
            includedParticipantIds = listOf("rj", "amit"),
            exactAmounts = mapOf("rj" to 60_00L, "amit" to 40_00L),
            payments = singlePayer,
        )
        assertEquals("exact", payload.splitType)
        assertEquals(emptyList<String>(), payload.participantIds)
        assertEquals(
            setOf(ParticipantInput("rj", amountMinor = 60_00), ParticipantInput("amit", amountMinor = 40_00)),
            payload.participants.toSet(),
        )
    }

    @Test
    fun `exact split rejects amounts that do not sum to total`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EXACT,
                includedParticipantIds = listOf("rj", "amit"),
                exactAmounts = mapOf("rj" to 60_00L, "amit" to 30_00L),
                payments = singlePayer,
            )
        }
    }

    @Test
    fun `exact split rejects a missing amount for an included participant`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EXACT,
                includedParticipantIds = listOf("rj", "amit"),
                exactAmounts = mapOf("rj" to 100_00L),
                payments = singlePayer,
            )
        }
    }

    @Test
    fun `percentage split builds participants with percentage_bp`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.PERCENTAGE,
            includedParticipantIds = listOf("rj", "amit"),
            percentagesBp = mapOf("rj" to 6000L, "amit" to 4000L),
            payments = singlePayer,
        )
        assertEquals(
            setOf(ParticipantInput("rj", percentageBp = 6000), ParticipantInput("amit", percentageBp = 4000)),
            payload.participants.toSet(),
        )
    }

    @Test
    fun `percentage split rejects percentages that do not sum to 100`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.PERCENTAGE,
                includedParticipantIds = listOf("rj", "amit"),
                percentagesBp = mapOf("rj" to 6000L, "amit" to 3000L),
                payments = singlePayer,
            )
        }
    }

    @Test
    fun `shares split builds participants with shares`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.SHARES,
            includedParticipantIds = listOf("rj", "amit"),
            shares = mapOf("rj" to 2L, "amit" to 1L),
            payments = singlePayer,
        )
        assertEquals(
            setOf(ParticipantInput("rj", shares = 2), ParticipantInput("amit", shares = 1)),
            payload.participants.toSet(),
        )
    }

    @Test
    fun `rejects when no participants are included`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EQUAL,
                includedParticipantIds = emptyList(),
                payments = singlePayer,
            )
        }
    }

    @Test
    fun `rejects when no payers are given`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EQUAL,
                includedParticipantIds = listOf("rj"),
                payments = emptyList(),
            )
        }
    }

    @Test
    fun `rejects when multiple payer amounts do not sum to total`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 100_00,
                splitMode = SplitMode.EQUAL,
                includedParticipantIds = listOf("rj", "amit"),
                payments = listOf(PayerInput("rj", 40_00), PayerInput("amit", 40_00)),
            )
        }
    }

    @Test
    fun `accepts multiple payers whose amounts sum exactly to total`() {
        val payload = ExpensePayloadBuilder.build(
            amountMinor = 100_00,
            splitMode = SplitMode.EQUAL,
            includedParticipantIds = listOf("rj", "amit"),
            payments = listOf(PayerInput("rj", 60_00), PayerInput("amit", 40_00)),
        )
        assertEquals(100_00L, payload.payments.sumOf { it.amountMinor })
    }

    @Test
    fun `rejects a non-positive amount`() {
        assertThrows(BalanceEngineException::class.java) {
            ExpensePayloadBuilder.build(
                amountMinor = 0,
                splitMode = SplitMode.EQUAL,
                includedParticipantIds = listOf("rj"),
                payments = singlePayer,
            )
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd /Users/rajat/splitmate/android && ./gradlew testDebugUnitTest --tests "app.splitmate.domain.expense.ExpensePayloadBuilderTest"
```

Expected: FAIL — `ExpensePayloadBuilder` (and the other new types) don't exist yet.

- [ ] **Step 3: Implement `ExpensePayloadBuilder`**

Create `android/app/src/main/java/app/splitmate/domain/expense/ExpensePayloadBuilder.kt`:

```kotlin
package app.splitmate.domain.expense

import app.splitmate.domain.balance.BalanceCalculator
import app.splitmate.domain.balance.BalanceEngineException

enum class SplitMode { EQUAL, EXACT, PERCENTAGE, SHARES }

data class PayerInput(val userId: String, val amountMinor: Long)

data class ParticipantInput(
    val userId: String,
    val amountMinor: Long? = null,
    val percentageBp: Long? = null,
    val shares: Long? = null,
)

data class ExpensePayload(
    val splitType: String,
    val participantIds: List<String>,
    val participants: List<ParticipantInput>,
    val payments: List<PayerInput>,
)

/**
 * Builds and validates the payload for a new expense, client-side, before it's sent
 * to the backend. Mirrors backend/app/balance_engine.py's validation rules exactly
 * (via BalanceCalculator) so the user sees an error instantly instead of after a
 * round trip. The backend re-validates everything and remains the source of truth.
 */
object ExpensePayloadBuilder {

    fun build(
        amountMinor: Long,
        splitMode: SplitMode,
        includedParticipantIds: List<String>,
        exactAmounts: Map<String, Long> = emptyMap(),
        percentagesBp: Map<String, Long> = emptyMap(),
        shares: Map<String, Long> = emptyMap(),
        payments: List<PayerInput>,
    ): ExpensePayload {
        if (amountMinor <= 0) throw BalanceEngineException("Enter a valid amount")
        if (includedParticipantIds.isEmpty()) throw BalanceEngineException("Choose at least one participant")
        if (payments.isEmpty()) throw BalanceEngineException("Choose who paid")

        val paidTotal = payments.sumOf { it.amountMinor }
        if (paidTotal != amountMinor) {
            throw BalanceEngineException("Payments ($paidTotal) must sum to the expense total ($amountMinor)")
        }

        return when (splitMode) {
            SplitMode.EQUAL -> {
                BalanceCalculator.equalSplit(amountMinor, includedParticipantIds)
                ExpensePayload("equal", includedParticipantIds, emptyList(), payments)
            }
            SplitMode.EXACT -> {
                val map = includedParticipantIds.associateWith {
                    exactAmounts[it] ?: throw BalanceEngineException("Enter an amount for every participant")
                }
                BalanceCalculator.exactSplit(amountMinor, map)
                ExpensePayload("exact", emptyList(), map.map { ParticipantInput(it.key, amountMinor = it.value) }, payments)
            }
            SplitMode.PERCENTAGE -> {
                val map = includedParticipantIds.associateWith {
                    percentagesBp[it] ?: throw BalanceEngineException("Enter a percentage for every participant")
                }
                BalanceCalculator.percentageSplit(amountMinor, map)
                ExpensePayload("percentage", emptyList(), map.map { ParticipantInput(it.key, percentageBp = it.value) }, payments)
            }
            SplitMode.SHARES -> {
                val map = includedParticipantIds.associateWith {
                    shares[it] ?: throw BalanceEngineException("Enter shares for every participant")
                }
                BalanceCalculator.sharesSplit(amountMinor, map)
                ExpensePayload("shares", emptyList(), map.map { ParticipantInput(it.key, shares = it.value) }, payments)
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd /Users/rajat/splitmate/android && ./gradlew testDebugUnitTest --tests "app.splitmate.domain.expense.ExpensePayloadBuilderTest"
```

Expected: PASS, all 12 tests.

- [ ] **Step 5: Commit**

```bash
cd /Users/rajat/splitmate
git add android/app/src/main/java/app/splitmate/domain/expense/ExpensePayloadBuilder.kt \
        android/app/src/test/java/app/splitmate/domain/expense/ExpensePayloadBuilderTest.kt
git commit -m "android: add ExpensePayloadBuilder with unit tests"
```

---

### Task 5: Android — rewrite AddExpenseViewModel

**Files:**
- Modify: `android/app/src/main/java/app/splitmate/ui/screens/expense/AddExpenseViewModel.kt` (full rewrite)

**Interfaces:**
- Consumes: `GroupRepository.getMembers` (Task 2),
  `ExpensePayloadBuilder.build` + its types (Task 4),
  `ExpenseRepository.createExpense` (existing, unchanged).
- Produces: `AddExpenseUiState` (new shape — Task 6 binds the screen to
  this), and these `AddExpenseViewModel` methods Task 6 calls directly:
  `setDescription(String)`, `setAmountText(String)`, `setCategory(String)`,
  `toggleMultiPayer(Boolean)`, `setSinglePayer(String)`,
  `togglePayerSelected(String)`, `setPayerAmountText(String, String)`,
  `setSplitMode(SplitMode)`, `toggleParticipant(String)`,
  `setExactAmountText(String, String)`, `setPercentageText(String, String)`,
  `setShares(String, Int)`, `submit()`.

- [ ] **Step 1: Replace the file**

Replace the entire contents of `AddExpenseViewModel.kt` with:

```kotlin
package app.splitmate.ui.screens.expense

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.remote.dto.ExpenseCreateRequest
import app.splitmate.data.remote.dto.ExpenseParticipantIn
import app.splitmate.data.remote.dto.ExpensePaymentIn
import app.splitmate.data.remote.dto.GroupMemberDto
import app.splitmate.data.repository.ExpenseRepository
import app.splitmate.data.repository.GroupRepository
import app.splitmate.domain.balance.BalanceEngineException
import app.splitmate.domain.expense.ExpensePayloadBuilder
import app.splitmate.domain.expense.PayerInput
import app.splitmate.domain.expense.SplitMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MemberOption(val id: String, val name: String)

data class AddExpenseUiState(
    val description: String = "",
    val amountText: String = "",
    val category: String = "other",
    val members: List<MemberOption> = emptyList(),
    val membersLoading: Boolean = true,
    // Payer selection
    val multiPayer: Boolean = false,
    val singlePayerId: String? = null,
    val selectedPayerIds: Set<String> = emptySet(),
    val payerAmountText: Map<String, String> = emptyMap(),
    // Split selection
    val splitMode: SplitMode = SplitMode.EQUAL,
    val includedParticipantIds: Set<String> = emptySet(),
    val exactAmountText: Map<String, String> = emptyMap(),
    val percentageText: Map<String, String> = emptyMap(),
    val shareCounts: Map<String, Int> = emptyMap(),
    // Derived / status
    val validationError: String? = null,
    val error: String? = null,
    val savedOffline: Boolean = false,
    val success: Boolean = false,
)

@HiltViewModel
class AddExpenseViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val groupRepository: GroupRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])
    private val _uiState = MutableStateFlow(AddExpenseUiState())
    val uiState: StateFlow<AddExpenseUiState> = _uiState

    init {
        viewModelScope.launch {
            try {
                val members = groupRepository.getMembers(groupId)
                mutate {
                    it.copy(
                        members = members.map { m -> MemberOption(m.user_id, m.name) },
                        membersLoading = false,
                        includedParticipantIds = members.map { m -> m.user_id }.toSet(),
                        shareCounts = members.associate { m -> m.user_id to 1 },
                    )
                }
            } catch (e: Exception) {
                mutate { it.copy(membersLoading = false, error = "Could not load group members") }
            }
        }
    }

    private fun mutate(transform: (AddExpenseUiState) -> AddExpenseUiState) {
        val next = transform(_uiState.value)
        _uiState.value = next.copy(validationError = computeValidationError(next))
    }

    fun setDescription(value: String) = mutate { it.copy(description = value) }
    fun setAmountText(value: String) = mutate { it.copy(amountText = value) }
    fun setCategory(value: String) = mutate { it.copy(category = value) }

    fun toggleMultiPayer(enabled: Boolean) = mutate {
        it.copy(multiPayer = enabled, singlePayerId = null, selectedPayerIds = emptySet(), payerAmountText = emptyMap())
    }

    fun setSinglePayer(memberId: String) = mutate { it.copy(singlePayerId = memberId) }

    fun togglePayerSelected(memberId: String) = mutate {
        val next = if (memberId in it.selectedPayerIds) it.selectedPayerIds - memberId else it.selectedPayerIds + memberId
        it.copy(selectedPayerIds = next)
    }

    fun setPayerAmountText(memberId: String, text: String) = mutate {
        it.copy(payerAmountText = it.payerAmountText + (memberId to text))
    }

    fun setSplitMode(mode: SplitMode) = mutate { it.copy(splitMode = mode) }

    fun toggleParticipant(memberId: String) = mutate {
        val next = if (memberId in it.includedParticipantIds) it.includedParticipantIds - memberId else it.includedParticipantIds + memberId
        it.copy(includedParticipantIds = next)
    }

    fun setExactAmountText(memberId: String, text: String) = mutate {
        it.copy(exactAmountText = it.exactAmountText + (memberId to text))
    }

    fun setPercentageText(memberId: String, text: String) = mutate {
        it.copy(percentageText = it.percentageText + (memberId to text))
    }

    fun setShares(memberId: String, shares: Int) = mutate {
        it.copy(shareCounts = it.shareCounts + (memberId to shares.coerceAtLeast(1)))
    }

    private fun parseAmountMinor(text: String): Long? = text.toDoubleOrNull()?.let { (it * 100).toLong() }

    private fun buildPayments(s: AddExpenseUiState): List<PayerInput> {
        return if (s.multiPayer) {
            s.selectedPayerIds.mapNotNull { id ->
                parseAmountMinor(s.payerAmountText[id].orEmpty())?.let { PayerInput(id, it) }
            }
        } else {
            s.singlePayerId?.let { id ->
                parseAmountMinor(s.amountText)?.let { listOf(PayerInput(id, it)) }
            } ?: emptyList()
        }
    }

    /** Returns null if the current state would build a valid payload, else the error message. */
    private fun computeValidationError(s: AddExpenseUiState): String? {
        val amountMinor = parseAmountMinor(s.amountText) ?: return "Enter a valid amount"
        return try {
            ExpensePayloadBuilder.build(
                amountMinor = amountMinor,
                splitMode = s.splitMode,
                includedParticipantIds = s.includedParticipantIds.toList(),
                exactAmounts = s.exactAmountText.mapNotNull { (k, v) -> parseAmountMinor(v)?.let { k to it } }.toMap(),
                percentagesBp = s.percentageText.mapNotNull { (k, v) ->
                    v.toDoubleOrNull()?.let { k to (it * 100).toLong() }
                }.toMap(),
                shares = s.shareCounts.mapValues { it.value.toLong() },
                payments = buildPayments(s),
            )
            null
        } catch (e: BalanceEngineException) {
            e.message
        }
    }

    fun submit() {
        val s = _uiState.value
        if (s.description.isBlank()) {
            mutate { it.copy(error = "Enter a description") }
            return
        }
        val amountMinor = parseAmountMinor(s.amountText)
        if (amountMinor == null) {
            mutate { it.copy(error = "Enter a valid amount") }
            return
        }
        val payload = try {
            ExpensePayloadBuilder.build(
                amountMinor = amountMinor,
                splitMode = s.splitMode,
                includedParticipantIds = s.includedParticipantIds.toList(),
                exactAmounts = s.exactAmountText.mapNotNull { (k, v) -> parseAmountMinor(v)?.let { k to it } }.toMap(),
                percentagesBp = s.percentageText.mapNotNull { (k, v) ->
                    v.toDoubleOrNull()?.let { k to (it * 100).toLong() }
                }.toMap(),
                shares = s.shareCounts.mapValues { it.value.toLong() },
                payments = buildPayments(s),
            )
        } catch (e: BalanceEngineException) {
            mutate { it.copy(error = e.message) }
            return
        }

        viewModelScope.launch {
            val request = ExpenseCreateRequest(
                description = s.description,
                amount_minor = amountMinor,
                currency = "INR",
                category = s.category,
                split_type = payload.splitType,
                participant_ids = payload.participantIds,
                participants = payload.participants.map {
                    ExpenseParticipantIn(it.userId, it.amountMinor, it.percentageBp?.toInt(), it.shares?.toInt())
                },
                payments = payload.payments.map { ExpensePaymentIn(it.userId, it.amountMinor) },
            )
            val synced = expenseRepository.createExpense(groupId, request)
            mutate { it.copy(success = true, savedOffline = !synced, error = null) }
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

```bash
cd /Users/rajat/splitmate/android && ./gradlew compileDebugKotlin
```

Expected: FAILS at this point — `AddExpenseScreen.kt` (Task 6) still
references the old `AddExpenseUiState` shape (`paidBy`, `participantIds`,
etc.) and `EXPENSE_CATEGORIES: List<String>`. That's expected; Task 6 fixes
the screen to match. Confirm the *only* errors are in `AddExpenseScreen.kt`,
not in `AddExpenseViewModel.kt` itself.

- [ ] **Step 3: Commit**

```bash
cd /Users/rajat/splitmate
git add android/app/src/main/java/app/splitmate/ui/screens/expense/AddExpenseViewModel.kt
git commit -m "android: rewrite AddExpenseViewModel for full split support"
```

---

### Task 6: Android — rewrite AddExpenseScreen UI

**Files:**
- Create: `android/app/src/main/java/app/splitmate/ui/screens/expense/PayerSection.kt`
- Create: `android/app/src/main/java/app/splitmate/ui/screens/expense/SplitSection.kt`
- Modify: `android/app/src/main/java/app/splitmate/ui/screens/expense/AddExpenseScreen.kt` (full rewrite)

**Interfaces:**
- Consumes: `AddExpenseUiState`, `MemberOption`, `SplitMode`, and every
  `AddExpenseViewModel` method listed in Task 5's Interfaces section, plus
  `CategoryPicker`/`EXPENSE_CATEGORIES` from Task 3.
- Produces: the screen's own composables — nothing else depends on these.

- [ ] **Step 1: Create `PayerSection.kt`**

```kotlin
package app.splitmate.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayerSection(
    state: AddExpenseUiState,
    onToggleMultiPayer: (Boolean) -> Unit,
    onSetSinglePayer: (String) -> Unit,
    onTogglePayerSelected: (String) -> Unit,
    onSetPayerAmountText: (String, String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Split the payment", modifier = Modifier.weight(1f))
            Switch(checked = state.multiPayer, onCheckedChange = onToggleMultiPayer)
        }
        Spacer(Modifier.height(8.dp))

        if (!state.multiPayer) {
            Text("Paid by", style = MaterialTheme.typography.labelLarge)
            FlowRowChips(state.members, selected = setOfNotNull(state.singlePayerId)) { onSetSinglePayer(it) }
        } else {
            Text("Who paid, and how much", style = MaterialTheme.typography.labelLarge)
            FlowRowChips(state.members, selected = state.selectedPayerIds) { onTogglePayerSelected(it) }
            Spacer(Modifier.height(8.dp))
            state.members.filter { it.id in state.selectedPayerIds }.forEach { member ->
                OutlinedTextField(
                    value = state.payerAmountText[member.id].orEmpty(),
                    onValueChange = { onSetPayerAmountText(member.id, it) },
                    label = { Text("${member.name}'s amount") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true,
                )
            }
            val allocated = state.selectedPayerIds.sumOf { state.payerAmountText[it]?.toDoubleOrNull() ?: 0.0 }
            val target = state.amountText.toDoubleOrNull() ?: 0.0
            Text("Allocated: $allocated of $target", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlowRowChips(members: List<MemberOption>, selected: Set<String>, onClick: (String) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth()) {
        items(members, key = { it.id }) { member ->
            FilterChip(
                selected = member.id in selected,
                onClick = { onClick(member.id) },
                label = { Text(member.name) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Create `SplitSection.kt`**

```kotlin
package app.splitmate.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.splitmate.domain.expense.SplitMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitSection(
    state: AddExpenseUiState,
    onSetSplitMode: (SplitMode) -> Unit,
    onToggleParticipant: (String) -> Unit,
    onSetExactAmountText: (String, String) -> Unit,
    onSetPercentageText: (String, String) -> Unit,
    onSetShares: (String, Int) -> Unit,
) {
    Column {
        Text("Split between", style = MaterialTheme.typography.labelLarge)

        val modes = SplitMode.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.splitMode == mode,
                    onClick = { onSetSplitMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }
        Spacer(Modifier.height(8.dp))

        FlowRowChips(state.members, selected = state.includedParticipantIds) { onToggleParticipant(it) }
        Spacer(Modifier.height(8.dp))

        val included = state.members.filter { it.id in state.includedParticipantIds }
        when (state.splitMode) {
            SplitMode.EQUAL -> { /* no extra input; preview shown via validationError being null */ }
            SplitMode.EXACT -> included.forEach { member ->
                OutlinedTextField(
                    value = state.exactAmountText[member.id].orEmpty(),
                    onValueChange = { onSetExactAmountText(member.id, it) },
                    label = { Text("${member.name}'s amount") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true,
                )
            }
            SplitMode.PERCENTAGE -> included.forEach { member ->
                OutlinedTextField(
                    value = state.percentageText[member.id].orEmpty(),
                    onValueChange = { onSetPercentageText(member.id, it) },
                    label = { Text("${member.name}'s %") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    singleLine = true,
                )
            }
            SplitMode.SHARES -> included.forEach { member ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(member.name, modifier = Modifier.weight(1f))
                    val shares = state.shareCounts[member.id] ?: 1
                    IconButton(onClick = { onSetShares(member.id, shares - 1) }) { Text("-") }
                    Text("$shares")
                    IconButton(onClick = { onSetShares(member.id, shares + 1) }) { Text("+") }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Replace `AddExpenseScreen.kt`**

```kotlin
package app.splitmate.ui.screens.expense

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AddExpenseScreen(onDone: () -> Unit, viewModel: AddExpenseViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.success) { if (state.success) onDone() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Text("Add expense", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = state.description,
            onValueChange = viewModel::setDescription,
            label = { Text("Description (e.g. Dinner)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.amountText,
            onValueChange = viewModel::setAmountText,
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        CategoryPicker(selected = state.category, onSelect = viewModel::setCategory, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))

        if (state.membersLoading) {
            CircularProgressIndicator()
        } else {
            PayerSection(
                state = state,
                onToggleMultiPayer = viewModel::toggleMultiPayer,
                onSetSinglePayer = viewModel::setSinglePayer,
                onTogglePayerSelected = viewModel::togglePayerSelected,
                onSetPayerAmountText = viewModel::setPayerAmountText,
            )
            Spacer(Modifier.height(16.dp))
            SplitSection(
                state = state,
                onSetSplitMode = viewModel::setSplitMode,
                onToggleParticipant = viewModel::toggleParticipant,
                onSetExactAmountText = viewModel::setExactAmountText,
                onSetPercentageText = viewModel::setPercentageText,
                onSetShares = viewModel::setShares,
            )
        }

        if (state.validationError != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.validationError!!, color = MaterialTheme.colorScheme.error)
        }
        if (state.error != null) {
            Spacer(Modifier.height(8.dp))
            Text(state.error!!, color = MaterialTheme.colorScheme.error)
        }
        if (state.savedOffline) {
            Spacer(Modifier.height(8.dp))
            Text("Saved offline - will sync automatically when you're back online.")
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::submit,
            enabled = state.validationError == null && state.description.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save expense") }
    }
}
```

- [ ] **Step 4: Verify it compiles**

```bash
cd /Users/rajat/splitmate/android && ./gradlew compileDebugKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run the full unit test suite**

```bash
cd /Users/rajat/splitmate/android && ./gradlew testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL` (existing `BalanceCalculatorTest` plus the
new `ExpensePayloadBuilderTest` from Task 4 all pass).

- [ ] **Step 6: Commit**

```bash
cd /Users/rajat/splitmate
git add android/app/src/main/java/app/splitmate/ui/screens/expense/
git commit -m "android: rewrite AddExpenseScreen with full split UI"
```

---

### Task 7: Deploy, install, and verify on-device

**Files:** none (deployment + manual verification only)

**Interfaces:** none — this task consumes the finished app from Tasks 1–6
and confirms the whole stack works end-to-end.

- [ ] **Step 1: Redeploy the backend to Railway**

```bash
cd /Users/rajat/splitmate && railway up ./backend --path-as-root --service backend -y --ci
```

Expected: `Deploy complete`.

- [ ] **Step 2: Verify the new endpoint on the live backend**

```bash
curl -s -w "\nHTTP_STATUS:%{http_code}\n" https://backend-production-cae14.up.railway.app/health
```

Expected: `{"status":"ok",...}` / `HTTP_STATUS:200`. (The members endpoint
itself needs an authed user + group, which is exercised on-device in
Step 4 below.)

- [ ] **Step 3: Rebuild and install the debug APK**

```bash
cd /Users/rajat/splitmate/android && ./gradlew assembleDebug
adb devices -l
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Expected: `BUILD SUCCESSFUL`, phone listed as `device` (not
`unauthorized`), `Success` from the install.

- [ ] **Step 4: Manual on-device verification**

Using the phone, for one group with at least 3 members:

1. Add an expense with **Equal** split, single payer, category "Food" —
   confirm it saves and balances update correctly on the group screen.
2. Add an expense with **Unequal (Exact)** split — confirm the Save
   button stays disabled until entered amounts sum exactly to the total,
   and that it saves correctly once they do.
3. Add an expense with **Percentage** split — confirm Save is disabled
   until percentages sum to 100%.
4. Add an expense with **Shares** split — confirm the +/- steppers work
   and the expense saves.
5. Add an expense with **"Split the payment" ON** (multiple payers) —
   confirm Save is disabled until payer amounts sum to the total, and
   that the resulting balances correctly reflect multiple payers.
6. Confirm every category chip (Food/Drinks/Bills/Groceries/Fuel/etc.)
   is selectable and shows its emoji.

If any step fails, fix the specific bug in the relevant Task's files
(don't patch around it here) and repeat Steps 3–4.

- [ ] **Step 5: Push and cut a new release**

```bash
cd /Users/rajat/splitmate
git push origin main
gh release create v0.2.0-debug \
  android/app/build/outputs/apk/debug/app-debug.apk \
  --repo X-rajat/splitmate \
  --title "SplitMate v0.2.0 (debug)" \
  --notes "Full expense-splitting UI: equal/unequal/percentage/shares splits, multiple payers, real member picker, icon-based categories."
```

Expected: a new `v0.2.0-debug` release with `app-debug.apk` attached.
