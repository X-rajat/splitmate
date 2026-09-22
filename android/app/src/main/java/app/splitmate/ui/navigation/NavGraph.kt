package app.splitmate.ui.navigation

sealed class Screen(val route: String) {
    data object Login : Screen("login")
    data object SignUp : Screen("signup")
    data object Home : Screen("home")
    data object Groups : Screen("groups")
    data object Friends : Screen("friends")
    data object Profile : Screen("profile")
    data object GroupDetail : Screen("group/{groupId}") {
        fun createRoute(groupId: String) = "group/$groupId"
    }
    data object AddExpense : Screen("group/{groupId}/add-expense") {
        fun createRoute(groupId: String) = "group/$groupId/add-expense"
    }
    data object JoinGroup : Screen("join/{token}") {
        fun createRoute(token: String) = "join/$token"
    }
    data object CreateGroup : Screen("create-group")
}
