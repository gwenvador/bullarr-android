package io.github.gwenvador.bullarr.data

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Moteur natif : Real-ESRGAN ou Real-CUGAN (même code d'appel, réseaux différents). */
enum class UpscaleEngine(val code: Int) { ESRGAN(0), CUGAN(1) }

/** Fichier d'un modèle téléchargeable : adresse figée sur un commit, taille et empreinte vérifiées. */
class ModelFile(val url: String, val size: Long, val sha256: String)

private const val UPSCAYL = "https://raw.githubusercontent.com/upscayl/upscayl/a00d55fee90e0f9435d5eaa86e76700df8199af8/resources/models"
private const val UPSCAYL_CUSTOM = "https://raw.githubusercontent.com/upscayl/custom-models/4b6d2cfa59c7442af115dfc6e50fd8d7d40b96ef/models"
private const val CUGAN = "https://raw.githubusercontent.com/nihui/realcugan-ncnn-vulkan/395302c5c70f1bff604c974e92e0a87e45c9f9ee/models"

/**
 * Catalogue : « Rapide » est intégré à l'app, les autres se téléchargent depuis les dépôts publics des projets
 * (upscayl, Real-CUGAN) dans le stockage de l'app. Les textes ne donnent pas de durée : elle dépend trop du téléphone.
 */
enum class UpscaleModel(
    val id: String,
    val label: String,
    val detail: String,
    val engine: UpscaleEngine,
    val scale: Int,
    val heavy: Boolean = false,
    /** Débruitage Real-CUGAN : -1 conservateur, 0 aucun, 1 à 3. */
    val noise: Int = -1,
    /** Nom des fichiers intégrés (assets/upscale), ou null pour un modèle à télécharger. */
    val asset: String? = null,
    val param: ModelFile? = null,
    val bin: ModelFile? = null,
) {
    Fast(
        "fast", "Rapide", "Nettoie le grain et le flou JPEG, lettrage net. Conseillé.",
        UpscaleEngine.ESRGAN, 2, asset = "realesr-animevideov3-x2",
    ),
    General(
        "general", "Général", "Real-ESRGAN polyvalent ×4 : traits très nets, un peu plus lent que Rapide.",
        UpscaleEngine.ESRGAN, 4,
        param = ModelFile("$UPSCAYL_CUSTOM/RealESRGAN_General_x4_v3.param", 5019, "22174924330297357434ad21ed0af7f4b820008d2a502b492754d130d4142714"),
        bin = ModelFile("$UPSCAYL_CUSTOM/RealESRGAN_General_x4_v3.bin", 2435272, "85ee266b632a765a725425ba6a5620c088c8aa2939a03063b2d83b3462724cc1"),
    ),
    GeneralDenoise(
        "general_wdn", "Général débruité", "Comme Général, en lissant davantage le grain des scans abîmés.",
        UpscaleEngine.ESRGAN, 4,
        param = ModelFile("$UPSCAYL_CUSTOM/RealESRGAN_General_WDN_x4_v3.param", 5019, "22174924330297357434ad21ed0af7f4b820008d2a502b492754d130d4142714"),
        bin = ModelFile("$UPSCAYL_CUSTOM/RealESRGAN_General_WDN_x4_v3.bin", 2435272, "74eae91a6230cbe5b931be6c49141faaff0034976933d4da9accb77c329c8a78"),
    ),
    CuganSe(
        "cugan_se", "Real-CUGAN", "Fidèle au dessin d'origine, garde la trame et les textures.",
        UpscaleEngine.CUGAN, 2, noise = -1,
        param = ModelFile("$CUGAN/models-se/up2x-conservative.param", 4761, "4b3b5c5710ad1d00503c8243417aa75f13fe497723e8b4952e8b1abfc08f9b84"),
        bin = ModelFile("$CUGAN/models-se/up2x-conservative.bin", 2573648, "91c72c136e7ff8556323d4449c5ceeff22d9829bf8463a01137cadb8d59b84a0"),
    ),
    CuganSeDenoise(
        "cugan_se_dn3", "Real-CUGAN débruité", "Aplats propres et traits nets, efface le grain du papier.",
        UpscaleEngine.CUGAN, 2, noise = 3,
        param = ModelFile("$CUGAN/models-se/up2x-denoise3x.param", 4761, "4b3b5c5710ad1d00503c8243417aa75f13fe497723e8b4952e8b1abfc08f9b84"),
        bin = ModelFile("$CUGAN/models-se/up2x-denoise3x.bin", 2573648, "610ebae63f388fe6f39615cc5f4eaeb038d5aa6ba8b299ba3b989f7f7a1a280a"),
    ),
    CuganPro(
        "cugan_pro", "Real-CUGAN Pro", "Version plus récente de Real-CUGAN, un peu plus nette.",
        UpscaleEngine.CUGAN, 2, noise = -1,
        param = ModelFile("$CUGAN/models-pro/up2x-conservative.param", 4765, "0ce323af8f840a68f7d43df195cb413184583f8ee3c43a3c1fdcb1c7b7f56821"),
        bin = ModelFile("$CUGAN/models-pro/up2x-conservative.bin", 2573648, "087b4f1836a084f432752503c9e12246a4f8446cfa2c5c919fcf490961b5b4f1"),
    ),
    CuganProDenoise(
        "cugan_pro_dn3", "Real-CUGAN Pro débruité", "Real-CUGAN Pro avec débruitage fort.",
        UpscaleEngine.CUGAN, 2, noise = 3,
        param = ModelFile("$CUGAN/models-pro/up2x-denoise3x.param", 4765, "0ce323af8f840a68f7d43df195cb413184583f8ee3c43a3c1fdcb1c7b7f56821"),
        bin = ModelFile("$CUGAN/models-pro/up2x-denoise3x.bin", 2573648, "815fe3bf3f14f010f4f7a26ab6d2c47330d80c789720639088ccfaf013cec651"),
    ),
    Quality(
        "quality", "Qualité", "Real-ESRGAN anime ×4 : traits et lettrage les plus fins, aplats lissés. " +
            "Beaucoup plus lent : les pages suivantes sont préparées pendant la lecture.",
        UpscaleEngine.ESRGAN, 4, heavy = true,
        param = ModelFile("$UPSCAYL/digital-art-4x.param", 30290, "2b8fb6e0ae4d2d85704ca08c119a2f5ea40add4f2ecd512eb7f4cd44b6127ed4"),
        bin = ModelFile("$UPSCAYL/digital-art-4x.bin", 8943500, "fe01c269cfd10cdef8e018ab66ebe750cf79c7af4d1f9c16c737e1295229bacc"),
    );

    val builtin get() = asset != null
    /** Taille du téléchargement, en octets. */
    val downloadSize get() = (param?.size ?: 0) + (bin?.size ?: 0)
}

/**
 * Upscaling des pages basse définition sur le processeur graphique (ncnn Vulkan).
 * Une page à la fois, dans l'ordre des demandes. Réglages et modèles téléchargés propres à l'appareil.
 */
object Upscaler {
    /**
     * Au-delà, la page est déjà assez fine : on ne l'agrandit pas. Réglé sur le serveur (Configuration → Upscaling,
     * GET /api/upscale/threshold) ; 1600 tant qu'il n'a pas répondu ou s'il est plus ancien. Dernière valeur gardée hors ligne.
     */
    var maxSourceSide by androidx.compose.runtime.mutableIntStateOf(1600)
        private set
    /** Plus grand côté d'une page agrandie (comme PageCache) : la sortie ×4 est réduite à cette taille. */
    private const val MAX_RESULT_SIDE = 3200

    /** Réglage du lecteur, observé par l'affichage des pages. */
    var enabled by mutableStateOf(false)
        private set
    var model by mutableStateOf(UpscaleModel.Fast)
        private set
    /** Raison pour laquelle l'appareil ne peut pas agrandir les pages (null tant que rien n'a échoué). */
    var unavailable by mutableStateOf<String?>(null)
        private set

    /** Modèles téléchargés présents sur l'appareil. */
    val installed = mutableStateMapOf<UpscaleModel, Boolean>()
    /** Téléchargements en cours : progression de 0 à 1. */
    val downloading = mutableStateMapOf<UpscaleModel, Float>()
    /** Dernière erreur de téléchargement, par modèle. */
    val downloadError = mutableStateMapOf<UpscaleModel, String>()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<UpscaleModel, Job>()
    private var loaded: UpscaleModel? = null
    private var libLoaded = false
    private var prefsRead = false

    @JvmStatic private external fun nativeInit(param: String, bin: String, engine: Int, scale: Int, noise: Int, heavy: Boolean): String?
    @JvmStatic private external fun nativeUpscale(src: Bitmap, dst: Bitmap): Int
    @JvmStatic private external fun nativeCancel(on: Boolean)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("reader", Context.MODE_PRIVATE)
    private fun dir(context: Context) = File(context.applicationContext.filesDir, "upscale")
    private fun modelDir(context: Context, m: UpscaleModel) = File(dir(context), "models/${m.id}")

    fun isInstalled(m: UpscaleModel) = m.builtin || installed[m] == true

    /** Lit les réglages enregistrés et l'inventaire des modèles téléchargés (une fois). */
    fun load(context: Context) {
        if (prefsRead) return
        migrateOldQuality(context)
        UpscaleModel.entries.filter { !it.builtin }.forEach { m ->
            val d = modelDir(context, m)
            installed[m] = File(d, "model.param").length() == m.param!!.size && File(d, "model.bin").length() == m.bin!!.size
        }
        val p = prefs(context)
        maxSourceSide = p.getInt("upscale_max_side", 1600)
        enabled = p.getBoolean("upscale", false)
        model = UpscaleModel.entries.firstOrNull { it.id == p.getString("upscale_model", null) }?.takeIf { isInstalled(it) }
            ?: UpscaleModel.Fast
        prefsRead = true
    }

    /** « Qualité » était intégré aux premières versions : sa copie déjà extraite devient le modèle téléchargé. */
    private fun migrateOldQuality(context: Context) {
        val old = listOf("param", "bin").map { File(dir(context), "realesrgan-x4plus-anime.$it") }
        if (old.none { it.exists() }) return
        val d = modelDir(context, UpscaleModel.Quality).apply { mkdirs() }
        val m = UpscaleModel.Quality
        val ok = old[0].length() == m.param!!.size && old[1].length() == m.bin!!.size &&
            sha256(old[0]) == m.param.sha256 && sha256(old[1]) == m.bin.sha256 &&
            old[0].renameTo(File(d, "model.param")) && old[1].renameTo(File(d, "model.bin"))
        if (!ok) d.deleteRecursively()
        old.forEach { it.delete() }
        File(dir(context), "realesrgan-x4plus-anime.version").delete()
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { i -> val buf = ByteArray(64 * 1024); while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Lit le seuil réglé sur le serveur ; garde la valeur connue si la route manque ou échoue. */
    suspend fun syncThreshold(context: Context, api: BullarrApi) {
        val v = runCatching { api.getObject("api/upscale/threshold").optInt("max_side", 0) }.getOrDefault(0)
        if (v !in 256..8192 || v == maxSourceSide) return
        withContext(Dispatchers.Main) { maxSourceSide = v }
        prefs(context).edit().putInt("upscale_max_side", v).apply()
        PageCache.resetUpscaleDecisions()
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        prefs(context).edit().putBoolean("upscale", on).apply()
    }

    fun setModel(context: Context, m: UpscaleModel) {
        if (!isInstalled(m)) return
        model = m
        prefs(context).edit().putString("upscale_model", m.id).apply()
    }

    /** Coupe l'upscaling et interrompt la page en cours (vérifié entre deux tuiles). */
    fun stop(context: Context) {
        setEnabled(context, false)
        if (libLoaded) nativeCancel(true)
    }

    /** Télécharge un modèle du catalogue (en arrière-plan, survit à la fermeture de l'écran). Choisi une fois prêt. */
    fun download(context: Context, m: UpscaleModel) {
        if (m.builtin || jobs[m]?.isActive == true) return
        val app = context.applicationContext
        downloadError.remove(m)
        downloading[m] = 0f
        jobs[m] = scope.launch {
            val d = modelDir(app, m).apply { mkdirs() }
            try {
                var done = 0L
                for ((f, name) in listOf(m.param!! to "model.param", m.bin!! to "model.bin")) {
                    val tmp = File(d, "$name.part")
                    val md = MessageDigest.getInstance("SHA-256")
                    val conn = URL(f.url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    try {
                        if (conn.responseCode != 200) throw IllegalStateException("serveur : code ${conn.responseCode}")
                        conn.inputStream.use { i ->
                            tmp.outputStream().use { o ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    ensureActive()
                                    val n = i.read(buf)
                                    if (n < 0) break
                                    o.write(buf, 0, n); md.update(buf, 0, n); done += n
                                    if (done > m.downloadSize) throw IllegalStateException("fichier inattendu")
                                    withContext(Dispatchers.Main) { downloading[m] = done.toFloat() / m.downloadSize }
                                }
                            }
                        }
                    } finally { conn.disconnect() }
                    val hash = md.digest().joinToString("") { "%02x".format(it) }
                    if (tmp.length() != f.size || hash != f.sha256) throw IllegalStateException("fichier altéré ou incomplet")
                    if (!tmp.renameTo(File(d, name))) throw IllegalStateException("écriture impossible")
                }
                withContext(Dispatchers.Main) {
                    installed[m] = true
                    setModel(app, m)
                    setEnabled(app, true)
                }
            } catch (e: Throwable) {
                d.deleteRecursively()
                withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        android.util.Log.w("BullarrUpscale", "téléchargement de ${m.id} échoué", e)
                        downloadError[m] = e.message ?: e.javaClass.simpleName
                    }
                }
            } finally {
                withContext(Dispatchers.Main + kotlinx.coroutines.NonCancellable) { downloading.remove(m); jobs.remove(m) }
            }
        }
    }

    fun cancelDownload(m: UpscaleModel) { jobs[m]?.cancel() }

    /** Supprime un modèle téléchargé ; s'il était choisi, on revient à Rapide. */
    fun delete(context: Context, m: UpscaleModel) {
        if (m.builtin) return
        cancelDownload(m)
        if (model == m) setModel(context, UpscaleModel.Fast)
        // Le modèle chargé reste en mémoire jusqu'au prochain choix : il ne lit plus ses fichiers.
        modelDir(context, m).deleteRecursively()
        installed[m] = false
    }

    fun eligible(b: Bitmap) = maxOf(b.width, b.height) <= maxSourceSide

    /** Fichiers du modèle prêts à charger (copie des assets pour un modèle intégré : compressés dans l'APK, pas de openFd). */
    private fun files(context: Context, m: UpscaleModel): Pair<File, File>? {
        if (!m.builtin) {
            val d = modelDir(context, m)
            return (File(d, "model.param") to File(d, "model.bin")).takeIf { it.first.exists() && it.second.exists() }
        }
        val d = dir(context).apply { mkdirs() }
        val stamp = File(d, "${m.asset}.version")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        val fs = listOf("param", "bin").map { File(d, "${m.asset}.$it") }
        if (stamp.takeIf { it.exists() }?.readText() != version || fs.any { it.length() == 0L }) {
            fs.forEach { f ->
                val tmp = File(d, f.name + ".tmp")
                context.assets.open("upscale/${f.name}").use { i -> tmp.outputStream().use { i.copyTo(it) } }
                if (!tmp.renameTo(f)) throw IllegalStateException("copie du modèle impossible")
            }
            stamp.writeText(version)
        }
        return fs[0] to fs[1]
    }

    /** Charge la bibliothèque et le modèle demandé. */
    private fun ensure(context: Context, m: UpscaleModel): Boolean {
        if (loaded == m) return true
        if (unavailable != null) return false
        try {
            if (!libLoaded) { System.loadLibrary("bullarr_upscale"); libLoaded = true }
            val (param, bin) = files(context, m) ?: return false
            loaded = null
            nativeInit(param.path, bin.path, m.engine.code, m.scale, m.noise, m.heavy)?.let {
                // Un modèle téléchargé illisible n'empêche pas les autres de fonctionner.
                if (m.builtin) unavailable = it
                android.util.Log.w("BullarrUpscale", "modèle ${m.id} : $it")
                return false
            }
            loaded = m
        } catch (e: Throwable) {
            android.util.Log.w("BullarrUpscale", "upscaling indisponible", e)
            unavailable = e.message ?: e.javaClass.simpleName
        }
        return loaded == m
    }

    /** Page agrandie (bitmap matériel si possible) avec le modèle choisi, ou null si l'appareil ne sait pas le faire. */
    suspend fun upscale(context: Context, src: Bitmap, m: UpscaleModel = model): Bitmap? =
        run(context, src, m, test = false, maxSide = MAX_RESULT_SIDE)?.first

    /**
     * Essai d'un modèle depuis les réglages, même si l'upscaling est coupé : image agrandie et durée du calcul en millisecondes
     * (sans le chargement du modèle). Résultat réduit à [maxSide] pour pouvoir en garder plusieurs en mémoire.
     */
    suspend fun test(context: Context, src: Bitmap, m: UpscaleModel, maxSide: Int = 2400): Pair<Bitmap, Long>? =
        run(context, src, m, test = true, maxSide = maxSide)

    private suspend fun run(context: Context, src: Bitmap, m: UpscaleModel, test: Boolean, maxSide: Int): Pair<Bitmap, Long>? = mutex.withLock {
        withContext(Dispatchers.Default) {
            // Coupé pendant l'attente du verrou : rien à faire.
            if ((!test && !enabled) || !ensure(context.applicationContext, m)) return@withContext null
            nativeCancel(false)
            val soft = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
            val out = Bitmap.createBitmap(soft.width * m.scale, soft.height * m.scale, Bitmap.Config.ARGB_8888)
            val t0 = android.os.SystemClock.elapsedRealtime()
            val rc = nativeUpscale(soft, out)
            val ms = android.os.SystemClock.elapsedRealtime() - t0
            android.util.Log.i("BullarrUpscale", "${m.id} ${soft.width}×${soft.height} → ×${m.scale} : code $rc en $ms ms")
            if (rc != 0) { out.recycle(); return@withContext null }
            // Sortie ×4 : ramenée sous maxSide (au-delà, l'écran n'en profite pas et la mémoire si).
            val ratio = maxSide.toFloat() / maxOf(out.width, out.height)
            val fitted = if (ratio >= 1f) out else Bitmap.createScaledBitmap(
                out, (out.width * ratio).toInt(), (out.height * ratio).toInt(), true,
            ).also { out.recycle() }
            (fitted.copy(Bitmap.Config.HARDWARE, false)?.also { fitted.recycle() } ?: fitted) to ms
        }
    }

    /** Interrompt le calcul en cours sans toucher au réglage (essai des modèles abandonné). */
    fun cancelCurrent() { if (libLoaded) nativeCancel(true) }
}
