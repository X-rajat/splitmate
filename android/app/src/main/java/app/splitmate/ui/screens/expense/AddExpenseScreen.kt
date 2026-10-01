package app.splitmate.ui.screens.expense

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.splitmate.ui.common.AddGuestDialog
import androidx.compose.foundation.layout.PaddingValues

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(onDone: () -> Unit, viewModel: AddExpenseViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showAddGuest by remember { mutableStateOf(false) }

    LaunchedEffect(state.success) { if (state.success) onDone() }

    if (showAddGuest) {
        AddGuestDialog(
            onConfirm = { name -> viewModel.addGuest(name); showAddGuest = false },
            onDismiss = { showAddGuest = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add expense", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        OutlinedTextField(
                            value = state.description,
                            onValueChange = viewModel::setDescription,
                            label = { Text("What was it for?") },
                            placeholder = { Text("Dinner, taxi, hotel...") },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = state.amountText,
                            onValueChange = viewModel::setAmountText,
                            label = { Text("Amount") },
                            leadingIcon = { Text("₹", style = MaterialTheme.typography.titleMedium) },
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(14.dp))
                        CategoryPicker(selected = state.category, onSelect = viewModel::setCategory, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (state.membersLoading) {
                item { CircularProgressIndicator() }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            PayerSection(
                                state = state,
                                onToggleMultiPayer = viewModel::toggleMultiPayer,
                                onSetSinglePayer = viewModel::setSinglePayer,
                                onTogglePayerSelected = viewModel::togglePayerSelected,
                                onSetPayerAmountText = viewModel::setPayerAmountText,
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            SplitSection(
                                state = state,
                                onSetSplitMode = viewModel::setSplitMode,
                                onToggleParticipant = viewModel::toggleParticipant,
                                onSetExactAmountText = viewModel::setExactAmountText,
                                onSetPercentageText = viewModel::setPercentageText,
                                onSetShares = viewModel::setShares,
                            )
                            TextButton(onClick = { showAddGuest = true }) {
                                Text("+ Add guest")
                            }
                        }
                    }
                }
            }

            item {
                if (state.validationError != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.validationError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                if (state.savedOffline) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Saved offline - will sync automatically when you're back online.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = viewModel::submit,
                    enabled = state.validationError == null && state.description.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Save expense", style = MaterialTheme.typography.titleMedium) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
