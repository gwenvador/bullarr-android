package io.github.gwenvador.bullarr.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.gwenvador.bullarr.data.Alerts
import io.github.gwenvador.bullarr.data.NOTIFICATIONS
import io.github.gwenvador.bullarr.data.readable
import kotlinx.coroutines.launch
import org.json.JSONObject

private val COL_W = 72.dp

/**
 * Onglet Configuration → Notifications, comme la page de réglages web : en haut un tableau
 * « quel évènement, vers quelle destination » avec des cases, puis la configuration de chaque
 * destination (ce téléphone, le bot Telegram du serveur).
 */
@Composable
fun NotificationsScreen(vm: AppViewModel, snackbar: SnackbarHostState) {
    val api = vm.api ?: return
    val telegram = remember { ConfigEditor(NOTIFICATIONS.first()) }
    LaunchedEffect(api) { if (telegram.loaded == null) telegram.load(api) }
    var telegramOpen by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { NotificationTable(vm, telegram) }
        item { SectionTitle("Ce téléphone") }
        item { PhoneDeliveryCard(vm) }
        item { SectionTitle("Telegram (bot du serveur)") }
        item { ConfigCard(api, snackbar, telegram, expanded = telegramOpen, onToggle = { telegramOpen = !telegramOpen }) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

/** Tableau évènements × destinations. Colonne Téléphone : réglage local ; colonne Telegram : réglage du serveur. */
@Composable
private fun NotificationTable(vm: AppViewModel, telegram: ConfigEditor) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val phone = remember { mutableStateMapOf<Alerts.Kind, Boolean>().apply { Alerts.Kind.entries.forEach { put(it, Alerts.isOn(context, it)) } } }
    // Cases Telegram : lues dans la configuration du serveur, enregistrées dès qu'on les coche.
    val tg = remember { mutableStateMapOf<String, Boolean>() }
    var tgBusy by remember { mutableStateOf<String?>(null) }
    var tgError by remember { mutableStateOf<String?>(null) }
    val loaded = telegram.loaded
    LaunchedEffect(loaded) {
        loaded ?: return@LaunchedEffect
        Alerts.Kind.entries.mapNotNull { it.telegramKey }.forEach { k -> tg[k] = loaded.optBoolean(k, true) }
    }
    val tgEnabled = loaded?.optBoolean("enabled", false) == true
    @Suppress("UNUSED_VARIABLE") val pushState = io.github.gwenvador.bullarr.data.Push.version
    val instant = io.github.gwenvador.bullarr.data.Push.active(context)

    fun setTelegram(key: String, value: Boolean) {
        val api = vm.api ?: return
        tgBusy = key; tgError = null
        scope.launch {
            try {
                // Relit le serveur pour ne changer que cette case (jeton inchangé : champ vide).
                val fresh = api.getObject("api/telegram/config")
                fresh.put(key, value).put("bot_token", "")
                api.postObject("api/telegram/config", fresh)
                tg[key] = value
            } catch (e: Exception) { tgError = e.readable() }
            tgBusy = null
        }
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("Notifications à envoyer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
            Text("Cochez, pour chaque évènement, où le recevoir.", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            // En-tête : destinations.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Évènement", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                Column(Modifier.width(COL_W), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = cs.primary, modifier = Modifier.size(20.dp))
                    Text("Téléphone", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
                Column(Modifier.width(COL_W).alpha(if (tgEnabled) 1f else 0.5f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Logo("telegram", "Telegram", 20.dp, tip = false)
                    Text("Telegram", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = cs.outlineVariant)
            Alerts.Kind.entries.forEachIndexed { i, kind ->
                Row(
                    Modifier.fillMaxWidth().background(if (i % 2 == 1) cs.surfaceVariant.copy(alpha = 0.35f) else androidx.compose.ui.graphics.Color.Transparent)
                        .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(kind.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            kind.detail + if (!kind.periodic && !instant) " Téléphone : avec les notifications instantanées seulement." else "",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Box(Modifier.width(COL_W), contentAlignment = Alignment.Center) {
                        Checkbox(checked = phone[kind] == true, onCheckedChange = { on -> phone[kind] = on; Alerts.set(context, kind, on) })
                    }
                    Box(Modifier.width(COL_W).alpha(if (tgEnabled) 1f else 0.5f), contentAlignment = Alignment.Center) {
                        val key = kind.telegramKey
                        when {
                            key != null && tgBusy == key -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            key != null -> Checkbox(checked = tg[key] ?: true, enabled = loaded != null, onCheckedChange = { setTelegram(key, it) })
                            kind.telegramAlways -> Text("Toujours", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
                            else -> Text("—", style = MaterialTheme.typography.bodyMedium, color = cs.outline)
                        }
                    }
                }
            }
            HorizontalDivider(color = cs.outlineVariant)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tgError?.let { Text("Telegram : $it", style = MaterialTheme.typography.bodySmall, color = cs.error) }
                if (loaded != null && !tgEnabled) Text(
                    "Le bot Telegram est désactivé : configurez-le plus bas pour que ces cases servent.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                Text(
                    if (instant) "Téléphone : instantané via ntfy ; les nouveautés sont vérifiées toutes les 30 minutes environ."
                    else "Téléphone : vérifié toutes les 30 minutes environ. Activez les notifications instantanées plus bas pour tout recevoir sur le moment.",
                    style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                )
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) Text(
                    "Les notifications de Bullarr sont bloquées : autorisez-les dans les réglages Android de l'application.",
                    style = MaterialTheme.typography.bodySmall, color = cs.error,
                )
            }
        }
    }
}

/** Configuration de la réception sur ce téléphone : notifications instantanées (ntfy). */
@Composable
private fun PhoneDeliveryCard(vm: AppViewModel) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val push = io.github.gwenvador.bullarr.data.Push
    @Suppress("UNUSED_VARIABLE") val pushState = push.version
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = cs.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Notifications instantanées : le serveur pousse chaque évènement via ntfy (UnifiedPush).
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(cs.surfaceVariant.copy(alpha = 0.5f)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                @Suppress("UNUSED_VARIABLE") val tick = push.version
                val distributors = remember(tick) { push.distributors(context) }
                val enabled = push.enabled(context)
                var testing by remember { mutableStateOf(false) }
                var testResult by remember { mutableStateOf<String?>(null) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Notifications instantanées", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Reçues tout de suite, sans vider la batterie, grâce à l'app gratuite ntfy.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    if (distributors.isNotEmpty()) Switch(
                        checked = enabled,
                        onCheckedChange = { on ->
                            testResult = null
                            if (on) push.enable(context, push.distributor(context)?.takeIf { d -> distributors.any { it.first == d } } ?: distributors.first().first)
                            else push.disable(context)
                        },
                    )
                }
                when {
                    distributors.isEmpty() -> {
                        Text(
                            "Installez ntfy (F-Droid ou Play Store), ouvrez-le une fois, puis revenez ici.",
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = { runCatching { uri.openUri(io.github.gwenvador.bullarr.data.Push.NTFY_URL) } }) { Text("Installer ntfy") }
                    }
                    !enabled -> {}
                    else -> {
                        val name = distributors.firstOrNull { it.first == push.distributor(context) }?.second ?: "ntfy"
                        val error = push.error(context)
                        Text(
                            when {
                                error != null -> error
                                push.active(context) -> "Actif via $name : imports et validations arrivent dès qu'ils ont lieu."
                                push.endpoint(context) != null -> "Enregistrement auprès du serveur Bullarr…"
                                else -> "En attente de $name… (ouvrez $name s'il ne répond pas)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (error != null) cs.error else if (push.active(context)) cs.primary else cs.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (push.active(context)) OutlinedButton(
                                enabled = !testing,
                                onClick = {
                                    val api = vm.api ?: return@OutlinedButton
                                    testing = true; testResult = null
                                    scope.launch {
                                        testResult = try { api.postObject("api/push/test", JSONObject()); "Envoyé : la notification doit arriver dans un instant." }
                                        catch (e: Exception) { e.readable() }
                                        testing = false
                                    }
                                },
                            ) { if (testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Tester") }
                            if (error != null || (push.endpoint(context) != null && !push.active(context))) OutlinedButton(onClick = {
                                scope.launch { push.syncServer(context.applicationContext) }
                            }) { Text("Réessayer") }
                        }
                        testResult?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}
