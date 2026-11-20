package com.aliasadi.tamdid.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.aliasadi.tamdid.R
import com.aliasadi.tamdid.core.ServiceCatalog
import com.aliasadi.tamdid.data.Clock
import com.aliasadi.tamdid.data.TamdidRepository
import com.aliasadi.tamdid.ui.screens.CalendarScreen
import com.aliasadi.tamdid.ui.screens.CalendarViewModel
import com.aliasadi.tamdid.ui.screens.CatalogScreen
import com.aliasadi.tamdid.ui.screens.DetailScreen
import com.aliasadi.tamdid.ui.screens.DetailViewModel
import com.aliasadi.tamdid.ui.screens.EditScreen
import com.aliasadi.tamdid.ui.screens.EditViewModel
import com.aliasadi.tamdid.ui.screens.HomeScreen
import com.aliasadi.tamdid.ui.screens.HomeViewModel
import com.aliasadi.tamdid.ui.screens.InsightsScreen
import com.aliasadi.tamdid.ui.screens.InsightsViewModel
import com.aliasadi.tamdid.ui.screens.SettingsScreen
import com.aliasadi.tamdid.ui.screens.SettingsViewModel
import com.aliasadi.tamdid.ui.screens.SubscriptionsScreen
import com.aliasadi.tamdid.ui.screens.SubscriptionsViewModel
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** Type-safe navigation destinations. */
object Routes {
    @Serializable data object Home
    @Serializable data object Subscriptions
    @Serializable data object Calendar
    @Serializable data object Insights
    @Serializable data object Settings
    @Serializable data object Catalog
    @Serializable data class Detail(val id: String)
    @Serializable data class Edit(val id: String? = null, val catalogId: String? = null)
}

private data class Tab(val route: Any, val routeClass: KClass<*>, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.Home, Routes.Home::class, R.string.tab_home, Icons.Rounded.Home),
    Tab(Routes.Subscriptions, Routes.Subscriptions::class, R.string.tab_subscriptions, Icons.AutoMirrored.Rounded.ViewList),
    Tab(Routes.Calendar, Routes.Calendar::class, R.string.tab_calendar, Icons.Rounded.CalendarMonth),
    Tab(Routes.Insights, Routes.Insights::class, R.string.tab_insights, Icons.Rounded.Insights),
    Tab(Routes.Settings, Routes.Settings::class, R.string.tab_settings, Icons.Rounded.Settings),
)

/** Everything the screens need, so tests can pass fakes. */
class AppDependencies(val repository: TamdidRepository, val clock: Clock, val catalog: () -> ServiceCatalog)

@Composable
fun TamdidApp(deps: AppDependencies, navController: NavHostController = rememberNavController()) {
    val language = currentLanguage()
    NavHost(
        navController = navController,
        startDestination = Routes.Home,
        modifier = Modifier.fillMaxSize(),
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() },
    ) {
        composable<Routes.Home> {
            MainTabs(navController, Tab(Routes.Home, Routes.Home::class, R.string.app_name, Icons.Rounded.Home)) { padding ->
                HomeScreen(viewModel { HomeViewModel(deps.repository, deps.clock) }, padding,
                    onAdd = { navController.navigate(Routes.Catalog) }, onOpen = { navController.navigate(Routes.Detail(it)) })
            }
        }
        composable<Routes.Subscriptions> {
            MainTabs(navController, tabs[1]) { padding ->
                SubscriptionsScreen(viewModel { SubscriptionsViewModel(deps.repository, deps.clock) }, padding) { navController.navigate(Routes.Detail(it)) }
            }
        }
        composable<Routes.Calendar> {
            MainTabs(navController, tabs[2]) { padding ->
                CalendarScreen(viewModel { CalendarViewModel(deps.repository, deps.clock) }, padding) { navController.navigate(Routes.Detail(it)) }
            }
        }
        composable<Routes.Insights> {
            MainTabs(navController, tabs[3]) { padding ->
                InsightsScreen(viewModel { InsightsViewModel(deps.repository, deps.clock) }, padding) { navController.navigate(Routes.Detail(it)) }
            }
        }
        composable<Routes.Settings> {
            MainTabs(navController, tabs[4], showFab = false) { padding ->
                SettingsScreen(viewModel { SettingsViewModel(deps.repository, deps.clock) }, padding)
            }
        }
        composable<Routes.Catalog> {
            CatalogScreen(
                catalog = deps.catalog(),
                onClose = { navController.popBackStack() },
                onPick = { id ->
                    navController.navigate(Routes.Edit(catalogId = id)) { popUpTo<Routes.Catalog> { inclusive = true } }
                },
            )
        }
        composable<Routes.Detail> { entry ->
            val route = entry.toRoute<Routes.Detail>()
            DetailScreen(
                viewModel(key = route.id) { DetailViewModel(route.id, deps.repository, deps.clock) },
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.Edit(id = route.id)) },
            )
        }
        composable<Routes.Edit> { entry ->
            val route = entry.toRoute<Routes.Edit>()
            EditScreen(
                viewModel(key = "${route.id}-${route.catalogId}") {
                    EditViewModel(route.id, route.catalogId, deps.repository, deps.clock, runCatching { deps.catalog() }.getOrNull(), language)
                },
                onClose = { navController.popBackStack() },
                onSaved = { id ->
                    if (route.id == null) {
                        navController.navigate(Routes.Detail(id)) { popUpTo<Routes.Edit> { inclusive = true } }
                    } else {
                        navController.popBackStack()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTabs(
    navController: NavHostController,
    current: Tab,
    showFab: Boolean = true,
    content: @Composable (PaddingValues) -> Unit,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(current.label)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(onClick = { navController.navigate(Routes.Catalog) }, modifier = Modifier.testTag("fab_add")) {
                    Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        },
        bottomBar = {
            NavigationBar {
                for (tab in tabs) {
                    NavigationBarItem(
                        selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(if (tab.label == R.string.tab_subscriptions) R.string.tab_subscriptions_short else tab.label), maxLines = 1) },
                    )
                }
            }
        },
        content = content,
    )
}
