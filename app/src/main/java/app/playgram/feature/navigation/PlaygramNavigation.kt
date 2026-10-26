package app.playgram.feature.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import app.playgram.core.design.LocalPlaygramColors
import app.playgram.feature.chats.ChatDetailScreen
import app.playgram.feature.downloads.DownloadsScreen
import app.playgram.feature.home.HomeScreen
import app.playgram.feature.library.LibraryScreen
import app.playgram.feature.player.PlayerScreen
import app.playgram.feature.tools.ToolsScreen

sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    object Home : BottomNavItem(NavRoutes.HOME, "Home", Icons.Outlined.Home)
    object Downloads : BottomNavItem(NavRoutes.DOWNLOADS, "Downloads", Icons.Outlined.FileDownload)
    object Library : BottomNavItem(NavRoutes.LIBRARY, "Library", Icons.Outlined.VideoLibrary)
    object Settings : BottomNavItem(NavRoutes.SETTINGS, "Settings", Icons.Outlined.Settings)
}

@Composable
fun PlaygramNavigation() {
    val navController = rememberNavController()
    val colors = LocalPlaygramColors.current

    val bottomNavItems = listOf(
        BottomNavItem.Home,
        BottomNavItem.Downloads,
        BottomNavItem.Library,
        BottomNavItem.Settings
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val tabRoutes = bottomNavItems.map { it.route }
    val showBottomBar = currentRoute in tabRoutes || currentRoute == NavRoutes.CHAT_DETAIL
    // The tab a detail screen belongs to: the nearest tab root below it in the back stack.
    val backStack by navController.currentBackStack.collectAsState()
    val currentTab = backStack.lastOrNull { it.destination.route in tabRoutes }?.destination?.route

    var homeScrollTop by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = colors.surface,
                    tonalElevation = 0.dp
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = (currentTab == item.route)
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (item.route == currentTab) {
                                    // Already in this tab (maybe deep inside a chat): go back to its root.
                                    navController.popBackStack(item.route, inclusive = false)
                                    if (item.route == NavRoutes.HOME) homeScrollTop++
                                } else if (item.route == NavRoutes.HOME) {
                                    // Home is the bottom of the stack: pop everything above it (a channel opened
                                    // from Library included). navigate() to the start destination does nothing there.
                                    navController.popBackStack(NavRoutes.HOME, inclusive = false)
                                    homeScrollTop++
                                } else {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title, fontSize = 12.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Visible, modifier = Modifier.wrapContentWidth(unbounded = true)) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = app.playgram.core.design.AccentBlueLight,
                                selectedTextColor = app.playgram.core.design.AccentBlueLight,
                                unselectedIconColor = colors.textSecondary,
                                unselectedTextColor = colors.textSecondary,
                                indicatorColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                    }
                }
            }
        },
        containerColor = colors.background
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            composable(NavRoutes.HOME) {
                HomeScreen(onNavigate = { route -> navController.navigate(route) }, scrollToTop = homeScrollTop)
            }

            composable(
                route = NavRoutes.CHAT_DETAIL,
                arguments = listOf(navArgument("chatId") { type = NavType.LongType })
            ) { backStack ->
                val chatId = backStack.arguments?.getLong("chatId") ?: 0L
                ChatDetailScreen(
                    chatId = chatId,
                    onBack = { navController.popBackStack() },
                    onNavigate = { route -> navController.navigate(route) }
                )
            }

            composable(NavRoutes.DOWNLOADS) {
                DownloadsScreen(onNavigate = { route -> navController.navigate(route) })
            }

            composable(NavRoutes.LIBRARY) {
                LibraryScreen(onNavigate = { route -> navController.navigate(route) })
            }

            composable(NavRoutes.SETTINGS) {
                ToolsScreen(onBack = null, onNavigate = { route -> navController.navigate(route) })
            }

            composable(
                route = NavRoutes.PLAYER,
                arguments = listOf(
                    navArgument("url") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        defaultValue = "Media Player"
                    }
                )
            ) { backStack ->
                val url = backStack.arguments?.getString("url") ?: ""
                val title = backStack.arguments?.getString("title") ?: "Media Player"
                PlayerScreen(
                    videoUrl = url,
                    videoTitle = title,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
