@file:Suppress("FunctionName")

package os.kei.ui.page.main.student.catalog.page

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogRouteState
import os.kei.ui.page.main.student.catalog.state.BaGuideCatalogViewModel

@Composable
internal fun collectBaGuideCatalogRouteState(catalogViewModel: BaGuideCatalogViewModel): BaGuideCatalogRouteState {
    val routeState by catalogViewModel.routeState.collectAsStateWithLifecycle()
    return routeState
}
