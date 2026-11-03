# Bullarr Android

Application Android (Kotlin, Jetpack Compose) qui pilote Bullarr à travers son API.
L'icône reprend le logo de Bullarr (`static/img/bullarr-icon.png`).
Interface en français, thème clair ou sombre selon le réglage du téléphone.

<p align="center">
  <a href="https://gwenvador.github.io/bullarr-android/repo/"><img src="https://gwenvador.github.io/bullarr/fdroid.svg" alt="Installer Bullarr avec F-Droid" width="30%"></a>
  <a href="https://github.com/gwenvador/bullarr-android/releases/latest"><img src="https://raw.githubusercontent.com/gwenvador/bullarr-android/main/assets/badges/signed-apk.svg" alt="Télécharger le dernier APK signé de Bullarr" width="30%"></a>
</p>

## Installation

1. Téléchargez `bullarr-android-<version>.apk` depuis les
   [releases de l'application](https://github.com/gwenvador/bullarr-android/releases), ouvrez-le sur le
   téléphone et autorisez l'installation d'applications inconnues si Android le demande.
   Avec un câble USB : `adb install -r bullarr-android-<version>.apk`.
2. Au premier lancement, renseignez l'**URL du serveur** (par exemple `http://192.168.1.10:4000`,
   le nom du serveur sur votre réseau suivi de `:4000`, ou l'adresse publique de Bullarr ; sans
   `http://`, l'app l'ajoute, et le HTTP en clair est autorisé), puis choisissez le mode d'accès :
   - **Clé API** : dans Bullarr, **Paramètres → Login → carte « Accès API »**, générez la clé puis
     copiez-la. Elle part dans l'en-tête `X-Api-Key`. Si l'authentification de Bullarr est
     désactivée, laissez la clé vide.
   - **Compte** : identifiant et mot de passe de Bullarr. L'app ouvre une session
     (`POST /login/password`), gère le jeton CSRF et se reconnecte seule à l'expiration.
   - **SSO** : l'identification OIDC se déroule dans une fenêtre intégrée ; l'app récupère la
     session à la fin. À l'expiration, il faut se reconnecter.
3. Touchez **Se connecter**. Une fois connecté, l'onglet **Connexion** propose **Tester**,
   **Se déconnecter** (efface clé, identifiants et session ; les albums hors ligne restent) et
   la vérification des mises à jour.

URL, clé, identifiants et session sont chiffrés sur l'appareil (EncryptedSharedPreferences,
clé maître dans le Keystore Android).

**Mises à jour** : Configuration → Connexion affiche la version installée et compare avec la
dernière release GitHub (l'APK doit s'appeler `bullarr-android-<version>.apk`).

### Dépôt F-Droid

Bullarr est aussi disponible dans un dépôt F-Droid, qui met l'app à jour sans passer par un APK manuel.
Ajoutez le dépôt dans F-Droid avec ce lien :

    https://gwenvador.github.io/bullarr-android/repo

Ou ouvrez ce lien depuis un téléphone qui a F-Droid :

    fdroidrepo://gwenvador.github.io/bullarr-android/repo?fingerprint=652E77168C22C56A08E277B00AC1D5B7F095B1F8FE6522A1508D1450BA803892

Empreinte SHA-256 de la clé de signature du dépôt, à vérifier si F-Droid la demande :

    65 2E 77 16 8C 22 C5 6A 08 E2 77 B0 0A C1 D5 B7 F0 95 B1 F8 FE 65 22 A1 50 8D 14 50 BA 80 38 92

### Vérifier l'APK

Les APK à partir de la 1.4.1 sont signés avec la clé de signature du projet (`CN=Bullarr`). Les versions précédentes (1.4 et avant) étaient signées avec le certificat de debug Android, et ne peuvent pas être mises à jour par-dessus. Pour vérifier un fichier téléchargé :

SHA-256 de `bullarr-android-1.4.1.apk` :

    a211a01a7a2e38b56a64b9b462e0fb509846d1a551f3d5129e7edebb1ef71fff

SHA-256 du certificat de signature (à comparer à `apksigner verify --print-certs`) :

    cef915fef814abb006e1f6ae31efbc778a3cd12cc548226662e73c987e15da30

    sha256sum bullarr-android-1.4.1.apk
    apksigner verify --print-certs bullarr-android-1.4.1.apk

## Navigation

La barre du bas reprend les 5 rubriques de la barre latérale web :

- **Bibliothèque** : séries avec drapeau d'origine (BD, Comics, Manga), recherche, **filtres**
  (genre, type, année, dernière mise à jour, votes, nombre de volumes, taille) et **tri**
  (alphabétique, popularité, date, taille). Toucher un tome ouvre sa fiche (résumé, auteurs,
  éditeur, note, fichier) ; la loupe à droite le cherche pour le télécharger. L'onglet
  **Hors ligne** liste les albums copiés sur le téléphone (voir plus bas). Un **appui long** sur
  une série la sélectionne (plusieurs possibles) : télécharger hors ligne, mettre à jour les
  métadonnées Bédéthèque, scanner, supprimer. Le bouton **+** ajoute une série par son nom
  (recherche Bédéthèque). Dans une série, un appui long sur un tome ouvre ses actions (lire,
  hors ligne, chercher, éditer, actualiser, mettre à jour, supprimer) ; tri et filtre des tomes
  en haut de la liste ; l'état « téléchargé » est indiqué sur chaque tome.
- **Recherche** : toutes les sources, envoi au client de téléchargement.
- **Suivi** : Activité (téléchargements, avec pause, reprise, « en premier » et suppression ;
  état de l'import en tête ; appui long pour sélectionner : supprimer, assigner ou corriger la série,
  **import manuel** avec conversion des dossiers JPG en CBZ ; erreur affichée pour un dossier non importable),
  Nouveautés (Tout, RSS, Telegram, EBDZ), Validation (choix manuels de l'acquisition automatique),
  Historique (imports, téléchargements, actions, filtre par type). Des pastilles comptent les éléments en attente.
- **Actions** : Enrichir (Indispensables BD, Panthéon, Thèmes, Top 100 BDGest, Par auteur), Surveillance (activer ou couper la
  surveillance d'une série), Vérification (rapports en lecture seule).
- **Configuration** : Connexion (URL et clé API de l'app), puis les réglages de Bullarr
  eux-mêmes — Indexeurs (Prowlarr, Fourtoutici, Anna's Archive, VK), Clients (aMule,
  qBittorrent, rTorrent, Deluge, Shelfmark, Komga), Notifications (bot Telegram), Imports,
  Recherche (ordre des sources, extensions, création de séries, recherche automatique),
  Surveillance. Chaque carte a un bouton Enregistrer et, quand l'API le permet, Tester.
  Indexeurs inclut le compte EBDZ et son scraping automatique, Imports le format de
  renommage, Accès la connexion à Bullarr (mot de passe, SSO). Le dernier onglet, Web,
  ouvre dans le navigateur ce qui n'est pas dans l'app : forums EBDZ, canaux Telegram,
  clé API, sauvegarde.

Dans Nouveautés, chaque ligne a un bouton Télécharger et un menu : ⚡ « Ajouter la série et
télécharger » (comme sur la page web), ouvrir la série, ouvrir la source, copier le lien,
chercher ailleurs. Chaque champ de saisie a une croix pour l'effacer. Les boutons sont des icônes : un appui long
affiche leur nom (infobulle).

## Lecture et albums hors ligne

Un tome possédé se lit dans l'app (bouton ▶) : pages en plein écran, zoom au pincement,
largeur de page ajustable, reprise à la dernière page. Les pages viennent du lecteur de
Bullarr (`GET /api/volumes/<id>/reader`, `…/reader/page/<n>`), qui sert des images quel que soit
le format du fichier (CBZ, CBR, PDF).

Le bouton de téléchargement d'un tome (ou de la série entière) copie ces pages sur le
téléphone pour les lire sans réseau. Les téléchargements tournent dans un service au premier
plan : ils continuent écran éteint, avec une notification (avancement, **Pause** ou
**Reprendre**, **Arrêter**). L'onglet **Hors ligne** permet de rechercher, trier, replier par
série, marquer lu ou non lu, mettre en pause ou reprendre, relancer les erreurs d'un coup et
supprimer des copies. Il ne contacte jamais le serveur.

## Écrans et routes utilisées

| Écran | Routes |
|---|---|
| Connexion | `GET /api/auth/me`, `POST /login/password`, `GET /login/redirect` (SSO), `GET /api/auth/csrf`, `GET /logout` |
| Activité (rafraîchie toutes les 4 s) | `GET /api/activity/status` ; `POST /api/activity/download-control` avec `{"tracking_id": N, "action": "pause"\|"resume"\|"top"}` ; suppression : `POST /api/activity/pending/remove` ou `POST /api/{qbittorrent,rtorrent,deluge,emule}/remove` |
| Bibliothèque | `GET /api/libraries`, `GET /api/library/<id>/series`, couvertures sous `/covers/…` |
| Lecteur et hors ligne | `GET /api/volumes/<id>/reader`, `GET /api/volumes/<id>/reader/page/<n>` |
| Détail d'une série | `GET /api/series/<id>` (la réponse contient déjà les tomes) |
| Recherche | `POST /api/missing-monitor/search` (une requête par source, en parallèle) ; envoi : `POST /api/{qbittorrent,rtorrent,deluge}/add`, `/api/emule/add`, `/api/telegram-channels/download`, `/api/fourtoutici/download`, `/api/vk/download` ; contrôle de remplacement : `POST /api/import/replacement-required` |
| Nouveautés | RSS : `GET /api/ebdz/rss/latest` ; Telegram : `GET /api/telegram-channels/latest?days=15` ; EBDZ : `GET /api/ebdz/latest` |
| Nouveautés, actions | envois ci-dessus ; ⚡ : `GET /api/bedetheque/info`, `POST /api/bedetheque/add-series`, `POST /api/auto-acquire/reviews/series-match`, `GET /api/search/prowlarr` |
| Configuration | `GET`/`POST` `/api/{emule,qbittorrent,rtorrent,deluge,shelfmark,komga,prowlarr,fourtoutici,annas-archive,vk,telegram}/config`, `/api/ebdz/config`, `/api/ebdz/auto-scrape/config`, `/api/settings/rename`, `/api/auth/config`, `/api/import/config`, `/api/missing-monitor/config` ; tests : `…/test` |
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
- L'état de l'import (en tête de l'Activité) est en lecture seule : fichier en cours, fichiers
  réservés et compteurs. L'import manuel passe par la sélection d'un téléchargement.

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
