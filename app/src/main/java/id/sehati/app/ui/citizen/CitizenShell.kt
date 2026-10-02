package id.sehati.app.ui.citizen

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import id.sehati.app.ui.theme.*

private enum class Tab(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector) {
    Home("home", "Beranda", Icons.Rounded.Home, Icons.Outlined.Home),
    Move("move", "Aktivitas", Icons.Rounded.DirectionsRun, Icons.Outlined.DirectionsRun),
    Food("food", "Makanan", Icons.Rounded.Restaurant, Icons.Outlined.Restaurant),
    Health("health", "Kesehatan", Icons.Rounded.Favorite, Icons.Outlined.FavoriteBorder),
    Profile("profile", "Profil", Icons.Rounded.Person, Icons.Outlined.Person),
}

/** Navigasi warga: 5 tab. Fitur sekunder (Akademi, Pelatih, QR, Notifikasi) berada di dalam halaman terkait. */
@Composable
fun CitizenShell(onLogout: () -> Unit, onRetakeAssessment: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    fun open(r: String) = nav.navigate(r) { launchSingleTop = true }
    fun tab(r: String) = nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }

    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            if (showBar) NavigationBar(containerColor = CardWhite, modifier = Modifier.testTag("citizen_nav")) {
                Tab.entries.forEach { t ->
                    val sel = route == t.route
                    NavigationBarItem(
                        selected = sel, onClick = { if (!sel) tab(t.route) },
                        icon = { androidx.compose.material3.Icon(if (sel) t.selected else t.unselected, contentDescription = null) },
                        label = { Text(t.label) }, modifier = Modifier.testTag("nav_${t.route}"),
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = PrimaryDark, selectedTextColor = PrimaryDark, indicatorColor = PrimaryLight, unselectedIconColor = TextMuted, unselectedTextColor = TextMuted),
                    )
                }
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = "home", modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(Motion.Medium, 60)) + slideInHorizontally(tween(Motion.Medium, easing = Motion.Emphasized)) { it / 12 } },
            exitTransition = { fadeOut(tween(Motion.Short)) },
            popEnterTransition = { fadeIn(tween(Motion.Medium, 60)) },
            popExitTransition = { fadeOut(tween(Motion.Short)) + slideOutHorizontally(tween(Motion.Medium)) { it / 12 } },
        ) {
            composable("home") { HomeScreen(onOpenHealth = { tab("health") }, onOpenMove = { tab("move") }, onOpenFood = { tab("food") }, onOpenAcademy = { id -> open(if (id == null) "academy" else "academy/$id") }, onOpenCoach = { open("coach") }) }
            composable("move") { MoveScreen() }
            composable("food") { FoodScreen() }
            composable("health") { HealthScreen(onShowQr = { open("qr") }, onOpenAcademy = { id -> open(if (id == null) "academy" else "academy/$id") }, onRetakeAssessment = onRetakeAssessment) }
            composable("profile") { ProfileScreen(onShowQr = { open("qr") }, onLogout = onLogout, onOpenNotifications = { open("notifications") }) }
            composable("qr") { QrScreen(onBack = { nav.popBackStack() }) }
            composable("academy") { AcademyScreen(onBack = { nav.popBackStack() }, onOpen = { open("academy/$it") }) }
            composable("academy/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                AcademyDetailScreen(e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("coach") { CoachScreen(onBack = { nav.popBackStack() }) }
            composable("notifications") { NotificationsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
