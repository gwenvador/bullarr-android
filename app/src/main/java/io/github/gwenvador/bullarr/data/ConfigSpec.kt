package io.github.gwenvador.bullarr.data

import org.json.JSONArray
import org.json.JSONObject

// Configuration de Bullarr : chaque réglage est lu avec GET <path> et écrit avec POST <path>.
// Les formats et les règles d'écriture viennent de blueprints/*/routes.py (voir README).

sealed interface Field {
    /** Chemin dans le JSON ; « a.b » désigne l'objet imbriqué a, clé b. */
    val key: String
    val label: String
}

data class FText(override val key: String, override val label: String, val hint: String? = null, val url: Boolean = false) : Field
data class FNumber(override val key: String, override val label: String) : Field
data class FSwitch(override val key: String, override val label: String, val help: String? = null) : Field
/** Secret : jamais relu en clair. Laissé vide = inchangé ; le serveur ne l'écrase que s'il est renseigné. */
data class FSecret(override val key: String, override val label: String) : Field
data class FChoice(override val key: String, override val label: String, val options: List<Pair<String, String>>) : Field
/** Liste ordonnée dont seul l'ordre se règle (priorité des sources). */
data class FOrder(override val key: String, override val label: String, val names: Map<String, String>, val help: String? = null) : Field
/** Sous-ensemble choisi parmi des options. */
data class FMulti(override val key: String, override val label: String, val options: List<Pair<String, String>>) : Field
/** Liste de valeurs saisies séparées par des virgules. */
data class FList(override val key: String, override val label: String, val hint: String? = null) : Field

data class TestCall(val post: Boolean, val path: String, val label: String = "Tester", val note: String? = null)

data class Integration(
    val id: String,
    val title: String,
    val path: String,
    val fields: List<Field>,
    val test: TestCall? = null,
    val help: String? = null,
    /** Phrase d'état tirée de la réponse (ex. nombre d'indexeurs). */
    val info: ((JSONObject) -> String?)? = null,
)

val SOURCE_NAMES = linkedMapOf(
    "prowlarr" to "Prowlarr", "ebdz" to "EBDZ", "telegram" to "Telegram",
    "fourtoutici" to "Fourtoutici", "vk" to "VK", "annas_archive" to "Anna's Archive",
)

private val ENABLED = FSwitch("enabled", "Activé")

val CLIENTS = listOf(
    Integration(
        "amule", "aMule / eMule", "api/emule/config",
        listOf(ENABLED, FChoice("type", "Type de client", listOf("amule" to "aMule (Linux/Mac)", "emule" to "eMule (Windows)")),
            FText("host", "Hôte"), FNumber("ec_port", "Port EC"), FSecret("password", "Mot de passe EC")),
        TestCall(false, "api/emule/test"),
    ),
    Integration(
        "qbittorrent", "qBittorrent", "api/qbittorrent/config",
        listOf(ENABLED, FText("url", "URL", "https://qbittorrent.exemple.fr", url = true), FNumber("port", "Port"),
            FText("username", "Utilisateur"), FSecret("password", "Mot de passe"),
            FText("default_category", "Catégorie par défaut", "BD")),
        TestCall(true, "api/qbittorrent/test"),
    ),
    Integration(
        "rtorrent", "rTorrent", "api/rtorrent/config",
        listOf(ENABLED, FText("url", "URL", url = true), FNumber("port", "Port"), FText("rpc_path", "Chemin RPC", "/RPC2"),
            FText("username", "Utilisateur"), FSecret("password", "Mot de passe")),
        TestCall(true, "api/rtorrent/test"),
    ),
    Integration(
        "deluge", "Deluge", "api/deluge/config",
        listOf(ENABLED, FText("url", "URL", url = true), FNumber("port", "Port"), FSecret("password", "Mot de passe")),
        TestCall(true, "api/deluge/test"),
    ),
    Integration(
        "shelfmark", "Shelfmark", "api/shelfmark/config",
        listOf(ENABLED, FText("base_url", "URL", "https://shelfmark.exemple.fr", url = true), FText("username", "Utilisateur"), FSecret("password", "Mot de passe")),
        TestCall(true, "api/shelfmark/test"),
    ),
    Integration(
        "komga", "Komga", "api/komga/config",
        listOf(ENABLED, FText("url", "URL", "komga.exemple.fr", url = true), FSecret("api_key", "Clé API")),
        TestCall(true, "api/komga/test"),
    ),
)

val INDEXERS = listOf(
    Integration(
        "prowlarr", "Prowlarr", "api/prowlarr/config",
        listOf(ENABLED, FText("url", "URL", "https://prowlarr.exemple.fr", url = true), FNumber("port", "Port"), FSecret("api_key", "Clé API")),
        TestCall(false, "api/prowlarr/test"),
        help = "Le choix des indexeurs à interroger se fait dans l'interface web.",
        info = { c -> c.optJSONArray("selected_indexers")?.length()?.let { "$it indexeur(s) sélectionné(s)" } },
    ),
    Integration(
        "fourtoutici", "Fourtoutici", "api/fourtoutici/config",
        listOf(ENABLED, FText("base_url", "URL du site", url = true)),
        TestCall(false, "api/fourtoutici/test"),
    ),
    Integration(
        "annas", "Anna's Archive", "api/annas-archive/config",
        listOf(ENABLED, FText("base_url", "URL du site", url = true)),
        TestCall(false, "api/annas-archive/test"),
    ),
    Integration(
        "vk", "VK — Au Phil des Bulles", "api/vk/config",
        listOf(ENABLED, FText("base_url", "URL du forum", url = true)),
        TestCall(false, "api/vk/test"),
        info = { c -> c.optJSONObject("index")?.int("series")?.let { "Index : $it séries" } },
    ),
)

val NOTIFICATIONS = listOf(
    Integration(
        "telegram", "Telegram (bot)", "api/telegram/config",
        listOf(ENABLED, FSecret("bot_token", "Jeton du bot"), FText("chat_id", "Identifiant du chat"),
            FSwitch("notify_import_completed", "Import terminé"), FSwitch("notify_import_available", "Fichier prêt à importer")),
        TestCall(true, "api/telegram/test", label = "Envoyer un message test", note = "Envoie un vrai message au chat configuré (réglages enregistrés)."),
    ),
)

private const val IMPORT = "api/import/config"

val IMPORTS = listOf(
    Integration(
        "import", "Import automatique", IMPORT,
        listOf(
            FSwitch("auto_import_enabled", "Importer automatiquement", "Importe un fichier dès qu'il est prêt et assigné à une série."),
            FSwitch("auto_assign_enabled", "Assigner automatiquement"),
            FSwitch("auto_convert_to_cbz", "Convertir en CBZ", "PDF et ZIP nus convertis à l'import."),
            FList("monitored_extensions", "Extensions surveillées", ".cbz, .cbr, .zip, .rar, .pdf"),
        ),
    ),
)

val SEARCH = listOf(
    Integration(
        "order", "Ordre des sources", IMPORT,
        listOf(FOrder("auto_acquire_sources", "Priorité de la recherche automatique", SOURCE_NAMES,
            "La source du haut est essayée en premier.")),
    ),
    Integration(
        "blocked", "Extensions désactivées", IMPORT,
        listOf(FList("blocked_search_extensions", "Extensions ignorées dans les résultats", "epub, mobi, mkv")),
    ),
    Integration(
        "series", "Création de séries", IMPORT,
        listOf(FSwitch("create_series_folder_on_add", "Créer le dossier à l'ajout d'une série")),
    ),
    Integration(
        "auto", "Recherche automatique", IMPORT,
        listOf(
            FSwitch("auto_acquire_on_add_enabled", "Chercher à l'ajout d'une série"),
            FSwitch("auto_acquire_pack_search_enabled", "Chercher aussi les packs"),
            FSwitch("prioritize_black_and_white", "Préférer le noir et blanc"),
        ),
    ),
)

val MONITOR = listOf(
    Integration(
        "monitor", "Surveillance des volumes", "api/missing-monitor/config",
        listOf(
            FSwitch("monitor_missing_volumes.enabled", "Volumes manquants", "Valable pour les séries activées dans Actions → Surveillance."),
            FChoice("monitor_missing_volumes.action", "Quand un fichier correspond à un tome manquant",
                listOf("download" to "Télécharger automatiquement", "notify" to "Notifier sur Telegram")),
            FSwitch("quality_upgrade.enabled", "Upgrade de qualité", "Remplace un fichier par un meilleur."),
            FNumber("quality_upgrade.min_size_increase_percent", "Augmentation de taille minimale (%)"),
        ),
    ),
)

// --- Lecture / écriture JSON -------------------------------------------------

fun JSONObject.getPath(path: String): Any? {
    var cur: Any? = this
    for (part in path.split('.')) {
        cur = (cur as? JSONObject)?.opt(part)?.takeUnless { it == JSONObject.NULL } ?: return null
    }
    return cur
}

fun JSONObject.putPath(path: String, value: Any?) {
    val parts = path.split('.')
    var cur = this
    for (p in parts.dropLast(1)) cur = cur.optJSONObject(p) ?: JSONObject().also { cur.put(p, it) }
    if (value == null) cur.remove(parts.last()) else cur.put(parts.last(), value)
}

private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

/** Valeur de départ d'un champ, telle qu'affichée : String, Boolean ou List<String>. */
fun initialValue(f: Field, o: JSONObject): Any = when (f) {
    is FSwitch -> when (val v = o.getPath(f.key)) { is Boolean -> v; is Number -> v.toInt() != 0; else -> false }
    is FSecret -> ""
    is FList -> (o.getPath(f.key) as? JSONArray).strings().joinToString(", ")
    is FOrder, is FMulti -> (o.getPath(f.key) as? JSONArray).strings()
    else -> o.getPath(f.key)?.toString() ?: ""
}

fun secretIsSet(f: FSecret, o: JSONObject): Boolean = when (val v = o.getPath(f.key)) {
    is Boolean -> v
    is String -> v.isNotEmpty()
    else -> false
}

/**
 * Corps du POST : l'état actuel du serveur, avec seulement les champs modifiés appliqués.
 * Les secrets non modifiés sont envoyés vides : le serveur les laisse alors tels quels.
 */
fun buildPayload(spec: Integration, fresh: JSONObject, initial: Map<String, Any>, edited: Map<String, Any>): JSONObject {
    val body = JSONObject(fresh.toString())
    for (f in spec.fields) {
        val now = edited[f.key] ?: continue
        val changed = now != initial[f.key]
        when (f) {
            is FSecret -> body.putPath(f.key, if (changed) now as String else "")
            else -> if (!changed) continue else when (f) {
                is FSwitch -> body.putPath(f.key, now as Boolean)
                is FNumber -> {
                    val t = (now as String).trim()
                    if (t.isEmpty()) body.putPath(f.key, null)
                    else body.putPath(f.key, t.toIntOrNull() ?: throw ApiException("« ${f.label} » doit être un nombre entier."))
                }
                is FList -> body.putPath(f.key, JSONArray((now as String).split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }))
                is FOrder -> body.putPath(f.key, JSONArray(now as List<*>))
                is FMulti -> {
                    @Suppress("UNCHECKED_CAST") val sel = now as List<String>
                    val kept = fresh.getPath(f.key).let { (it as? JSONArray).strings() }.filter { it in sel }
                    body.putPath(f.key, JSONArray(kept + sel.filter { it !in kept }))
                }
                is FText -> body.putPath(f.key, (now as String).trim())
                is FChoice -> body.putPath(f.key, now as String)
                is FSecret -> {}
            }
        }
    }
    return body
}
