package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.gwenvador.bullarr.ui.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.SeriesSummary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Filtres de la bibliothèque : plusieurs valeurs d'un même groupe = « ou », groupes différents = « et ». */
data class LibraryFilters(
    val types: Set<String> = emptySet(),
    val origins: Set<String> = emptySet(),
    val decades: Set<Int> = emptySet(),
    val updated: Set<String> = emptySet(),
    val volumes: Set<String> = emptySet(),
    val sizes: Set<String> = emptySet(),
    val votes: Set<String> = emptySet(),
) {
    val activeCount get() = listOf(types, origins, decades, updated, volumes, sizes, votes).count { it.isNotEmpty() }
    val isEmpty get() = activeCount == 0

    fun matches(s: SeriesSummary, now: LocalDateTime = LocalDateTime.now()): Boolean =
        (types.isEmpty() || typeOf(s) in types) &&
            (origins.isEmpty() || (s.origin ?: "Autre") in origins) &&
            (decades.isEmpty() || s.yearInt?.let { it / 10 * 10 in decades } == true) &&
            (updated.isEmpty() || updated.any { updatedMatches(it, s, now) }) &&
            (votes.isEmpty() || votes.any { voteBucket(it, s.votes) }) &&
            (volumes.isEmpty() || volumes.any { volumeBucket(it, s.volumeCount) }) &&
            (sizes.isEmpty() || s.sizeBytes?.let { b -> sizes.any { sizeBucket(it, b) } } == true)
}

val TYPE_LABELS = linkedMapOf("serie" to "Série", "oneshot" to "One-shot", "integrale" to "Intégrale")
val UPDATED_LABELS = linkedMapOf("7" to "7 derniers jours", "30" to "30 derniers jours", "old" to "Plus ancien")
val VOLUME_LABELS = linkedMapOf("0" to "Aucun", "1" to "1", "2-5" to "2 à 5", "6-10" to "6 à 10", "11-20" to "11 à 20", "21+" to "21 et plus")
val VOTE_LABELS = linkedMapOf("0" to "Aucun vote", "1-10" to "1 à 10", "11-50" to "11 à 50", "51-100" to "51 à 100", "100+" to "Plus de 100")
val SIZE_LABELS = linkedMapOf("<100" to "< 100 Mo", "100-500" to "100 à 500 Mo", "500-1000" to "500 Mo à 1 Go", "1-5" to "1 à 5 Go", ">5" to "> 5 Go")

fun typeOf(s: SeriesSummary) = when {
    s.isIntegral -> "integrale"
    s.isOneshot -> "oneshot"
    else -> "serie"
}

private val SCAN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

private fun updatedMatches(key: String, s: SeriesSummary, now: LocalDateTime): Boolean {
    val at = s.lastScanned?.let { runCatching { LocalDateTime.parse(it.take(19), SCAN) }.getOrNull() } ?: return key == "old"
    val days = java.time.Duration.between(at, now).toDays()
    return when (key) { "7" -> days <= 7; "30" -> days <= 30; else -> days > 30 }
}

fun volumeBucket(key: String, n: Int) = when (key) {
    "0" -> n == 0; "1" -> n == 1; "2-5" -> n in 2..5; "6-10" -> n in 6..10; "11-20" -> n in 11..20; else -> n >= 21
}

fun voteBucket(key: String, n: Int) = when (key) {
    "0" -> n == 0; "1-10" -> n in 1..10; "11-50" -> n in 11..50; "51-100" -> n in 51..100; else -> n > 100
}

private const val MO = 1_000_000L
fun sizeBucket(key: String, b: Long) = when (key) {
    "<100" -> b < 100 * MO; "100-500" -> b in 100 * MO until 500 * MO; "500-1000" -> b in 500 * MO until 1000 * MO
    "1-5" -> b in 1000 * MO until 5000 * MO; else -> b >= 5000 * MO
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryFilterSheet(all: List<SeriesSummary>, filters: LibraryFilters, resultCount: Int, onChange: (LibraryFilters) -> Unit, onDismiss: () -> Unit) {
    val decades = all.mapNotNull { it.yearInt?.let { y -> y / 10 * 10 } }.distinct().sortedDescending()
    val origins = all.map { it.origin ?: "Autre" }.distinct().sorted()
    val hasSizes = all.any { it.sizeBytes != null }

    fun <T> toggle(set: Set<T>, v: T) = if (v in set) set - v else set + v

    @Composable
    fun <T> Group(title: String, options: List<Pair<T, String>>, selected: Set<T>, count: (T) -> Int, flags: Boolean = false, set: (Set<T>) -> Unit) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (k, label) ->
                    FilterChip(selected = k in selected, onClick = { set(toggle(selected, k)) }, label = {
                        if (flags && hasLogo(label.lowercase())) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Logo(label.lowercase(), label, 20.dp, tip = false)
                            Text("${count(k)}")
                        } else Text("$label · ${count(k)}")
                    })
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtres", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onChange(LibraryFilters()) }, enabled = !filters.isEmpty) { Text("Réinitialiser") }
                }
            }
            item { Group("Type", TYPE_LABELS.toList(), filters.types, { k -> all.count { typeOf(it) == k } }) { onChange(filters.copy(types = it)) } }
            if (origins.isNotEmpty()) item {
                Group("Genre", origins.map { it to it }, filters.origins, { k -> all.count { (it.origin ?: "Autre") == k } }, flags = true) { onChange(filters.copy(origins = it)) }
            }
            item {
                Group("Année de parution", decades.map { it to "${it}s" }, filters.decades, { k -> all.count { s -> s.yearInt?.let { it / 10 * 10 == k } == true } }) { onChange(filters.copy(decades = it)) }
            }
            item {
                val now = LocalDateTime.now()
                Group("Dernière mise à jour", UPDATED_LABELS.toList(), filters.updated, { k -> all.count { updatedMatches(k, it, now) } }) { onChange(filters.copy(updated = it)) }
            }
            item { Group("Votes", VOTE_LABELS.toList(), filters.votes, { k -> all.count { voteBucket(k, it.votes) } }) { onChange(filters.copy(votes = it)) } }
            item { Group("Nombre de volumes", VOLUME_LABELS.toList(), filters.volumes, { k -> all.count { volumeBucket(k, it.volumeCount) } }) { onChange(filters.copy(volumes = it)) } }
            if (hasSizes) item {
                Group("Taille", SIZE_LABELS.toList(), filters.sizes, { k -> all.count { s -> s.sizeBytes?.let { sizeBucket(k, it) } == true } }) { onChange(filters.copy(sizes = it)) }
            } else item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Taille", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Indisponible : cette version de Bullarr ne renvoie pas la taille par série. Le filtre apparaîtra dès qu'elle sera fournie.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text("Voir $resultCount série(s)") }
            }
        }
    }
}

/** Tri de la bibliothèque ; l'ordre par défaut de chaque critère est le plus utile (populaire, récent, lourd d'abord). */
enum class LibrarySort(val label: String, val defaultDesc: Boolean) {
    Title("Alphabétique", false), Popularity("Popularité", true), Date("Date", true), Size("Taille", true);

    fun sorted(list: List<SeriesSummary>, desc: Boolean): List<SeriesSummary> {
        val byTitle = compareBy<SeriesSummary> { it.title.lowercase() }
        val c: Comparator<SeriesSummary> = when (this) {
            Title -> byTitle
            Popularity -> compareBy<SeriesSummary> { it.votes }.thenBy(byTitle) { it }
            Date -> compareBy<SeriesSummary> { it.lastScanned ?: "" }.thenBy(byTitle) { it }
            Size -> compareBy<SeriesSummary> { it.sizeBytes ?: -1L }.thenBy(byTitle) { it }
        }
        return list.sortedWith(if (desc) c.reversed() else c)
    }
}
