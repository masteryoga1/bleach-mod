# Feature roadmap

This is the checklist the mod is built from. It covers what Bleach KD (1.12.2) had and what canon Bleach (the manga, the anime and Thousand-Year Blood War) has that KD left out, sorted into the rounds of the plan. Where KD and canon disagree, canon wins, and the "Canon notes" section at the end lists those disagreements.

Tick an item when it lands in a merged pull request. Reorder, cut or add items freely; nothing below is locked in.

**Tags**

- **[Canon+KD]**: in canon and in KD.
- **[Canon]**: in canon, missing from KD.
- **[KD]**: a KD invention, kept only because it fits canon and plays well.
- *(optional)*: low priority or off by default.

## Step 2: Core system

The foundations every path builds on: what a player's soul is, their spiritual energy, and how the game shows it.

- [x] **Soul state** [Canon+KD]: every player starts as a living, spiritually aware Human. Their data records a soul state (Human, Plus, Shinigami, Hollow, Arrancar) plus separate power tiers for Shinigami, Hollow, Quincy and Fullbring, so hybrids like Visored and Substitute Shinigami fit later.
- [x] **Spiritual awareness** [Canon+KD]: players below a reiryoku threshold cannot see or hit spirits. Hollows, Pluses and spirit-form players are hidden from them, with a faint shimmer and distant Hollow screams just below the threshold. *Hidden spirit players can still be heard, but Hollows and Pluses are heard only by those who can see them, and the crosshair passes through hidden spirits. Potions from the unaware pass through spirits, Instant Health and Harming included. Just below the threshold (two thirds of it by default) hidden spirits give off a faint shimmer and Hollow screams sound faint and far away; with the default starting reiryoku every player starts aware, so this matters only on servers that lower it.*
- [x] **Reiryoku and reiatsu** [Canon+KD]: one spiritual-energy pool that regenerates over time, faster in reishi-dense places and while resting. Running dry causes exhaustion (Slowness and Weakness) and ends any release. *Running dry ends the pressure aura and seals a Shikai. Reishi-dense places arrive with Soul Society.*
- [x] **Energy bar on the HUD** [Canon+KD]: a reiryoku bar beside the hotbar, plus slots for a chain timer, mask timer or Quincy reishi when those exist. Hidden for unaware Humans. *The chain gauge is in, and turns red during Encroachment. The mask timer and Quincy reishi arrive with their paths. During Shikai the bar is outlined in the blade's colour, and a gather bar fills above it while a Getsuga Tenshō gathers.*
- [x] **Zankensoki stats** [Canon]: Zanjutsu (swordplay), Hakuda (unarmed), Hohō (speed) and Kidō, plus Reiatsu Control, all levelled by use. Stats map onto vanilla attributes (attack damage, movement speed and so on) instead of KD's nine invented stats. *Kidō trains once kidō exists in step 4.*
- [x] **Death into a Plus** [Canon+KD]: an aware Human who dies comes back as a Plus at the death point, with a Chain of Fate on their chest. *A death spot inside a wall, in a liquid, fire or something else harmful, outside the world border, or over the void sends the Plus to their normal respawn point instead. A respawn anchor that was not needed gets its charge back. The chain and the Hollow mask are placeholders built from vanilla models.*
- [x] **Chain of Fate and Encroachment** [Canon+KD]: the chain shortens over time, and near the end its links eat each other. A Konsō before it runs out saves the soul; if it runs out, the hole and mask form and the player becomes a Hollow. *The chain lasts an hour, wears twice as fast below 30% health and half as fast near a Shinigami (a player, a Substitute or an NPC), and dying as a Plus costs five minutes of it. The last quarter brings Encroachment pangs, and a chain that runs out makes the player a Hollow. A Konsō from a Shinigami frees the soul.*
- [x] **Reiatsu difference** [Canon]: attacks from someone with far lower spiritual pressure do much less damage, like Kenpachi shrugging off weak blows. This replaces KD's full-armour damage shield. *Hits land fully up to a 3:1 gap, then shrink, never below a tenth. Hollows, Pluses and Shinigami NPCs have reiatsu of their own (a Hollow's grows with its size and with every Plus it devours); other creatures count their max health.*
- [x] **Reiatsu pressure aura** [Canon]: a toggle that floods the area with your reiatsu, slowing and frightening much weaker players and mobs while draining your energy. *On R by default. Releasing costs a second and a half's worth of reiryoku up front, then drains 5% of the maximum a second with no regeneration. After holding it in, your reiatsu takes two seconds to settle, with no regeneration and no new release. It reaches further as reiryoku grows, and creatures it weighs down hard stop attacking and run away (a hissing creeper's fuse dies down). It spares your mount, your pets and players you couldn't hurt anyway (PvP off, or a team without friendly fire).*
- [x] **Stats screen** [Canon+KD]: shows soul state, stats, reiryoku, zanpakutō name and release state, and what comes next on your path. *Soul state, reiryoku, the Chain of Fate, whether reiatsu is released, the Hollows purified and slain and Pluses sent on, stats, awakened powers and the next step, plus whether the soul is a Substitute or an Academy student and, for a Plus, which way the Academy lies. Until the blade has a name it shows the Asauchi bond; after, the zanpakutō's name and whether it is sealed, in Shikai or always released.*
- [ ] **Keybinds** [KD]: separate, rebindable keys for Release, Seal, movement technique, two technique slots, Devour and the stats screen, in a Bleach category. No overloaded vanilla keys. *So far: the stats screen on K, reiatsu release on R, Shunpo on V, Reishi Footing (unbound; jumping again while falling also works and can be turned off in the client config), and zanpakutō Release on Z, Seal on B and a Technique key on G. The second technique slot and Devour arrive with their features.*
- [x] **`/bleach` admin commands** [KD]: one command to set soul state, reiryoku, stats and zanpakutō for testing and servers. *`/bleach zanpakuto` sets the Asauchi bond, the hidden leaning and the spirit, teaches the name, and seals or resets the blade.*
- [ ] **Server config** [KD]: energy caps, regen and growth rates, path toggles, mob griefing and spawn rates. *So far: reiryoku caps, regen, growth and stat rates, death and the Chain of Fate (including turning Hollowfication off), reiatsu difference, the pressure aura, graduation, Konsō, the Substitute route, Shunpo, reishi footing, the Shihakushō set bonus, Garganta arrivals, Hollows, the first Hollow, Pluses, the Asauchi bond, Jinzen, Shikai, Getsuga Tenshō, the Denreishinki and the shimmer band. The other settings arrive with their features.*
- [x] **Reiryoku growth** [Canon+KD]: reiryoku grows by fighting spiritual beings, surviving close calls and training. *Only spiritual beings count: Hollows, and players who are Hollows or Arrancar. Purifying a Hollow with a zanpakutō gives half as much again, and one far weaker than you gives less. Killing a Hollow or Arrancar player grows reiryoku at most once every 10 minutes, since they come back as they were. A server option brings back growth from ordinary hostile mobs, off by default.*
- [ ] **Bleach equipment slots** [Canon+KD] *(optional)*: extra slots for a sheath, haori or badge, cloak and glove, through Curios or a small custom menu.
- [ ] **Advancement tree** [KD]: an advancement for each step of each path, so players can see the route. *So far: a Bleach tab for training, reiryoku growth, the pressure aura, becoming a Plus, Encroachment and Hollowfication, then passing on, enrolling, graduating, Konsō, becoming a Substitute, Shunpo, reishi footing, the Shihakushō, the first Garganta, surviving the first Hollow, striking the mask, purification, Fishbone D, ten Konsō, Jinzen, the Asauchi's voice, the zanpakutō's name, Shikai and Getsuga Tenshō. Each path adds its own as it arrives.*

## Step 3: Shinigami path

The first playable slice: become a Shinigami, awaken your zanpakutō and hunt Hollows in the Overworld. Soul Society does not exist yet, so the Academy is a placeholder Overworld building.

**Becoming a Shinigami**

- [x] **Academy instructor and Asauchi** [Canon+KD]: an instructor NPC in a small Overworld Academy building enrolls aware players and hands out an Asauchi. Graduation checks the four Zankensoki stats. *So far: the Academy is a small building in plains, forests, taiga, savanna, meadows, cherry groves and snowy plains, and if none lies within reach one is built near world spawn: straight away in a new world, and in one already played only on ground no player has spent time on, so it never lands on a build. The instructor cannot be hurt, pushed, pulled or led away. A Plus senses the way to it. Only souls that have passed on can enroll: a Plus asks the instructor for Konsō first, and the living are warned away. The exams ask for level 5 Zanjutsu, Hakuda and Hohō; Kidō is examined through Reiatsu Control level 2 until kidō exists in step 4. Graduates get their first Shinigami tier, 25 reiryoku and a Shihakushō. The Asauchi never breaks.*
- [x] **Substitute Shinigami** [Canon]: Ichigo's route. A wounded Shinigami NPC, or another Shinigami player, transfers power to a living Human, who becomes a Substitute Shinigami and keeps their body. *A wounded Shinigami sometimes turns up at night near an aware Human with at least 60 reiryoku. A Shinigami player with at least 80 reiryoku can lend half of it for 20 minutes, during which they regenerate at half speed and cannot use Shunpo, Konsō, reishi footing or their release. Either way the Human gains 25 reiryoku, their first Shinigami tier and an Asauchi, and stays alive and visible. A Substitute who dies becomes a full Shinigami. The badge and leaving the body come later.*
- [x] **Asauchi bond** [Canon+KD]: carrying the Asauchi, meditating with it and killing Hollows with it builds a bond. At full bond the blade can awaken. *While a Shinigami, Substitute or Rukongai soul carries an unbound Asauchi, it bonds: 2 a minute for carrying it, 1 for each stretch of meditation, 25 for a Jinzen (once every 5 minutes), up to 50 for purifying a Hollow with it (half the Hollow's reiatsu), 15 for a close call (30 for a Substitute) and 30 for a full night's sleep (once every 20 minutes). The blade whispers at each quarter. A soul whose power is lent does not bond.*
- [x] **Jinzen and the inner world** [Canon]: sit in Jinzen (sneak and use with the blade across your lap) to meet your zanpakutō spirit. In this step it is a dialogue screen with a test; the real inner-world dimension comes later. *Sneak and hold use with the blade in hand, the off hand empty, on solid ground. After three still seconds a screen opens on the spirit's world. Below a quarter bond the blade is silent; below full the spirit stirs and shows what it is becoming. At full bond, with 100 reiryoku and Shinigami power, the spirit sets its test: picking out the red ribbon in time, answering its question, or striking through closing jaws. A failed test can be retried after 3 minutes, with more time for the ribbons. Moving, being hurt or closing the screen ends Jinzen; it counts as meditating while it lasts.*
- [x] **Zanpakutō spirit, name and release command** [Canon]: the spirit reveals its name and its release command (like "Scatter, Senbonzakura"). Learning the name turns the Asauchi into your own zanpakutō, bound to you. *Passing the test teaches the name and the command, grows reiryoku by 10, and binds the Asauchi to its owner: it takes the spirit's name and colour and answers to nobody else. A named soul's blade is kept through death, and a new Asauchi takes the spirit's form again if the old blade is lost.*
- [x] **Zanpakutō type** [Canon+KD]: a hidden affinity built up from how and where you fight while bonding with the Asauchi decides which kind of spirit you meet: power type, kidō type (elemental, poison, illusion) or constant release. *How the blade was used while bonding decides the spirit: purifying, mask strikes, Hakuda and Zanjutsu lean to power; Jinzen, reishi footing, Reiatsu Control and fighting in rain, water, snow or desert lean to kidō; released reiatsu, close calls and raw reiryoku without control lean to constant release, and Substitutes lean that way most. No kidō-type spirit ships yet, so a kidō leaning meets the next-closest kind.*
- [x] **Canon zanpakutō presets** [Canon]: spirits are defined in data. Step 3 ships **Zangetsu** and one simpler power type such as **Zabimaru**; more presets arrive in later rounds. *Spirits are datapack files (`data/<namespace>/bleach/zanpakuto_spirit/`), read when the world loads. Zangetsu (constant release, Getsuga Tenshō) and Zabimaru (power type, "Howl", a segmented whip-sword) ship now.*

**Shikai and combat**

- [x] **Shikai release** [Canon+KD]: press Release to say the command and name. The same blade changes form and gains its ability while reiryoku drains; it seals again when you choose or run dry. *Release on Z says the command and the name to aware souls nearby; the blade changes form (Zabimaru: 8 damage and 3 blocks more reach) and drains reiryoku with no regeneration (Zabimaru 0.6% of maximum a second, 2% less per Reiatsu Control level, down to 40%). It seals on B, when reiryoku runs out, after a second out of hand, when tossed, on sleep, death or logout, and when power is lent. Zangetsu is released once and never seals.*
- [x] **Getsuga Tenshō** [Canon+KD]: Zangetsu's signature move. Reiatsu gathered in the blade leaves the tip on the swing as a pale blue-white crescent that pierces several targets. Damage scales with reiatsu, and breaking blocks follows the mobGriefing rule. *Hold the Technique key with Zangetsu released to gather reiatsu (full in a second and a half) and let go to swing it out: 8 damage plus 0.8 per square root of maximum reiryoku, up to 60, scaled by the charge, piercing 3 to 5 targets and purifying Hollows. It costs 6% to 16% of maximum reiryoku with a 1-second cooldown. It is weaker until it has been used ten times and Zangetsu names it. It cuts soft blocks (hardness up to 0.8, no block entities) only where mob griefing, spawn protection and building rights allow.*
- [x] **Shunpo** [Canon+KD]: a short flash-step dash whose distance grows with Hohō. No outfit needed. *On V, from Hohō 5: 6 blocks plus 0.2 per Hohō level, up to 16, for 5% of maximum reiryoku with a 1.5-second cooldown, falling to 2.5% and half a second at Hohō 50. On the ground it runs flat along WASD, climbing or dropping a block at a time; in the air it follows your gaze and ends on reishi footing. It stops at walls, lava, fire and the world border. Shinigami, Substitutes and Academy students can use it, and Slowness shortens it.*
- [x] **Konsō** [Canon+KD]: tap a Plus on the forehead with the pommel to send it on. This is the core early loop and grows reiatsu. *Sneak and use your Asauchi on a Plus player, who accepts by using an empty hand on you within 30 seconds. They become a Rukongai soul with no chain and are sent to the nearest Academy, a stand-in for the Rukongai until Soul Society exists. The Shinigami gains 3 maximum reiryoku, for up to 10 Konsō a day. The instructor and the wounded Shinigami perform it too. Plus spirits are sent on the same way, without asking, sinking away into the light, and count toward the same daily limit.*
- [x] **Reishi footing** [Canon]: spiritually trained players can stand and walk on hardened reishi in mid-air, draining a little energy. *Jump again while falling, or press the Reishi Footing key, to stand on hardened reishi; sneak to sink through it. Needs Hohō 3 and Shinigami power or Academy enrolment. It drains 1.5% of maximum reiryoku a second with no regeneration, down to a quarter of that with Hohō. It ends on solid ground or a ladder, and a soul who logs out standing on it is standing on it again when they come back.*
- [x] **Shihakushō** [Canon+KD]: the black Shinigami uniform with waraji sandals and tabi, made at a tailoring block, with the sheathed blade drawn on the hip. Clothes are cosmetic with a small set bonus; real defence comes from reiatsu. *Kosode, hakama, and waraji with tabi, made from wool and wheat at a Tailoring Table. They give no armour and never wear out; for Shinigami, Substitutes and students the full set adds 10% reiryoku regeneration. A zanpakutō in the hotbar but not in hand is drawn sheathed at the hip opposite the sword arm. Zabimaru's sheath is dark red with a red hilt, and Zangetsu, which has no sheath, is carried across the back wrapped in white cloth.*
- [x] **Denreishinki** [Canon]: the soul pager. It pings the direction and distance of nearby Hollow arrivals. *Handed over at graduation and with a Shinigami's power, and craftable from iron, redstone and an amethyst shard. It beeps with the direction and rough distance of every Hollow arrival within 96 blocks, and of every Plus that turns Hollow, and used, it points to the nearest Hollow. It answers only to Shinigami power or an Academy student.*

**Hollows and Pluses**

- [x] **Hollows arrive through Garganta tears** [Canon]: instead of spawning in the dark like zombies, Hollows drop out of rifts in the sky near spiritually aware players, at any hour. Higher reiatsu draws more of them. *A tear opens 12 to 24 blocks from a spiritually aware soul in the Overworld, up to 6 blocks above the ground, and its Hollows drop out a second and a half later and land unharmed. The chance is 6% a minute at 40 reiatsu and grows with the square root of reiatsu up to 40%, half as likely again at night or for a Plus, and twice as likely while reiatsu is released and a quarter more likely again for a soul whose constant-release zanpakutō never seals. At least 2 minutes pass between arrivals, and none come while 4 Hollows are within 64 blocks, within 32 blocks of the Academy instructor, in the first minute after joining or respawning, or before a soul's first Hollow. Stronger souls draw Hound packs and second Hollows rather than stronger Hollows. No tears on Peaceful, with mob spawning off, for Hollows, or for creative and spectator players. A Hollow with no prey for 2 minutes leaves through a tear of its own.*
- [x] **Hollow base mob** [Canon+KD]: one Hollow base with data-driven variants (model, size, attributes, on-hit effect). KD's numbers are the balance starting point: 70 health and 6 attack on Normal. *One Hollow base with a table of kinds (model, size range, attributes, reiatsu, on-hit effect, weak point and arrival weight), plus health and damage multipliers in the server config. The Serpent Hollow is the standard: 70 health and 6 attack on Normal. Easy has five sevenths of the health and no on-hit effects, Hard doubles armour, and damage follows vanilla's difficulty scaling. Each Hollow rolls a size, and its health and reiatsu grow with it. Hollows have souls with their own reiatsu, heal a quarter of a heart a second when left alone, cannot burn, drown or take fall damage, and never break blocks. They hunt aware souls and Pluses, never Shinigami NPCs, and never the unaware or Hollow players unless the server config says so. Mundane creatures such as iron golems, wolves and Wardens cannot see or hurt spirits, and spirits pass through them, through unaware players, pressure plates and tripwires without a trace.*
- [x] **Mask weak point and purification** [Canon]: hits to the mask deal bonus damage. A zanpakutō kill purifies the Hollow and sends its soul on, and the mask dissolves. *A blow or arrow that meets the mask before the body deals double damage, cracks the mask for 10 seconds, stops it healing and makes it reel. After five blows to the body in a row, a soul is told to aim for the mask. A killing blow from a zanpakutō in the hands of a Shinigami, Substitute or Academy student purifies the Hollow: it turns white and dissolves into light, and the soul it once was rises and passes on. Purifying gives half as much reiryoku again and 5 Zanjutsu XP. Any other kill only destroys it. A Getsuga Tenshō purifies too, and one that meets the mask first strikes it.*
- [x] **Fishbone D** [Canon+KD]: the first Hollow of the series, used as the scripted "first Hollow" encounter when a player first becomes aware. *On the first night after ten minutes of aware play, Fishbone D tears through the sky to hunt a Human or Plus: 80 health, 6 attack, slow crushing blows. For a Human who could take a Shinigami's power, a wounded Shinigami stumbles in half a minute before it lands. No other Hollow comes until it is over. If it loses its prey, or the fight lasts 5 minutes, it retreats and comes back 10 minutes later.*
- [x] **Generic early Hollows** [KD]: three or four animal-like Hollows to start with: a wall-climbing spider type, a poisonous serpent type and a pack wolf type. *A wall-climbing Spider Hollow (60 health, nauseating bite), Hound Hollows that hunt in packs of 2 to 4 (40 health each, from 60 reiatsu) and a rearing Serpent Hollow (70 health, poison, from 80 reiatsu). Placeholder looks built on the vanilla spider and wolf models and simple custom ones, with the mask drawn as its own layer that cracks.*
- [x] **Pluses** [Canon+KD]: ambient Plus spirits with a visible chain that flee from Hollows. When villagers die they leave Pluses behind. A Plus whose chain runs out turns into a Hollow. *Pluses wander near aware souls, three times as often in villages, and a villager or wandering trader who dies leaves one where it fell, unless it was turned into a zombie villager. A villager's Plus looks as the villager did, with its chain hanging from its chest, and stays near its home or workplace. Pluses flee Hollows and Hollow players, and murmur when an aware soul speaks to them. Their chain lasts 20 minutes, a Hollow's bite takes 30 seconds off it (for Plus players too), and only a Hollow can end one: any other killing blow costs it 5 minutes of chain instead. A Plus whose chain runs out becomes a Hollow on the spot, once there is room there for one, and one a Hollow kills is devoured and heals it.*
- [ ] **Hollow mask fragments** [KD] *(optional)*: cracking a Hollow's mask in a fight can knock off fragments, used as a crafting material. *Arrives with Hollow Bait in step 4, the first thing that uses them.*
- [x] **Hexapodus** [Canon] *(optional)*: an insect Hollow that hunts Pluses first. *A six-legged insect Hollow (50 health, slowing bite) that goes for Pluses before most souls, and only turns from one for a soul of far greater reiatsu. It runs them down, and comes three times as often where Pluses are.*

## Step 4: Quincy

- [ ] **Quincy bloodline** [Canon]: Quincy are born, not made. Heritage is a first-join choice or a rare random trait (server config), and powers awaken through training with a Quincy Cross.
- [ ] **Quincy Cross and Heilig Bogen** [Canon+KD]: the cross forms a reishi bow that fires Heilig Pfeil without ammo. Damage scales with reishi control and charge.
- [ ] **Reishi gathering** [Canon+KD]: Quincy power comes from absorbing ambient reishi, stronger in reishi-dense places.
- [ ] **Hirenkyaku** [Canon]: the Quincy dash, on the same movement key as Shunpo.
- [ ] **Blut Vene and Blut Arterie** [Canon+KD]: one toggle between defence (Vene) and offence (Arterie). They cannot both be active.
- [ ] **Seele Schneider** [Canon+KD]: a vibrating reishi blade that can also be fired from the bow.
- [ ] **Gintō spells** [Canon]: silver tubes of reiryoku used as spell catalysts: Heizen, Gritz, Wolke and Sprenger (five Seele Schneider in a pentagon).
- [ ] **Sanrei Glove** [Canon+KD]: a training glove that makes the bow harder to use. Removing a mastered glove enables Letzt Stil.
- [ ] **Quincy: Letzt Stil** [Canon+KD]: a huge one-time power-up after removing a mastered Sanrei Glove. Afterwards Quincy powers are lost until restored (in canon, by a Heilig Pfeil 19 mm right of the heart).
- [ ] **Ginrei Kojaku** [Canon+KD]: the web-like bow of restored power, with rapid fire and Licht Regen volleys.
- [ ] **Quincy: Vollständig** [Canon+KD]: the top transformation: reishi wings, a halo, and Sklaverei draining reishi from nearby enemies.
- [ ] **Hollow poisoning** [Canon+KD]: Hollow reiatsu is poison to Quincy and builds up when they are hit by Hollows.
- [ ] **Hollow Bait** [Canon+KD]: a crushable Quincy tablet that draws waves of Hollows to an area for about a day, sometimes ending with a Menos Grande.
- [ ] **Quincy and Wandenreich outfits** [Canon+KD]: cosmetic sets.
- [ ] **Quincy NPCs** [Canon+KD]: ranged NPCs with bow attacks, Hirenkyaku and Blut.
- [ ] **Ransotengai** [Canon] *(optional)*: reishi threads that keep you fighting when your body would give out.

## Step 4: Hollows and Arrancar

- [ ] **Playing as a Hollow** [Canon+KD]: when the chain runs out, the player becomes a Hollow, with a mask and hole, soul hunger instead of food, Hollow regeneration and Hollow attacks.
- [ ] **Devouring** [Canon+KD]: a Devour key used on weakened spiritual targets restores hunger and energy and counts toward evolution.
- [ ] **Menos Grande evolution** [Canon+KD]: devouring leads from Hollow to Gillian (building-sized), then Adjuchas (who must keep devouring or regress), then Vasto Lorde (human-sized, captain-level).
- [ ] **Cero** [Canon+KD]: a charged beam for Gillian and above, Arrancar and masked Visored. Gran Rey Cero comes later.
- [ ] **Hollow techniques** [Canon]: Bala (a quick reiatsu shot), Sonído (the Arrancar flash step), Hierro (hardened skin), Pesquisa (sensing) and Negación (the rescue light).
- [ ] **Becoming an Arrancar** [Canon+KD]: a Menos tears off its mask, rarely and imperfectly by itself, or reliably with the Hōgyoku. The player keeps a mask fragment and the hole, and their Hollow power is sealed in a zanpakutō.
- [ ] **Resurrección** [Canon+KD]: the Arrancar's release command frees their Hollow form, with a real form change and a heal.
- [ ] **Visored** [Canon+KD]: Hollowfied Shinigami who must defeat their inner Hollow. The mask is a timed power-up whose duration grows with training.
- [ ] **Hollow mutations** [KD] *(optional)*: body-part passives such as wall climbing, gliding or a poison tail while in Hollow form.
- [ ] **Gillian and Adjuchas mobs** [Canon+KD]: Gillian are near-identical giants with long-nosed masks that fire Cero from the mouth. Adjuchas hunt in packs and heal by eating weaker Hollows.
- [ ] **Arrancar NPCs and ranks** [Canon]: Números, Fracción, Privaron Espada and Espada, with number tattoos and rank-based strength.
- [ ] **Arrancar and Visored cosmetics** [Canon+KD]: the white Arrancar uniform, the mask remnant and hole, and canon-styled Visored masks.
- [ ] **More Hollow variants** [KD] *(optional)*: flying, brute and Hueco Mundo desert Hollows reworked from KD's roster.

## Step 4: Kidō

- [ ] **Kidō system** [Canon]: numbered Hadō (destruction), Bakudō (binding) and Kaidō (healing) spells, defined in data and gated by the Kidō stat. KD had no real Kidō.
- [ ] **Incantations** [Canon]: typing the full incantation in chat casts at full power; casting by key (Eishōhaki) is faster but weaker.
- [ ] **First Hadō** [Canon]: #1 Shō, #4 Byakurai, #31 Shakkahō, #33 Sōkatsui, then #58 Tenran and #63 Raikōhō.
- [ ] **First Bakudō** [Canon]: #1 Sai, #4 Hainawa, #9 Geki, #21 Sekienton (smoke escape), #39 Enkōsen (shield), #61 Rikujōkōrō and #81 Dankū.
- [ ] **Kaidō** [Canon]: channelled healing on a target.

## Step 4: Soul Society and Hueco Mundo

**Soul Society**

- [ ] **Soul Society dimension** [Canon+KD]: Seireitei as a walled circular city at the centre, ringed by the Rukongai, which gets rougher further out (80 districts in each direction).
- [ ] **Konsō arrival** [Canon]: a Plus sent on by Konsō arrives in the Rukongai.
- [ ] **Shin'ō Academy** [Canon+KD]: the real Academy inside Seireitei replaces the Step 3 placeholder. *Academy uniforms (white with blue or red trim), dummy drills and the Hollow-hunt field trial come with it.*
- [ ] **Seireitei walls, gates and gatekeepers** [Canon]: a Sekkiseki wall that blocks spiritual travel, the four great gates and their giant gatekeepers.
- [ ] **Sekkiseki** [Canon+KD]: stone that nullifies reiryoku; spiritual projectiles fizzle against it.
- [ ] **Sōkyoku Hill and the Senzaikyū** [Canon]: the central mesa with the white tower and the hidden training ground beneath.
- [ ] **Gotei 13 barracks** [Canon]: thirteen division barracks with banners, sharing a white-plaster and dark-roof palette.
- [ ] **Seireitei building blocks** [Canon+KD]: plaster, roof tiles, shōji, sliding doors and tatami.

**Travel**

- [ ] **Senkaimon and Hell Butterflies** [Canon+KD]: a Shinigami turns their zanpakutō like a key to open a Senkaimon. A Hell Butterfly guides you safely through.
- [ ] **Dangai** [Canon]: the corridor between worlds. Without a Hell Butterfly you are dropped into it, with walls that grab you and the Kōtotsu sweeping behind.
- [ ] **Garganta** [Canon+KD]: Menos-class Hollows and Arrancar tear open a Garganta at will; others need Urahara's technology.

**Hueco Mundo**

- [ ] **Hueco Mundo dimension** [Canon+KD]: white sand dunes under a black sky, quartz-like trees, no water, and a moon whose phase is opposite the Overworld's.
- [ ] **Forest of Menos** [Canon+KD]: the vast cavern under the sand where Gillian roam and the quartz trees take root.
- [ ] **Las Noches** [Canon]: the all-white fortress with a central dome, five towers and an artificial blue sky inside.
- [ ] **White sand and quartz blocks** [Canon+KD]: the Hueco Mundo building palette.

**Human World**

- [ ] **Urahara Shop** [Canon]: a rare Overworld shop that sells Hollow Bait and other gear, with an underground training room.
- [ ] **Gentei Reiin** [Canon] *(optional)*: high-ranked Shinigami have their power capped in the Human World unless released.

## Step 4: Bosses

- [ ] **Grand Fisher** [Canon+KD]: a hidden-reiatsu ambush boss whose lure, grown from its brow, looks like a girl or a loved one.
- [ ] **Menos Grande** [Canon+KD]: a Gillian arriving through the sky as the finale of a Hollow Bait event.
- [ ] **Vasto Lorde** [Canon+KD]: rare roaming bosses deep in Hueco Mundo.
- [ ] **Espada** [Canon]: named Las Noches bosses with a sealed phase and a Resurrección phase, starting with Grimmjow.
- [ ] **Bankai** [Canon+KD]: materialise and subjugate your zanpakutō spirit in a timed duel (a Tenshintai makes it possible quickly), then release Bankai with its own name and form.
- [ ] **Shrieker and the Gate of Hell** [Canon]: a mini-boss with exploding minions. Killing a sinful Hollow with a zanpakutō opens the Gate of Hell and drags it in.
- [ ] **Runuganga** [Canon] *(optional)*: the sand guardian of Las Noches, weak to freezing.
- [ ] **Sōsuke Aizen and the Hōgyoku** [Canon] *(optional)*: the endgame boss, with Kyōka Suigetsu illusions. The Hōgyoku is his unique drop, not a farmable item.
- [ ] **Sternritter** [Canon] *(optional)*: Thousand-Year Blood War bosses with a Schrift each and medallions that steal Bankai, recovered with Shin'eiyaku.

## Later

- [ ] **More zanpakutō presets** [Canon]: Senbonzakura, Hyōrinmaru, Sode no Shirayuki, Tobiume, Haineko, Ashisogi Jizō, Suzumebachi, Ryūjin Jakka, Nejibana and others, each with its canon command and ability.
- [ ] **Bankai mastery** [Canon+KD]: mastery lowers drain and unlocks moves; the black and red Bankai Getsuga Tenshō.
- [ ] **Gotei 13 divisions and ranks** [Canon]: join a division after graduating, rise through the seats, earn a lieutenant badge and a captain's haori.
- [ ] **Gigai and Gikongan** [Canon]: an artificial body for spirits, and the Soul Candy pill that pops your soul out of it.
- [ ] **Fullbring** [Canon+KD]: a birth trait (your mother survived a Hollow before you were born) that draws out the soul of a cherished object.
- [ ] **Spiritual sensing** [Canon]: briefly outline nearby spiritual beings, coloured by race.
- [ ] **Advanced Hohō and Hakuda** [Canon]: Utsusemi, Senka and Shunkō.
- [ ] **Schatten Bereich** [Canon+KD] *(optional)*: the Quincy realm in Seireitei's shadows, around the ice palace Silbern.
- [ ] **Soul King Palace** [Canon] *(optional)*: the Royal Guard's floating palaces and their power-ups.
- [ ] **Hell** [Canon+KD] *(optional)*: Kushanāda and Togabito, reached only through the Gate of Hell.
- [ ] **Mugetsu** [Canon] *(optional)*: Zangetsu's final Getsuga Tenshō, which seals your powers afterwards.
- [ ] **Cosmetics** [Canon+KD] *(optional)*: haori, Karakura High uniforms, Urahara's hat and similar.
- [ ] **Shihakushō lining** [KD] *(optional)*: tailor a piece of armour into the uniform so it keeps its defence.

## Left out on purpose

These KD features are not coming over because they contradict canon or are not Bleach at all:

- Element points from Hollow kills and KD's 18-type element combos (shikai comes from the wielder's soul, not kill counts).
- Crafting zanpakutō from reishi and soul steel, or naming them on an anvil.
- Becoming a Quincy, Visored or Fullbringer by right-clicking a Reishi Block or eating mask shards.
- The Reishi Block race altar and the quartz-lamp Arrancar ritual.
- Hollow masks as wearable loot armour with powers (trophy cosmetics only).
- "Spirit Candy" as energy food.
- Hollow Knight (another game's character), Hollow Dragon, Creeper, Blaze, Ore and other non-canon Hollow types, and KD's extra Gillian types.
- KD's elemental Kidō throwables.
- KD's music discs, which contain copyrighted Bleach soundtrack audio.
- Bount (anime filler) *(could return later behind a config option)*.

## Canon notes

Where KD differs from canon, and what this mod does instead:

- **Spellings**: Resurrección (not "Resurrecion"), Asauchi (not "Asuchi"), Quincy: Letzt Stil (not "Leztz Stil"), Quincy: Vollständig, Blut Vene and Blut Arterie, Hirenkyaku, Gintō.
- **Getsuga Tenshō** belongs to Zangetsu, not to a "lunar" element. It leaves the blade with the swing, pale blue-white in Shikai and black with a red edge in Bankai. Zangetsu gives it its name once it has been used.
- **Shikai** is unlocked by learning the spirit's name in Jinzen, then saying its fixed command and name. **Bankai** needs the spirit to be materialised and subjugated, and has its own name and form. Zangetsu is a constant-release zanpakutō, with no command and no sealed form once released.
- **The Asauchi** is a nameless blade that takes on its owner's soul over time spent together, so the spirit comes from how the owner fought while bonding, not from a menu, kill counts or an anvil.
- **Hollows** come through Garganta from Hueco Mundo or form from Pluses whose chain eroded. They hunt by day too, and their mask is their weak point. A zanpakutō purifies one and sends its soul on; any other weapon only destroys it.
- **Gillian** fire Cero from the mouth and all look nearly alike. **Vasto Lorde** are human-sized.
- **Arrancar** are Hollows that removed their masks. Many lose their high-speed regeneration, which KD has backwards. **Segunda Etapa** was unique to Ulquiorra.
- **Quincy** are born (Echt or Gemischt) and **Gintō** are spell catalysts, not something to drink. **Sprenger** uses five Seele Schneider in a pentagon.
- **Konsō** is done with the pommel and gives no loot. **Hell Butterflies** guide Senkaimon travel and carry messages, and live in Seireitei rather than spawning in the wild.
- **Seireitei** has four named gates with gatekeepers: Hakutōmon (Jidanbō), Kokuryōmon (Danzōmaru), Shōryūmon (Kaiwan) and Shuwaimon (Higonyūdō). It is far larger than KD's town.
- **Sekkiseki** nullifies reiryoku. In KD it is just a hard block.
- **Hueco Mundo's** moon phase is the opposite of the Human World's, and Las Noches is entirely white.
- **The Hōgyoku** is a unique artifact, not a kill-count drop.
- **Ashido Kanō** is an anime-only ally in the Forest of Menos, not a Visored and not a random hostile Shinigami.
- **Becoming a Shinigami**: only the souls of the dead enter the Academy, after Konsō. The living gain Shinigami power only when a Shinigami gives it, as Rukia did for Ichigo, which leaves the giver nearly powerless and is a crime under Soul Society law. Until Soul Society exists, Konsō sends a player's soul to the nearest Academy building instead of the Rukongai.
- **Substitutes** fight in their own body for now. In canon the Shinigami form is the soul outside the body; soul form, the badge and Kon come later.
- **Pluses** look like the people they were and linger where they died or near home; a villager who dies leaves one. Hollows hunt them first, and a Plus whose chain runs out becomes a Hollow on the spot. KD's Whole NPCs spawned anywhere.
