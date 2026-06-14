package com.offpay.app.presentation.navigation

/**
 * Navigation routes for the single-activity architecture.
 * Each tab corresponds to a top-level destination that lives inside
 * [MainScaffold]'s NavHost.
 *
 * Tabs (bottom nav): Pay / Scan / Balance / Settings.
 * History is reachable from the Pay screen top bar and from Settings.
 * Faq is a modal screen reached from Settings, onboarding, and error states.
 *
 * PIN entry is now inline on Pay & Balance — no dedicated route.
 */
sealed class Screen(val route: String) {
    object Pay : Screen("pay")
    object Scan : Screen("scan")
    object Balance : Screen("balance")
    object Settings : Screen("settings")
    object History : Screen("history")
    object Faq : Screen("faq")
    object Legal : Screen("legal")
    object Privacy : Screen("privacy")
    object Terms : Screen("terms")
}
