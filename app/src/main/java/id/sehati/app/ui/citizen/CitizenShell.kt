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
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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
    Home("home", "Beranda", Icons.Outlined.Home, Icons.Outlined.Home),
    Move("move", "Aktivitas", Icons.Outlined.DirectionsRun, Icons.Outlined.DirectionsRun),
    Food("food", "Makanan", Icons.Outlined.Restaurant, Icons.Outlined.Restaurant),
    Health("health", "Kesehatan", Icons.Outlined.Favorite, Icons.Outlined.MonitorHeart),
    Profile("profile", "Profil", Icons.Outlined.Person, Icons.Outlined.Person),
}

/** Navigasi warga: 5 tab. Fitur sekunder (Akademi, Pelatih, QR, Notifikasi) berada di dalam halaman terkait. */
@Composable
fun CitizenShell(onLogout: () -> Unit, onRetakeAssessment: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = Tab.entries.any { it.route == route }

    fun open(r: String) = nav.navigate(r) { launchSingleTop = true }
    val deepLink by id.sehati.app.ui.app.DeepLinks.route.collectAsStateWithLifecycle()
    LaunchedEffect(deepLink, entry) {
        val r = deepLink ?: return@LaunchedEffect
        if (entry == null) return@LaunchedEffect
        id.sehati.app.ui.app.DeepLinks.consume()
        if (Tab.entries.any { it.route == r }) nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
        else runCatching { nav.navigate(r) { launchSingleTop = true } }
    }
    fun tab(r: String) = nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }

    Scaffold(
        containerColor = Background,
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            androidx.compose.animation.AnimatedVisibility(showBar,
                enter = androidx.compose.animation.slideInVertically { it } + fadeIn(),
                exit = androidx.compose.animation.slideOutVertically { it } + fadeOut()) {
                SehatiBottomBar(route) { tab(it) }
            }
        },
    ) { pad ->
        NavHost(
            nav, startDestination = "home", modifier = Modifier.padding(pad),
            enterTransition = {
                val tabs = Tab.entries.map { it.route }
                if (initialState.destination.route in tabs && targetState.destination.route in tabs) fadeIn(tween(Motion.Medium))
                else fadeIn(tween(Motion.Medium)) + slideInHorizontally(tween(420, easing = Motion.Emphasized)) { it / 4 }
            },
            exitTransition = { fadeOut(tween(Motion.Short)) + slideOutHorizontally(tween(420, easing = Motion.Emphasized)) { -it / 10 } },
            popEnterTransition = { fadeIn(tween(Motion.Medium)) + slideInHorizontally(tween(420, easing = Motion.Emphasized)) { -it / 10 } },
            popExitTransition = { fadeOut(tween(Motion.Short)) + slideOutHorizontally(tween(420, easing = Motion.Emphasized)) { it / 4 } },
        ) {
            composable("home") { HomeScreen(onOpenHealth = { tab("health") }, onOpenMove = { tab("move") }, onOpenFood = { tab("food") }, onOpenAcademy = { id -> open(if (id == null) "academy" else "academy/$id") }, onOpenCoach = { open("coach") }, onOpenRisk = { open("heart") }, onOpenChallenges = { open("challenges") },
                onEmergency = { open("emergency") }, onOpenMeds = { open("medications") }, onBreath = { open("breathing") }, onReminders = { open("reminders") }) }
            composable("move") { MoveScreen() }
            composable("food") { FoodScreen() }
            composable("health") { HealthScreen(onShowQr = { open("qr") }, onOpenAcademy = { id -> open(if (id == null) "academy" else "academy/$id") }, onRetakeAssessment = onRetakeAssessment) }
            composable("profile") { ProfileScreen(onShowQr = { open("qr") }, onLogout = onLogout, onOpenNotifications = { open("notifications") }, onOpenReminders = { open("reminders") }, onOpenMeds = { open("medications") }) }
            composable("qr") { QrScreen(onBack = { nav.popBackStack() }) }
            composable("academy") { AcademyScreen(onBack = { nav.popBackStack() }, onOpen = { open("academy/$it") }) }
            composable("academy/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                AcademyDetailScreen(e.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
            }
            composable("heart") { HeartRiskScreen(onBack = { nav.popBackStack() }, onOpenAcademy = { open("academy/$it") }) }
            composable("challenges") { ChallengesScreen(onBack = { nav.popBackStack() }, onOpenAcademy = { open("academy/$it") }) }
            composable("coach") { CoachScreen(onBack = { nav.popBackStack() }, onOpenAcademy = { open("academy/$it") }, onEmergency = { open("emergency") }) }
            composable("emergency") { EmergencyScreen(onBack = { nav.popBackStack() }) }
            composable("breathing") { BreathingScreen(onBack = { nav.popBackStack() }) }
            composable("medications") { MedicationScreen(onBack = { nav.popBackStack() }, onOpenReminders = { open("reminders") }) }
            composable("reminders") { ReminderSettingsScreen(onBack = { nav.popBackStack() }, onOpenMedications = { open("medications") }) }
            composable("notifications") { NotificationsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

/** Navigasi bawah: garis rambut di atas, ikon garis + label selalu tampil, garis aksen tipis meluncur ke tab aktif. */
@Composable
private fun SehatiBottomBar(route: String?, onSelect: (String) -> Unit) {
    val tabs = Tab.entries
    val selIndex = tabs.indexOfFirst { it.route == route }.coerceAtLeast(0)
    Surface(color = CardWhite, modifier = Modifier.testTag("citizen_nav")) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            HorizontalDivider(color = Hairline, thickness = 1.dp)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val slot = maxWidth / tabs.size
                val x by androidx.compose.animation.core.animateDpAsState(slot * selIndex + (slot - 28.dp) / 2, motionTween(Motion.Medium), label = "ind")
                Box(Modifier.offset(x = x).width(28.dp).height(2.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(1.dp)).background(Primary))
                Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEach { t ->
                        val sel = route == t.route
                        val tint by androidx.compose.animation.animateColorAsState(if (sel) Primary else TextMuted, tween(Motion.Medium), label = "tint")
                        Column(
                            Modifier.weight(1f).heightIn(min = 52.dp)
                                .clickable(role = androidx.compose.ui.semantics.Role.Tab) { if (!sel) onSelect(t.route) }
                                .semantics { selected = sel; contentDescription = t.label }
                                .testTag("nav_${t.route}"),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            androidx.compose.material3.Icon(t.unselected, null, tint = tint, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(t.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}
