package io.github.gwenvador.bullarr.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.BullarrApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Avatars déjà décodés (SVG proposés par le serveur ou photo WebP), par URL et taille. */
private object AvatarCache {
    val bitmaps = object : LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(api: BullarrApi, url: String, px: Int): Bitmap? {
        val key = "$url@$px"
        bitmaps.get(key)?.let { return it }
        val bytes = runCatching { api.getBytes(url.trimStart('/')) }.getOrNull() ?: return null
        val bmp = withContext(Dispatchers.Default) { decode(bytes, px) } ?: return null
        bitmaps.put(key, bmp)
        return bmp
    }

    private fun decode(bytes: ByteArray, px: Int): Bitmap? {
        val head = String(bytes, 0, minOf(bytes.size, 256)).trimStart()
        if (head.startsWith("<")) return runCatching {
            val svg = com.caverock.androidsvg.SVG.getFromString(String(bytes))
            svg.documentWidth = px.toFloat(); svg.documentHeight = px.toFloat()
            Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888).also { svg.renderToCanvas(Canvas(it)) }
        }.getOrNull()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

/** Avatar rond d'un compte ; icône neutre tant que l'image n'est pas chargée (ou sans avatar). */
@Composable
fun Avatar(api: BullarrApi?, url: String?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bmp by remember(url, px) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(api, url, px) { if (api != null && url != null) bmp = AvatarCache.load(api, url, px) }
    Box(
        modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        else Icon(Icons.Outlined.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.6f))
    }
}
