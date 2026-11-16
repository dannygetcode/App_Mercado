package co.daniel.mercado.ui.nav

import kotlinx.serialization.Serializable

/** Rutas tipadas (D-12). Las cuatro primeras llevan barra inferior. */
@Serializable
data object HomeRoute

/** [needsReviewOnly] abre el catálogo filtrado en "Por revisar" (tarjeta del Inicio). */
@Serializable
data class CatalogRoute(val needsReviewOnly: Boolean = false)

@Serializable
data object HistoryRoute

@Serializable
data object SettingsRoute

/** `productId == 0` crea un producto nuevo. */
@Serializable
data class ProductEditRoute(val productId: Long = 0)

@Serializable
data class TripDetailRoute(val tripId: Long)

@Serializable
data object ShoppingListRoute

@Serializable
data object TripRoute

@Serializable
data object CloseTripRoute

/** Solo debug. */
@Serializable
data object DiagnosticsRoute
