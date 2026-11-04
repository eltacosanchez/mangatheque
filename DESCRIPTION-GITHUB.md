# Textes à coller sur GitHub

## 1. Nom du dépôt (champ « Repository name »)

```
mangatheque
```

## 2. Description courte (champ « Description », sous le nom)

```
📚 Ma Mangathèque — appli Android pour gérer ma collection de mangas : scan ISBN, tomes possédés et manquants, statut de lecture, passages préférés en photo, mode magasin. 100 % hors ligne, mise à jour via les Releases.
```

## 3. Visibilité

Coche **Private**.

Conséquence : le téléphone a besoin d'un jeton pour lire les Releases d'un dépôt privé.
Dans l'appli → Réglages → « Jeton GitHub » (voir README, section « Dépôt privé »).

## 4. Texte de la première Release (tag `v1.0.1`, titre « Version 1.0.1 »)

```
## Version 1.0.1

### Plus rapide au scan
- Google Books et Open Library sont interrogés en même temps (au lieu de l'un après l'autre).
- Analyse caméra en résolution réduite, détection limitée au format EAN-13 des ISBN.
- Délais réseau raccourcis : au pire 10 secondes avant de proposer la saisie manuelle.

### Plus clair
- Vibration et bandeau « Code détecté » dès la lecture du code-barres.
- Écran « Recherche en cours… » avec barre de progression.

### Mises à jour
- Prise en charge des dépôts GitHub privés grâce à un jeton d'accès (Réglages).
```

Joindre le fichier `releases/mangatheque-1.0.1.apk` dans la zone « Attach binaries ».
