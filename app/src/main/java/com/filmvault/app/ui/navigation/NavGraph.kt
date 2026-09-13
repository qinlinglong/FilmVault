package com.filmvault.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation.navArgument
import com.filmvault.app.di.AppModule
import com.filmvault.app.ui.screens.CatalogScreen
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
    NavHost(navController = nav, startDestination = startDestination) {
        composable("auth") { AuthGate(nav) }
        composable("login") { LoginScreen(nav) }
        composable("home") { HomeScreen(nav) }
        composable("hot") { HotScreen(nav) }
        composable("search") { SearchScreen(nav) }
        composable("library") { LibraryScreen(nav) }
        composable("settings") { SettingsScreen(nav) }
        composable("about") { AboutScreen(nav) }
        composable(
            route = "catalog/{dir}?sort={sort}",
            arguments = listOf(
                navArgument("dir") { type = NavType.StringType },
                navArgument("sort") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { back ->
            val dir = back.arguments?.getString("dir") ?: "mv"
            val sort = back.arguments?.getString("sort") ?: ""
            val label = Constants.CATEGORIES.find { it.dir == dir }?.label ?: "列表"
            CatalogScreen(nav, dir, label, sort)
        }
        composable(
            route = "detail/{dir}/{id}",
            arguments = listOf(
                navArgument("dir") { type = NavType.StringType },
                navArgument("id") { type = NavType.StringType },
            ),
        ) { back ->
            val dir = back.arguments?.getString("dir") ?: "mv"
            val id = back.arguments?.getString("id") ?: ""
            DetailScreen(nav, dir, id)
        }
        composable(
            route = "player/{url}?lineId={lineId}&episode={episode}&episodeCount={episodeCount}&lineName={lineName}",
            arguments = listOf(
                navArgument("url") { type = NavType.StringType },
                navArgument("lineId") { type = NavType.StringType; defaultValue = "" },
                navArgument("episode") { type = NavType.IntType; defaultValue = 1 },
                navArgument("episodeCount") { type = NavType.IntType; defaultValue = 1 },
                navArgument("lineName") { type = NavType.StringType; defaultValue = "" },
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
            )
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
