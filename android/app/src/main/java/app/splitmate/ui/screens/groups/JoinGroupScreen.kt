package app.splitmate.ui.screens.groups

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.splitmate.data.remote.dto.GroupDto
import app.splitmate.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    private val groupRepository: GroupRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val token: String = checkNotNull(savedStateHandle["token"])
    private val _group = MutableStateFlow<GroupDto?>(null)
    val group: StateFlow<GroupDto?> = _group
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _joined = MutableStateFlow(false)
    val joined: StateFlow<Boolean> = _joined

    fun join() {
        viewModelScope.launch {
            try {
                val response = groupRepository.joinGroup(token)
                _group.value = response.group
                _joined.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "This invitation link is invalid or has expired"
            }
        }
    }
}

@Composable
fun JoinGroupScreen(onJoined: (String) -> Unit, onDecline: () -> Unit, viewModel: JoinGroupViewModel = hiltViewModel()) {
    val group by viewModel.group.collectAsState()
    val joined by viewModel.joined.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(joined) { if (joined) group?.let { onJoined(it.id) } }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("You've been invited", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("Join this expense group on SplitMate?", style = MaterialTheme.typography.bodyMedium)
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = viewModel::join) { Text("Join group") }
            OutlinedButton(onClick = onDecline) { Text("Decline") }
        }
    }
}
