# Bleach

A Bleach mod for Minecraft 1.21.1 on NeoForge. The goal is to stay as close to Tite Kubo's manga and anime as a Minecraft mod can: become a Shinigami, a Quincy or a Hollow, fight Hollows, and travel to Soul Society and Hueco Mundo.

[Bleach KD](https://www.curseforge.com/minecraft/mc-mods/bleach_kd) (KnightDemon, continuing LittleBreadLoaf's 1.12.2 mod) is used as a design reference only. No code or art is copied from it; the art in this repo is placeholder for now.

The roadmap lives in [docs/FEATURES.md](docs/FEATURES.md).

## Status

Step 1 of the plan: the project builds, the mod loads, and it adds a **Bleach** creative tab with an **Asauchi**, the nameless blade every Shinigami starts with.

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
