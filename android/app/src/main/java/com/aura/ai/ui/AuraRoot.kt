package com.aura.ai.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aura.ai.auraApp
import com.aura.ai.ui.screens.AuthScreen
import com.aura.ai.ui.screens.ChatScreen
import com.aura.ai.ui.screens.HistoryScreen
import com.aura.ai.ui.screens.HomeScreen
import com.aura.ai.ui.screens.ImagesScreen
import com.aura.ai.ui.screens.ProScreen
import com.aura.ai.ui.screens.ProfileScreen
import com.aura.ai.ui.screens.SettingsScreen
import com.aura.ai.ui.screens.ToolsScreen
import com.aura.ai.ui.theme.Aura
import com.aura.ai.ui.theme.AuraBackground
import com.aura.ai.ui.theme.glass

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val TOOLS = "tools"
    const val SETTINGS = "settings"
    const val IMAGES = "images"
    const val PROFILE = "profile"
    const val PRO = "pro"
    const val CHAT = "chat/{chatId}?mode={mode}&draft={draft}"

    fun chat(chatId: String = "new", mode: String = "chat", draft: String = "") =
        "chat/$chatId?mode=$mode&draft=${Uri.encode(draft)}"
}

/** Space reserved at the bottom of tab screens for the floating tab bar. */
val BottomBarSpace = 104.dp

@Composable
fun AuraRoot() {
    val app = LocalContext.current.auraApp
    val loggedIn by app.repository.loggedIn.collectAsStateWithLifecycle()

    LaunchedEffect(loggedIn) {
        if (loggedIn) app.billing.connect()
    }

    AuraBackground {
        AnimatedContent(
            targetState = loggedIn,
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(250)) },
            label = "auth",
        ) { signedIn ->
            if (signedIn) MainNavigation() else AuthScreen()
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Главная", Icons.Rounded.Home),
    Tab(Routes.HISTORY, "История", Icons.Rounded.History),
    Tab(Routes.TOOLS, "Инструменты", Icons.Rounded.GridView),
    Tab(Routes.SETTINGS, "Настройки", Icons.Rounded.Settings),
)

@Composable
private fun MainNavigation() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showTabs = tabs.any { it.route == route }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            enterTransition = { fadeIn(tween(260)) + slideInHorizontally(tween(260)) { it / 12 } },
            exitTransition = { fadeOut(tween(200)) },
            popEnterTransition = { fadeIn(tween(260)) },
            popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(tween(260)) { it / 12 } },
        ) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(Routes.HISTORY) { HistoryScreen(nav) }
            composable(Routes.TOOLS) { ToolsScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }
            composable(Routes.IMAGES) { ImagesScreen(nav) }
            composable(Routes.PROFILE) { ProfileScreen(nav) }
            composable(Routes.PRO) { ProScreen(nav) }
            composable(
                Routes.CHAT,
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType },
                    navArgument("mode") { type = NavType.StringType; defaultValue = "chat" },
                    navArgument("draft") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStack ->
                val args = backStack.arguments
                ChatScreen(
                    nav = nav,
                    chatId = args?.getString("chatId") ?: "new",
                    mode = args?.getString("mode") ?: "chat",
                    draft = args?.getString("draft").orEmpty(),
                )
            }
        }

        AnimatedVisibility(
            visible = showTabs,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            TabBar(current = route, nav = nav)
        }
    }
}

@Composable
private fun TabBar(current: String?, nav: NavHostController) {
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .glass(RoundedCornerShape(30.dp), strong = true)
            .padding(vertical = 8.dp),
    ) {
        tabs.forEach { tab ->
            val selected = current == tab.route
            val color by animateColorAsState(if (selected) Aura.Lilac else Aura.TextSecondary, label = "tab")
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                        if (!selected) {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(tab.icon, tab.label, tint = color, modifier = Modifier.size(26.dp))
                Spacer(Modifier.height(3.dp))
                Text(tab.label, color = color, fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}
