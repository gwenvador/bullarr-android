package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.gwenvador.bullarr.ui.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.R

// Logos des sources et des clients (repris de static/img de Bullarr) et drapeaux d'origine.

private val LOGOS = mapOf(
    "prowlarr" to R.drawable.src_prowlarr,
    "ebdz" to R.drawable.src_ebdz,
    "telegram" to R.drawable.src_telegram,
    "fourtoutici" to R.drawable.src_fourtoutici,
    "vk" to R.drawable.src_vk,
    "annas_archive" to R.drawable.src_annas_archive,
    "rss" to R.drawable.src_rss,
    "qbittorrent" to R.drawable.src_qbittorrent,
    "amule" to R.drawable.src_emule,
    "emule" to R.drawable.src_emule,
    "rtorrent" to R.drawable.src_rtorrent,
    "deluge" to R.drawable.src_deluge,
    "shelfmark" to R.drawable.src_shelfmark,
    "komga" to R.drawable.src_komga,
    "ntfy" to R.drawable.src_ntfy,
    "bedetheque" to R.drawable.bedetheque_logo,
    "fnac" to R.drawable.shop_fnac, "amazon" to R.drawable.shop_amazon, "bdfugue" to R.drawable.shop_bdfugue,
    // Genre / origine : même convention que la page web (BD = drapeau français).
    "bd" to R.drawable.src_flag_france, "europe" to R.drawable.src_flag_france,
    "comics" to R.drawable.src_flag_us, "usa" to R.drawable.src_flag_us,
    "manga" to R.drawable.src_flag_japan, "asie" to R.drawable.src_flag_japan,
)

/** Clé de logo pour un libellé quelconque (« qBittorrent », « aMule », « Prowlarr — C411 »…). */
fun logoKey(label: String?): String? {
    val l = label?.lowercase() ?: return null
    return LOGOS.keys.firstOrNull { l == it } ?: when {
        "prowlarr" in l -> "prowlarr"
        "qbittorrent" in l -> "qbittorrent"
        "amule" in l || "emule" in l -> "amule"
        "telegram" in l -> "telegram"
        "vk" in l -> "vk"
        "anna" in l -> "annas_archive"
        "fourtoutici" in l -> "fourtoutici"
        "rtorrent" in l -> "rtorrent"
        "deluge" in l -> "deluge"
        "ebdz" in l -> "ebdz"
        "rss" in l -> "rss"
        else -> null
    }
}

fun hasLogo(key: String?) = key != null && key in LOGOS

/** Libellé court affiché au long appui (et lu par TalkBack) : une icône garde son sens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WithTip(label: String, content: @Composable () -> Unit) {
    @Suppress("DEPRECATION")
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) { content() }
}

/** Logo d'une source, d'un client ou d'une origine ; rien si la clé est inconnue. */
@Composable
fun Logo(key: String?, label: String, size: Dp = 18.dp, modifier: Modifier = Modifier, tip: Boolean = true) {
    val res = key?.let { LOGOS[it] } ?: return
    val image = @Composable { Image(painterResource(res), contentDescription = label, modifier = modifier.size(size).clip(RoundedCornerShape(size / 5))) }
    // Dans un bouton, un onglet ou une puce : pas d'infobulle, elle intercepterait l'appui.
    if (tip) WithTip(label) { image() } else image()
}

/** Icône d'état avec un court texte facultatif (nombre, page) ; le libellé complet est au long appui. */
@Composable
fun IconTag(icon: ImageVector, label: String, tint: Color, text: String? = null, size: Dp = 18.dp) {
    WithTip(label) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(3.dp),
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size))
            if (text != null) Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = tint)
        }
    }
}

/** Icône d'état teintée, avec son libellé au long appui. */
@Composable
fun StatusIcon(icon: ImageVector, label: String, tint: Color, size: Dp = 18.dp) {
    WithTip(label) { Box { Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size)) } }
}
