package io.github.gwenvador.bullarr.ui

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Ouvre [uri] dans le navigateur, jamais dans Bullarr : l'APK déclare les liens de son serveur
 * (App Links), donc un simple ACTION_VIEW de /login/redirect revenait à l'app elle-même
 * (« Connectez-vous d'abord à votre serveur Bullarr ») au lieu d'ouvrir le SSO.
 */
fun openInBrowser(context: Context, uri: Uri) {
    val pm = context.packageManager
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
    val own = context.packageName
    // Navigateur par défaut, sinon le premier navigateur installé (hors Bullarr et le sélecteur « android »).
    val pkg = pm.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        ?.takeIf { it != own && it != "android" }
        ?: pm.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }.firstOrNull { it != own }
    val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
    if (pkg != null) intent.setPackage(pkg)
    context.startActivity(intent)
}
