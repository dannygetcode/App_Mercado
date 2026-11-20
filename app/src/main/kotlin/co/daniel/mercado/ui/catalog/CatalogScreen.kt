package co.daniel.mercado.ui.catalog

import co.daniel.mercado.ui.components.ScreenTitle
import co.daniel.mercado.ui.components.SearchField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.daniel.mercado.R
import co.daniel.mercado.domain.model.CatalogFilter
import co.daniel.mercado.domain.model.Category
import co.daniel.mercado.domain.model.Pesos
import co.daniel.mercado.domain.model.Product
import co.daniel.mercado.domain.model.UnitType
import co.daniel.mercado.domain.rules.MoneyFormat
import co.daniel.mercado.ui.theme.MercadoTheme
import co.daniel.mercado.ui.theme.MoneyTextStyle

/** Catálogo (04-ux): buscador arriba, filtros en chips, lista y "Nuevo producto" en su franja de abajo. */
@Composable
fun CatalogScreen(
    state: CatalogUiState,
    onEvent: (CatalogEvent) -> Unit,
    onOpenProduct: (Long) -> Unit,
    onNewProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.statusBarsPadding()) {
            ScreenTitle(stringResource(R.string.nav_catalog))
            SearchField(
                value = state.filter.query,
                onValueChange = { onEvent(CatalogEvent.Query(it)) },
                placeholder = stringResource(R.string.catalog_search_hint),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            FilterRow(state, onEvent)
            HorizontalDivider()
            Box(Modifier.weight(1f)) {
                if (!state.loading && state.rows.isEmpty()) {
                    Text(
                        text = stringResource(if (state.isFiltered) R.string.catalog_no_results else R.string.catalog_empty),
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(state.rows, key = { it.product.id }) { row ->
                        CatalogRowItem(
                            row = row,
                            ordering = state.filter.favoritesOnly,
                            isFirst = row == state.rows.first(),
                            isLast = row == state.rows.last(),
                            onClick = { onOpenProduct(row.product.id) },
                            onMove = { up -> onEvent(CatalogEvent.MoveFavorite(row.product.id, up)) },
                        )
                    }
                }
            }
            // Franja propia para "Nuevo producto": no flota sobre la lista ni tapa productos.
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                FilledTonalButton(
                    onClick = onNewProduct,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).height(56.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.catalog_new_product), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun FilterRow(state: CatalogUiState, onEvent: (CatalogEvent) -> Unit) {
    LazyRow(
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = state.filter.favoritesOnly,
                onClick = { onEvent(CatalogEvent.ToggleFavorites) },
                label = { Text(stringResource(R.string.catalog_filter_favorites)) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
        if (state.needsReviewCount > 0 || state.filter.needsReviewOnly) {
            item {
                FilterChip(
                    selected = state.filter.needsReviewOnly,
                    onClick = { onEvent(CatalogEvent.ToggleNeedsReview) },
                    label = { Text(stringResource(R.string.catalog_filter_review, state.needsReviewCount)) },
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
        items(state.categories, key = { it.id }) { category ->
            FilterChip(
                selected = state.filter.categoryId == category.id,
                onClick = { onEvent(CatalogEvent.SelectCategory(category.id)) },
                label = { Text(category.name) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun CatalogRowItem(
    row: CatalogRow,
    ordering: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    onMove: (up: Boolean) -> Unit,
) {
    val product = row.product
    val price = MoneyFormat.format(product.refPrice)
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(product.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                text = listOfNotNull(
                    row.categoryName,
                    if (product.unitType == UnitType.KG) stringResource(R.string.catalog_price_kg, price) else price,
                    stringResource(R.string.catalog_needs_review).takeIf { product.needsReview },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.merge(MoneyTextStyle),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (ordering) {
                Row {
                    IconButton(onClick = { onMove(true) }, enabled = !isFirst, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.catalog_move_up, product.name))
                    }
                    IconButton(onClick = { onMove(false) }, enabled = !isLast, modifier = Modifier.size(48.dp)) {
                        Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.catalog_move_down, product.name))
                    }
                }
            } else if (product.favorite) {
                Icon(
                    painter = painterResource(R.drawable.ic_star),
                    contentDescription = stringResource(R.string.favorite),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        },
    )
}

@Preview
@Composable
private fun CatalogScreenPreview() {
    MercadoTheme {
        CatalogScreen(
            CatalogUiState(
                loading = false,
                categories = listOf(Category(1, "Lácteos"), Category(2, "Frutas y verduras")),
                rows = listOf(
                    CatalogRow(Product(1, "Arroz Diana 1 kg", 1, refPrice = Pesos(5_900), favorite = true), "Granos y despensa"),
                    CatalogRow(Product(2, "Limón Tahití", 2, UnitType.KG, Pesos(3_000)), "Frutas y verduras"),
                    CatalogRow(Product(3, "Producto ····7897", 2, refPrice = Pesos(2_000), needsReview = true), "Otros"),
                ),
                needsReviewCount = 1,
            ),
            {}, {}, {},
        )
    }
}

@Preview
@Composable
private fun CatalogFavoritesPreview() {
    MercadoTheme(darkTheme = true) {
        CatalogScreen(
            CatalogUiState(
                loading = false,
                filter = CatalogFilter(favoritesOnly = true),
                rows = listOf(
                    CatalogRow(Product(1, "Arroz Diana 1 kg", 1, refPrice = Pesos(5_900), favorite = true), "Granos y despensa"),
                    CatalogRow(Product(2, "Leche entera", 1, refPrice = Pesos(4_300), favorite = true), "Lácteos"),
                ),
            ),
            {}, {}, {},
        )
    }
}
