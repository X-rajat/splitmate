package app.splitmate.ui.screens.friends

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.splitmate.data.remote.dto.UserDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(viewModel: FriendsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Friends") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it; viewModel.search(it) },
                label = { Text("Search users by name or email") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            if (state.searchResults.isNotEmpty()) {
                Text("Results", style = MaterialTheme.typography.titleSmall)
                LazyColumn { items(state.searchResults, key = { it.id }) { user -> UserRow(user) { viewModel.sendRequest(user.id) } } }
                Spacer(Modifier.height(16.dp))
            }
            Text("Your friends", style = MaterialTheme.typography.titleMedium)
            LazyColumn { items(state.friends, key = { it.id }) { user -> FriendRow(user) } }
        }
    }
}

@Composable
private fun UserRow(user: UserDto, onAdd: () -> Unit) {
    Card {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(user.name)
            TextButton(onClick = onAdd) { Text("Add") }
        }
    }
}

@Composable
private fun FriendRow(user: UserDto) {
    Card {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(user.name, style = MaterialTheme.typography.titleSmall)
            Text(user.email ?: user.mobile.orEmpty(), style = MaterialTheme.typography.bodySmall)
        }
    }
}
