package com.psychologist.financial.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import com.psychologist.financial.navigation.BottomNavItem
import com.psychologist.financial.navigation.bottomNavItems

/**
 * BottomNavigation
 *
 * Reusable bottom navigation bar composable.
 * Shows 5 tabs: Pacientes, Consultas, Pagamentos, Dashboard, Exportar.
 *
 * Navigation behavior:
 * - Tapping a tab navigates to its root destination
 * - Tapping the current tab pops to the root (restores scroll state)
 * - Back stack is saved/restored per tab (singleTop mode)
 * - State is restored when switching back to a tab
 *
 * Usage:
 * ```kotlin
 * BottomNavigation(navController = navController)
 * ```
 */
@Composable
fun AppBottomNavigation(
    navController: NavController,
    items: List<BottomNavItem> = bottomNavItems
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        items.forEach { item ->
            NavigationBarItem(
                selected = isRouteSelected(currentRoute, item.route),
                onClick = {
                    navController.navigate(item.route) {
                        // Pop up to start destination to avoid building a large back stack
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // Avoid multiple copies of the same destination on back stack
                        launchSingleTop = true
                        // Restore state when reselecting a previously selected item
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.contentDescription
                    )
                },
                label = {
                    Text(text = item.label)
                }
            )
        }
    }
}

/**
 * Determines if a bottom nav item should be highlighted as selected.
 *
 * Handles route matching for destinations with arguments:
 * - Exact match for simple routes (e.g., "dashboard")
 * - Prefix match for routes with arguments (e.g., "appointments/0?..." matches "appointments/{patientId}?...")
 */
private fun isRouteSelected(currentRoute: String?, itemRoute: String): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == itemRoute) return true

    // Extract base path (before '?' and '{') for comparison
    val itemBase = itemRoute.substringBefore("?").substringBefore("{").trimEnd('/')
    val currentBase = currentRoute.substringBefore("?").substringBefore("{").trimEnd('/')

    return currentBase == itemBase
}
