package app.splitmate.ui.screens.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLoggedOut: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()

    LaunchedEffect(loggedOut) { if (loggedOut) onLoggedOut() }

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            user?.let {
                Text(it.name, style = MaterialTheme.typography.headlineSmall)
                it.email?.let { e -> Text(e, style = MaterialTheme.typography.bodyMedium) }
                it.mobile?.let { m -> Text(m, style = MaterialTheme.typography.bodyMedium) }
                Spacer(Modifier.height(8.dp))
                Text("Default currency: ${it.default_currency}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(32.dp))
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            SettingsRow("Dark mode")
            SettingsRow("Notifications")
            SettingsRow("Default currency")
            SettingsRow("Privacy")
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = viewModel::logout, modifier = Modifier.fillMaxWidth()) { Text("Logout") }
        }
    }
}

@Composable
private fun SettingsRow(label: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text(label) }
}
