# Bleach

A Bleach mod for Minecraft 1.21.1 on NeoForge. The goal is to stay as close to Tite Kubo's manga and anime as a Minecraft mod can: become a Shinigami, a Quincy or a Hollow, fight Hollows, and travel to Soul Society and Hueco Mundo.

[Bleach KD](https://www.curseforge.com/minecraft/mc-mods/bleach_kd) (KnightDemon, continuing LittleBreadLoaf's 1.12.2 mod) is used as a design reference only. No code or art is copied from it; the art in this repo is placeholder for now.

The roadmap lives in [docs/FEATURES.md](docs/FEATURES.md).

## Status

Step 2 is done, step 3, the Shinigami path, is playable from the Academy to Shikai, and the first part of step 4, becoming a Quincy, is in. So far:

- Every player has a soul: a soul state (starting as a living Human), a reiryoku pool that regenerates (twice as fast while meditating or sleeping), and the Zankensoki stats, which level up by use.
- Reiryoku grows by beating Hollows (more for purifying them with a zanpakutō), performing Konsō, surviving close calls (including a Totem of Undying save) and levelling stats.
- A spiritually aware Human who dies comes back where they fell as a Plus, with a Chain of Fate hanging from their chest. The chain wears away over an hour of play. In its last quarter Encroachment sets in, with pangs of pain and blindness, and when it runs out the player becomes a Hollow, with a mask (a placeholder skull for now) and a burst of reiryoku. A Konsō from a Shinigami saves a Plus.
- Players below the awareness threshold (30 maximum reiryoku by default) cannot see Pluses, Hollows or other spirits, and their hits, arrows and potions pass straight through them. Just below the threshold, a faint shimmer and distant screams give hidden Hollows away.
- Reiatsu difference: hits from someone with far less spiritual pressure than you do much less damage.
- Press **R** to release your reiatsu. It drains reiryoku and weighs down much weaker creatures and players nearby with Slowness and Mining Fatigue (and Weakness for the far weaker), and creatures far below you stop attacking and run. Running dry ends it and exhausts you. It spares your mount, your pets and players you can't hurt (PvP off, or a team without friendly fire).
- A reiryoku bar beside the hotbar (outlined while reiatsu is released, with a chain gauge beside it for a Plus), and a soul screen on **K**. Every key can be rebound under the Bleach category.
- The Shin'ō Academy: a small building that generates in plains, forests, taiga, savanna, meadows, cherry groves and snowy plains, with one built near world spawn, on untouched ground, if none is within reach. A Plus senses which way it lies. Its instructor enrolls souls that have passed on and hands them an Asauchi, an unbreakable sword that awakens into their own zanpakutō.
- Graduation: a student with level 5 Zanjutsu, Hakuda and Hohō and level 2 Reiatsu Control (standing in for Kidō until kidō exists) becomes a Shinigami, with 25 more reiryoku and a Shihakushō.
- The Substitute route: a wounded Shinigami sometimes turns up at night near an aware Human, and can give them its power. A Shinigami player can do the same by sneaking and using their blade on a Human twice; when the Human accepts by using an empty hand on them, half the Shinigami's reiryoku is lent for 20 minutes. The Human stays alive as a Substitute Shinigami and becomes a full Shinigami if they die.
- Konsō: sneak and use your blade on a Plus player, who accepts by using an empty hand on you. They pass on and are sent to the nearest Academy, and you gain reiryoku. Plus spirits are sent on the same way, without asking.
- Hollows come through Garganta tears in the sky near spiritually aware souls, day or night, more often the stronger your reiatsu: Spider Hollows that climb walls, Hound Hollows in packs, Serpent Hollows and the Plus-hunting Hexapodus.
- Strike a Hollow's mask for double damage. Killing one with a zanpakutō purifies it and sends its soul on.
- Your first Hollow is Fishbone D, and a wounded Shinigami may arrive just ahead of it, offering her power.
- Pluses wander the world with their chains, and villagers who die leave one behind. Leave a Plus alone too long and it becomes a Hollow.
- Shinigami carry a Denreishinki, the soul pager, which beeps when Hollows arrive nearby.
- Press **V** to flash step (Shunpo) from Hohō 5: a few blocks in an instant, further, cheaper and more often as Hohō grows.
- Reishi footing from Hohō 3: jump again while falling (or press the Reishi Footing key, unbound at first) to stand on hardened reishi in mid-air, and sneak to sink through it. It drains reiryoku while you stand.
- The Shihakushō (kosode, hakama, and waraji with tabi), made from wool and wheat at a Tailoring Table. It gives no armour, but the full set speeds up reiryoku regeneration for Shinigami, Substitutes and students. A zanpakutō in your hotbar is drawn sheathed at your hip.
- Bond with your Asauchi by carrying it, meditating, sleeping, surviving close calls and purifying Hollows with it. It whispers as the bond grows.
- Jinzen: sneak and hold use with your blade on solid ground to meet its spirit, and pass its test to learn its name. How you fought while bonding decides which spirit you meet (Zangetsu or Zabimaru for now; spirits are datapack files).
- Press **Z** to release Shikai and **B** to seal it. Shikai drains reiryoku. Zabimaru lashes out several blocks; Zangetsu never seals once released.
- Hold **G** with Zangetsu released to gather a Getsuga Tenshō and let go to swing it: a piercing crescent that purifies Hollows and cuts soft blocks where mob griefing allows.
- New players are asked once whether Quincy blood runs in them (Human, Quincy, or let fate decide). Quincy blood is Echt or Gemischt and comes with a Quincy Cross.
- Hold use with the Cross until the Heilig Bogen forms, then draw and fire Heilig Pfeil arrows of reishi. They destroy Hollows instead of purifying them. Reishi refills by itself, and fast while meditating with the Cross, more in forests and near an open Garganta.
- From Quincy tier 2, **V** does Hirenkyaku and you can stand on reishi. **N** calls Blut Vene (defence) and Blut Arterie (attack).
- Hollow attacks poison a Quincy, and at 100 the soul tears apart. An awakened Quincy comes back alive rather than as a Plus, and cannot take a Shinigami's power or enroll at the Academy.
- The white Quincy garb, from the Tailoring Table.
- A Bleach advancement tab, `/bleach` admin commands, a per-world server config (`serverconfig/bleach-server.toml`) and a client config (`config/bleach-client.toml`, for turning off footing on jump).

How each stat trains:

| Stat | Trained by | Effect |
|---|---|---|
| Zanjutsu | landing sword hits (double on Hollows, more on the mask) | +3% sword damage per level |
| Hakuda | landing unarmed hits (double on Hollows, more on the mask) | +0.25 unarmed damage per level |
| Hohō | sprinting, flash stepping | +1% movement speed per level; Shunpo from level 5, 0.2 blocks further and cheaper per level; reishi footing from level 3 |
| Kidō | casting kidō (coming in step 4) | none yet; the Academy examines Reiatsu Control in its place |
| Reiatsu Control | meditating (crouching still), up to a minute at a time; landing Heilig Pfeil on Hollows | +2% reiryoku regeneration per level; 2% less Shikai drain per level (down to 40%); +0.1 Heilig Pfeil damage, 2% faster reishi channelling and 0.1 blocks more Hirenkyaku per level; the Academy's Kidō exam |

Admin commands (permission level 2):

```
/bleach info [player]
/bleach soul <players> <human|plus|rukongai|shinigami|hollow|arrancar>
/bleach enroll <players>
/bleach reiryoku <players> <max> [current]
/bleach stat <players> <stat> <level>
/bleach tier <players> <shinigami|hollow|quincy|fullbring> <tier>
/bleach chain <players> <seconds>
/bleach reset <players>
/bleach academy
/bleach garganta <players> [spider|hexapodus|hound|serpent|fishbone_d]
/bleach firsthollow <players> <reset|skip|start>
/bleach zanpakuto <players> bond <0..1000>
/bleach zanpakuto <players> affinity <power|kido|constant_release> <0..10000>
/bleach zanpakuto <players> spirit <id>
/bleach zanpakuto <players> name
/bleach zanpakuto <players> seal
/bleach zanpakuto <players> reset
/bleach quincy <players> bloodline <none|gemischt|echt>
/bleach quincy <players> training <points>
/bleach quincy <players> reishi <amount>
/bleach quincy <players> poison <0..100>
/bleach quincy <players> blut <off|vene|arterie>
/bleach quincy <players> reset
```

`/bleach soul … shinigami` also grants the first Shinigami tier, `/bleach enroll` enrolls souls that have passed on (Rukongai souls) at the Academy, and `/bleach academy` shows where the nearest Academy is. `/bleach garganta` opens a tear now, ignoring the chance and caps (not on Peaceful), and `/bleach firsthollow` resets, skips or starts the Fishbone D encounter. `/bleach zanpakuto` sets the Asauchi bond, the hidden leaning (power, kidō or constant release) and the spirit, teaches the name as if the test was passed, and seals or resets the blade. `/bleach quincy` sets the bloodline (as if chosen, but quietly: no message and no Quincy Cross; an awakened Quincy can switch between Gemischt and Echt but cannot be left without Quincy blood until reset), Quincy training (raising the tier to match), reishi, Hollow poison and Blut, or resets the Quincy power and asks about the bloodline again.

## Building

You need JDK 21.

```sh
./gradlew build             # the mod jar ends up in build/libs/
./gradlew runClient         # start Minecraft with the mod
./gradlew runGameTestServer # run the automated game tests on a headless server
```

Every pull request is built on GitHub Actions, which also runs the game tests and a client smoke test (`./gradlew runClientSmokeTest`: it starts the game, makes a world, answers the bloodline question as a Human, plays through becoming a Plus, a Hollow and then a Shinigami who tailors, talks to an instructor, flash steps, stands on reishi footing and places an Academy, then summons each Hollow, strikes a mask, purifies a Hollow, sends a Plus on, opens a Garganta and checks what a soul just below the threshold sees, then sits in Jinzen with an Asauchi, picks the red ribbon, learns Zangetsu's name, releases it, fires a Getsuga Tenshō, opens the soul screen, then is born an Echt Quincy in the white garb, fires a Heilig Pfeil into the sky and another at a Hollow, calls Blut Vene, uses Hirenkyaku and opens the soul screen again, and quits). The built jar and the smoke test's screenshots can be downloaded from the run's **Artifacts** section.

`tools/` holds the scripts that make the placeholder art and the structure templates. They need only Python 3, and `--check` confirms the committed files match what they make.

Zanpakutō spirits are data: a datapack adds one at `data/<namespace>/bleach/zanpakuto_spirit/<name>.json`. They are read when a world loads, not on `/reload`.

## Versions

| | |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.262 |
| ModDevGradle | 2.0.148 |
| Gradle | 8.14.6 |
