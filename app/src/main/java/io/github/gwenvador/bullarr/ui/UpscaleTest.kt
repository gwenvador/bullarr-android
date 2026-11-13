package io.github.gwenvador.bullarr.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.gwenvador.bullarr.data.PageCache
import io.github.gwenvador.bullarr.data.UpscaleModel
import io.github.gwenvador.bullarr.data.Upscaler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Image d'essai : au-delà du seuil réglé sur le serveur, le lecteur n'agrandit pas la page ; l'essai la refuse aussi. */
private val TEST_MAX_SIDE get() = Upscaler.maxSourceSide

/** Raison du refus si l'image est trop grande pour l'upscaling (comme dans le lecteur), sinon null. */
private fun tooLarge(w: Int, h: Int) = if (maxOf(w, h) <= TEST_MAX_SIDE) null
    else "Image de $w×$h px : déjà en haute définition, l'upscaling ne concerne que les images de $TEST_MAX_SIDE px au plus."

/** Réglages → Upscaling : essai des modèles présents sur le téléphone, sur une page en cours de lecture ou une image choisie. */
@Composable
fun UpscaleTestCard(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val recent = remember { vm.reads.recent().firstOrNull() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        loading = true; error = null
        scope.launch {
            val (bmp, why) = withContext(Dispatchers.IO) {
                runCatching {
                    // Taille lue d'abord : une image trop grande n'est pas décodée.
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    tooLarge(bounds.outWidth, bounds.outHeight)?.let { return@runCatching null to it }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } to null
                }.getOrDefault(null to null)
            }
            source = bmp
            if (bmp == null) error = why ?: "Image illisible"
            loading = false
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Tester les modèles", style = MaterialTheme.typography.titleSmall)
        Text(
            "Agrandit une image avec chaque modèle présent sur ce téléphone, affiche le temps de calcul et compare les résultats. " +
                "Comme dans le lecteur, seules les images de ${Upscaler.maxSourceSide} px au plus sont agrandies.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val api = vm.api
            if (recent != null && api != null) OutlinedButton(enabled = !loading, onClick = {
                loading = true; error = null
                scope.launch {
                    val b = runCatching {
                        val page = (recent.page + 1).coerceIn(1, maxOf(1, recent.total))
                        PageCache.get(api, recent.volumeId, page, vm.offline.store)
                    }.getOrNull()
                    val why = b?.let { tooLarge(it.width, it.height) }
                    source = b?.takeIf { why == null }
                    if (source == null) error = why ?: "Page indisponible"
                    loading = false
                }
            }) {
                Icon(Icons.Outlined.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Page en cours")
            }
            OutlinedButton(enabled = !loading, onClick = {
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Outlined.Image, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Choisir une image")
            }
            if (loading) CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterVertically), strokeWidth = 2.dp)
        }
        recent?.let {
            Text(
                "Page en cours : ${listOfNotNull(it.seriesTitle, it.label ?: it.title).joinToString(" · ")}, page ${it.page + 1}",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
            )
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.error) }
    }
    source?.let { UpscaleTestDialog(it) { source = null } }
}

/** Plein écran : calcul de chaque modèle à la suite, temps affichés, comparaison gauche/droite avec un curseur et le zoom. */
@Composable
private fun UpscaleTestDialog(src: Bitmap, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val models = remember { UpscaleModel.entries.filter { Upscaler.isInstalled(it) } }
    val results = remember { mutableStateMapOf<UpscaleModel, Pair<Bitmap, Long>>() }
    val failed = remember { mutableStateMapOf<UpscaleModel, Boolean>() }
    var running by remember { mutableStateOf<UpscaleModel?>(null) }
    // null = image d'origine.
    var left by remember { mutableStateOf<UpscaleModel?>(null) }
    var right by remember { mutableStateOf<UpscaleModel?>(null) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(src) {
        for (m in models) {
            running = m
            val r = runCatching { Upscaler.test(context, src, m) }.getOrNull()
            if (r != null) { results[m] = r; if (right == null) right = m } else failed[m] = true
        }
        running = null
    }
    // Fermé avant la fin : on interrompt le calcul en cours (vérifié entre deux tuiles).
    DisposableEffect(Unit) { onDispose { if (running != null) Upscaler.cancelCurrent() } }

    fun image(m: UpscaleModel?): Bitmap? = if (m == null) src else results[m]?.first
    fun name(m: UpscaleModel?) = m?.label ?: "Original"

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = cs.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Essai des modèles", style = MaterialTheme.typography.titleMedium)
                        Text("Image de ${src.width}×${src.height} px", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Fermer") }
                }
                // Temps de calcul de chaque modèle (sans le chargement du modèle).
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    models.forEach { m ->
                        val r = results[m]
                        Column(
                            Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, cs.outlineVariant, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(m.label, style = MaterialTheme.typography.labelMedium)
                            when {
                                r != null -> Text("%.1f s".format(java.util.Locale.FRANCE, r.second / 1000.0), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = cs.primary)
                                failed[m] == true -> Text("échec", style = MaterialTheme.typography.bodySmall, color = cs.error)
                                running == m -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp))
                                    Text("calcul…", style = MaterialTheme.typography.bodySmall)
                                }
                                else -> Text("en attente", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
                // Comparaison : à gauche du curseur l'image choisie à gauche, à droite l'autre. Pincer pour zoomer, double tap pour revenir.
                Box(
                    Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.Black)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 8f)
                                offset = if (scale == 1f) Offset.Zero else offset + pan
                            }
                        }
                        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero }) },
                ) {
                    Box(
                        Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                    ) {
                        image(left)?.let { Image(it.asImageBitmap(), name(left), Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        image(right)?.let {
                            Image(
                                it.asImageBitmap(), name(right),
                                Modifier.fillMaxSize().drawWithContent {
                                    // Le trait est à l'écran, hors zoom : on ramène sa position dans le repère de l'image zoomée
                                    // (zoom centré, puis déplacement).
                                    val cut = (size.width * split - size.width / 2 - offset.x) / scale + size.width / 2
                                    clipRect(left = cut.coerceIn(0f, size.width)) { this@drawWithContent.drawContent() }
                                },
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                    // Repère du curseur (hors zoom) et noms des deux côtés.
                    Box(Modifier.fillMaxSize().drawWithContent {
                        drawContent()
                        val x = size.width * split
                        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
                    })
                    Text(
                        name(left), color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.TopStart).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text(
                        name(right), color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Slider(value = split, onValueChange = { split = it }, modifier = Modifier.padding(horizontal = 16.dp))
                SidePicker("Gauche", left, models, results.keys) { left = it }
                SidePicker("Droite", right, models, results.keys) { right = it }
                Text(
                    "Pincez pour zoomer, double tap pour revenir. Le temps dépend du téléphone et de la taille de l'image.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun SidePicker(title: String, current: UpscaleModel?, models: List<UpscaleModel>, done: Set<UpscaleModel>, onPick: (UpscaleModel?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp))
        FilterChip(selected = current == null, onClick = { onPick(null) }, label = { Text("Original") })
        models.forEach { m ->
            FilterChip(selected = current == m, enabled = m in done, onClick = { onPick(m) }, label = { Text(m.label) })
        }
    }
}
