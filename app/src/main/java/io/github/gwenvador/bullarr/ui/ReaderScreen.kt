package io.github.gwenvador.bullarr.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.PageCache
import io.github.gwenvador.bullarr.data.ReaderMeta
import io.github.gwenvador.bullarr.data.parseReaderMeta
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Tome à lire. */
data class ReaderTarget(val volumeId: Int, val title: String)

private val Context.activity: Activity?
    get() = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity; else -> null }

/** Dernière page lue par tome (index 0-based). Absent = jamais lu, ou album fini : on repart du début. */
private class Progress(context: Context) {
    private val prefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
    /** Réglage global : la page prend la largeur de l'écran (défilement vertical) au lieu de tenir entière. */
    var fitWidth: Boolean
        get() = prefs.getBoolean("fit_width", false)
        set(value) = prefs.edit().putBoolean("fit_width", value).apply()
    fun get(volumeId: Int): Int? = prefs.getInt("page_$volumeId", -1).takeIf { it >= 0 }
    fun save(volumeId: Int, page: Int, pageCount: Int) {
        prefs.edit().apply { if (page >= pageCount - 1) remove("page_$volumeId") else putInt("page_$volumeId", page) }.apply()
    }
}

@Composable
fun ReaderScreen(vm: AppViewModel, target: ReaderTarget, onClose: () -> Unit) {
    val api = vm.api ?: return
    val view = LocalView.current
    val context = LocalContext.current

    // Plein écran immersif, écran allumé ; la mémoire des pages est rendue à la fermeture.
    DisposableEffect(Unit) {
        val window = context.activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
            PageCache.clear()
        }
    }
    BackHandler(onBack = onClose)

    var meta by remember(target.volumeId) { mutableStateOf<ReaderMeta?>(null) }
    var error by remember(target.volumeId) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(target.volumeId, attempt) {
        error = null
        try { meta = parseReaderMeta(target.volumeId, api.getObject("api/volumes/${target.volumeId}/reader")) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.readable() }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val m = meta
        when {
            m != null -> Reader(api, m, Progress(context), onClose)
            else -> {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (error == null) CircularProgressIndicator(color = Color.White)
                    else {
                        Text(error!!, color = Color.White, textAlign = TextAlign.Center)
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { attempt++ }) { Text("Réessayer") }
                            TextButton(onClick = onClose) { Text("Fermer") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Reader(api: BullarrApi, meta: ReaderMeta, progress: Progress, onClose: () -> Unit) {
    val total = meta.pageCount
    if (total <= 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ce tome n'a aucune page lisible.", color = Color.White)
                TextButton(onClick = onClose) { Text("Fermer") }
            }
        }
        return
    }
    val start = (progress.get(meta.volumeId) ?: 0).coerceIn(0, total - 1)
    val pager = rememberPagerState(initialPage = start) { total }
    val scope = rememberCoroutineScope()
    var bars by remember { mutableStateOf(true) }
    var barsTick by remember { mutableIntStateOf(0) }
    var zoomed by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableStateOf<Float?>(null) }
    var fitWidth by remember { mutableStateOf(progress.fitWidth) }
    val current = pager.currentPage

    // Barres masquées après quelques secondes.
    LaunchedEffect(bars, barsTick) { if (bars) { delay(3500); bars = false } }
    // Mémorise la page ; un album fini repart du début.
    LaunchedEffect(current) { progress.save(meta.volumeId, current, total) }
    // Précharge d'abord la page courante, puis les 2 suivantes (et la précédente pour revenir).
    LaunchedEffect(current) {
        for (i in listOf(current, current + 1, current + 2, current - 1)) {
            if (i in 0 until total) launch { runCatching { PageCache.get(api, meta.volumeId, i + 1) } }
        }
    }

    fun go(delta: Int) {
        val to = (pager.currentPage + delta).coerceIn(0, total - 1)
        if (to != pager.currentPage) scope.launch { pager.animateScrollToPage(to) }
    }

    Box(
        Modifier.fillMaxSize().pointerInput(total) {
            detectTapGestures { pos ->
                val x = pos.x / size.width
                when {
                    x < 0.3f -> go(-1)
                    x > 0.7f -> go(+1)
                    else -> bars = !bars
                }
            }
        },
    ) {
        // Les 2 pages suivantes sont déjà composées : le glissement n'attend rien.
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, beyondViewportPageCount = 2, key = { it }) { index ->
            PageView(api, meta.volumeId, index + 1, isCurrent = index == pager.currentPage, fitWidth = fitWidth) { zoomed = it }
        }

        AnimatedVisibility(bars, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Fermer le lecteur", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(meta.seriesTitle ?: meta.title ?: "", color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(meta.label, meta.title?.takeIf { it != meta.seriesTitle }).joinToString(" · ")
                    if (sub.isNotEmpty()) Text(sub, color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Page entière ↔ pleine largeur (on fait défiler la page vers le bas).
                TextButton(onClick = { fitWidth = !fitWidth; progress.fitWidth = fitWidth; barsTick++ }) {
                    Icon(
                        if (fitWidth) Icons.Outlined.FitScreen else Icons.Outlined.SwapVert,
                        contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 6.dp),
                    )
                    Text(if (fitWidth) "Page entière" else "Pleine largeur", color = Color.White)
                }
            }
        }

        AnimatedVisibility(bars, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("${(sliderPos?.toInt() ?: (current + 1))} / $total", color = Color.White, style = MaterialTheme.typography.titleMedium)
                if (total > 1) {
                    Slider(
                        value = sliderPos ?: (current + 1).toFloat(),
                        onValueChange = { sliderPos = it; barsTick++ },
                        onValueChangeFinished = {
                            val to = (sliderPos?.toInt() ?: (current + 1)) - 1
                            sliderPos = null
                            scope.launch { pager.scrollToPage(to.coerceIn(0, total - 1)) }
                        },
                        valueRange = 1f..total.toFloat(),
                        steps = (total - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f), activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
                        ),
                    )
                }
            }
        }
    }
}

/** Une page : chargement, erreur lisible avec réessai, zoom par pincement (jusqu'à ×5) puis déplacement. */
@Composable
private fun PageView(api: BullarrApi, volumeId: Int, page: Int, isCurrent: Boolean, fitWidth: Boolean, onZoom: (Boolean) -> Unit) {
    var bitmap by remember(volumeId, page) { mutableStateOf(PageCache.cached(volumeId, page)) }
    var error by remember(volumeId, page) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(volumeId, page, attempt) {
        if (bitmap != null) return@LaunchedEffect
        error = null
        try { bitmap = PageCache.get(api, volumeId, page) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Page $page : ${e.readable()}" }
    }

    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Une page qui n'est plus affichée revient à sa taille.
    LaunchedEffect(isCurrent, fitWidth) { if (!isCurrent || fitWidth) { scale = 1f; offset = Offset.Zero } }
    LaunchedEffect(scale, isCurrent) { if (isCurrent) onZoom(scale > 1.01f) }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Ne consomme que le pincement ou le déplacement d'une page zoomée : le glissement
            // à un doigt d'une page à sa taille reste au pager.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.size > 1 || scale > 1f) {
                        val zoom = event.calculateZoom()
                        val pan = event.calculatePan()
                        var s = (scale * zoom).coerceIn(1f, 5f)
                        if (s < 1.03f) s = 1f
                        val maxX = size.width * (s - 1f) / 2f
                        val maxY = size.height * (s - 1f) / 2f
                        offset = if (s == 1f) Offset.Zero
                        else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                        scale = s
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        when {
            b != null && fitWidth -> Box(
                // Pleine largeur : centrée si elle est plus basse que l'écran (portrait),
                // sinon on la fait défiler (paysage).
                Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            ) {
                Column(Modifier.align(Alignment.Center).verticalScroll(rememberScrollState())) {
                    Image(
                        b.asImageBitmap(), contentDescription = "Page $page", contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            b != null -> Image(
                b.asImageBitmap(), contentDescription = "Page $page", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            error != null -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = Color.White, textAlign = TextAlign.Center)
                TextButton(onClick = { attempt++ }) { Text("Réessayer") }
            }
            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}
