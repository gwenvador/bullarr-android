package io.github.gwenvador.bullarr.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import io.github.gwenvador.bullarr.ui.Icon
import io.github.gwenvador.bullarr.ui.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.gwenvador.bullarr.data.BullarrApi

/**
 * Connexion SSO : ouvre /login/redirect de Bullarr dans une WebView (le fournisseur OIDC gère
 * son propre écran), puis récupère le cookie de session posé par /callback. Terminé dès que la
 * WebView atterrit sur une page de Bullarr autre que les pages de connexion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OidcLoginDialog(serverUrl: String, onCookie: (String) -> Unit, onCancel: () -> Unit) {
    val base = BullarrApi.normalizeBaseUrl(serverUrl)
    val cookies = CookieManager.getInstance()
    var problem by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = { Text("Connexion SSO") },
                    navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Outlined.Close, contentDescription = "Annuler") } },
                )
                problem?.let { ErrorCard(it, Modifier.padding(8.dp)) }
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        cookies.removeAllCookies(null)
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = object : WebViewClient() {
                                private var done = false
                                override fun onReceivedError(view: WebView, req: android.webkit.WebResourceRequest, err: android.webkit.WebResourceError) {
                                    if (req.isForMainFrame) problem = "Impossible de charger ${req.url.host}:${req.url.port.takeIf { it > 0 } ?: ""} — ${err.description}. " +
                                        "Le téléphone doit pouvoir joindre le serveur et le fournisseur SSO."
                                }
                                override fun onReceivedHttpError(view: WebView, req: android.webkit.WebResourceRequest, r: android.webkit.WebResourceResponse) {
                                    if (req.isForMainFrame && r.statusCode >= 400) problem = "${req.url.host}${req.url.path} a répondu HTTP ${r.statusCode} ${r.reasonPhrase.orEmpty()}."
                                }
                                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) = check(url)
                                override fun onPageFinished(view: WebView, url: String) = check(url)
                                private fun check(url: String) {
                                    if (done) return
                                    val u = android.net.Uri.parse(url)
                                    val path = u.path.orEmpty()
                                    val ours = u.host == base.host && (u.port.takeIf { it > 0 } ?: base.port) == base.port
                                    if (ours && !path.startsWith("/login") && path != "/callback") {
                                        val c = cookies.getCookie(base.toString()).orEmpty()
                                        if (c.isNotBlank()) { done = true; onCookie(c) }
                                    }
                                }
                            }
                            loadUrl(base.toString().trimEnd('/') + "/login/redirect")
                        }
                    },
                )
            }
        }
    }
}
