package io.github.gwenvador.bullarr.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.automirrored.outlined.FormatTextdirectionLToR
import androidx.compose.material.icons.automirrored.outlined.FormatTextdirectionRToL
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.outlined.SwipeVertical
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.OfflineStore
import io.github.gwenvador.bullarr.data.PageCache
import io.github.gwenvador.bullarr.data.ReaderMeta
import io.github.gwenvador.bullarr.data.ReadingProgress
import io.github.gwenvador.bullarr.data.parseReaderMeta
import io.github.gwenvador.bullarr.data.parseSeriesDetail
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Tome à lire. */
data class ReaderTarget(val volumeId: Int, val title: String)

private val Context.activity: Activity?
    get() = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity; else -> null }

@Composable
fun ReaderScreen(vm: AppViewModel, target: ReaderTarget, onClose: () -> Unit) {
    // Hors connexion (non connecté) : un client factice suffit, les albums copiés sont lus sans réseau.
    val online = vm.api != null
    val api = vm.api ?: remember { BullarrApi("http://localhost/", "") }
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
        try {
            // Copie hors ligne : le lecteur démarre sans réseau.
            meta = vm.offline.store.metaOf(target.volumeId)
                ?: parseReaderMeta(target.volumeId, api.getObject("api/volumes/${target.volumeId}/reader"))
        }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.readable() }
    }

    // Tomes voisins de la série (possédés) : le précédent à la première page, le suivant à la dernière.
    var neighbours by remember(target.volumeId) { mutableStateOf<Pair<ReaderTarget?, ReaderTarget?>>(null to null) }
    LaunchedEffect(meta?.seriesId) {
        val m = meta ?: return@LaunchedEffect
        val sid = m.seriesId ?: return@LaunchedEffect
        neighbours = try {
            if (!online) throw IllegalStateException()
            val d = parseSeriesDetail(api.getObject("api/series/$sid"))
            val owned = d.volumes.filter { it.owned }
            val i = owned.indexOfFirst { it.id == m.volumeId }
            fun at(j: Int) = owned.getOrNull(j)?.takeIf { i >= 0 }?.let { ReaderTarget(it.id, "${d.title} · ${it.label}") }
            at(i - 1) to at(i + 1)
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            // Sans réseau : les tomes voisins parmi les albums copiés sur le téléphone.
            val copies = vm.offline.store.list().map { it.meta }.filter { it.seriesId == sid }
                .sortedBy { Regex("\\d+").find(it.label.orEmpty())?.value?.toIntOrNull() ?: Int.MAX_VALUE }
            val i = copies.indexOfFirst { it.volumeId == m.volumeId }
            fun at(j: Int) = copies.getOrNull(j)?.takeIf { i >= 0 }?.let { ReaderTarget(it.volumeId, listOfNotNull(it.seriesTitle, it.label).joinToString(" · ")) }
            at(i - 1) to at(i + 1)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val m = meta
        when {
            // Une clé par tome : passer au suivant repart d'un lecteur neuf.
            m != null -> key(m.volumeId) {
                val manga = vm.series.firstOrNull { it.id == m.seriesId }?.origin == "Asie"
                Reader(api, online, vm.offline.store, m, ReadingProgress(context), manga, neighbours.first, neighbours.second, onOpen = { vm.reading = it }, onClose = onClose)
            }
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

/** Couverture seule, puis les pages deux par deux (2-3, 4-5…), comme un album ouvert. */
private fun spreadsOf(total: Int, double: Boolean): List<List<Int>> =
    if (!double || total < 3) (0 until total).map { listOf(it) }
    else listOf(listOf(0)) + (1 until total).chunked(2)

@Composable
private fun Reader(
    api: BullarrApi, online: Boolean, offline: OfflineStore, meta: ReaderMeta, progress: ReadingProgress,
    manga: Boolean, prev: ReaderTarget?, next: ReaderTarget?, onOpen: (ReaderTarget) -> Unit, onClose: () -> Unit,
) {
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
    val scope = rememberCoroutineScope()
    var bars by remember { mutableStateOf(true) }
    var barsTick by remember { mutableIntStateOf(0) }
    var zoomed by remember { mutableStateOf(false) }
    var sliderPos by remember { mutableStateOf<Float?>(null) }
    var fitWidth by remember { mutableStateOf(progress.fitWidth) }
    var webtoon by remember { mutableStateOf(progress.webtoon) }
    // Sens manga (droite → gauche) : par série, activé d'office pour une série d'origine Asie.
    var rtl by remember { mutableStateOf(progress.rtl(meta.seriesId, manga)) }
    var doublePref by remember { mutableStateOf(progress.doublePage) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val double = landscape && doublePref && !webtoon
    val spreads = remember(total, double) { spreadsOf(total, double) }
    fun spreadOf(page: Int) = spreads.indexOfFirst { page in it }.coerceAtLeast(0)

    // Dernière page affichée : sert de point de reprise quand la vue change (webtoon, double page).
    val lastSeen = remember { intArrayOf(start) }
    val pager = key(double) { rememberPagerState(initialPage = spreadOf(lastSeen[0])) { spreads.size } }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = start)
    // Webtoon : la page courante est celle qui occupe le milieu de l'écran (la dernière une fois en bas).
    val listPage by remember {
        derivedStateOf {
            val info = list.layoutInfo
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            when {
                info.visibleItemsInfo.isEmpty() -> list.firstVisibleItemIndex
                !list.canScrollForward -> total - 1
                else -> info.visibleItemsInfo.firstOrNull { it.offset <= mid && it.offset + it.size > mid }?.index ?: list.firstVisibleItemIndex
            }
        }
    }
    val spread = spreads.getOrElse(pager.currentPage) { listOf(0) }
    val current = if (webtoon) listPage else spread.first()
    val atEnd = if (webtoon) listPage == total - 1 else spread.last() == total - 1
    SideEffect { lastSeen[0] = current }
    LaunchedEffect(webtoon) { if (webtoon) list.scrollToItem(lastSeen[0]) else pager.scrollToPage(spreadOf(lastSeen[0])) }

    // Barres masquées après quelques secondes.
    LaunchedEffect(bars, barsTick) { if (bars) { delay(3500); bars = false } }
    // Mémorise la page (et l'album pour « Continuer la lecture ») ; un album fini repart du début.
    LaunchedEffect(current, atEnd) { progress.save(meta.volumeId, if (atEnd) total - 1 else current, total, meta) }
    // Envoie la page lue à Bullarr, qui la transmet à Komga (sans effet si Komga n'est pas relié).
    LaunchedEffect(current, atEnd) {
        if (!online) return@LaunchedEffect
        delay(2000)
        runCatching {
            api.postObject(
                "api/volumes/${meta.volumeId}/reader/progress",
                JSONObject().put("page", if (atEnd) total else current + 1).put("completed", atEnd),
            )
        }
    }
    // Précharge d'abord la page courante, puis les suivantes (et la précédente pour revenir).
    LaunchedEffect(current) {
        for (i in listOf(current, current + 1, current + 2, current + 3, current - 1)) {
            if (i in 0 until total) launch { runCatching { PageCache.get(api, meta.volumeId, i + 1, offline) } }
        }
    }

    fun go(delta: Int) {
        if (delta > 0 && atEnd) { next?.let(onOpen); return }
        if (delta < 0 && current == 0) { prev?.let(onOpen); return }
        scope.launch {
            if (webtoon) list.animateScrollToItem((current + delta).coerceIn(0, total - 1))
            else pager.animateScrollToPage((pager.currentPage + delta).coerceIn(0, spreads.size - 1))
        }
    }
    fun jump(page: Int) {
        val p = page.coerceIn(0, total - 1)
        scope.launch { if (webtoon) list.scrollToItem(p) else pager.scrollToPage(spreadOf(p)) }
    }

    // Zoom du mode webtoon : pincement à deux doigts, puis déplacement.
    var wScale by remember { mutableStateOf(1f) }
    var wOffset by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(webtoon) { wScale = 1f; wOffset = Offset.Zero }

    // Appui : bords = page précédente / suivante, centre = barres. Toujours la version à jour
    // (page courante, fin d'album, pager recréé après une rotation), jamais celle du premier affichage.
    val onTap by rememberUpdatedState { x: Float ->
        when {
            x < 0.3f -> go(if (rtl) +1 else -1)
            x > 0.7f -> go(if (rtl) -1 else +1)
            else -> bars = !bars
        }
    }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { pos -> onTap(pos.x / size.width) }
        },
    ) {
        if (webtoon) {
            // Webtoon : pages à la largeur de l'écran, collées les unes aux autres, on fait défiler.
            Box(
                Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pinch = event.changes.size > 1
                            if (pinch || wScale > 1f) {
                                val s = if (pinch) (wScale * event.calculateZoom()).coerceIn(1f, 4f).let { if (it < 1.03f) 1f else it } else wScale
                                val pan = event.calculatePan()
                                val maxX = size.width * (s - 1f) / 2f
                                val maxY = size.height * (s - 1f)
                                wOffset = if (s == 1f) Offset.Zero else Offset(
                                    (wOffset.x + pan.x).coerceIn(-maxX, maxX),
                                    if (pinch) (wOffset.y + pan.y).coerceIn(-maxY, 0f) else wOffset.y,
                                )
                                wScale = s
                                // Le pincement ne fait pas défiler la liste ; à un doigt, le défilement vertical reste à la liste.
                                if (pinch) event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
            ) {
                LazyColumn(
                    Modifier.fillMaxSize().graphicsLayer(
                        scaleX = wScale, scaleY = wScale, translationX = wOffset.x, translationY = wOffset.y,
                        transformOrigin = TransformOrigin(0.5f, 0f),
                    ),
                    state = list,
                ) {
                    items(total, key = { it }) { index -> WebtoonPage(api, offline, meta.volumeId, index + 1) }
                }
            }
        } else {
            // Les 2 vues suivantes sont déjà composées : le glissement n'attend rien. Manga : la suivante est à gauche.
            // Recréé en entier quand on passe d'une à deux pages (rotation) : l'ancien découpage ne doit
            // plus servir, sinon il demande une vue qui n'existe pas.
            key(double) {
                val views = spreads
                HorizontalPager(
                    pager, Modifier.fillMaxSize(), reverseLayout = rtl, userScrollEnabled = !zoomed,
                    beyondViewportPageCount = 2, key = { i -> views.getOrNull(i)?.first() ?: (-1 - i) },
                ) { index ->
                    val pages = views.getOrNull(index) ?: return@HorizontalPager
                    PageView(
                        api, offline, meta.volumeId, pages.map { it + 1 }, isCurrent = index == pager.currentPage,
                        fitWidth = fitWidth && !double, rtl = rtl,
                    ) { zoomed = it }
                }
            }
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
                // Sens de lecture : gauche → droite ou manga (droite → gauche), mémorisé pour la série.
                if (!webtoon) IconButton(onClick = { rtl = !rtl; progress.setRtl(meta.seriesId, rtl); barsTick++ }) {
                    Icon(
                        if (rtl) Icons.AutoMirrored.Outlined.FormatTextdirectionRToL else Icons.AutoMirrored.Outlined.FormatTextdirectionLToR,
                        contentDescription = if (rtl) "Sens manga (droite → gauche)" else "Sens de lecture gauche → droite", tint = Color.White,
                    )
                }
                // Double page en paysage.
                if (landscape && !webtoon) IconButton(onClick = { doublePref = !doublePref; progress.doublePage = doublePref; zoomed = false; barsTick++ }) {
                    // « 2× » : deux pages côte à côte ; grisé quand le mode est coupé.
                    Text(
                        "2×", color = Color.White, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.alpha(if (doublePref) 1f else 0.45f),
                    )
                }
                // Pages ↔ webtoon (lecture en défilement vertical continu) : libellé explicite, c'est le mode qu'on va prendre.
                TextButton(
                    onClick = { webtoon = !webtoon; progress.webtoon = webtoon; zoomed = false; barsTick++ },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        if (webtoon) Icons.Outlined.ViewCarousel else Icons.Outlined.SwipeVertical,
                        contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 4.dp),
                    )
                    Text(if (webtoon) "Pages" else "Webtoon", color = Color.White, style = MaterialTheme.typography.labelLarge)
                }
                // Page entière ↔ pleine largeur (on fait défiler la page vers le bas).
                if (!webtoon && !double) IconButton(onClick = { fitWidth = !fitWidth; progress.fitWidth = fitWidth; barsTick++ }) {
                    Icon(
                        if (fitWidth) Icons.Outlined.FitScreen else Icons.Outlined.SwapVert,
                        contentDescription = if (fitWidth) "Page entière" else "Pleine largeur", tint = Color.White,
                    )
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Première page : retour au tome précédent.
            AnimatedVisibility(current == 0 && prev != null && !atEnd, enter = fadeIn(), exit = fadeOut()) {
                androidx.compose.material3.OutlinedButton(
                    onClick = { prev?.let(onOpen) },
                    modifier = Modifier.padding(bottom = 12.dp).then(if (bars) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Black.copy(alpha = 0.7f), contentColor = Color.White),
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Tome précédent : ${prev?.title?.substringAfterLast(" · ") ?: ""}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Dernière page : encart « Fin de l'album », avec le tome suivant s'il existe, même barres masquées.
            AnimatedVisibility(atEnd, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .then(if (bars) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                        .clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                        Text("Fin de l'album", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        if (next != null) "Marqué comme lu." else "Marqué comme lu. Pas d'autre tome possédé à suivre dans cette série.",
                        color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onClose) { Text("Fermer", color = Color.White) }
                        if (next != null) Button(
                            onClick = { onOpen(next) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Text("Tome suivant : ${next.title.substringAfterLast(" · ")}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
            AnimatedVisibility(bars, enter = fadeIn(), exit = fadeOut()) {
                Column(
                    Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Manga : miniatures et curseur vont de droite à gauche, comme les pages.
                    val direction = if (rtl && !webtoon) LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Thumbnails(api, offline, meta.volumeId, total, sliderPos?.toInt()?.minus(1) ?: current) { to ->
                            barsTick++
                            jump(to)
                        }
                    }
                    Text("${(sliderPos?.toInt() ?: (current + 1))} / $total", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    if (total > 1) CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        Slider(
                            value = sliderPos ?: (current + 1).toFloat(),
                            onValueChange = { sliderPos = it; barsTick++ },
                            onValueChangeFinished = {
                                val to = (sliderPos?.toInt() ?: (current + 1)) - 1
                                sliderPos = null
                                jump(to)
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
}

/** Bandeau de miniatures : la page courante est cerclée et centrée ; toucher une miniature y saute. */
@Composable
private fun Thumbnails(api: BullarrApi, offline: OfflineStore, volumeId: Int, total: Int, current: Int, onPick: (Int) -> Unit) {
    val state = rememberLazyListState()
    LaunchedEffect(current) {
        val width = state.layoutInfo.viewportSize.width
        state.animateScrollToItem(current.coerceIn(0, total - 1), scrollOffset = -(width / 2 - 36 * 3))
    }
    LazyRow(
        Modifier.fillMaxWidth().padding(bottom = 6.dp), state = state,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(List(total) { it }, key = { _, i -> i }) { _, index ->
            val selected = index == current
            var bmp by remember(volumeId, index) { mutableStateOf(PageCache.cachedThumb(volumeId, index + 1)) }
            LaunchedEffect(volumeId, index) {
                if (bmp == null) bmp = try { PageCache.thumb(api, volumeId, index + 1, offline) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { null }
            }
            Box(
                Modifier.height(88.dp).aspectRatio(0.7f).clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(if (selected) 2.dp else 0.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(4.dp))
                    .clickable { onPick(index) },
                contentAlignment = Alignment.Center,
            ) {
                bmp?.let { Image(it.asImageBitmap(), contentDescription = "Page ${index + 1}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Text(
                    "${index + 1}", color = Color.White, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
                )
            }
        }
    }
}

/** État de chargement d'une page. */
private class PageLoad(val bitmap: android.graphics.Bitmap?, val error: String?, val retry: () -> Unit)

/** Chargement d'une page : bitmap en cache, sinon téléchargée ; erreur lisible avec réessai. */
@Composable
private fun rememberPage(api: BullarrApi, offline: OfflineStore, volumeId: Int, page: Int): PageLoad {
    var bitmap by remember(volumeId, page) { mutableStateOf(PageCache.cached(volumeId, page)) }
    var error by remember(volumeId, page) { mutableStateOf<String?>(null) }
    var attempt by remember(volumeId, page) { mutableIntStateOf(0) }
    LaunchedEffect(volumeId, page, attempt) {
        if (bitmap != null) return@LaunchedEffect
        error = null
        try { bitmap = PageCache.get(api, volumeId, page, offline) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = "Page $page : ${e.readable()}" }
    }
    return PageLoad(bitmap, error) { attempt++ }
}

@Composable
private fun PageError(message: String, onRetry: () -> Unit) {
    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = Color.White, textAlign = TextAlign.Center)
        TextButton(onClick = onRetry) { Text("Réessayer") }
    }
}

/** Page du mode webtoon : pleine largeur, hauteur selon l'image (une hauteur provisoire pendant le chargement). */
@Composable
private fun WebtoonPage(api: BullarrApi, offline: OfflineStore, volumeId: Int, page: Int) {
    val p = rememberPage(api, offline, volumeId, page)
    val b = p.bitmap
    if (b != null) Image(
        b.asImageBitmap(), contentDescription = "Page $page", contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().aspectRatio(b.width.toFloat() / b.height.coerceAtLeast(1)),
    )
    else Box(Modifier.fillMaxWidth().aspectRatio(0.72f), contentAlignment = Alignment.Center) {
        if (p.error != null) PageError(p.error, p.retry) else CircularProgressIndicator(color = Color.White)
    }
}

/** Une moitié de double page : collée au pli (côté intérieur). */
@Composable
private fun HalfPage(api: BullarrApi, offline: OfflineStore, volumeId: Int, page: Int, alignment: Alignment, modifier: Modifier) {
    val p = rememberPage(api, offline, volumeId, page)
    val b = p.bitmap
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            b != null -> Image(b.asImageBitmap(), contentDescription = "Page $page", contentScale = ContentScale.Fit, alignment = alignment, modifier = Modifier.fillMaxSize())
            p.error != null -> PageError(p.error, p.retry)
            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}

/**
 * Une vue du lecteur : une page, ou deux côte à côte (paysage). Zoom par pincement (jusqu'à ×5) puis
 * déplacement ; erreur lisible avec réessai.
 */
@Composable
private fun PageView(
    api: BullarrApi, offline: OfflineStore, volumeId: Int, pages: List<Int>, isCurrent: Boolean,
    fitWidth: Boolean, rtl: Boolean, onZoom: (Boolean) -> Unit,
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    // Une page qui n'est plus affichée revient à sa taille.
    LaunchedEffect(isCurrent, fitWidth) { if (!isCurrent || fitWidth) { scale = 1f; offset = Offset.Zero } }
    LaunchedEffect(scale, isCurrent) { if (isCurrent) onZoom(scale > 1.01f) }
    val zoom = Modifier.graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // Ne consomme que le pincement ou le déplacement d'une page zoomée : le glissement
            // à un doigt d'une page à sa taille reste au pager.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val event = awaitPointerEvent()
                    if (event.changes.size > 1 || scale > 1f) {
                        val z = event.calculateZoom()
                        val pan = event.calculatePan()
                        var s = (scale * z).coerceIn(1f, 5f)
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
        if (pages.size > 1) {
            // Double page : la première page à gauche (à droite en manga), les deux collées au pli.
            CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                Row(Modifier.fillMaxSize().then(zoom)) {
                    HalfPage(api, offline, volumeId, pages[0], Alignment.CenterEnd, Modifier.weight(1f).fillMaxHeight())
                    HalfPage(api, offline, volumeId, pages[1], Alignment.CenterStart, Modifier.weight(1f).fillMaxHeight())
                }
            }
        } else {
            val page = pages.first()
            val p = rememberPage(api, offline, volumeId, page)
            val b = p.bitmap
            when {
                b != null && fitWidth -> Box(
                    // Pleine largeur : centrée si elle est plus basse que l'écran (portrait),
                    // sinon on la fait défiler (paysage).
                    Modifier.fillMaxSize().then(zoom),
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
                    modifier = Modifier.fillMaxSize().then(zoom),
                )
                p.error != null -> PageError(p.error, p.retry)
                else -> CircularProgressIndicator(color = Color.White)
            }
        }
    }
}
