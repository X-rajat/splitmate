package app.splitmate.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.splitmate.ui.theme.SplitMateAmber
import app.splitmate.ui.theme.SplitMateTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLoggedOut: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val user by viewModel.user.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    var showLogoutConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(loggedOut) { if (loggedOut) onLoggedOut() }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Log out?") },
            text = { Text("You'll need to log in again to see your groups and expenses.") },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; viewModel.logout() }) {
                    Text("Log out", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showLogoutConfirm = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ProfileHeader(
                name = user?.name,
                email = user?.email,
                mobile = user?.mobile,
                currency = user?.default_currency,
            )

            Column {
                Text(
                    "PREFERENCES",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Column {
                        SettingsRow(Icons.Filled.DarkMode, "Dark mode", SplitMateTeal, "Follows system")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsRow(Icons.Filled.Notifications, "Notifications", SplitMateAmber, "On")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsRow(Icons.Filled.CurrencyExchange, "Default currency", Color(0xFF5B6EF5), user?.default_currency ?: "INR")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsRow(Icons.Filled.Language, "Language", Color(0xFF2AA198), "English")
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        SettingsRow(Icons.Filled.Shield, "Privacy", Color(0xFFC5433F), null)
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            OutlinedButton(
                onClick = { showLogoutConfirm = true },
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                Icon(Icons.Filled.Logout, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Log out", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun ProfileHeader(name: String?, email: String?, mobile: String?, currency: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(SplitMateTeal, Color(0xFF0B4F44))))
                .padding(24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(SplitMateAmber, Color(0xFFF2C065)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (name?.trim()?.firstOrNull() ?: '?').uppercase(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        name ?: "Loading...",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    val contact = email ?: mobile
                    if (contact != null) {
                        Text(contact, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                    }
                    if (currency != null) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = Color.White.copy(alpha = 0.18f),
                        ) {
                            Text(
                                currency,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(icon: ImageVector, label: String, accent: Color, trailingText: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.small)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (trailingText != null) {
            Text(trailingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
