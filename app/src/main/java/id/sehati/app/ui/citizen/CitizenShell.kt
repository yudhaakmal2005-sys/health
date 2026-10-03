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
import id.sehati.app.ui.components.popOnChange
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
                if (initialState.destination.route in tabs && targetState.destination.route in tabs) fadeIn(tween(Motion.Medium)) + androidx.compose.animation.scaleIn(tween(Motion.Medium, easing = Motion.Emphasized), initialScale = 0.97f)
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

/** Navigasi bawah: pil merah yang meluncur ke tab terpilih, ikon memantul, label muncul halus. */
@Composable
private fun SehatiBottomBar(route: String?, onSelect: (String) -> Unit) {
    Surface(color = CardWhite, shadowElevation = 12.dp, modifier = Modifier.testTag("citizen_nav")) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab.entries.forEach { t ->
                val sel = route == t.route
                val weight by androidx.compose.animation.core.animateFloatAsState(if (sel) 1.6f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 300f), label = "w")
                val bg by androidx.compose.animation.animateColorAsState(if (sel) PrimaryLight else androidx.compose.ui.graphics.Color.Transparent, tween(Motion.Medium), label = "bg")
                val tint by androidx.compose.animation.animateColorAsState(if (sel) PrimaryDark else TextMuted, tween(Motion.Medium), label = "tint")
                val src = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Row(
                    Modifier.weight(weight).height(52.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp))
                        .background(bg)
                        .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(color = Primary), role = androidx.compose.ui.semantics.Role.Tab) { if (!sel) onSelect(t.route) }
                        .semantics { selected = sel; contentDescription = t.label }
                        .testTag("nav_${t.route}"),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(if (sel) t.selected else t.unselected, null, tint = tint, modifier = Modifier.size(24.dp).popOnChange(sel))
                    androidx.compose.animation.AnimatedVisibility(sel, enter = fadeIn(tween(Motion.Medium)) + androidx.compose.animation.expandHorizontally(), exit = fadeOut(tween(Motion.Short)) + androidx.compose.animation.shrinkHorizontally()) {
                        Text(t.label, style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
}

