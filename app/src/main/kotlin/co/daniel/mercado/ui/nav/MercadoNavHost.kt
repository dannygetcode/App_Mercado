package co.daniel.mercado.ui.nav

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import co.daniel.mercado.BuildConfig
import co.daniel.mercado.R
import co.daniel.mercado.di.AppContainer
import co.daniel.mercado.domain.usecase.TripLimit
import co.daniel.mercado.scanner.SharedBarcodeSource
import co.daniel.mercado.ui.close.CloseTripScreen
import co.daniel.mercado.ui.close.CloseTripViewModel
import co.daniel.mercado.ui.diagnostics.ScanDiagnosticsScreen
import co.daniel.mercado.ui.home.HomeScreen
import co.daniel.mercado.ui.home.HomeViewModel
import co.daniel.mercado.ui.trip.TripScreen
import co.daniel.mercado.ui.trip.TripViewModel
import kotlin.reflect.KClass

/** Lo que pide el arranque: nada, abrir la compra (acceso directo) o el diagnóstico (solo debug). */
enum class LaunchAction { NONE, NEW_TRIP, DIAGNOSTICS }

private enum class TopLevel(val route: Any, val routeClass: KClass<*>, @param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    HOME(HomeRoute, HomeRoute::class, R.string.nav_home, R.drawable.ic_home),
    CATALOG(CatalogRoute(), CatalogRoute::class, R.string.nav_catalog, R.drawable.ic_catalog),
    HISTORY(HistoryRoute, HistoryRoute::class, R.string.nav_history, R.drawable.ic_history),
    SETTINGS(SettingsRoute, SettingsRoute::class, R.string.nav_settings, R.drawable.ic_settings),
}

private fun NavDestination?.isTopLevel(): Boolean =
    this != null && TopLevel.entries.any { top -> hierarchy.any { it.hasRoute(top.routeClass) } }

/**
 * Navegación de la app (04-ux, mapa). Si hay una compra abierta al arrancar, abre directo en
 * "Compra en curso". [launchAction] lo consume una vez y avisa con [onLaunchActionHandled].
 */
@Composable
fun MercadoNavHost(
    container: AppContainer,
    launchAction: LaunchAction,
    onLaunchActionHandled: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    var checkedOpenTrip by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (checkedOpenTrip) return@LaunchedEffect
        checkedOpenTrip = true
        if (launchAction == LaunchAction.NONE && container.data().trips.getActive() != null) {
            navController.navigate(TripRoute)
        }
    }
    LaunchedEffect(launchAction) {
        when (launchAction) {
            LaunchAction.NONE -> return@LaunchedEffect
            LaunchAction.NEW_TRIP -> {
                // Abierta o no, termina en la compra; si no había, empieza con el límite por defecto.
                container.data().startTrip(TripLimit.Default)
                navController.navigate(TripRoute) { popUpTo(HomeRoute) }
            }
            LaunchAction.DIAGNOSTICS -> if (BuildConfig.DEBUG) navController.navigate(DiagnosticsRoute)
        }
        onLaunchActionHandled()
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        bottomBar = {
            if (destination.isTopLevel()) {
                NavigationBar {
                    TopLevel.entries.forEach { top ->
                        NavigationBarItem(
                            selected = destination?.hierarchy?.any { it.hasRoute(top.routeClass) } == true,
                            onClick = {
                                navController.navigate(top.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(painterResource(top.icon), contentDescription = null) },
                            label = { Text(stringResource(top.label)) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(inner).consumeWindowInsets(inner),
        ) {
            composable<HomeRoute> {
                val home: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
                val homeState by home.state.collectAsStateWithLifecycle()
                HomeScreen(
                    state = homeState,
                    onStartOrContinue = {
                        if (homeState.openTripTotal != null) navController.navigate(TripRoute) else home.openLimitSheet()
                    },
                    onStartTrip = { limit -> home.startTrip(limit) { navController.navigate(TripRoute) } },
                    onDismissLimit = home::dismissLimitSheet,
                    onOpenDiagnostics = if (BuildConfig.DEBUG) {
                        { navController.navigate(DiagnosticsRoute) }
                    } else {
                        null
                    },
                )
            }
            composable<CatalogRoute> { PendingScreen(R.string.nav_catalog) }
            composable<HistoryRoute> { PendingScreen(R.string.nav_history) }
            composable<SettingsRoute> { PendingScreen(R.string.nav_settings) }
            composable<TripRoute> {
                val trip: TripViewModel = viewModel(factory = TripViewModel.Factory)
                TripScreen(
                    viewModel = trip,
                    cameraSource = container.tripBarcodeSource,
                    decoderFactory = container::newBarcodeDecoder,
                    onBack = { navController.backToHome() },
                    onClose = {
                        trip.dismissSheet()
                        navController.navigate(CloseTripRoute)
                    },
                    onNoActiveTrip = { navController.backToHome() },
                )
            }
            composable<CloseTripRoute> {
                val close: CloseTripViewModel = viewModel(factory = CloseTripViewModel.Factory)
                CloseTripScreen(
                    viewModel = close,
                    onBackToTrip = { navController.popBackStack() },
                    onDone = { navController.backToHome() },
                )
            }
            if (BuildConfig.DEBUG) {
                composable<DiagnosticsRoute> {
                    val source = remember { SharedBarcodeSource() }
                    ScanDiagnosticsScreen(
                        source = source,
                        decoderFactory = container::newBarcodeDecoder,
                        feedback = container.scanFeedback,
                        clock = container.clock,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}

/** Vuelve al Inicio quitando de la pila la compra y el cierre. */
fun NavHostController.backToHome() {
    if (!popBackStack(HomeRoute, inclusive = false)) {
        navigate(HomeRoute) { popUpTo(graph.id) { inclusive = true } }
    }
}

@Composable
private fun PendingScreen(@StringRes title: Int) {
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
    }
}
