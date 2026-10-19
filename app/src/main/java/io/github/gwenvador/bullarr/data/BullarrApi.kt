package io.github.gwenvador.bullarr.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/** Erreur destinée à l'utilisateur : [message] est déjà lisible, en français. */
class ApiException(message: String, val httpCode: Int? = null) : Exception(message)

/**
 * Client des routes /api/ de Bullarr. La clé part dans l'en-tête X-Api-Key ; Accept:
 * application/json fait renvoyer à Bullarr un 401 JSON plutôt qu'une redirection vers
 * la page de connexion quand la clé manque.
 */
class BullarrApi(rawBaseUrl: String, private val apiKey: String) {

    val baseUrl: HttpUrl = normalizeBaseUrl(rawBaseUrl)

    fun absoluteUrl(path: String): String =
        baseUrl.toString().trimEnd('/') + "/" + path.trimStart('/')

    suspend fun getJson(path: String, query: Map<String, String> = emptyMap()): Any =
        execute("GET", path, query, null)

    suspend fun getObject(path: String, query: Map<String, String> = emptyMap()): JSONObject =
        getJson(path, query) as? JSONObject
            ?: throw ApiException("GET $path : réponse inattendue (un objet JSON était attendu).")

    suspend fun getArray(path: String, query: Map<String, String> = emptyMap()): JSONArray =
        getJson(path, query) as? JSONArray
            ?: throw ApiException("GET $path : réponse inattendue (une liste JSON était attendue).")

    suspend fun postObject(path: String, body: JSONObject): JSONObject =
        execute("POST", path, emptyMap(), body) as? JSONObject
            ?: throw ApiException("POST $path : réponse inattendue (un objet JSON était attendu).")

    suspend fun deleteObject(path: String, body: JSONObject? = null): JSONObject =
        execute("DELETE", path, emptyMap(), body) as? JSONObject
            ?: throw ApiException("DELETE $path : réponse inattendue (un objet JSON était attendu).")

    /** Image binaire (page du lecteur). Une réponse JSON d'erreur devient une ApiException lisible. */
    suspend fun getBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder().addPathSegments(path.trimStart('/')).build()
        val builder = Request.Builder().url(url).header("Accept", "image/*")
        if (apiKey.isNotBlank()) builder.header("X-Api-Key", apiKey.trim())
        val label = "GET /${path.trimStart('/')}"
        val response = try {
            client.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw ApiException(networkMessage(e, url))
        }
        response.use { resp ->
            val type = resp.header("Content-Type").orEmpty()
            if (resp.isSuccessful && type.startsWith("image/")) {
                return@withContext try { resp.body?.bytes() ?: ByteArray(0) } catch (e: IOException) {
                    throw ApiException("$label : téléchargement interrompu (${e.message ?: e.javaClass.simpleName}).")
                }
            }
            val text = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
            val detail = runCatching { extractError(JSONObject(text)) }.getOrNull()
                ?: if (resp.isSuccessful) "réponse inattendue (type $type au lieu d'une image)" else "erreur HTTP ${resp.code}"
            val hint = when (resp.code) {
                401 -> "\nVérifiez la clé API (Configuration → Connexion)."
                in 500..599 -> "\nErreur interne de Bullarr (HTTP ${resp.code})."
                else -> ""
            }
            throw ApiException("$label : $detail$hint", resp.code)
        }
    }

    private suspend fun execute(method: String, path: String, query: Map<String, String>, body: JSONObject?): Any =
        withContext(Dispatchers.IO) {
            val url = baseUrl.newBuilder().apply {
                addPathSegments(path.trimStart('/'))
                query.forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            val builder = Request.Builder().url(url).header("Accept", "application/json")
            if (apiKey.isNotBlank()) builder.header("X-Api-Key", apiKey.trim())
            if (body != null) builder.method(method, body.toString().toRequestBody(JSON))
            else builder.method(method, null)

            val label = "$method /${path.trimStart('/')}"
            val response = try {
                client.newCall(builder.build()).execute()
            } catch (e: IOException) {
                throw ApiException(networkMessage(e, url))
            }
            response.use { resp ->
                val text = try { resp.body?.string().orEmpty() } catch (e: IOException) {
                    throw ApiException("$label : lecture de la réponse interrompue (${e.message ?: e.javaClass.simpleName}).")
                }
                val contentType = resp.header("Content-Type").orEmpty()
                val parsed: Any? = try {
                    if (text.isBlank()) null else JSONTokener(text).nextValue()
                } catch (_: JSONException) { null }

                if (parsed !is JSONObject && parsed !is JSONArray) {
                    val looksHtml = contentType.contains("html") || text.trimStart().startsWith("<")
                    val reason = when {
                        looksHtml && (resp.isRedirect || resp.priorResponse != null || text.contains("login", true)) ->
                            "le serveur a renvoyé la page de connexion au lieu de JSON. Vérifiez la clé API."
                        looksHtml -> "le serveur a renvoyé une page HTML au lieu de JSON (HTTP ${resp.code}). " +
                            "L'URL pointe-t-elle bien sur Bullarr (sans chemin après le port) ?"
                        text.isBlank() -> "réponse vide (HTTP ${resp.code})."
                        else -> "réponse illisible (HTTP ${resp.code}) : ${text.take(160)}"
                    }
                    throw ApiException("$label : $reason", resp.code)
                }

                val error = (parsed as? JSONObject)?.let { extractError(it) }
                val failed = (parsed as? JSONObject)?.let { it.has("success") && !it.optBoolean("success", true) } == true
                if (!resp.isSuccessful || failed) {
                    val detail = error ?: "erreur HTTP ${resp.code}"
                    val hint = when (resp.code) {
                        401 -> "\nGénérez une clé dans Bullarr : Paramètres → Login → Accès API."
                        403 -> "\nAccès refusé par le serveur."
                        404 -> "\nRoute introuvable sur ce serveur (version de Bullarr trop ancienne ?)."
                        in 500..599 -> "\nErreur interne de Bullarr (HTTP ${resp.code})."
                        else -> ""
                    }
                    throw ApiException("$label : $detail$hint", resp.code)
                }
                parsed
            }
        }

    private fun extractError(obj: JSONObject): String? {
        val raw = obj.opt("error") ?: obj.opt("message") ?: return null
        return when (raw) {
            JSONObject.NULL -> null
            is String -> raw.ifBlank { null }
            else -> raw.toString()
        }
    }

    private fun networkMessage(e: IOException, url: HttpUrl): String = when (e) {
        is UnknownHostException -> "Serveur introuvable : « ${url.host} ». Vérifiez l'URL et que le téléphone " +
            "est sur le bon réseau (Wi-Fi local, VPN…)."
        is ConnectException -> "Connexion refusée par ${url.host}:${url.port}. Bullarr est-il démarré et le port correct ?"
        is SocketTimeoutException -> "Le serveur ne répond pas (délai dépassé) : ${url.host}:${url.port}."
        is SSLException -> "Erreur HTTPS (${e.message ?: "certificat"}). Si le serveur n'a pas de certificat, utilisez http://."
        else -> "Erreur réseau vers ${url.host} : ${e.message ?: e.javaClass.simpleName}"
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .build()

        /** « monserveur:4000 » → « http://monserveur:4000/ » ; lève ApiException si invalide. */
        fun normalizeBaseUrl(raw: String): HttpUrl {
            var s = raw.trim()
            if (s.isEmpty()) throw ApiException("Indiquez l'URL du serveur Bullarr.")
            if (!s.contains("://")) s = "http://$s"
            if (!s.endsWith("/")) s += "/"
            return s.toHttpUrlOrNull()
                ?: throw ApiException("URL invalide : « ${raw.trim()} ». Exemple : http://192.168.1.10:4000")
        }
    }
}

fun Throwable.readable(): String = when (this) {
    is ApiException -> message ?: "Erreur inconnue"
    is JSONException -> "Réponse inattendue du serveur : ${message ?: "JSON invalide"}"
    else -> "${javaClass.simpleName} : ${message ?: "erreur inconnue"}"
}
