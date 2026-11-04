# Ma Mangathèque — application Android

Application native Android (Kotlin + Jetpack Compose) pour gérer ta collection de mangas,
installable en APK sans Play Store. Testée pour un Pixel 9a (Android 15), compatible Android 8 et plus.

> 🤖 Projet personnel réalisé avec l'aide d'une IA (Claude, d'Anthropic) pour l'écriture du code.

**Télécharger :** la dernière version (fichier `.apk`) est dans l'onglet [Releases](../../releases/latest).

## Ce que fait l'application

| Onglet | Fonction |
|---|---|
| **Collection** | Statistiques, recherche, filtres par genre / statut de lecture / série en cours ou terminée, étagère « Je suis en train de lire », grille des séries. Bouton **Scanner**. |
| **En magasin** | Gros texte « J'ai : 1–12 » / « Il me manque : 13, 15 ». Bouton « Je l'ai déjà ? » pour scanner un code-barres en rayon et savoir tout de suite si tu l'as. |
| **Passages** | Galerie de toutes tes photos de passages préférés, avec petit mot et numéro de page. |
| **Réglages** | Export / restauration de la collection (.zip), mise à jour via GitHub Releases. |

Scanner : vise le code-barres ISBN au dos → l'application interroge **Google Books** puis **Open Library**,
pré‑remplit titre de série, numéro de tome, auteur, éditeur, genres, résumé et couverture.
Tu vérifies et corriges tout avant d'enregistrer. Si aucune couverture n'est trouvée, un bouton
**« Prendre la couverture en photo »** apparaît : tu alignes le livre dans le cadre, le recadrage est automatique.
L'ISBN est toujours conservé, et le **préfixe ISBN commun** de la série sert à reconnaître les tomes suivants.

Fiche tome : statut **À lire / En cours / Lu** (coché par toi), note de 1 à 5 étoiles, note perso,
photos de passages préférés (appareil photo ou galerie) avec mot et numéro de page.

Fiche série : tomes possédés et manquants (cases en pointillés, un tap = « Je l'ai ! »), genres,
série en cours ou terminée, nombre de tomes parus.

Toutes les données restent **sur le téléphone** (base SQLite + photos dans le dossier privé de l'application).

---

## 1. Installer l'APK sur le Pixel 9a

L'APK signé se trouve après compilation dans :

```
app/build/outputs/apk/release/app-release.apk
```

1. Copie `app-release.apk` sur le téléphone (câble USB, Drive, mail…).
2. Ouvre le fichier depuis l'application **Fichiers**.
3. Android demande d'autoriser l'installation depuis cette source : accepte.
4. Lance **Ma Mangathèque**, autorise l'appareil photo au premier scan.

Pour les mises à jour automatiques, l'application te demandera une seule fois d'autoriser
« Installer des applications inconnues » pour elle-même.

## 2. Recompiler l'APK sur ce PC

Tout est déjà installé (JDK 21 portable + SDK Android, sans droits administrateur) :

- JDK : `C:\Users\eltac\AppData\Local\Android\jdk\jdk-21.0.13+9`
- SDK : `C:\Users\eltac\AppData\Local\Android\Sdk`

Depuis le dossier du projet, dans PowerShell :

```powershell
.\gradlew.bat assembleRelease
```

L'APK est régénéré dans `app\build\outputs\apk\release\app-release.apk`.

## 3. La clé de signature — À NE JAMAIS PERDRE

Android n'accepte une mise à jour **que si elle est signée avec la même clé** que la version installée.
Si tu perds cette clé, il faudra désinstaller l'application (et donc perdre les données sur le téléphone,
sauf si tu as fait un export) avant de réinstaller.

La clé est dans le dossier du projet :

```
keystore/
├── mangatheque-release.jks   ← la clé elle-même (validité : 100 ans)
└── keystore.properties       ← alias et mots de passe (lus automatiquement par Gradle)
```

**Fais-en au moins deux copies** ailleurs (clé USB, Drive, gestionnaire de mots de passe).
Les deux fichiers vont ensemble : sans `keystore.properties` tu n'as pas le mot de passe.

- Si ton dépôt GitHub est **privé**, tu peux laisser ces fichiers versionnés (c'est le cas par défaut).
- Si ton dépôt est **public**, décommente les deux lignes à la fin de `.gitignore` et garde la clé hors du dépôt.

## 4. Publier une mise à jour sur GitHub (ce que l'application vérifie à l'ouverture)

L'application appelle `https://api.github.com/repos/<toi>/<depot>/releases/latest`, compare le tag
(ex. `v1.0.1`) avec sa version, affiche « Mise à jour disponible », télécharge le fichier `.apk`
attaché à la release et ouvre l'installateur.

### Configurer le dépôt dans l'application

Réglages → « Dépôt GitHub » → tape `ton-pseudo/nom-du-depot`. (Ou mets-le en dur dans
`gradle.properties` : `updateRepo=ton-pseudo/nom-du-depot` avant de compiler.)

### Dépôt privé : ajouter un jeton

Un dépôt privé n'est pas lisible sans identification. Il faut créer un jeton d'accès personnel :

1. GitHub → photo de profil → **Settings** → tout en bas **Developer settings** → **Personal access tokens** → **Fine-grained tokens** → **Generate new token**.
2. Nom : `mangatheque-telephone`. Expiration : choisis la plus longue (ou « No expiration » si proposé).
3. **Repository access** : « Only select repositories » → coche `mangatheque`.
4. **Permissions** → Repository permissions → **Contents** : *Read-only*. Rien d'autre.
5. **Generate token**, copie la longue chaîne `github_pat_…` (elle ne sera plus affichée ensuite).
6. Sur le téléphone : Réglages → « Jeton GitHub » → colle-la.

Le jeton ne sert qu'à lire les Releases ; il est stocké uniquement sur le téléphone.
Quand il expire, l'appli affiche « Jeton GitHub refusé » : il suffit d'en générer un nouveau.

### Méthode A — automatique avec GitHub Actions (recommandée)

Le fichier `.github/workflows/release.yml` compile et publie l'APK signé dès que tu pousses un tag.

Une seule fois, dans ton dépôt GitHub → **Settings → Secrets and variables → Actions → New repository secret** :

| Secret | Valeur |
|---|---|
| `KEYSTORE_BASE64` | résultat de la commande PowerShell ci-dessous (collé depuis le presse-papiers) |
| `KEYSTORE_PASSWORD` | `storePassword` de `keystore/keystore.properties` |
| `KEY_ALIAS` | `mangatheque` |
| `KEY_PASSWORD` | `keyPassword` de `keystore/keystore.properties` |

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("keystore\mangatheque-release.jks")) | Set-Clipboard
```

Ensuite, pour chaque nouvelle version :

1. Dans `app/build.gradle.kts`, augmente `versionCode` (+1) et `versionName` (ex. `1.0.1`).
2. `git commit -am "Version 1.0.1"`
3. `git tag v1.0.1` puis `git push && git push origin v1.0.1`

Deux minutes plus tard, la release et son APK sont en ligne. Le tag **doit** correspondre au `versionName`.

### Méthode B — à la main

1. Augmente `versionCode` et `versionName`, compile avec `.\gradlew.bat assembleRelease`.
2. Sur GitHub → **Releases → Draft a new release** → tag `v1.0.1` → joins `app-release.apk` → Publish.

## 5. Sauvegarde des données

Réglages → **Exporter** crée un `.zip` contenant `mangatheque.json` (séries, tomes, statuts, notes)
et le dossier `photos/` (couvertures et passages). **Restaurer** remplace la collection par ce fichier.
Fais un export avant tout changement de téléphone.

## Structure du code

```
app/src/main/java/fr/mangatheque/app/
├── MangaApp.kt                 Application (base + dépôt)
├── data/
│   ├── Entities.kt             Series, Volume, Moment (Room)
│   ├── Daos.kt / AppDatabase.kt
│   ├── Repository.kt           accès données, préfixe ISBN, devinette de série
│   ├── BookLookup.kt           Google Books + Open Library, découpage « Titre, Tome N »
│   ├── Images.kt               import/redimensionnement/recadrage automatique
│   ├── Backup.kt               export/import .zip
│   ├── Updater.kt              GitHub Releases + DownloadManager + installateur
│   └── Prefs.kt
└── ui/
    ├── MainActivity.kt         navigation + barre d'onglets
    ├── AppViewModel.kt         état, brouillon d'ajout, mises à jour
    ├── theme/Theme.kt          couleurs et polices de la maquette
    ├── components/             couvertures générées, puces, panneaux…
    └── screens/                Home, Series, SeriesEdit, Volume, Scan, AddVolume,
                                CoverCamera, Store, Moments, Settings
```

Polices : Dela Gothic One et Zen Kaku Gothic New (licence SIL Open Font License, Google Fonts).
