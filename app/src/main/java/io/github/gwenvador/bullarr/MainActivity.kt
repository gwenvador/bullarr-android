package io.github.gwenvador.bullarr

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.automirrored.outlined.Login
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import io.github.gwenvador.bullarr.ui.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.gwenvador.bullarr.ui.ActivityScreen
import io.github.gwenvador.bullarr.ui.AppViewModel
import io.github.gwenvador.bullarr.ui.ConfigScreen
import io.github.gwenvador.bullarr.ui.WebSettingsScreen
import io.github.gwenvador.bullarr.data.CLIENTS
import io.github.gwenvador.bullarr.data.IMPORTS
import io.github.gwenvador.bullarr.data.ACCESS
import io.github.gwenvador.bullarr.data.INDEXERS
import io.github.gwenvador.bullarr.data.MONITOR
import io.github.gwenvador.bullarr.data.NOTIFICATIONS
import io.github.gwenvador.bullarr.data.SEARCH
import io.github.gwenvador.bullarr.ui.BullarrTheme
import io.github.gwenvador.bullarr.ui.DownloadDialogs
import io.github.gwenvador.bullarr.ui.EnrichScreen
import io.github.gwenvador.bullarr.ui.HistoryScreen
import io.github.gwenvador.bullarr.ui.LibraryScreen
import io.github.gwenvador.bullarr.ui.LoginScreen
import io.github.gwenvador.bullarr.ui.MonitorScreen
import io.github.gwenvador.bullarr.ui.NewsScreen
import io.github.gwenvador.bullarr.ui.OfflineScreen
import io.github.gwenvador.bullarr.ui.ReaderScreen
import io.github.gwenvador.bullarr.ui.SearchScreen
import io.github.gwenvador.bullarr.ui.Section
import io.github.gwenvador.bullarr.ui.SeriesScreen
import io.github.gwenvador.bullarr.ui.VerificationScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Android 13+ : sans cette autorisation, la notification de téléchargement reste masquée.
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        io.github.gwenvador.bullarr.ui.ThemePref.init(this)
        setContent { BullarrTheme { BullarrApp() } }
        handleAuthIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    /** Retour du SSO par navigateur : bullarr://auth?code=… */
    private fun handleAuthIntent(intent: android.content.Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "bullarr" || data.host != "auth") return
        val vm = androidx.lifecycle.ViewModelProvider(this)[io.github.gwenvador.bullarr.ui.AppViewModel::class.java]
        val code = data.getQueryParameter("code")
        if (code.isNullOrBlank()) vm.ssoError = data.getQueryParameter("error") ?: "Le serveur n'a renvoyé aucun code de connexion."
        else vm.completeSso(code)
        intent.data = null
    }
}

@Composable
private fun BullarrApp(vm: AppViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    SideEffect { vm.downloads.snackbar = snackbar }

    if (vm.api == null) {
        // Non connecté : seulement la connexion et les albums déjà copiés sur le téléphone.
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null) }, label = { Text("Connexion", fontSize = 11.sp) })
                        NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Outlined.DownloadForOffline, contentDescription = null) }, label = { Text("Hors ligne", fontSize = 11.sp) })
                    }
                },
            ) { pad ->
                Box(Modifier.fillMaxSize().padding(pad)) {
                    if (tab == 0) LoginScreen(vm, firstRun = true, onSaved = { vm.section = Section.Library }) else OfflineScreen(vm)
                }
            }
            vm.reading?.let { target -> ReaderScreen(vm, target, onClose = { vm.reading = null; vm.progressVersion++ }) }
        }
        return
    }

    val openSeries: (Int, String) -> Unit = { id, title -> vm.openSeriesById(id, title) }
    BackHandler(enabled = vm.openSeries != null) { vm.openSeries = null }
    BackHandler(enabled = vm.openSeries == null && vm.section != Section.Library) { vm.section = Section.Library }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Section.entries.forEach { s ->
                        NavigationBarItem(
                            selected = vm.section == s,
                            onClick = { vm.openSeries = null; vm.section = s },
                            icon = {
                                androidx.compose.material3.BadgedBox(badge = {
                                    val n = if (s == Section.Tracking) vm.importCount + vm.newsCount + vm.reviewCount else 0
                                    if (n > 0) androidx.compose.material3.Badge { Text(if (n > 99) "99+" else "$n") }
                                }) { Icon(s.icon, contentDescription = null) }
                            },
                            label = { Text(s.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                val series = vm.openSeries
                if (series != null) {
                    SeriesScreen(vm, series, onBack = { vm.openSeries = null }, onSearch = { ctx ->
                        vm.search.prefill(ctx)
                        vm.goToSearch()
                    })
                } else {
                    val section = vm.section
                    io.github.gwenvador.bullarr.ui.Polling(60_000, vm.api) { vm.refreshBadges() }
                    Column(Modifier.fillMaxSize()) {
                        val sub = vm.subTab[section] ?: 0
                        if (section.subs.isNotEmpty()) {
                            PrimaryScrollableTabRow(selectedTabIndex = sub, edgePadding = 8.dp) {
                                section.subs.forEachIndexed { i, label ->
                                    Tab(selected = sub == i, onClick = { vm.subTab[section] = i; if (section == Section.Tracking && label == "Nouveautés") vm.markNewsSeen() }, text = {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(label, maxLines = 1)
                                            val n = if (section == Section.Tracking) when (label) {
                                                "Activité" -> vm.activityCount + vm.importCount
                                                "Nouveautés" -> vm.newsCount; "Validation" -> vm.reviewCount; else -> 0
                                            } else 0
                                            if (n > 0) androidx.compose.material3.Badge { Text(if (n > 99) "99+" else "$n") }
                                        }
                                    })
                                }
                            }
                        }
                        Box(Modifier.fillMaxSize()) {
                            when (section) {
                                Section.Library -> if (sub == 0) LibraryScreen(vm) { vm.openSeries = it } else OfflineScreen(vm)
                                Section.Search -> SearchScreen(vm)
                                Section.Tracking -> when (sub) {
                                    0 -> ActivityScreen(vm, snackbar)
                                    1 -> {
                                        androidx.compose.runtime.LaunchedEffect(Unit) { vm.markNewsSeen() }
                                        NewsScreen(vm, openSeries)
                                    }
                                    2 -> io.github.gwenvador.bullarr.ui.ValidationScreen(vm)
                                    else -> HistoryScreen(vm, openSeries)
                                }
                                Section.Actions -> when (sub) {
                                    0 -> EnrichScreen(vm, snackbar, openSeries)
                                    1 -> MonitorScreen(vm, snackbar, openSeries)
                                    else -> VerificationScreen(vm, snackbar, openSeries)
                                }
                                Section.Config -> when (sub) {
                                    0 -> LoginScreen(vm, firstRun = false, onSaved = {})
                                    1 -> ConfigScreen(vm, snackbar, INDEXERS)
                                    2 -> ConfigScreen(vm, snackbar, CLIENTS)
                                    3 -> ConfigScreen(vm, snackbar, NOTIFICATIONS)
                                    4 -> ConfigScreen(vm, snackbar, IMPORTS)
                                    5 -> ConfigScreen(vm, snackbar, SEARCH)
                                    6 -> ConfigScreen(vm, snackbar, MONITOR)
                                    7 -> ConfigScreen(vm, snackbar, ACCESS)
                                    else -> WebSettingsScreen(vm)
                                }
                            }
                        }
                    }
                }
            }
        }
        DownloadDialogs(vm.downloads)
        // Lecteur : plein écran par-dessus l'app, qui garde son état dessous.
        vm.reading?.let { target -> ReaderScreen(vm, target, onClose = { vm.reading = null; vm.progressVersion++ }) }
    }
}
