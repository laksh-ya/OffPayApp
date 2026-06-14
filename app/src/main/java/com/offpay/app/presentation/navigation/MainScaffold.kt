package com.offpay.app.presentation.navigation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.offpay.app.presentation.screens.BalanceScreen
import com.offpay.app.presentation.screens.FaqScreen
import com.offpay.app.presentation.screens.HistoryScreen
import com.offpay.app.presentation.screens.LegalScreen
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
    NeoPopNavItem(Screen.Balance.route, "Balance", Icons.Default.AccountBalance),
    NeoPopNavItem(Screen.Settings.route, "Settings", Icons.Default.Settings)
)

/**
 * Top-level scaffold:
 *  - First-launch flow gates the rest until the user finishes onboarding.
 *  - Bottom nav with 4 tabs: Pay / Scan / Balance / Settings.
 *  - History reachable from Pay (top-bar shortcut) and Settings.
 *  - PIN entry is INLINE on the Pay & Balance forms — there is no
 *    standalone PIN screen.
 *  - FAQ is a modal route pushed from Settings/Scanner/Pay error states.
 *  - Bottom nav hides during onboarding, an active session, scanner, or
 *    FAQ/History modals.
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
        MainScaffold(
            app = app,
            onReplayOnboarding = {
                scope.launch { app.prefsRepo.setFirstLaunchComplete(false) }
            }
        )
    }
}

@Composable
private fun MainScaffold(app: OffPayApplication, onReplayOnboarding: () -> Unit) {
    val navController = rememberNavController()
    val backstackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backstackEntry?.destination?.route ?: Screen.Pay.route

    val permissions by rememberPermissionStatus()

    val payViewModel = rememberPayViewModel(app)
    val balanceViewModel = rememberBalanceViewModel(app)
    val historyViewModel = rememberHistoryViewModel(app)

    val payVmSession by payViewModel.sessionState.collectAsState()
    val balanceVmSession by balanceViewModel.sessionState.collectAsState()

    val sessionActive = payVmSession is SessionState.Running ||
        balanceVmSession is SessionState.Running
    val hideBottomNav = sessionActive ||
        currentRoute == Screen.Scan.route ||
        currentRoute == Screen.Faq.route ||
        currentRoute == Screen.History.route ||
        currentRoute == Screen.Legal.route

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
                        onNavigateHistory = {
                            navController.navigate(Screen.History.route) {
                                launchSingleTop = true
                            }
                        },
                        onNavigateFaq = {
                            navController.navigate(Screen.Faq.route) {
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
                        onClose = { navController.popBackStack() },
                        onOpenFaq = {
                            navController.navigate(Screen.Faq.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.Balance.route) {
                    BalanceScreen(
                        viewModel = balanceViewModel,
                        historyViewModel = historyViewModel,
                        onOpenHistory = {
                            navController.navigate(Screen.History.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.History.route) {
                    HistoryScreen(
                        viewModel = historyViewModel,
                        onPay = {
                            navController.navigate(Screen.Pay.route) {
                                popUpTo(Screen.Pay.route) { inclusive = true }
                            }
                        },
                        onPayAgain = { txn ->
                            // Pre-fill the Pay form with this transaction's
                            // recipient + amount + note, then route back.
                            // The user still has to enter a PIN before any
                            // money moves — we never re-execute silently.
                            payViewModel.prefillFromTransaction(
                                vpa = txn.vpa,
                                amount = txn.amount,
                                note = txn.note
                            )
                            navController.navigate(Screen.Pay.route) {
                                popUpTo(Screen.Pay.route) { inclusive = true }
                            }
                        },
                        onClose = { navController.popBackStack() }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        prefsRepo = app.prefsRepo,
                        historyViewModel = historyViewModel,
                        permissions = permissions,
                        versionName = "1.0.1",
                        onClearAllData = { /* historyViewModel.clearHistory already wired */ },
                        onOpenFaq = {
                            navController.navigate(Screen.Faq.route) {
                                launchSingleTop = true
                            }
                        },
                        onOpenHistory = {
                            navController.navigate(Screen.History.route) {
                                launchSingleTop = true
                            }
                        },
                        onOpenLegal = {
                            navController.navigate(Screen.Legal.route) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
                composable(Screen.Faq.route) {
                    FaqScreen(
                        onClose = { navController.popBackStack() },
                        onReplayOnboarding = onReplayOnboarding
                    )
                }
                composable(Screen.Legal.route) {
                    LegalScreen(onClose = { navController.popBackStack() })
                }
                // Legacy routes kept for deep-link compat
                composable(Screen.Privacy.route) {
                    LegalScreen(onClose = { navController.popBackStack() })
                }
                composable(Screen.Terms.route) {
                    LegalScreen(onClose = { navController.popBackStack() })
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

/**
 * Writes [text] to the system clipboard with a "UPI ID" label. Used by
 * MANUAL mode to pre-stage the recipient VPA before opening the dialer.
 */
private fun writeToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("UPI ID", text))
}

/**
 * Shows a system-level Toast that survives our activity losing focus —
 * critical for MANUAL mode, where we hand off to the native dialer the
 * moment after copying the UPI ID. The Compose snackbar inside the app
 * disappears the instant the dialer takes the foreground.
 *
 * Uses applicationContext so the Toast queue isn't tied to the activity
 * that triggered it (it's still the user's request, this just keeps the
 * Toast alive across the activity transition).
 */
private fun showSystemToast(context: Context, text: String) {
    Toast.makeText(context.applicationContext, text, Toast.LENGTH_LONG).show()
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
            },
            clipboardWriter = { text -> writeToClipboard(context, text) },
            systemToast = { text -> showSystemToast(context, text) }
        )
    }
}

@Composable
private fun rememberBalanceViewModel(app: OffPayApplication): BalanceViewModel {
    val context = LocalContext.current
    return remember {
        BalanceViewModel(
            actionRunner = app.actionRunner,
            prefsRepo = app.prefsRepo,
            overlayController = app.overlayController,
            onDialerFallback = { code ->
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$code"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            clipboardWriter = { text -> writeToClipboard(context, text) },
            systemToast = { text -> showSystemToast(context, text) }
        )
    }
}

@Composable
private fun rememberHistoryViewModel(app: OffPayApplication): HistoryViewModel {
    return remember { HistoryViewModel(app.historyRepo) }
}
