package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.Image
import androidx.compose.material.icons.outlined.Search
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

// Encre et papier de BD : rouge brique, papier crème le jour, nuit chaude la nuit.
private val Light = lightColorScheme(
    primary = Color(0xFFB3471F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF3A0A00),
    secondary = Color(0xFF2F5D7C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3E6F5),
    onSecondaryContainer = Color(0xFF0A2233),
    tertiary = Color(0xFF6B6000),
    tertiaryContainer = Color(0xFFF6E77A),
    onTertiaryContainer = Color(0xFF201C00),
    background = Color(0xFFFBF7F2),
    onBackground = Color(0xFF221A15),
    surface = Color(0xFFFBF7F2),
    onSurface = Color(0xFF221A15),
    surfaceVariant = Color(0xFFF1E4DA),
    onSurfaceVariant = Color(0xFF53443C),
    surfaceContainer = Color(0xFFF4EDE6),
    surfaceContainerHigh = Color(0xFFEEE6DE),
    outline = Color(0xFF85736A),
    outlineVariant = Color(0xFFD8C2B8),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB596),
    onPrimary = Color(0xFF5E1700),
    primaryContainer = Color(0xFF8A3210),
    onPrimaryContainer = Color(0xFFFFDBCC),
    secondary = Color(0xFF9BCBEE),
    onSecondary = Color(0xFF003450),
    secondaryContainer = Color(0xFF1C4A68),
    onSecondaryContainer = Color(0xFFD3E6F5),
    tertiary = Color(0xFFD9CA61),
    tertiaryContainer = Color(0xFF514800),
    onTertiaryContainer = Color(0xFFF6E77A),
    background = Color(0xFF17130F),
    onBackground = Color(0xFFEDE0D8),
    surface = Color(0xFF17130F),
    onSurface = Color(0xFFEDE0D8),
    surfaceVariant = Color(0xFF53443C),
    onSurfaceVariant = Color(0xFFD8C2B8),
    surfaceContainer = Color(0xFF231D19),
    surfaceContainerHigh = Color(0xFF2E2723),
    outline = Color(0xFFA08D84),
    outlineVariant = Color(0xFF53443C),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun BullarrTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
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
fun EmptyState(title: String, detail: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Tag(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(6.dp)) {
        Text(
            text, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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
 * Champ texte avec une croix pour l'effacer dès qu'il contient quelque chose.
 * [trailing] s'ajoute après la croix (ex. œil de la clé API).
 */
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
) {
    androidx.compose.material3.OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, singleLine = true,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        leadingIcon = leadingIcon,
        trailingIcon = if (value.isEmpty() && trailing == null) null else {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (value.isNotEmpty()) {
                        androidx.compose.material3.IconButton(onClick = { onValueChange("") }) {
                            Icon(androidx.compose.material.icons.Icons.Outlined.Clear, contentDescription = "Effacer")
                        }
                    }
                    trailing?.invoke()
                }
            }
        },
        supportingText = supportingText?.let { { Text(it) } },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

/** Champ de recherche compact (40 dp) : loupe, texte, croix pour effacer. Pour les listes denses. */
@Composable
fun CompactSearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    androidx.compose.foundation.text.BasicTextField(
        value = value, onValueChange = onValueChange, singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = cs.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(cs.primary),
        modifier = modifier.fillMaxWidth().height(40.dp),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxSize().background(cs.surfaceVariant, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
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
