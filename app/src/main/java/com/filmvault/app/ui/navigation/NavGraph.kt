package com.filmvault.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.filmvault.app.di.AppModule
import com.filmvault.app.ui.screens.CatalogScreen
import com.filmvault.app.ui.screens.CacheScreen
import com.filmvault.app.ui.screens.AboutScreen
import com.filmvault.app.ui.screens.DetailScreen
import com.filmvault.app.ui.screens.HomeScreen
import com.filmvault.app.ui.screens.HotScreen
import com.filmvault.app.ui.screens.LibraryScreen
import com.filmvault.app.ui.screens.LoginScreen
import com.filmvault.app.ui.screens.PlayerScreen
import com.filmvault.app.ui.screens.SearchScreen
import com.filmvault.app.ui.screens.SettingsScreen
import com.filmvault.app.util.Constants

@Composable
fun AppNavHost(startDestination: String = "auth") {
    val nav = rememberNavController()
    val currentRoute = nav.currentBackStackEntryAsState().value?.destination?.route
    val isPlayerRoute = currentRoute?.startsWith("player/") == true
    Box(
        modifier = Modifier.fillMaxSize().then(
            if (isPlayerRoute) Modifier else Modifier.safeDrawingPadding(),
        ),
    ) {
    NavHost(navController = nav, startDestination = startDestination) {
        composable("auth") { AuthGate(nav) }
        composable("login") { LoginScreen(nav) }
        composable("home") { HomeScreen(nav) }
        composable("hot") { HotScreen(nav) }
        composable("search") { SearchScreen(nav) }
        composable("library") { LibraryScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("cache") { CacheScreen(nav) }
        composable("about") { AboutScreen(nav) }
        composable(
            route = "catalog/{dir}?sort={sort}&year={year}",
            arguments = listOf(
                navArgument("dir") { type = NavType.StringType },
                navArgument("sort") { type = NavType.StringType; defaultValue = "" },
                navArgument("year") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { back ->
            val dir = back.arguments?.getString("dir") ?: "mv"
            val sort = back.arguments?.getString("sort") ?: ""
            val year = back.arguments?.getString("year") ?: ""
            val label = Constants.CATEGORIES.find { it.dir == dir }?.label ?: "列表"
            CatalogScreen(nav, dir, label, sort, year)
        }
        composable(
            route = "detail/{dir}/{id}?localOnly={localOnly}",
            arguments = listOf(
                navArgument("dir") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
                navArgument("localOnly") { type = NavType.BoolType; defaultValue = false },
            ),
        ) { back ->
            val dir = back.arguments?.getString("dir") ?: "mv"
            val id = back.arguments?.getString("id") ?: ""
            DetailScreen(nav, dir, id, back.arguments?.getBoolean("localOnly") == true)
        }
        composable(
            route = "player/{url}?lineId={lineId}&episode={episode}&episodeCount={episodeCount}&lineName={lineName}&resourceTitle={resourceTitle}&cacheKey={cacheKey}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("lineId") { type = NavType.StringType; defaultValue = "" },
                navArgument("episode") { type = NavType.IntType; defaultValue = 1 },
                navArgument("episodeCount") { type = NavType.IntType; defaultValue = 1 },
                navArgument("lineName") { type = NavType.StringType; defaultValue = "" },
                navArgument("resourceTitle") { type = NavType.StringType; defaultValue = "" },
                navArgument("cacheKey") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { back ->
            val url = back.arguments?.getString("url") ?: ""
            PlayerScreen(
                nav = nav,
                url = url,
                lineId = back.arguments?.getString("lineId").orEmpty(),
                startEpisode = back.arguments?.getInt("episode") ?: 1,
                episodeCount = back.arguments?.getInt("episodeCount") ?: 1,
                lineName = back.arguments?.getString("lineName").orEmpty(),
                resourceTitle = back.arguments?.getString("resourceTitle").orEmpty(),
                cacheKey = back.arguments?.getString("cacheKey").orEmpty(),
            )
        }
    }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(nav, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && !AppModule.repository.isLoggedIn()) {
                val route = nav.currentBackStackEntry?.destination?.route
                if (route != "login" && route != "auth") {
                    nav.navigate("login") {
                        popUpTo(nav.graph.startDestinationId) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun AuthGate(nav: NavHostController) {
    LaunchedEffect(Unit) {
        nav.navigate(if (AppModule.repository.isLoggedIn()) "home" else "login") {
            popUpTo("auth") { inclusive = true }
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
