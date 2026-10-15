# Bleach

A Bleach mod for Minecraft 1.21.1 on NeoForge. The goal is to stay as close to Tite Kubo's manga and anime as a Minecraft mod can: become a Shinigami, a Quincy or a Hollow, fight Hollows, and travel to Soul Society and Hueco Mundo.

[Bleach KD](https://www.curseforge.com/minecraft/mc-mods/bleach_kd) (KnightDemon, continuing LittleBreadLoaf's 1.12.2 mod) is used as a design reference only. No code or art is copied from it; the art in this repo is placeholder for now.

The roadmap lives in [docs/FEATURES.md](docs/FEATURES.md).

## Status

Step 2 of the plan is in progress. So far:

- Every player has a soul: a soul state (starting as a living Human), a reiryoku pool that regenerates (twice as fast while meditating or sleeping), and the Zankensoki stats, which level up by use. Running reiryoku dry causes exhaustion, though nothing spends it until the pressure aura and techniques arrive.
- Reiryoku grows by beating hostile creatures, surviving close calls (including a Totem of Undying save) and levelling stats.
- A reiryoku bar beside the hotbar, and a soul screen on **K** (rebindable under the Bleach category).
- `/bleach` admin commands and a per-world server config (`serverconfig/bleach-server.toml`).

How each stat trains:

| Stat | Trained by | Effect |
|---|---|---|
| Zanjutsu | landing sword hits | +3% sword damage per level |
| Hakuda | landing unarmed hits | +0.25 unarmed damage per level |
| Hohō | sprinting | +1% movement speed per level |
| Kidō | casting kidō (coming in step 4) | none yet |
| Reiatsu Control | meditating (crouching still), up to a minute at a time | +2% reiryoku regeneration per level |

Admin commands (permission level 2):

```
/bleach info [player]
/bleach soul <players> <human|plus|shinigami|hollow|arrancar>
/bleach reiryoku <players> <max> [current]
/bleach stat <players> <stat> <level>
/bleach tier <players> <shinigami|hollow|quincy|fullbring> <tier>
/bleach reset <players>
```

## Building

You need JDK 21.

```sh
./gradlew build             # the mod jar ends up in build/libs/
./gradlew runClient         # start Minecraft with the mod
./gradlew runGameTestServer # run the automated game tests on a headless server
```

Every pull request is built on GitHub Actions, which also runs the game tests. The built jar can be downloaded from the run's **Artifacts** section.

## Versions

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.262 |
| ModDevGradle | 2.0.148 |
| Gradle | 8.14.6 |
