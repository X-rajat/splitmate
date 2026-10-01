package app.splitmate.ui.screens.expense

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.splitmate.domain.expense.SplitMode

private fun SplitMode.label(): String = when (this) {
    SplitMode.EQUAL -> "Equal"
    SplitMode.EXACT -> "Unequal"
    SplitMode.PERCENTAGE -> "%"
    SplitMode.SHARES -> "Shares"
}

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
        Text("Split between", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        val modes = SplitMode.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            modes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = state.splitMode == mode,
                    onClick = { onSetSplitMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                ) { Text(mode.label()) }
            }
        }
        Spacer(Modifier.height(8.dp))

        MemberChipRow(state.members, selected = state.includedParticipantIds) { onToggleParticipant(it) }
        Spacer(Modifier.height(8.dp))

        val included = state.members.filter { it.user_id in state.includedParticipantIds }
        when (state.splitMode) {
            SplitMode.EQUAL -> Unit
            SplitMode.EXACT -> included.forEach { member ->
                OutlinedTextField(
                    value = state.exactAmountText[member.user_id].orEmpty(),
                    onValueChange = { onSetExactAmountText(member.user_id, it) },
                    label = { Text("${member.displayName()}'s amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            SplitMode.PERCENTAGE -> included.forEach { member ->
                OutlinedTextField(
                    value = state.percentageText[member.user_id].orEmpty(),
                    onValueChange = { onSetPercentageText(member.user_id, it) },
                    label = { Text("${member.displayName()}'s %") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                )
            }
            SplitMode.SHARES -> included.forEach { member ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Text(member.displayName(), modifier = Modifier.weight(1f))
                    val shares = state.shareCounts[member.user_id] ?: 1
                    IconButton(onClick = { onSetShares(member.user_id, shares - 1) }) { Text("-") }
                    Text("$shares")
                    IconButton(onClick = { onSetShares(member.user_id, shares + 1) }) { Text("+") }
                }
            }
        }
    }
}
