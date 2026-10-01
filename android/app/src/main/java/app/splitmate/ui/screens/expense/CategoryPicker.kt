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
    ExpenseCategoryOption("shopping", "Shopping", "🛖"),
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
