package io.github.gwenvador.bullarr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.MarkunreadMailbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

/** Les 5 rubriques de la barre latérale de Bullarr (nav.js : Suivi et Actions sont des groupes). */
enum class Section(val label: String, val icon: ImageVector, val subs: List<String>) {
    // Pas d'onglets : « Hors ligne » s'ouvre par un bouton de la barre d'outils (subTab 1).
    Library("Bibliothèque", Icons.Outlined.CollectionsBookmark, emptyList()),
    Search("Recherche", Icons.Outlined.Search, emptyList()),
    // Lecteurs seulement : leurs demandes de BD (les administrateurs les traitent dans Validation).
    Requests("Demandes", Icons.Outlined.MarkunreadMailbox, emptyList()),
    Tracking("Suivi", Icons.Outlined.Timeline, listOf("Activité", "Nouveautés", "Validation", "Historique")),
    Actions("Actions", Icons.Outlined.Build, listOf("Enrichir", "Surveillance", "Vérification")),
    Config("Réglages", Icons.Outlined.Settings, listOf("Profil", "Indexeurs", "Clients", "Notifications", "Imports", "Recherche", "Surveillance", "Apparence", "Accès", "IA", "Web", "À propos")),
}
