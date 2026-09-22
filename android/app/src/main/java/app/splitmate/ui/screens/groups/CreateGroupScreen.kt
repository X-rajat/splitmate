package app.splitmate.ui.screens.groups

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateGroupViewModel @Inject constructor(private val groupRepository: GroupRepository) : ViewModel() {
    private val _created = MutableStateFlow<String?>(null)
    val created: StateFlow<String?> = _created

    fun create(name: String, description: String, currency: String) {
        viewModelScope.launch {
            val group = groupRepository.createGroup(name, description.ifBlank { null }, currency, emptyList())
            _created.value = group.id
        }
    }
}

private val CURRENCIES = listOf("INR", "USD", "EUR", "GBP", "AED")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGroupScreen(onCreated: (String) -> Unit, viewModel: CreateGroupViewModel = hiltViewModel()) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("INR") }
    var expanded by remember { mutableStateOf(false) }
    val created by viewModel.created.collectAsState()

    LaunchedEffect(created) { created?.let(onCreated) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("New group", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Group name (e.g. Goa Trip 2026)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(description, { description = it }, label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                readOnly = true, value = currency, onValueChange = {}, label = { Text("Currency") },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CURRENCIES.forEach { c ->
                    DropdownMenuItem(text = { Text(c) }, onClick = { currency = c; expanded = false })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { viewModel.create(name, description, currency) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Create group")
        }
    }
}
