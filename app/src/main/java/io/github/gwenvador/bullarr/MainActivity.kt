package io.github.gwenvador.bullarr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
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
import io.github.gwenvador.bullarr.data.INDEXERS
import io.github.gwenvador.bullarr.data.MONITOR
import io.github.gwenvador.bullarr.data.NOTIFICATIONS
import io.github.gwenvador.bullarr.data.SEARCH
import io.github.gwenvador.bullarr.ui.BullarrTheme
import io.github.gwenvador.bullarr.ui.DownloadDialogs
import io.github.gwenvador.bullarr.ui.EnrichScreen
import io.github.gwenvador.bullarr.ui.HistoryScreen
import io.github.gwenvador.bullarr.ui.ImportScreen
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
        setContent { BullarrTheme { BullarrApp() } }
    }
}

@Composable
private fun BullarrApp(vm: AppViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    SideEffect { vm.downloads.snackbar = snackbar }

    if (vm.api == null) {
        Scaffold { pad ->
            Box(Modifier.padding(pad)) { LoginScreen(vm, firstRun = true, onSaved = { vm.section = Section.Library }) }
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
                            icon = { Icon(s.icon, contentDescription = null) },
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
                    Column(Modifier.fillMaxSize()) {
                        val sub = vm.subTab[section] ?: 0
                        if (section.subs.isNotEmpty()) {
                            PrimaryScrollableTabRow(selectedTabIndex = sub, edgePadding = 8.dp) {
                                section.subs.forEachIndexed { i, label ->
                                    Tab(selected = sub == i, onClick = { vm.subTab[section] = i }, text = { Text(label, maxLines = 1) })
                                }
                            }
                        }
                        Box(Modifier.fillMaxSize()) {
                            when (section) {
                                Section.Library -> if (sub == 0) LibraryScreen(vm) { vm.openSeries = it } else OfflineScreen(vm)
                                Section.Search -> SearchScreen(vm)
                                Section.Tracking -> when (sub) {
                                    0 -> ActivityScreen(vm, snackbar)
                                    1 -> ImportScreen(vm)
                                    2 -> NewsScreen(vm, openSeries)
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
        vm.reading?.let { target -> ReaderScreen(vm, target, onClose = { vm.reading = null }) }
    }
}
