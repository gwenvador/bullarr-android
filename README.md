# Bullarr Android

Application Android (Kotlin, Jetpack Compose) qui pilote Bullarr à travers son API.
L'icône reprend le logo de Bullarr (`static/img/bullarr-icon.png`).
Interface en français, thème clair ou sombre selon le réglage du téléphone.

## Installation

1. Téléchargez `bullarr-android-<version>.apk` depuis les
   [releases de Bullarr](https://github.com/gwenvador/bullarr/releases), ouvrez-le sur le
   téléphone et autorisez l'installation d'applications inconnues si Android le demande.
   Avec un câble USB : `adb install -r bullarr-android-<version>.apk`.
2. Au premier lancement, renseignez :
   - **URL du serveur** : par exemple `http://192.168.1.10:4000`, le nom du serveur sur
     votre réseau (Tailscale…) suivi de `:4000`, ou l'adresse publique de Bullarr. Sans `http://`, l'app l'ajoute. Le HTTP en clair est autorisé.
   - **Clé API** : dans Bullarr, ouvrez **Paramètres → Login → carte « Accès API »**,
     générez la clé puis copiez-la.
3. Touchez **Tester** : l'app appelle `GET /api/auth/me` avec l'en-tête `X-Api-Key`.
   Une clé fausse donne « Clé API invalide ». Touchez ensuite **Enregistrer**.

L'URL et la clé sont chiffrées sur l'appareil (EncryptedSharedPreferences, clé maître
dans le Keystore Android). Vous pouvez les changer dans l'onglet **Connexion**.

## Navigation

La barre du bas reprend les 5 rubriques de la barre latérale web :

- **Bibliothèque** : séries, fiche série. Toucher un tome ouvre sa fiche (résumé, auteurs,
  éditeur, note, fichier) ; la loupe à droite le cherche pour le télécharger.
- **Recherche** : toutes les sources, envoi au client de téléchargement.
- **Suivi** : Activité (téléchargements), Import, Nouveautés (Tout, RSS, Telegram, EBDZ),
  Historique (imports, téléchargements, actions).
- **Actions** : Enrichir (Indispensables BD, Panthéon), Surveillance (activer ou couper la
  surveillance d'une série), Vérification (rapports en lecture seule).
- **Configuration** : Connexion (URL et clé API de l'app), puis les réglages de Bullarr
  eux-mêmes — Indexeurs (Prowlarr, Fourtoutici, Anna's Archive, VK), Clients (aMule,
  qBittorrent, rTorrent, Deluge, Shelfmark, Komga), Notifications (bot Telegram), Imports,
  Recherche (ordre des sources, extensions, création de séries, recherche automatique),
  Surveillance. Chaque carte a un bouton Enregistrer et, quand l'API le permet, Tester.
  Le dernier onglet, Web, ouvre dans le navigateur ce qui n'est pas dans l'app : EBDZ
  (identifiants et forums), canaux Telegram, Login/SSO et clé API, format de renommage,
  sauvegarde.

Dans Nouveautés, chaque ligne a un bouton Télécharger et un menu : ⚡ « Ajouter la série et
télécharger » (comme sur la page web), ouvrir la série, ouvrir la source, copier le lien,
chercher ailleurs. Chaque champ de saisie a une croix pour l'effacer.

## Écrans et routes utilisées

| Écran | Routes |
|---|---|
| Connexion | `GET /api/auth/me` |
| Activité (rafraîchie toutes les 4 s) | `GET /api/activity/status` ; `POST /api/activity/download-control` avec `{"tracking_id": N, "action": "pause"\|"resume"\|"top"}` |
| Bibliothèque | `GET /api/libraries`, `GET /api/library/<id>/series`, couvertures sous `/covers/…` |
| Détail d'une série | `GET /api/series/<id>` (la réponse contient déjà les tomes) |
| Recherche | `POST /api/missing-monitor/search` (une requête par source, en parallèle) ; envoi : `POST /api/{qbittorrent,rtorrent,deluge}/add`, `/api/emule/add`, `/api/telegram-channels/download`, `/api/fourtoutici/download`, `/api/vk/download` ; contrôle de remplacement : `POST /api/import/replacement-required` |
| Nouveautés | RSS : `GET /api/ebdz/rss/latest` ; Telegram : `GET /api/telegram-channels/latest?days=15` ; EBDZ : `GET /api/ebdz/latest` |
| Nouveautés, actions | envois ci-dessus ; ⚡ : `GET /api/bedetheque/info`, `POST /api/bedetheque/add-series`, `POST /api/auto-acquire/reviews/series-match`, `GET /api/search/prowlarr` |
| Configuration | `GET`/`POST` `/api/{emule,qbittorrent,rtorrent,deluge,shelfmark,komga,prowlarr,fourtoutici,annas-archive,vk,telegram}/config`, `/api/import/config`, `/api/missing-monitor/config` ; tests : `…/test` |
| Historique | `GET /api/import/history`, `/api/missing-monitor/history`, `/api/actions/history` |
| Enrichir | `GET /api/bedetheque/indispensables`, `/api/bedetheque/pantheon`, `POST /api/bedetheque/add-series` |
| Surveillance | `GET /api/missing-monitor/libraries`, `/api/missing-monitor/libraries/<id>/series`, `POST /api/missing-monitor/series/<id>/monitor` |
| Vérification | `GET /api/settings/verification?type=…` |
| Import (rafraîchi toutes les 5 s) | `GET /api/import/progress`, `GET /api/import/pending-count` |

Les formats de réponse ont été relevés dans `blueprints/*/routes.py`. Les erreurs
`{"success": false, "error": "..."}` (ou `{"error": "..."}`) s'affichent avec la route
appelée et une piste de résolution. Les pannes réseau ont aussi leur message : hôte
introuvable, connexion refusée, délai dépassé, HTTPS.

Pause, reprise et « En premier » ne sont proposés que là où Bullarr les gère :
qBittorrent, aMule et Telegram. Ils n'apparaissent pas pour rTorrent, Deluge ni VK.

## Recherche

L'onglet **Recherche** interroge Prowlarr, EBDZ, Telegram, Fourtoutici, VK et Anna's Archive.
Chaque source s'affiche dès qu'elle répond. Les tomes confirmés passent en premier ; les
autres portent le motif donné par Bullarr (« Tome 11 demandé, mais… »).

Depuis une fiche série, touchez un tome (ou « Chercher cette série ») : la recherche est
alors **rattachée** à ce tome, comme sur la page web. Le téléchargement est suivi dans
Activité puis importé au bon endroit. Si le tome a déjà un fichier, l'app prévient qu'il
sera remplacé.

Chaque envoi demande une confirmation. Prowlarr part vers le client torrent activé dans
Bullarr (au choix s'il y en a plusieurs ; catégorie par défaut de qBittorrent), EBDZ vers
aMule, Telegram, Fourtoutici et VK vers leur téléchargeur intégré. Anna's Archive n'est
proposé qu'en lien.


## Enregistrer un réglage

À l'enregistrement, l'app relit d'abord le réglage sur le serveur, n'y applique que les
champs modifiés, puis l'envoie : une autre modification faite entre-temps n'est pas écrasée.
Les mots de passe et clés ne sont jamais relus en clair ; laissés vides, ils restent
inchangés. Le bouton Tester utilise les réglages **enregistrés**.

## Limites connues (côté API)

- Il n'existe pas de route de recherche de séries. La recherche filtre donc dans l'app
  la liste complète des séries (quelques secondes pour une grande bibliothèque), sans tenir
  compte des accents.
- L'écran Import est en lecture seule : il affiche le fichier en cours, les fichiers
  réservés et les compteurs. Il ne lance ni n'arrête aucun import.

## Compiler

Prérequis : un JDK 17 ou plus récent (celui d'Android Studio suffit) et le SDK Android
(`local.properties` → `sdk.dir`).

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleRelease
```

L'APK est produit dans `app/build/outputs/apk/release/app-release.apk` (signé avec la clé de
debug ; `./gradlew assembleDebug` produit la variante debug).

## Code source

Application compagnon de [Bullarr](https://github.com/gwenvador/bullarr). Identifiant
Android : `io.github.gwenvador.bullarr`.
