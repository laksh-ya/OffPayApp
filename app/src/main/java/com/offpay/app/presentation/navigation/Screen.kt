package com.offpay.app.presentation.navigation

/**
 * Navigation routes for the single-activity architecture.
 * Each tab corresponds to a top-level destination that lives inside
 * [MainScaffold]'s NavHost.
 */
sealed class Screen(val route: String) {
    object Pay : Screen("pay")
    object Scan : Screen("scan")
    object History : Screen("history")
    object Settings : Screen("settings")
}
