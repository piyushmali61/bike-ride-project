package com.bikeride.intercom.feature.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bikeride.intercom.feature.ui.screens.*

/**
 * Root composable for the Smart Intercom app.
 * Hosts navigation between all screens (Section 18).
 */
@Composable
fun IntercomApp(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onCreateConnection = { navController.navigate(Screen.CreateConnection.route) },
                onJoinConnection = { navController.navigate(Screen.JoinConnection.route) },
                onStartSession = { navController.navigate(Screen.Session.route) },
                onSettings = { navController.navigate(Screen.Settings.route) },
                onRidingMode = { navController.navigate(Screen.RidingMode.route) }
            )
        }
        composable(Screen.CreateConnection.route) {
            CreateConnectionScreen(
                onBack = { navController.popBackStack() },
                onConnected = {
                    navController.navigate(Screen.Session.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }
        composable(Screen.JoinConnection.route) {
            JoinConnectionScreen(
                onBack = { navController.popBackStack() },
                onConnected = {
                    navController.navigate(Screen.Session.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }
        composable(Screen.Session.route) {
            SessionScreen(
                onDisconnect = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onRidingMode = { navController.navigate(Screen.RidingMode.route) }
            )
        }
        composable(Screen.RidingMode.route) {
            RidingModeScreen(
                onExit = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Diagnostics.route) {
            DiagnosticsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Navigation destinations matching Section 18 screen-by-screen design.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object CreateConnection : Screen("create_connection")
    data object JoinConnection : Screen("join_connection")
    data object Session : Screen("session")
    data object RidingMode : Screen("riding_mode")
    data object Settings : Screen("settings")
    data object Diagnostics : Screen("diagnostics")
}
