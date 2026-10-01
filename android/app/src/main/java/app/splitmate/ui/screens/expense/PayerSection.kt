package app.splitmate.ui.screens.expense

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.splitmate.data.remote.dto.GroupMemberDto

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
            Text("Split the payment", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Switch(checked = state.multiPayer, onCheckedChange = onToggleMultiPayer)
        }
        Spacer(Modifier.height(8.dp))

        if (!state.multiPayer) {
            Text("Paid by", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            MemberChipRow(state.members, selected = setOfNotNull(state.singlePayerId)) { onSetSinglePayer(it) }
        } else {
            Text("Who paid, and how much", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            MemberChipRow(state.members, selected = state.selectedPayerIds) { onTogglePayerSelected(it) }
            Spacer(Modifier.height(8.dp))
            state.members.filter { it.user_id in state.selectedPayerIds }.forEach { member ->
                OutlinedTextField(
                    value = state.payerAmountText[member.user_id].orEmpty(),
                    onValueChange = { onSetPayerAmountText(member.user_id, it) },
                    label = { Text("${member.displayName()}'s amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            val allocated = state.selectedPayerIds.sumOf { state.payerAmountText[it]?.toDoubleOrNull() ?: 0.0 }
            val target = state.amountText.toDoubleOrNull() ?: 0.0
            Text(
                "Allocated: $allocated of $target",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberChipRow(members: List<GroupMemberDto>, selected: Set<String>, onClick: (String) -> Unit) {
    LazyRow(modifier = Modifier.fillMaxWidth()) {
        items(members, key = { it.user_id }) { member ->
            FilterChip(
                selected = member.user_id in selected,
                onClick = { onClick(member.user_id) },
                label = { Text(member.displayName()) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
