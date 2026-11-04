package fr.mangatheque.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import fr.mangatheque.app.ui.theme.extra
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.mangatheque.app.ui.screens.AddVolumeScreen
import fr.mangatheque.app.ui.screens.CoverCameraScreen
import fr.mangatheque.app.ui.screens.HomeScreen
import fr.mangatheque.app.ui.screens.MomentsScreen
import fr.mangatheque.app.ui.screens.ScanScreen
import fr.mangatheque.app.ui.screens.SeriesEditScreen
import fr.mangatheque.app.ui.screens.SeriesScreen
import fr.mangatheque.app.ui.screens.SettingsScreen
import fr.mangatheque.app.ui.screens.StoreScreen
import fr.mangatheque.app.ui.screens.UpdateDialog
import fr.mangatheque.app.ui.screens.VolumeScreen
import fr.mangatheque.app.ui.theme.MangathequeTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = when (vm.themeMode) {
                "clair" -> false
                "sombre" -> true
                else -> isSystemInDarkTheme()
            }
            // Icônes de la barre d'état lisibles quel que soit le thème choisi.
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            MangathequeTheme(darkTheme = dark) {
                MangaNav(vm)
            }
        }
        vm.checkUpdates(manual = false)
    }
}

object Routes {
    const val HOME = "home"
    const val STORE = "store"
    const val MOMENTS = "moments"
    const val SETTINGS = "settings"
    const val SCAN = "scan?mode={mode}"
    const val ADD = "add"
    const val SERIES = "series/{id}"
    const val SERIES_EDIT = "series/{id}/edit"
    const val VOLUME = "volume/{id}"
    const val COVER = "cover/{target}"

    fun scan(mode: String = "add") = "scan?mode=$mode"
    fun series(id: Long) = "series/$id"
    fun seriesEdit(id: Long) = "series/$id/edit"
    fun volume(id: Long) = "volume/$id"
    fun cover(target: String) = "cover/$target"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Collection", Icons.Outlined.AutoStories, Icons.Rounded.AutoStories),
    Tab(Routes.STORE, "En magasin", Icons.Outlined.Storefront, Icons.Rounded.Storefront),
    Tab(Routes.MOMENTS, "Passages", Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite),
    Tab(Routes.SETTINGS, "Réglages", Icons.Outlined.Settings, Icons.Rounded.Settings)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MangaNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val showBar = current in tabs.map { it.route }
    val snackbar = remember { SnackbarHostState() }
    val update by vm.update.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBar) NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                tabs.forEach { t ->
                    val selected = current == t.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(if (selected) t.iconSelected else t.icon, t.label) },
                        label = { Text(t.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.extra.onPop,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.extra.pop,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { padding ->
        // consumeWindowInsets : évite que chaque écran rajoute une 2e marge en haut (espace vide inutile).
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            NavHost(nav, startDestination = Routes.HOME) {
                composable(Routes.HOME) {
                    HomeScreen(vm,
                        onOpenSeries = { nav.navigate(Routes.series(it)) },
                        onOpenVolume = { nav.navigate(Routes.volume(it)) },
                        onScan = { nav.navigate(Routes.scan("add")) },
                        onAddManual = { vm.startManualDraft(null); nav.navigate(Routes.ADD) },
                        onNewSeries = { nav.navigate(Routes.seriesEdit(0)) },
                        onOpenSettings = {
                            nav.navigate(Routes.SETTINGS) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        })
                }
                composable(Routes.STORE) {
                    StoreScreen(vm, onOpenSeries = { nav.navigate(Routes.series(it)) }, onScan = { nav.navigate(Routes.scan("check")) })
                }
                composable(Routes.MOMENTS) {
                    MomentsScreen(vm, onOpenVolume = { nav.navigate(Routes.volume(it)) })
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(vm)
                }
                composable(Routes.SCAN, arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "add" })) { entry ->
                    val mode = entry.arguments?.getString("mode") ?: "add"
                    ScanScreen(vm, mode = mode,
                        onBack = { nav.popBackStack() },
                        onIsbn = { isbn ->
                            vm.startDraft(isbn)
                            nav.navigate(Routes.ADD) { popUpTo(Routes.SCAN) { inclusive = true } }
                        },
                        onOpenSeries = { id -> nav.navigate(Routes.series(id)) })
                }
                composable(Routes.ADD) {
                    AddVolumeScreen(vm,
                        onCancel = { vm.cancelDraft(); nav.popBackStack() },
                        onSaved = { id -> nav.navigate(Routes.volume(id)) { popUpTo(Routes.ADD) { inclusive = true } } },
                        onSavedScanNext = { nav.navigate(Routes.scan("add")) { popUpTo(Routes.ADD) { inclusive = true } } },
                        onTakeCover = { nav.navigate(Routes.cover("draft")) },
                        onScanAgain = { nav.navigate(Routes.scan("add")) { popUpTo(Routes.ADD) { inclusive = true } } })
                }
                composable(Routes.SERIES, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    SeriesScreen(vm, id,
                        onBack = { nav.popBackStack() },
                        onOpenVolume = { nav.navigate(Routes.volume(it)) },
                        onEdit = { nav.navigate(Routes.seriesEdit(id)) },
                        onAddVolume = { vm.startManualDraft(id); nav.navigate(Routes.ADD) },
                        onScan = { nav.navigate(Routes.scan("add")) },
                        onTakeCover = { nav.navigate(Routes.cover("series:$id")) },
                        onGenre = { genre ->
                            // Retour à la collection, filtrée sur ce genre uniquement.
                            vm.query = ""; vm.readFilter = null; vm.ongoingFilter = null
                            vm.genreFilter = genre
                            nav.navigate(Routes.HOME) {
                                popUpTo(Routes.HOME) { inclusive = false }
                                launchSingleTop = true
                            }
                        })
                }
                composable(Routes.SERIES_EDIT, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    SeriesEditScreen(vm, id,
                        onBack = { nav.popBackStack() },
                        onSaved = { newId ->
                            if (id == 0L) nav.navigate(Routes.series(newId)) { popUpTo(Routes.HOME) } else nav.popBackStack()
                        })
                }
                composable(Routes.VOLUME, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    VolumeScreen(vm, id,
                        onBack = { nav.popBackStack() },
                        onOpenSeries = { sid -> nav.navigate(Routes.series(sid)) { popUpTo(Routes.HOME) } },
                        onOpenVolume = { vid -> nav.navigate(Routes.volume(vid)) { popUpTo(Routes.VOLUME) { inclusive = true } } },
                        onTakeCover = { nav.navigate(Routes.cover("volume:$id")) })
                }
                composable(Routes.COVER, arguments = listOf(navArgument("target") { type = NavType.StringType })) { entry ->
                    val target = entry.arguments?.getString("target") ?: "draft"
                    CoverCameraScreen(
                        onBack = { nav.popBackStack() },
                        onCaptured = { file -> vm.applyCover(target, file); nav.popBackStack() })
                }
            }
            if (update is UpdateState.Available && !vm.updateDialogDismissed || update is UpdateState.Downloading || update is UpdateState.Downloaded) {
                UpdateDialog(vm)
            }
        }
    }
}
