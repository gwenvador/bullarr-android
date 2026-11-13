package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.ApiException
import io.github.gwenvador.bullarr.data.BullarrApi
import io.github.gwenvador.bullarr.data.VolumeRow
import io.github.gwenvador.bullarr.data.readable
import io.github.gwenvador.bullarr.data.str
import org.json.JSONObject

/** Seuils de l'upscaling serveur (GET /api/upscale/threshold), comme le site (nav.js). */
data class UpscaleLimits(val maxSide: Int = 1600, val downscaleChoices: List<Int> = listOf(2000, 2400, 2800, 3200), val heavyPageBytes: Long = 3L * 1024 * 1024) {
    companion object {
        suspend fun load(api: BullarrApi): UpscaleLimits = try {
            val o = api.getObject("api/upscale/threshold")
            UpscaleLimits(
                maxSide = o.optInt("max_side", 1600),
                downscaleChoices = o.optJSONArray("downscale_choices")?.let { a -> List(a.length()) { a.optInt(it) } } ?: listOf(2000, 2400, 2800, 3200),
                heavyPageBytes = o.optLong("heavy_page_bytes", 3L * 1024 * 1024),
            )
        } catch (e: Exception) { UpscaleLimits() }
    }

    private fun isCbz(v: VolumeRow) = v.format.equals("cbz", ignoreCase = true)
    fun isHeavy(v: VolumeRow): Boolean {
        val pages = v.pageCount ?: 0; val size = v.fileSize ?: 0
        return pages > 0 && size > 0 && size / pages >= heavyPageBytes
    }

    /** Raison pour laquelle le tome ne peut pas être upscalé, sinon null (library.js). */
    fun upscaleBlocked(v: VolumeRow): String? = when {
        !v.owned -> "Tome absent du disque"
        v.sdBackup -> if (v.backupKind == "downscale") "Tome réduit : restaurez d'abord l'original" else "Déjà upscalé : restaurez d'abord la version SD"
        !isCbz(v) -> "Seuls les tomes cbz peuvent être upscalés : convertissez-le d'abord en cbz"
        v.pageSide <= 0 -> "Définition des pages pas encore mesurée"
        v.pageSide > maxSide -> "Déjà en bonne définition : pages de ${v.pageSide} px, au-dessus du seuil de $maxSide px (Réglages → Upscaling)"
        else -> null
    }

    /** Raison pour laquelle le tome ne peut pas être réduit, sinon null. */
    fun downscaleBlocked(v: VolumeRow): String? {
        val smallest = downscaleChoices.minOrNull()
        return when {
            !v.owned -> "Tome absent du disque"
            v.upscaleBusy -> "Traitement en cours pour ce tome"
            v.sdBackup -> if (v.backupKind == "downscale") "Déjà réduit : restaurez d'abord l'original" else "Tome upscalé : restaurez d'abord la version SD"
            !isCbz(v) -> "Seuls les tomes cbz peuvent être réduits : convertissez-le d'abord en cbz"
            v.pageSide <= 0 -> "Définition des pages pas encore mesurée"
            smallest == null -> "Aucune définition de réduction au-dessus du seuil de basse définition"
            v.pageSide <= smallest && !isHeavy(v) -> "Rien à réduire : pages de ${v.pageSide} px (sous $smallest px)" +
                (if ((v.pageCount ?: 0) > 0 && (v.fileSize ?: 0) > 0) String.format(" et %.1f Mo par page en moyenne", v.fileSize!! / v.pageCount!! / 1048576.0) else "")
            else -> null
        }
    }

    /** Tomes d'une série : à upscaler, à réduire (lourds), en cours, restaurables. */
    fun upscalable(vs: List<VolumeRow>) = vs.filter { !it.upscaleBusy && upscaleBlocked(it) == null }
    fun reducible(vs: List<VolumeRow>) = vs.filter { downscaleBlocked(it) == null && (isHeavy(it) || it.pageSide > (downscaleChoices.maxOrNull() ?: 3200)) }
    fun busy(vs: List<VolumeRow>) = vs.filter { it.owned && it.upscaleBusy }
    fun restorable(vs: List<VolumeRow>) = vs.filter { it.owned && it.sdBackup && !it.upscaleBusy }

    /** Raison affichée quand aucun tome de la série ne peut être upscalé. */
    fun seriesUpscaleReason(vs: List<VolumeRow>): String {
        val owned = vs.filter { it.owned }
        return when {
            owned.isEmpty() -> "Aucun tome sur le disque"
            owned.all { it.sdBackup } -> "Tous les tomes sont déjà upscalés ou réduits"
            owned.none { isCbz(it) } -> "Seuls les tomes cbz peuvent être upscalés : convertissez-les d'abord en cbz"
            else -> "Aucun tome en basse définition : toutes les pages dépassent le seuil de $maxSide px (Réglages → Upscaling)"
        }
    }

    fun seriesDownscaleReason(vs: List<VolumeRow>): String {
        val owned = vs.filter { it.owned }
        return when {
            owned.isEmpty() -> "Aucun tome sur le disque"
            owned.none { isCbz(it) } -> "Seuls les tomes cbz peuvent être réduits : convertissez-les d'abord en cbz"
            owned.all { it.sdBackup || it.upscaleBusy } -> "Tous les tomes ont déjà un original gardé (upscalés ou réduits)"
            else -> "Aucun tome trop lourd : pages de ${downscaleChoices.maxOrNull() ?: 3200} px au plus et moins de ${heavyPageBytes / 1048576} Mo par page"
        }
    }
}

private fun JSONObject.check(): JSONObject {
    if (!optBoolean("success", true)) throw ApiException(str("error") ?: "Erreur inconnue")
    return this
}

object ServerUpscale {
    suspend fun upscale(api: BullarrApi, ids: List<Int>, model: String): String {
        if (ids.size == 1) {
            api.postObject("api/volumes/${ids[0]}/upscale", JSONObject().put("model", model)).check()
            return "Upscaling HD en file d'attente"
        }
        val r = api.postObject("api/upscale/batch", JSONObject().put("volume_ids", org.json.JSONArray(ids)).put("model", model)).check()
        return "${r.optJSONArray("queued")?.length() ?: 0} tome(s) en file d'attente pour l'upscaling HD"
    }

    suspend fun downscale(api: BullarrApi, ids: List<Int>, target: Int, keep: Boolean): String {
        val opts = JSONObject().put("target", target).put("keep", keep)
        if (ids.size == 1) {
            api.postObject("api/volumes/${ids[0]}/downscale", opts).check()
            return "Réduction en file d'attente"
        }
        val r = api.postObject("api/upscale/batch", JSONObject().put("volume_ids", org.json.JSONArray(ids)).put("downscale", opts)).check()
        return "${r.optJSONArray("queued")?.length() ?: 0} tome(s) en file d'attente pour la réduction"
    }

    suspend fun cancel(api: BullarrApi, ids: List<Int>) = ids.forEach { api.deleteObject("api/volumes/$it/upscale").check() }
    suspend fun restore(api: BullarrApi, ids: List<Int>) = ids.forEach { api.postObject("api/volumes/$it/upscale/restore", JSONObject()).check() }
}

/** Choix du modèle avant un upscaling serveur (modèles présents, celui par défaut présélectionné). */
@Composable
fun UpscaleModelDialog(api: BullarrApi, title: String, text: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var cfg by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var chosen by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val c = api.getObject("api/settings/upscale"); cfg = c; chosen = c.str("model")
            if (c.optBoolean("runtime", true).not()) error = "Le moteur d'upscaling n'est pas disponible sur ce serveur"
        } catch (e: Exception) { error = e.readable() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val c = cfg
                if (c == null && error == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (c != null) {
                    val models = ServerModels(c)
                    if (models.present.isEmpty()) Text("Aucun modèle présent sur le serveur.", color = MaterialTheme.colorScheme.error)
                    models.present.forEach { id ->
                        Row(Modifier.fillMaxWidth().clickable { chosen = id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = chosen == id, onClick = { chosen = id })
                            Column(Modifier.weight(1f)) {
                                Text(models.label(id) + if (id == c.str("model")) " · par défaut" else "", fontWeight = FontWeight.Medium)
                                models.detail(id)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = error == null && chosen != null && cfg?.let { ServerModels(it).present.contains(chosen) } == true,
                onClick = { chosen?.let(onPick) }) { Text("Upscaler") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** Choix de la définition voulue et du sort de l'original avant une réduction (nav.js pickDownscaleOptions). */
@Composable
fun DownscaleDialog(limits: UpscaleLimits, title: String, text: String, side: Int, onDismiss: () -> Unit, onPick: (Int, Boolean) -> Unit) {
    val choices = limits.downscaleChoices
    val below = if (side > 0) choices.filter { it < side } else choices
    var target by remember { mutableStateOf(if (2400 in below) 2400 else below.lastOrNull() ?: choices.firstOrNull()) }
    var keep by remember { mutableStateOf(true) }
    val hints = mapOf(2000 to "Tablette, liseuse : gain maximal", 2400 to "Net sur un grand écran, bon compromis", 2800 to "Grand écran en double page", 3200 to "Très grand écran : gain plus faible")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (choices.isEmpty()) Text("Aucune définition de réduction possible au-dessus du seuil de basse définition", color = MaterialTheme.colorScheme.error)
                Text("Définition voulue" + if (side > 0) " (pages actuelles : $side px)" else "", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                choices.forEach { n ->
                    Row(Modifier.fillMaxWidth().clickable { target = n }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = target == n, onClick = { target = n })
                        Column {
                            Text("$n px", fontWeight = FontWeight.Medium)
                            Text(if (side in 1..n) "Pages déjà plus petites : seulement réencodées" else hints[n] ?: "Plus grand côté des pages",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Text("Fichier d'origine", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                listOf(true to ("Garder l'original" to "Restaurable à tout moment ; la place n'est gagnée qu'en le supprimant ensuite (Actions → Upscaling)."),
                    false to ("Supprimer l'original" to "La place est gagnée tout de suite, mais on ne peut plus revenir en arrière.")).forEach { (k, t) ->
                    Row(Modifier.fillMaxWidth().clickable { keep = k }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = keep == k, onClick = { keep = k })
                        Column {
                            Text(t.first, fontWeight = FontWeight.Medium)
                            Text(t.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = target != null, onClick = { target?.let { onPick(it, keep) } }) { Text("Réduire") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
