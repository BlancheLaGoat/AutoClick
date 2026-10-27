# AutoClick

Auto-clicker Android sans pub, sans Internet, sans tracker. Jusqu'à 1 ms entre deux clics, plusieurs cibles, bulle flottante.

## Obtenir l'APK (sans rien installer sur ton PC)

1. Crée un repo vide sur GitHub (ex. `BlancheLaGoat/AutoClick`).
2. Dans ce dossier :
   ```
   git init -b main
   git add .
   git commit -m "AutoClick"
   git remote add origin https://github.com/BlancheLaGoat/AutoClick.git
   git push -u origin main
   ```
3. Onglet **Actions** du repo → le build se lance tout seul (~3 min).
4. L'APK apparaît dans **Releases** (`AutoClick.apk`). Ouvre la page depuis ton téléphone et télécharge-le.

Chaque push recompile et publie une nouvelle release. L'APK est toujours signé avec la même clé (`app/autoclick.keystore`), donc les mises à jour s'installent par-dessus.

Alternative : ouvrir le dossier dans Android Studio → *Build > Build APK(s)*.

## Installation sur le téléphone

1. Installe l'APK (autorise « sources inconnues » pour ton navigateur / gestionnaire de fichiers).
2. Ouvre AutoClick → **Activer le service d'accessibilité** → AutoClick → ON.
3. Si c'est grisé (Android 13+ : « paramètre restreint ») : **Ouvrir les infos de l'appli** → ⋮ → **Autoriser les paramètres restreints**, puis recommence l'étape 2.
4. **Désactiver l'optimisation de batterie** (évite qu'Android coupe le service).

## Utilisation

Bulle : `✥` déplacer · `▶/■` lancer/arrêter · `+`/`−` cibles · `⚙` réglages · `✕` masquer.
Glisse les ronds rouges là où il faut cliquer. Pendant le clic, les ronds deviennent transparents aux appuis.

## Vitesse : ce qui est réaliste

- Réglage par défaut : appui 1 ms + pause 1 ms → un clic toutes les 2 ms, soit **~500 clics/s théoriques**.
- L'appli regroupe 10 appuis par geste envoyé au système, ce qui évite la latence d'aller-retour entre chaque clic. Entre deux lots il reste quelques ms de latence système.
- Beaucoup d'applis/jeux ne traitent les appuis qu'à chaque image (60–120 Hz), et certains ignorent les clics trop rapprochés. Le compteur sous ▶ affiche la vitesse réelle obtenue.
