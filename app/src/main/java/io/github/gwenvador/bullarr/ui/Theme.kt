package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import io.github.gwenvador.bullarr.ui.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.gwenvador.bullarr.data.CoverLoader

/**
 * Palettes, identiques à celles du site (Configuration → Thème) : fond, surface, surface-alt,
 * bordure, texte, texte atténué, accent, accent survol, en clair puis en sombre.
 */
class Tones(val bg: Long, val surface: Long, val alt: Long, val border: Long, val text: Long, val muted: Long, val accent: Long, val accentHover: Long)

enum class Palette(val label: String, val light: Tones, val dark: Tones, val rounder: Boolean = false) {
    Atelier("Atelier",
        Tones(0xFFF2ECE1, 0xFFFFFDF8, 0xFFF7F2E9, 0xFFE0D2B8, 0xFF2B2318, 0xFF666666, 0xFFA8672A, 0xFF8A5220),
        Tones(0xFF241F18, 0xFF2C261E, 0xFF342C22, 0xFF4D4335, 0xFFECE3D3, 0xFFA89A80, 0xFFC98A4B, 0xFFDBA066)),
    Ardoise("Ardoise",
        Tones(0xFFF4F6FB, 0xFFFFFFFF, 0xFFF8FAFD, 0xFFE3E8F1, 0xFF182033, 0xFF68738A, 0xFF6366F1, 0xFF4F46E5),
        Tones(0xFF131826, 0xFF1B2132, 0xFF232A3E, 0xFF343D55, 0xFFE6E9F2, 0xFF97A0B5, 0xFF818CF8, 0xFFA5B4FC),
        rounder = true),
    Sauge("Sauge",
        Tones(0xFFEEF2EC, 0xFFFBFDF9, 0xFFF3F7F0, 0xFFD3DFCD, 0xFF1D2A20, 0xFF5F6F62, 0xFF3F7D58, 0xFF2F6346),
        Tones(0xFF161D18, 0xFF1D261F, 0xFF243028, 0xFF36473A, 0xFFE3ECE4, 0xFF95A898, 0xFF6FBF8E, 0xFF8FD4A8)),
    Ocean("Océan",
        Tones(0xFFEDF3F7, 0xFFFBFDFF, 0xFFF2F7FA, 0xFFD2E0EA, 0xFF132433, 0xFF5B7084, 0xFF0E7490, 0xFF0B5D73),
        Tones(0xFF0F1A22, 0xFF15232D, 0xFF1B2C38, 0xFF2C4352, 0xFFE1EDF4, 0xFF8EA7B8, 0xFF22B8CF, 0xFF5FD0E0)),
    Prune("Prune",
        Tones(0xFFF5F0F4, 0xFFFFFCFE, 0xFFF9F3F8, 0xFFE6D6E3, 0xFF2A1A28, 0xFF6F5D6C, 0xFF9D3F86, 0xFF7F2F6C),
        Tones(0xFF1D151C, 0xFF251B24, 0xFF2E222C, 0xFF473645, 0xFFF0E4EE, 0xFFAB98A8, 0xFFD47FBF, 0xFFE3A0D2));

    fun tones(dark: Boolean) = if (dark) this.dark else light
}

/** Mélange [a] dans [b] à la proportion [t] (teintes claires : accent dans la surface, 10–30 %). */
private fun mix(a: Color, b: Color, t: Float) = Color(
    red = a.red * t + b.red * (1 - t), green = a.green * t + b.green * (1 - t), blue = a.blue * t + b.blue * (1 - t), alpha = 1f,
)

fun colorSchemeOf(p: Palette, dark: Boolean): androidx.compose.material3.ColorScheme {
    val t = p.tones(dark)
    val bg = Color(t.bg); val surface = Color(t.surface); val alt = Color(t.alt); val border = Color(t.border)
    val text = Color(t.text); val muted = Color(t.muted); val accent = Color(t.accent); val hover = Color(t.accentHover)
    val onAccent = if (dark) bg else Color.White
    return if (dark) darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = mix(accent, surface, 0.30f), onPrimaryContainer = text,
        secondary = hover, onSecondary = onAccent,
        secondaryContainer = mix(accent, surface, 0.18f), onSecondaryContainer = text,
        tertiary = Color(0xFFD9CA61), onTertiary = Color(0xFF363100),
        tertiaryContainer = Color(0xFF514800), onTertiaryContainer = Color(0xFFF6E77A),
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = alt, onSurfaceVariant = muted,
        surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = alt, surfaceContainerHighest = surface,
        surfaceBright = alt, surfaceDim = bg, surfaceTint = accent,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = Color(p.light.accent),
        outline = muted, outlineVariant = border,
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
    ) else lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = mix(accent, surface, 0.22f), onPrimaryContainer = text,
        secondary = hover, onSecondary = onAccent,
        secondaryContainer = mix(accent, surface, 0.12f), onSecondaryContainer = text,
        tertiary = Color(0xFF6B6000), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFF6E77A), onTertiaryContainer = Color(0xFF201C00),
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = alt, onSurfaceVariant = muted,
        surfaceContainerLowest = surface, surfaceContainerLow = surface, surfaceContainer = alt,
        surfaceContainerHigh = alt, surfaceContainerHighest = surface,
        surfaceBright = surface, surfaceDim = bg, surfaceTint = accent,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = Color(p.dark.accent),
        outline = muted, outlineVariant = border,
        error = Color(0xFFBA1A1A), onError = Color.White, errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
    )
}

/** Thème choisi dans l'app : suivre le téléphone, toujours clair ou toujours sombre. Mémorisé sur l'appareil. */
enum class ThemeMode(val label: String) { System("Système"), Light("Clair"), Dark("Sombre") }

object ThemePref {
    private var prefs: android.content.SharedPreferences? = null
    var mode by mutableStateOf(ThemeMode.System); private set
    var palette by mutableStateOf(Palette.Atelier); private set

    fun init(context: android.content.Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE)
        mode = runCatching { ThemeMode.valueOf(prefs!!.getString("theme", null) ?: "System") }.getOrDefault(ThemeMode.System)
        palette = runCatching { Palette.valueOf(prefs!!.getString("palette", null) ?: "Atelier") }.getOrDefault(Palette.Atelier)
    }

    fun choosePalette(value: Palette) {
        palette = value
        prefs?.edit()?.putString("palette", value.name)?.apply()
    }

    fun set(value: ThemeMode) {
        mode = value
        prefs?.edit()?.putString("theme", value.name)?.apply()
    }
}

@Composable
fun BullarrTheme(content: @Composable () -> Unit) {
    val dark = when (ThemePref.mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Icônes de la barre d'état et de navigation lisibles sur le fond choisi, même si le téléphone est dans l'autre mode.
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) androidx.compose.runtime.SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    val palette = ThemePref.palette
    val scheme = remember(palette, dark) { colorSchemeOf(palette, dark) }
    // Ardoise : coins plus arrondis, comme sur le site.
    val shapes = if (palette.rounder) androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(32.dp),
    ) else androidx.compose.material3.Shapes()
    MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
}

/** Onglet Configuration → Apparence : réglages propres à l'app sur ce téléphone. */
@Composable
fun AppearanceScreen() {
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemePicker()
        Spacer(Modifier.height(8.dp))
        PalettePicker()
        Text(
            "« Système » suit le mode clair ou sombre du téléphone. Ces réglages sont propres à cet appareil.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Onglet Réglages → Upscaling : upscaling des pages, propre à cet appareil. */
@Composable
fun ReaderSettingsScreen(vm: AppViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        UpscaleSettings(vm)
        UpscaleTestCard(vm)
        if (!vm.isReader) {
            androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
            ServerUpscaleSettings(vm)
        }
    }
}

/** Lecteur : amélioration des pages basse définition (activation et modèle). */
@Composable
fun UpscaleSettings(vm: AppViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(vm.api) {
        io.github.gwenvador.bullarr.data.Upscaler.load(context)
        vm.api?.let { io.github.gwenvador.bullarr.data.Upscaler.syncThreshold(context, it) }
    }
    val up = io.github.gwenvador.bullarr.data.Upscaler
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { up.setEnabled(context, !up.enabled) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Upscaling des pages basse définition", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Agrandit et nettoie les scans de faible résolution (jusqu'à ${up.maxSourceSide} px, seuil réglé sur le serveur) avec une IA, sur le téléphone. " +
                        "Aussi réglable dans le lecteur avec la baguette magique. Propre à cet appareil.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            androidx.compose.material3.Switch(checked = up.enabled, onCheckedChange = { up.setEnabled(context, it) })
        }
        Text("Modèles", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        Text(
            "Rapide est inclus dans l'app. Les autres se téléchargent depuis les dépôts publics des projets upscayl et Real-CUGAN (GitHub), " +
                "puis restent sur le téléphone.",
            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
        )
        io.github.gwenvador.bullarr.data.UpscaleModel.entries.forEach { m -> UpscaleModelRow(m) }
        up.unavailable?.let {
            Text("Indisponible sur cet appareil : $it", style = MaterialTheme.typography.bodySmall, color = cs.error)
        }
    }
}

/** Un modèle du catalogue : choix s'il est présent, sinon téléchargement (avec progression, annulation et erreur). */
@Composable
private fun UpscaleModelRow(m: io.github.gwenvador.bullarr.data.UpscaleModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val up = io.github.gwenvador.bullarr.data.Upscaler
    val cs = MaterialTheme.colorScheme
    val present = up.isInstalled(m)
    val selected = present && up.model == m
    val progress = up.downloading[m]
    val mb = "%.1f Mo".format(java.util.Locale.FRANCE, m.downloadSize / 1_000_000.0)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(14.dp))
            .clickable(enabled = present) { up.setModel(context, m) }.padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (present) androidx.compose.material3.RadioButton(selected = selected, onClick = { up.setModel(context, m) })
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                Text(m.label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                Text(m.detail, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            when {
                progress != null -> TextButton(onClick = { up.cancelDownload(m) }) { Text("Annuler") }
                !present -> TextButton(onClick = { up.download(context, m) }) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Download, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(mb)
                }
                !m.builtin -> IconButton(onClick = { up.delete(context, m) }) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Delete, "Supprimer le modèle $mb", tint = cs.onSurfaceVariant)
                }
            }
        }
        if (progress != null) androidx.compose.material3.LinearProgressIndicator(
            progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        up.downloadError[m]?.let {
            Text("Téléchargement impossible : $it", style = MaterialTheme.typography.bodySmall, color = cs.error, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Choix de la palette (les mêmes que sur le site), avec un aperçu dans le mode clair/sombre courant. */
@Composable
fun PalettePicker(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Couleurs", style = MaterialTheme.typography.titleSmall)
        Palette.entries.forEach { p ->
            val t = p.tones(dark)
            val selected = ThemePref.palette == p
            val cs = MaterialTheme.colorScheme
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outlineVariant, RoundedCornerShape(14.dp))
                    .clickable { ThemePref.choosePalette(p) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Aperçu : le fond, une carte, une ligne de texte et un bouton à l'accent.
                Box(
                    Modifier.size(92.dp, 56.dp).clip(RoundedCornerShape(if (p.rounder) 12.dp else 8.dp)).background(Color(t.bg))
                        .border(1.dp, Color(t.border), RoundedCornerShape(if (p.rounder) 12.dp else 8.dp)).padding(7.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize().clip(RoundedCornerShape(if (p.rounder) 8.dp else 5.dp)).background(Color(t.surface)).padding(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(Modifier.width(44.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(Color(t.text)))
                        Box(Modifier.width(30.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(t.muted)))
                        Box(Modifier.width(26.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(t.accent)))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.label, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                        listOf(t.accent, t.accentHover, t.alt, t.border).forEach {
                            Box(Modifier.size(12.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Color(it))
                                .border(1.dp, cs.outlineVariant, androidx.compose.foundation.shape.CircleShape))
                        }
                    }
                }
                if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = "Palette choisie", tint = cs.primary)
            }
        }
    }
}

/** Choix du thème : Système / Clair / Sombre. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ThemePicker(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Thème", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = ThemePref.mode == m, onClick = { ThemePref.set(m) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    icon = {
                        Icon(
                            when (m) {
                                ThemeMode.System -> Icons.Outlined.PhoneAndroid
                                ThemeMode.Light -> Icons.Outlined.LightMode
                                ThemeMode.Dark -> Icons.Outlined.DarkMode
                            },
                            contentDescription = null, modifier = Modifier.size(18.dp),
                        )
                    },
                ) { Text(m.label) }
            }
        }
    }
}

@Composable
fun ErrorCard(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                if (onRetry != null) {
                    TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("Réessayer") }
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String? = null, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Outlined.Inbox) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Icône discrète au-dessus du message : un écran vide se lit d'un coup d'œil.
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), modifier = Modifier.size(48.dp).padding(bottom = 4.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun Tag(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Text(
            text, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Tag de série : pastille discrète bordée, « # » en couleur d'accent. */
@Composable
fun TopicTag(text: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = Color.Transparent, contentColor = cs.onSurface, shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, cs.outlineVariant), modifier = modifier,
    ) {
        Text(
            androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = cs.primary)); append("#"); pop(); append(text)
            },
            fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/** Couverture chargée depuis Bullarr, avec un dos de livre neutre en attendant. */
@Composable
fun Cover(url: String?, width: Dp, modifier: Modifier = Modifier) {
    val height = width * 1.38f
    val px = with(LocalDensity.current) { width.roundToPx() }
    var bitmap by remember(url) { mutableStateOf(url?.let { CoverLoader.cached(it) }) }
    LaunchedEffect(url) {
        if (url != null && bitmap == null) bitmap = CoverLoader.load(url, px)
    }
    Box(
        modifier.size(width, height).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val b = bitmap
        if (b != null) {
            Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(width / 2.2f))
        }
    }
}

/**
 * Fond des champs de saisie : gris neutre (pas la teinte brune du thème). Sur une carte, un peu
 * plus foncé que la carte ; sur le fond de page, un peu plus clair.
 */
@Composable
fun fieldColor(onCard: Boolean = false): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when {
        dark && onCard -> Color(0xFF1E1E21)
        dark -> Color(0xFF2B2B2F)
        onCard -> Color(0xFFF1F1F3)
        else -> Color(0xFFE9E9EC)
    }
}

/**
 * Champ de saisie des formulaires : libellé au-dessus, case arrondie de 44 dp sur un fond plus
 * sombre que la carte (pas de grand cadre), liseré de la couleur principale quand il a le focus.
 * [password] : texte masqué avec un œil pour l'afficher.
 */
@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    password: Boolean = false,
    clearable: Boolean = false,
    singleLine: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var shown by remember { mutableStateOf(false) }
    val transformation = if (password && !shown) androidx.compose.ui.text.input.PasswordVisualTransformation() else visualTransformation
    val shape = RoundedCornerShape(12.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        label?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = if (focused) cs.primary else cs.onSurfaceVariant) }
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = transformation,
            interactionSource = interaction,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = cs.onSurface),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(shape).background(fieldColor(onCard = true))
                        .border(if (focused) 1.5.dp else 0.dp, if (focused) cs.primary else androidx.compose.ui.graphics.Color.Transparent, shape)
                        .padding(start = if (leadingIcon != null) 4.dp else 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    leadingIcon?.let { Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { it() } }
                    Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
                        inner()
                    }
                    if (clearable && value.isNotEmpty()) IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Clear, contentDescription = "Effacer", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    if (password) IconButton(onClick = { shown = !shown }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (shown) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (shown) "Masquer" else "Afficher", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp),
                        )
                    }
                    trailing?.invoke()
                    if (!clearable && !password && trailing == null) Spacer(Modifier.width(10.dp))
                }
            },
        )
        supportingText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
    }
}

/** Champ des réglages (même style que [FormField]) avec une croix pour l'effacer. */
@Composable
fun ClearableField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    supportingText: String? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
) = FormField(
    value, onValueChange, modifier, label = label, placeholder = placeholder, clearable = true, leadingIcon = leadingIcon,
    trailing = trailing, supportingText = supportingText, visualTransformation = visualTransformation,
    keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
)

/** Champ de recherche compact (40 dp) : loupe, texte, croix pour effacer. Pour les listes denses. */
@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
) {
    val cs = MaterialTheme.colorScheme
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, interactionSource = interaction,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
        modifier = modifier.fillMaxWidth().height(40.dp),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().clip(shape).background(fieldColor())
                    .border(1.dp, if (focused) cs.primary else cs.outlineVariant.copy(alpha = 0.5f), shape)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showIcon) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Clear, contentDescription = "Effacer", tint = cs.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).clip(RoundedCornerShape(10.dp)).clickable { onValueChange("") },
                    )
                }
            }
        },
    )
}
