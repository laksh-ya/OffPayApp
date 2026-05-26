package com.offpay.app.presentation.navigation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.offpay.app.OffPayApplication
import com.offpay.app.domain.SessionState
import com.offpay.app.offPayApp
import com.offpay.app.presentation.BalanceViewModel
import com.offpay.app.presentation.HistoryViewModel
import com.offpay.app.presentation.PayViewModel
import com.offpay.app.presentation.permissions.rememberPermissionStatus
import com.offpay.app.presentation.screens.HistoryScreen
import com.offpay.app.presentation.screens.PayScreen
import com.offpay.app.presentation.screens.ScanScreen
import com.offpay.app.presentation.screens.SettingsScreen
import com.offpay.app.presentation.screens.onboarding.OnboardingFlow
import com.offpay.app.presentation.ui.components.NeoPopBottomNav
import com.offpay.app.presentation.ui.components.NeoPopNavItem
import com.offpay.app.presentation.ui.theme.NeoPopColors
import kotlinx.coroutines.launch

private val NAV_ITEMS = listOf(
    NeoPopNavItem(Screen.Pay.route, "Pay", Icons.Default.Payments),
    NeoPopNavItem(Screen.Scan.route, "Scan", Icons.Default.QrCodeScanner),
    NeoPopNavItem(Screen.History.route, "History", Icons.Default.History),
    NeoPopNavItem(Screen.Settings.route, "Settings", Icons.Default.Settings)
)

/**
 * Top-level scaffold:
 *  - First-launch flow gates the rest until the user finishes onboarding.
 *  - Bottom nav with 4 tabs.
 *  - QR scan navigates back to Pay with the scanned data autofilled.
 *  - Bottom nav hides during onboarding and during an active USSD session.
 */
@Composable
fun OffPayApp() {
    val app = LocalContext.current.offPayApp
    val firstLaunchDone by app.prefsRepo.firstLaunchComplete.collectAsState(initial = true)
    val scope = rememberCoroutineScope()

    if (!firstLaunchDone) {
        OnboardingFlow(onComplete = {
            scope.launch { app.prefsRepo.setFirstLaunchComplete(true) }
        })
    } else {
        MainScaffold(app = app)
    }
}

@Composable
private fun MainScaffold(app: OffPayApplication) {
    val navController = rememberNavController()
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route ?: Screen.Pay.route

    val permissions by rememberPermissionStatus()
    val context = LocalContext.current

    // ViewModels — manual DI from the app singletons. Stable instances across
    // tab switches because we anchor them to the NavHost lifecycle.
    val payViewModel = rememberPayViewModel(app)
    val historyViewModel = rememberHistoryViewModel(app)

    val payVmSession by payViewModel.sessionState.collectAsState()
    val sessionActive = payVmSession is SessionState.Running

    val hideBottomNav = sessionActive || currentRoute == Screen.Scan.route

    Column(
        Modifier
            .fillMaxSize()
            .background(NeoPopColors.Black)
    ) {
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Pay.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Pay.route) {
                    PayScreen(
                        viewModel = payViewModel,
                        onNavigateScan = {
                            navController.navigate(Screen.Scan.route) {
                                launchSingleTop = true
                            }
                        },
                        permissions = permissions
                    )
                }
                composable(Screen.Scan.route) {
                    ScanScreen(
                        qrManager = app.qrScannerManager,
                        onResult = { raw ->
                            payViewModel.onQrScanned(raw)
                            navController.navigate(Screen.Pay.route) {
                                popUpTo(Screen.Pay.route) { inclusive = true }
                            }
                        },
                        onClose = { navController.popBackStack() }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onPay = {
                            navController.navigate(Screen.Pay.route) {
                                popUpTo(Screen.Pay.route) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        prefsRepo = app.prefsRepo,
                        historyViewModel = historyViewModel,
                        permissions = permissions,
                        versionName = "1.0.0",
                        onClearAllData = { /* historyViewModel.clearHistory already wired */ }
                    )
                }
            }
        }
        if (!hideBottomNav) {
            NeoPopBottomNav(
                items = NAV_ITEMS,
                selectedKey = currentRoute,
                onSelect = { key ->
                    if (key != currentRoute) {
                        navController.navigate(key) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun rememberPayViewModel(app: OffPayApplication): PayViewModel {
    val context = LocalContext.current
    return remember {
        PayViewModel(
            actionRunner = app.actionRunner,
            historyRepo = app.historyRepo,
            prefsRepo = app.prefsRepo,
            carrierDetector = app.carrierDetector,
            overlayController = app.overlayController,
            onDialerFallback = { code ->
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$code"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        )
    }
}

@Composable
private fun rememberHistoryViewModel(app: OffPayApplication): HistoryViewModel {
    return remember { HistoryViewModel(app.historyRepo) }
}
