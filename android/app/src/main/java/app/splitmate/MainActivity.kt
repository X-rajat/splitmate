package app.splitmate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import app.splitmate.ui.navigation.Screen
import app.splitmate.ui.screens.auth.LoginScreen
import app.splitmate.ui.screens.auth.SignUpScreen
import app.splitmate.ui.screens.expense.AddExpenseScreen
import app.splitmate.ui.screens.friends.FriendsScreen
import app.splitmate.ui.screens.groups.CreateGroupScreen
import app.splitmate.ui.screens.groups.GroupDetailScreen
import app.splitmate.ui.screens.groups.JoinGroupScreen
import app.splitmate.ui.screens.home.HomeScreen
import app.splitmate.ui.screens.profile.ProfileScreen
import app.splitmate.ui.theme.SplitMateTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Deep link entry point (spec section 8): both the App Link
        // (https://splitmate.app/join/{token}) and the splitmate://join/{token}
        // fallback carry the token as the last path segment.
        val deepLinkToken: String? = intent?.data?.let { uri: Uri ->
            if (uri.pathSegments.contains("join")) uri.lastPathSegment else null
        }

        setContent {
            SplitMateTheme {
                SplitMateNavHost(deepLinkToken)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask launchMode means a repeat deep-link click while the app is already
        // running arrives here instead of onCreate; restart the activity to re-derive
        // deepLinkToken cleanly rather than trying to push a second nav graph at runtime.
        setIntent(intent)
        recreate()
    }
}

private data class BottomTab(val screen: Screen, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val BOTTOM_TABS = listOf(
    BottomTab(Screen.Home, "Home", Icons.Filled.Home),
    BottomTab(Screen.Groups, "Groups", Icons.Filled.Group),
    BottomTab(Screen.Friends, "Friends", Icons.Filled.History),
    BottomTab(Screen.Profile, "Profile", Icons.Filled.Person),
)

@Composable
fun SplitMateNavHost(deepLinkToken: String?) {
    val navController = rememberNavController()
    val startDestination = if (deepLinkToken != null) Screen.JoinGroup.createRoute(deepLinkToken) else Screen.Login.route

    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route
    val showBottomBar = BOTTOM_TABS.any { it.screen.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BOTTOM_TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.screen.route,
                            onClick = {
                                navController.navigate(tab.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = startDestination, modifier = Modifier.padding(padding)) {
            composable(Screen.Login.route) {
                LoginScreen(
                    onLoggedIn = { navController.navigateAndClear(Screen.Home.route) },
                    onGoToSignUp = { navController.navigate(Screen.SignUp.route) },
                )
            }
            composable(Screen.SignUp.route) {
                SignUpScreen(
                    onSignedUp = { navController.navigateAndClear(Screen.Home.route) },
                    onBackToLogin = { navController.popBackStack() },
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onOpenGroup = { id -> navController.navigate(Screen.GroupDetail.createRoute(id)) },
                    onCreateGroup = { navController.navigate(Screen.CreateGroup.route) },
                )
            }
            composable(Screen.Groups.route) {
                HomeScreen(
                    onOpenGroup = { id -> navController.navigate(Screen.GroupDetail.createRoute(id)) },
                    onCreateGroup = { navController.navigate(Screen.CreateGroup.route) },
                )
            }
            composable(Screen.CreateGroup.route) {
                CreateGroupScreen(onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate(Screen.GroupDetail.createRoute(id))
                })
            }
            composable(Screen.GroupDetail.route) { backStackEntry ->
                val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
                GroupDetailScreen(
                    onAddExpense = { navController.navigate(Screen.AddExpense.createRoute(groupId)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.AddExpense.route) {
                AddExpenseScreen(onDone = { navController.popBackStack() })
            }
            composable(Screen.JoinGroup.route) {
                JoinGroupScreen(
                    onJoined = { id -> navController.navigateAndClear(Screen.GroupDetail.createRoute(id)) },
                    onDecline = { navController.navigateAndClear(Screen.Login.route) },
                )
            }
            composable(Screen.Friends.route) { FriendsScreen() }
            composable(Screen.Profile.route) {
                ProfileScreen(onLoggedOut = { navController.navigateAndClear(Screen.Login.route) })
            }
        }
    }
}

private fun NavHostController.navigateAndClear(route: String) {
    navigate(route) {
        popUpTo(0) { inclusive = true }
    }
}
