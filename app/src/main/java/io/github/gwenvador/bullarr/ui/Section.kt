package io.github.gwenvador.bullarr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.ui.graphics.vector.ImageVector

/** Les 5 rubriques de la barre latérale de Bullarr (nav.js : Suivi et Actions sont des groupes). */
enum class Section(val label: String, val icon: ImageVector, val subs: List<String>) {
    Library("Bibliothèque", Icons.Outlined.CollectionsBookmark, emptyList()),
    Search("Recherche", Icons.Outlined.Search, emptyList()),
    Tracking("Suivi", Icons.Outlined.Timeline, listOf("Activité", "Import", "Nouveautés", "Historique")),
    Actions("Actions", Icons.Outlined.Build, listOf("Enrichir", "Surveillance", "Vérification")),
    Config("Configuration", Icons.Outlined.Settings, listOf("Connexion", "Indexeurs", "Clients", "Notifications", "Imports", "Recherche", "Surveillance", "Web")),
}
