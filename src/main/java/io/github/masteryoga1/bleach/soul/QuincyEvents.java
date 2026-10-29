package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.CeroBeam;
import io.github.masteryoga1.bleach.entity.FishboneD;
import io.github.masteryoga1.bleach.entity.HeiligPfeil;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.item.QuincyGarbItem;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.quincy.Bloodline;
import io.github.masteryoga1.bleach.quincy.BloodlineChoice;
import io.github.masteryoga1.bleach.quincy.BlutMode;
import io.github.masteryoga1.bleach.quincy.QuincyKey;
import io.github.masteryoga1.bleach.registry.ModItems;
import io.github.masteryoga1.bleach.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * The Quincy path on the server: the bloodline question, drawing the Heilig Bogen and loosing its
 * arrows, training and the tiers it brings, gathering reishi, Blut Vene and Blut Arterie, Hollow
 * poison, and a soul torn apart. The rules themselves are in {@link QuincyLogic}. What can be done
 * without a connection takes a {@link Player}, so game tests can use mock players.
 */
public final class QuincyEvents {
    public static final ResourceKey<DamageType> SOUL_DESTRUCTION =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("soul_destruction"));

    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 50;
    private static final int TITLE_FADE_OUT = 20;
    /** A latent soul is told its reishi slipped away at most this often. */
    private static final int FIZZLE_MESSAGE_INTERVAL_TICKS = 1200;
    private static final int FIZZLE_PARTICLES = 4;
    private static final int CHANNEL_PARTICLES = 3;
    /** How far from the chest gathered reishi starts drifting in. */
    private static final double CHANNEL_PARTICLE_REACH = 1.5;
    private static final int DESTROYED_PARTICLES = 12;

    private QuincyEvents() {
    }

    public static void register(IEventBus bus) {
        // Normal priority: with the blow to a Hollow's mask, after Zanjutsu and Hakuda, and before
        // ReiatsuEvents weighs the reiatsu difference.
        bus.addListener(EventPriority.NORMAL, QuincyEvents::onIncomingDamage);
        bus.addListener(QuincyEvents::onDamagePre);
        bus.addListener(QuincyEvents::onDamageTaken);
    }

    /** What tears a soul apart: Hollow poison that has run its course, or a Quincy's chain running out. */
    public static DamageSource soulDestruction(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(SOUL_DESTRUCTION));
    }

    // ---- Logging in, respawning and the bloodline ----

    /**
     * Runs on every refresh (logging in, respawning, {@code /bleach reset}): settles an old save,
     * lets Blut go, tells a soul that just came back what happened to it, and settles the bloodline of
     * a living Human who has not settled it yet, by asking or by fate, as the server is set up. A
     * player still on the death screen is left for the refresh that comes with respawning, so a Cross
     * is never put into a dead player's inventory.
     */
    public static void onRefresh(ServerPlayer player, SoulData data) {
        if (player instanceof FakePlayer) {
            return;
        }
        QuincyLogic.settleOldSave(data);
        data.setQuincy(data.quincy().withBlut(BlutMode.OFF));
        // Stages already reached are not news after a respawn or a relog.
        data.poisonStage = QuincyLogic.poisonStage(data.quincy().poison());
        if (data.soulReformed || data.quincyReborn) {
            String key = data.soulReformed ? "message.bleach.quincy.soul_reformed" : "message.bleach.quincy.reborn";
            data.soulReformed = false;
            data.quincyReborn = false;
            player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        // Before the bloodline below, which returns early for almost everyone.
        QuincyArtsEvents.onRefresh(player, data, player.level().getGameTime());
        if (!player.isAlive() || !QuincyLogic.needsBloodlineChoice(data)) {
            return;
        }
        switch (BleachConfig.get(BleachConfig.QUINCY_BLOODLINE_MODE)) {
            case CHOICE -> {
                if (!data.bloodlineAsked) {
                    data.bloodlineAsked = true;
                    BleachNetwork.openBloodline(player);
                }
            }
            case RANDOM -> {
                Bloodline bloodline = QuincyLogic.rollBloodline(BloodlineChoice.FATE, player.getRandom());
                setBloodline(player, data, bloodline, false);
                if (bloodline != Bloodline.NONE) {
                    player.sendSystemMessage(Component.translatable("message.bleach.bloodline.stirs")
                            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    handDownCross(player);
                }
            }
            case OFF -> {
            }
        }
    }

    /**
     * The player answered the bloodline question. Only an answer to a question this session asked
     * counts, only once, and only while the soul is still a living Human the question is for (not one
     * made a Plus or given a Shinigami's power since); anything else is ignored. A soul that dies
     * before answering is asked again when it respawns, if it still qualifies.
     */
    public static void chooseBloodline(ServerPlayer player, BloodlineChoice choice) {
        SoulData data = SoulLogic.get(player);
        if (data.quincy().decided() || !data.bloodlineAsked || !player.isAlive()
                || !QuincyLogic.needsBloodlineChoice(data)) {
            return;
        }
        Bloodline bloodline = QuincyLogic.rollBloodline(choice, player.getRandom());
        if (bloodline == Bloodline.NONE) {
            setBloodline(player, data, bloodline, false);
            player.sendSystemMessage(Component.translatable(choice == BloodlineChoice.FATE
                    ? "message.bleach.bloodline.fate_none" : "message.bleach.bloodline.human")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            setBloodline(player, data, bloodline, true);
        }
        BleachNetwork.syncSoul(player, data);
    }

    /**
     * Settles the bloodline for good. Announced, the player is told, and Quincy blood comes with a
     * Quincy Cross if the server hands them down.
     */
    public static void setBloodline(Player player, SoulData data, Bloodline bloodline, boolean announce) {
        data.setQuincy(data.quincy().withBloodline(bloodline).withDecided(true));
        if (!announce) {
            return;
        }
        if (bloodline == Bloodline.NONE) {
            player.sendSystemMessage(Component.translatable("message.bleach.bloodline.human").withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendSystemMessage(Component.translatable("message.bleach.bloodline.quincy", bloodline.displayName())
                .withStyle(ChatFormatting.AQUA));
        handDownCross(player);
    }

    /** Gives a Quincy Cross, into the inventory or at the feet if it is full, when the server hands them down. */
    private static void handDownCross(Player player) {
        if (!BleachConfig.get(BleachConfig.QUINCY_GIVE_CROSS)) {
            return;
        }
        ItemStack cross = new ItemStack(ModItems.QUINCY_CROSS.get());
        if (!player.getInventory().add(cross) && !cross.isEmpty()) {
            player.drop(cross, false);
        }
        player.sendSystemMessage(Component.translatable("message.bleach.bloodline.cross").withStyle(ChatFormatting.GRAY));
    }

    /** Takes the Quincy power and the bloodline away, so the soul is asked about its blood again. */
    public static void resetQuincy(ServerPlayer player) {
        QuincyArtsEvents.endAll(player, SoulLogic.get(player));
        SoulData data = SoulLogic.get(player);
        QuincyRansotengai.closeTrial(player, data);
        QuincyArts ended = QuincyArtsLogic.arts(data);
        data.setQuincy(QuincyProgress.NONE);
        QuincyArtsLogic.keepLandingGrace(ended, data);
        data.setTier(PowerPath.QUINCY, 0);
        data.bloodlineAsked = false;
        onRefresh(player, data);
    }

    // ---- Every tick ----

    /**
     * Runs every tick for every living player, from {@link SoulEvents}: keeps Blut paid for or ends
     * it, works out how rich in reishi the place is and gathers it once a second, and lets Hollow
     * poison wear off or do its harm.
     *
     * @param meditating whether the player is meditating (crouched and still, or sitting in Jinzen)
     */
    public static void tick(ServerPlayer player, SoulData data, boolean meditating) {
        QuincyArtsEvents.tick(player, data, player.level().getGameTime());
        QuincyProgress quincy = data.quincy();
        if (!QuincyLogic.isAwakened(data) && quincy.blut() == BlutMode.OFF && quincy.poison() <= 0.0F
                && data.poisonStage == 0) {
            return;
        }
        long now = player.level().getGameTime();
        tickBlut(player, data);
        boolean channelling = meditating && QuincyLogic.holdsCross(player);
        if (player.tickCount % QuincyLogic.GATHER_INTERVAL_TICKS == 0) {
            data.reishiDensity = density(player);
            // The pool shrinks with the tier or maximum reiryoku it was filled for.
            QuincyLogic.setReishi(data, QuincyLogic.reishi(data));
            gather(player, data, channelling);
        }
        tickPoison(player, data, now, channelling);
    }

    /** Pays a tick of Blut, or lets it go when it cannot go on. */
    private static void tickBlut(ServerPlayer player, SoulData data) {
        BlutMode blut = data.quincy().blut();
        if (blut == BlutMode.OFF) {
            return;
        }
        if (!QuincyLogic.canUsePowers(data) || player.isSpectator()) {
            endBlut(player, data, null);
        } else if (data.quincy().poison() >= QuincyLogic.POISON_BLOCK) {
            endBlut(player, data, "message.bleach.blut.poisoned");
        } else if (!QuincyLogic.drainReishi(data, QuincyLogic.blutDrainPerSecond(blut) / 20.0F)) {
            endBlut(player, data, "message.bleach.blut.dry");
        }
    }

    /**
     * How rich in reishi the player's surroundings are: the place itself, and more inside another
     * soul's released reiatsu (a Hollow's or an Arrancar's poisons instead), never above the cap.
     */
    private static double density(ServerPlayer player) {
        double density = QuincyLogic.placeDensity(player.level(), player.position());
        for (ServerPlayer other : player.serverLevel().players()) {
            if (other == player || !other.isAlive() || !SoulLogic.hasSoul(other)) {
                continue;
            }
            SoulData theirs = SoulLogic.get(other);
            float radius = SoulLogic.pressureRadius(theirs);
            if (theirs.isReiatsuReleased() && !QuincyLogic.isHollowSource(other)
                    && other.distanceToSqr(player) <= radius * radius) {
                density += QuincyLogic.AURA_DENSITY_BONUS;
                break;
            }
        }
        return Math.min(QuincyLogic.MAX_DENSITY, density);
    }

    /**
     * A second's worth of reishi gathering, when the soul can gather: not while Blut runs or on reishi
     * footing (unless Letzt Stil burns, which gathers through both), nor as a spectator. Channelled
     * reishi is seen drifting into the chest.
     */
    private static void gather(ServerPlayer player, SoulData data, boolean channelling) {
        if (!QuincyLogic.canUsePowers(data) || player.isSpectator() || !player.isAlive()
                || (!QuincyArtsLogic.gathersThroughEverything(data)
                        && (data.quincy().blut() != BlutMode.OFF || ReishiFooting.isActive(player)))) {
            return;
        }
        float perSecond = QuincyLogic.gatherPerSecond(data, data.reishiDensity, channelling, QuincyGarbItem.isFullSet(player));
        if (perSecond <= 0.0F) {
            return;
        }
        QuincyLogic.setReishi(data, QuincyLogic.reishi(data) + perSecond);
        if (channelling) {
            RandomSource random = player.getRandom();
            double chestY = player.getY(0.6);
            for (int i = 0; i < CHANNEL_PARTICLES; i++) {
                double angle = random.nextDouble() * 2.0 * Math.PI;
                double dx = Math.cos(angle) * CHANNEL_PARTICLE_REACH;
                double dy = random.nextDouble() - 0.4;
                double dz = Math.sin(angle) * CHANNEL_PARTICLE_REACH;
                // A count of 0 sends one particle moving along (-dx, -dy, -dz): in towards the chest.
                SpiritSenses.particles(player.serverLevel(), ParticleTypes.END_ROD, player.getX() + dx, chestY + dy,
                        player.getZ() + dz, 0, -dx, -dy, -dz, 0.05);
            }
        }
    }

    /**
     * Hollow poison wears off a minute after it last built up, faster while channelling where reishi
     * is rich. Its stages are announced as they are reached, the heaviest hurts now and then, and all
     * of it tears the soul apart.
     */
    private static void tickPoison(ServerPlayer player, SoulData data, long now, boolean channelling) {
        float poison = data.quincy().poison();
        // Full poison never wears off: it tears the soul apart below, whenever the soul can feel it.
        if (poison > 0.0F && poison < QuincyProgress.MAX_POISON
                && now - data.lastPoisonAt >= QuincyLogic.POISON_DECAY_DELAY_TICKS) {
            boolean dense = channelling && data.reishiDensity >= QuincyLogic.CHANNEL_DECAY_MIN_DENSITY;
            poison = Math.max(0.0F, poison - QuincyLogic.poisonDecayPerTick(dense));
            data.setQuincy(data.quincy().withPoison(poison));
        }
        int stage = QuincyLogic.poisonStage(poison);
        if (!QuincyLogic.canUsePowers(data) || player.isCreative() || player.isSpectator()) {
            // Nothing to announce or suffer: only keep track, so nothing is announced late.
            data.poisonStage = stage;
            return;
        }
        if (stage >= 4) {
            // Nothing can hurt the player right now (between dimensions): full poison waits for a later tick.
            if (!canDestroySoulNow(player)) {
                return;
            }
            if (!destroySoul(player, data)) {
                data.setQuincy(data.quincy().withPoison(QuincyLogic.POISON_BLOCK));
                data.poisonStage = 2;
            }
            return;
        }
        int was = data.poisonStage;
        data.poisonStage = stage;
        if (stage > was) {
            String step = stage == 3 ? "75" : stage == 2 ? "50" : "25";
            player.sendSystemMessage(Component.translatable("message.bleach.hollow_poison." + step)
                    .withStyle(ChatFormatting.DARK_PURPLE));
            if (stage >= 2) {
                Milestones.grant(player, Milestones.HOLLOW_POISON);
            }
        } else if (stage < 2 && was >= 2) {
            player.sendSystemMessage(Component.translatable("message.bleach.hollow_poison.eased")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (stage >= 3 && now - data.lastPangAt >= QuincyLogic.PANG_INTERVAL_TICKS) {
            data.lastPangAt = now;
            pang(player);
        }
    }

    /** Heavy poison pounds at the heart: a little pain that never kills, a lurch, and a heartbeat only the player hears. */
    private static void pang(ServerPlayer player) {
        if (player.getHealth() > QuincyLogic.PANG_MIN_HEALTH) {
            player.hurt(player.damageSources().magic(), QuincyLogic.PANG_DAMAGE);
        }
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, QuincyLogic.NAUSEA_TICKS, 0));
        player.playNotifySound(ModSounds.HOLLOW_POISON.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Each meditation grant with a Cross in hand trains a Quincy, awakened or still able to awaken, but not while sealed. */
    public static void onMeditationGrant(Player player, SoulData data) {
        if (QuincyLogic.holdsCross(player) && !QuincyArtsLogic.isSealed(data)
                && (QuincyLogic.isAwakened(data) || QuincyLogic.canAwaken(data))) {
            train(player, data, QuincyLogic.MEDITATION_TRAINING);
        }
    }

    // ---- The Heilig Bogen ----

    /**
     * Each tick the Cross is drawn, on the server. The arts come first (the rite, the glove's strain,
     * Ginrei Kojaku's stream, Licht Regen; see {@link QuincyArtsEvents#onBowHeld}). The draw is heard
     * as it starts. Held past a full draw, the bow costs reishi to keep (twice as much in the
     * unmastered glove), and comes apart once there is none.
     *
     * @param ticksDrawn ticks since the draw started
     */
    public static void onBowHeld(Player player, int ticksDrawn) {
        if (!SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (QuincyArtsEvents.onBowHeld(player, data, ticksDrawn)) {
            return;
        }
        if (ticksDrawn == 1) {
            playToHolder(player, ModSounds.QUINCY_CROSS_DRAW.get(), 0.6F, 1.0F);
        }
        if (ticksDrawn <= QuincyArtsLogic.drawTicks(data) || !QuincyLogic.canUsePowers(data)) {
            return;
        }
        float reishi = QuincyLogic.reishi(data);
        if (reishi <= 0.0F) {
            player.stopUsingItem();
            tell(player, Component.translatable("message.bleach.heilig_bogen.collapsed"));
            return;
        }
        float upkeep = QuincyLogic.HOLD_UPKEEP_PER_SECOND * QuincyArtsLogic.holdUpkeepMultiplier(data);
        QuincyLogic.setReishi(data, reishi - upkeep / 20.0F);
    }

    /**
     * The Cross was let go, on the server. An awakened Quincy fires, if the shot can be paid for. A
     * latent soul's reishi slips away again, but a long enough draw trains the blood (three times as
     * much with a Hollow near), and the draw that awakens it looses its first arrow for free.
     *
     * @param ticksDrawn how long the Cross was drawn, in ticks
     */
    public static void release(Player player, int ticksDrawn) {
        if (!SoulLogic.hasSoul(player) || ticksDrawn < QuincyLogic.MIN_DRAW_TICKS) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        // The rite, a stream or a Licht Regen gather let go, or a Letzt Pfeil (see QuincyArtsEvents#onRelease).
        if (QuincyArtsEvents.onRelease(player, data, ticksDrawn)) {
            return;
        }
        float power = QuincyArtsLogic.drawPower(ticksDrawn, QuincyArtsLogic.drawTicks(data));
        if (QuincyLogic.canUsePowers(data)) {
            float paid = QuincyLogic.payForShot(data, power);
            if (paid < 0.0F) {
                tell(player, Component.translatable("message.bleach.heilig_bogen.too_weak"));
                playToHolder(player, ModSounds.QUINCY_CROSS_FIZZLE.get(), 0.6F, 1.0F);
                return;
            }
            fire(player, data, paid);
            return;
        }
        if (!QuincyLogic.canAwaken(data)) {
            return;
        }
        long now = player.level().getGameTime();
        boolean counted = ticksDrawn >= QuincyLogic.AWAKENING_DRAW_TICKS
                && now - data.lastDrawTrainingAt >= QuincyLogic.AWAKENING_COOLDOWN_TICKS;
        if (counted) {
            data.lastDrawTrainingAt = now;
            int points = QuincyLogic.AWAKENING_TRAINING;
            if (hollowNear(player)) {
                points *= QuincyLogic.AWAKENING_HOLLOW_MULTIPLIER;
            }
            train(player, data, points);
            if (QuincyLogic.isAwakened(data)) {
                // The bow forms at last, and the arrow it was drawn for flies.
                fire(player, data, power);
                return;
            }
        }
        fizzle(player);
        if (counted && now - data.lastFizzleMessageAt >= FIZZLE_MESSAGE_INTERVAL_TICKS) {
            data.lastFizzleMessageAt = now;
            tell(player, Component.translatable("message.bleach.quincy_cross.fizzle"));
        }
    }

    /** Whether a living Hollow is close enough to this soul to stir its blood. */
    private static boolean hollowNear(Player player) {
        double range = QuincyLogic.AWAKENING_HOLLOW_RANGE;
        return !player.level().getEntitiesOfClass(Hollow.class, player.getBoundingBox().inflate(range),
                hollow -> hollow.isAlive() && hollow.distanceToSqr(player) <= range * range).isEmpty();
    }

    /** The reishi gathered in a latent soul's hand slips away: a fizzle, and a few motes of light at the hand. */
    private static void fizzle(Player player) {
        playToHolder(player, ModSounds.QUINCY_CROSS_FIZZLE.get(), 0.6F, 1.0F);
        if (player.level() instanceof ServerLevel level) {
            Vec3 hand = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.5)).subtract(0.0, 0.3, 0.0);
            SpiritSenses.particles(level, ParticleTypes.END_ROD, hand.x, hand.y, hand.z, FIZZLE_PARTICLES,
                    0.1, 0.1, 0.1, 0.02);
        }
    }

    /**
     * Looses a Heilig Pfeil drawn this far (already paid for) from just under the eyes, the way the
     * player looks, as the soul's form shapes it; or a Seele Schneider from the other hand instead
     * (see {@link QuincyArtsEvents#fireSeele}). The arts then add what goes with the shot (see
     * {@link QuincyArtsEvents#afterFire}).
     */
    public static void fire(Player player, SoulData data, float power) {
        Level level = player.level();
        if (level.isClientSide()) {
            return;
        }
        if (QuincyArtsEvents.fireSeele(player, data, power)) {
            return;
        }
        Vec3 from = player.getEyePosition().subtract(0.0, 0.1, 0.0);
        Vec3 direction = player.getViewVector(1.0F);
        float damage = QuincyLogic.arrowDamage(data, power);
        float speed = QuincyLogic.shotSpeed(power) * QuincyArtsLogic.shotSpeedMultiplier(data);
        float inaccuracy = QuincyLogic.shotInaccuracy(power);
        HeiligPfeil.launch(level, player, from, direction, power, damage,
                QuincyLogic.pierceFor(data, power) + QuincyArtsLogic.extraPierce(data, power), speed, inaccuracy);
        SpiritSenses.play(player, ModSounds.HEILIG_PFEIL_FIRE.get(), SoundSource.PLAYERS, 1.0F, 0.9F + 0.3F * power, false);
        player.awardStat(Stats.ITEM_USED.get(ModItems.QUINCY_CROSS.get()));
        QuincyArtsEvents.afterFire(player, data, power, from, direction, damage, speed, inaccuracy);
    }

    /**
     * Adds Quincy training (see {@link QuincyLogic#addTraining}). With no points, this only brings
     * the tier up to the training. A tier gained is celebrated: a title, a sound, a word in chat, and
     * word of any Blut the blood is now ready for, and the third tier hands down the Sanrei Glove.
     * Training enough since the power was restored opens Vollständig, which is said once.
     */
    public static void train(Player player, SoulData data, int points) {
        int before = QuincyLogic.tier(data);
        boolean wasUnlocked = QuincyArtsLogic.vollUnlocked(data);
        int gained = QuincyLogic.addTraining(data, points);
        if (!wasUnlocked && QuincyArtsLogic.vollUnlocked(data)) {
            player.sendSystemMessage(Component.translatable("message.bleach.vollstandig.unlocked",
                    Component.keybind("key.bleach.quincy_form")).withStyle(ChatFormatting.AQUA));
        }
        if (gained <= 0) {
            return;
        }
        int tier = QuincyLogic.tier(data);
        SpiritSenses.play(player, ModSounds.QUINCY_AWAKEN.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT));
            serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.translatable("title.bleach.quincy.subtitle." + tier)));
            serverPlayer.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.bleach.quincy")));
        }
        if (before == 0) {
            player.sendSystemMessage(Component.translatable("message.bleach.quincy.awakened").withStyle(ChatFormatting.AQUA));
        }
        if (tier > 1) {
            player.sendSystemMessage(Component.translatable("message.bleach.quincy.tier", tier).withStyle(ChatFormatting.AQUA));
        }
        for (BlutMode mode : new BlutMode[] {BlutMode.VENE, BlutMode.ARTERIE}) {
            if (before < QuincyLogic.blutUnlockTier(QuincyLogic.bloodline(data), mode) && QuincyLogic.blutUnlocked(data, mode)) {
                player.sendSystemMessage(Component.translatable("message.bleach.blut.unlocked", mode.displayName(),
                        Component.keybind("key.bleach.blut")).withStyle(ChatFormatting.AQUA));
            }
        }
        QuincyArtsEvents.onTierReached(player, data, before, tier);
    }

    // ---- Heilig Pfeil hits ----

    /**
     * A Heilig Pfeil hurt a creature. A hit on a spirit earns the milestone; a hit on a Hollow, or a
     * Hollow or Arrancar player, trains Reiatsu Control and the Quincy power, more for the mask, each
     * at most once in a while. A shot that kills counts as a destroyed soul instead (see
     * {@link #onSoulDestroyed}).
     */
    public static void onArrowLanded(@Nullable Entity owner, LivingEntity target, boolean maskStruck) {
        if (!(owner instanceof Player player) || player.level().isClientSide() || !SoulLogic.hasSoul(player)) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer && SoulLogic.isSpirit(target)) {
            Milestones.grant(serverPlayer, Milestones.HEILIG_PFEIL);
        }
        if (!QuincyLogic.isHollowLike(target)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        if (now - data.lastArrowXpAt >= QuincyLogic.ARROW_XP_COOLDOWN_TICKS) {
            data.lastArrowXpAt = now;
            SoulLogic.addStatXp(player, data, Stat.REIATSU_CONTROL, 1);
        }
        if (target.isAlive() && now - data.lastHitTrainingAt >= QuincyLogic.HIT_TRAINING_COOLDOWN_TICKS) {
            data.lastHitTrainingAt = now;
            train(player, data, maskStruck ? QuincyLogic.MASK_HIT_TRAINING : QuincyLogic.HIT_TRAINING);
            QuincyArtsEvents.onArrowHit(player, data, target);
        }
    }

    /**
     * A Heilig Pfeil killed a Hollow, and destroyed its soul instead of sending it on. The Quincy
     * grows for it (more for Fishbone D), and every Shinigami nearby feels it.
     */
    public static void onSoulDestroyed(Player killer, Hollow hollow) {
        if (!SoulLogic.hasSoul(killer)) {
            return;
        }
        SoulData data = SoulLogic.get(killer);
        data.setQuincy(data.quincy().withDestroyed(data.quincy().destroyed() + 1));
        train(killer, data, hollow instanceof FishboneD ? QuincyLogic.FISHBONE_D_TRAINING : QuincyLogic.DESTROY_TRAINING);
        QuincyArtsEvents.onSoulDestroyed(killer, data);
        killer.displayClientMessage(Component.translatable("message.bleach.quincy.destroyed")
                .withStyle(ChatFormatting.DARK_AQUA), true);
        if (!(hollow.level() instanceof ServerLevel level)) {
            return;
        }
        SpiritSenses.particles(level, ParticleTypes.SOUL, hollow.getX(), hollow.getY(0.5), hollow.getZ(),
                DESTROYED_PARTICLES, hollow.getBbWidth() * 0.4, hollow.getBbHeight() * 0.4, hollow.getBbWidth() * 0.4, 0.03);
        double rangeSqr = QuincyLogic.DESTROYED_NEARBY_RANGE * QuincyLogic.DESTROYED_NEARBY_RANGE;
        for (ServerPlayer other : level.players()) {
            if (other != killer && other.isAlive() && other.distanceToSqr(hollow) <= rangeSqr && SoulLogic.hasSoul(other)
                    && ShinigamiLogic.hasShinigamiPower(SoulLogic.get(other))) {
                other.displayClientMessage(Component.translatable("message.bleach.quincy.destroyed_nearby")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** A Heilig Pfeil killed a Hollow or Arrancar player: it counts as a soul destroyed, and no more. */
    public static void onHollowPlayerDestroyed(Player killer, Player victim) {
        if (killer != victim && SoulLogic.hasSoul(killer)) {
            SoulData data = SoulLogic.get(killer);
            data.setQuincy(data.quincy().withDestroyed(data.quincy().destroyed() + 1));
            QuincyArtsEvents.onSoulDestroyed(killer, data);
        }
    }

    // ---- Hollow poison ----

    /** A Hollow was purified here: its light washes some poison out of every Quincy close by. */
    public static void onPurifiedNearby(ServerLevel level, Vec3 at) {
        double rangeSqr = QuincyLogic.PURIFICATION_CURE_RANGE * QuincyLogic.PURIFICATION_CURE_RANGE;
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.distanceToSqr(at) > rangeSqr || !SoulLogic.hasSoul(player)) {
                continue;
            }
            SoulData data = SoulLogic.get(player);
            if (QuincyLogic.isAwakened(data) && QuincyLogic.cure(data, QuincyLogic.PURIFICATION_CURE)) {
                player.displayClientMessage(Component.translatable("message.bleach.hollow_poison.cured")
                        .withStyle(ChatFormatting.AQUA), true);
            }
        }
    }

    /** One pulse of a Hollow's or an Arrancar's released aura reached this player. */
    public static void onHollowAura(Player target, float amount) {
        if (SoulLogic.hasSoul(target)) {
            addPoison(target, SoulLogic.get(target), amount);
        }
    }

    /**
     * Adds Hollow poison, up to the most there can be, and puts off its wearing off. Only a Quincy who
     * can use their power is poisoned, never in creative or spectator mode, and only while the server
     * has Hollow poison turned on.
     */
    public static void addPoison(Player player, SoulData data, float amount) {
        if (!(amount > 0.0F) || !BleachConfig.get(BleachConfig.HOLLOW_POISON_ENABLED) || !QuincyLogic.canUsePowers(data)
                || player.isCreative() || player.isSpectator()) {
            return;
        }
        float poison = Math.min(QuincyProgress.MAX_POISON, data.quincy().poison() + amount);
        data.setQuincy(data.quincy().withPoison(poison));
        data.lastPoisonAt = player.level().getGameTime();
    }

    /**
     * Whether a soul could be torn apart this tick. Not while nothing can hurt the player, as while
     * they pass between dimensions; that is a reason to wait, not a death that was cancelled.
     */
    public static boolean canDestroySoulNow(Player player) {
        return !player.isInvulnerableTo(soulDestruction(player.level()));
    }

    /**
     * Tears the soul apart: the player dies, past any totem, and comes back a living Human, weaker
     * (see {@link SoulLogic#afterDeath}). Never for a player in creative or spectator mode, or one
     * already dead.
     *
     * @return false if nothing happened, or the death was cancelled and the player still lives
     */
    public static boolean destroySoul(Player player, SoulData data) {
        if (player.level().isClientSide() || player.isCreative() || player.isSpectator() || !player.isAlive()) {
            return false;
        }
        data.markSoulDestroyed();
        player.hurt(soulDestruction(player.level()), Float.MAX_VALUE);
        if (player.isAlive()) {
            data.clearSoulDestroyed();
            return false;
        }
        return true;
    }

    // ---- Sensing Hollows ----

    /**
     * A Garganta opened here. Every Quincy in range whose Denreishinki did not already beep feels it:
     * one who can use their power knows which way and roughly how far, and latent blood only runs cold.
     */
    public static void senseHollows(ServerLevel level, Vec3 at) {
        double range = BleachConfig.get(BleachConfig.QUINCY_SENSE_RANGE);
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || !SoulLogic.hasSoul(player)
                    || player.distanceToSqr(at) > range * range || GargantaEvents.pagerReaches(player, at, now)) {
                continue;
            }
            SoulData data = SoulLogic.get(player);
            if (QuincyLogic.canUsePowers(data)) {
                double dx = at.x - player.getX();
                double dz = at.z - player.getZ();
                player.displayClientMessage(Component.translatable("message.bleach.quincy.senses_hollow",
                        Component.translatable(ShinigamiLogic.compassKey(dx, dz)),
                        ShinigamiLogic.roundedDistance(Math.sqrt(dx * dx + dz * dz), QuincyLogic.SENSE_DISTANCE_STEP)),
                        true);
            } else if (QuincyLogic.isLatent(data)) {
                player.displayClientMessage(Component.translatable("message.bleach.quincy.chill")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
            }
        }
    }

    // ---- Blut ----

    /**
     * A Quincy key was pressed. The Quincy Form key, sneaking or not, is the arts' to answer (see
     * {@link QuincyArtsEvents#onFormKey}). For the Blut key, presses too close together are ignored.
     * Sneaking, or the key's sneaking variant, lets Blut go; otherwise the key steps to the next Blut
     * (see {@link QuincyLogic#nextBlut}). A sealed power has no Blut to call on.
     */
    public static void onKey(ServerPlayer player, QuincyKey key) {
        long now = player.level().getGameTime();
        if (key == QuincyKey.FORM || key == QuincyKey.FORM_SNEAK) {
            // With its own debounce, apart from Blut's.
            QuincyArtsEvents.onFormKey(player, key == QuincyKey.FORM_SNEAK, now);
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (now - data.lastBlutPressAt < QuincyLogic.BLUT_PRESS_DEBOUNCE_TICKS) {
            return;
        }
        data.lastBlutPressAt = now;
        if (QuincyArtsLogic.isSealed(data)) {
            tell(player, Component.translatable("message.bleach.letzt_stil.sealed"));
            return;
        }
        if (!QuincyLogic.canUsePowers(data)) {
            tell(player, Component.translatable("message.bleach.blut.untrained"));
            return;
        }
        BlutMode current = data.quincy().blut();
        BlutMode next = QuincyLogic.nextBlut(data, current, key == QuincyKey.BLUT_OFF || player.isShiftKeyDown());
        if (next != current) {
            setBlut(player, data, next, false);
        }
    }

    /**
     * Calls on a Blut, or lets it go. Calling on one needs the blood settled from the last change,
     * the poison below half, the Blut unlocked and the reishi to start it, unless forced (by command).
     * Letting go always works.
     *
     * @return whether the Blut changed
     */
    public static boolean setBlut(Player player, SoulData data, BlutMode mode, boolean force) {
        BlutMode current = data.quincy().blut();
        if (mode == current) {
            return false;
        }
        long now = player.level().getGameTime();
        if (mode != BlutMode.OFF && !force) {
            if (now < data.blutSettlesAt) {
                tell(player, Component.translatable("message.bleach.blut.settling"));
                return false;
            }
            if (data.quincy().poison() >= QuincyLogic.POISON_BLOCK) {
                tell(player, Component.translatable("message.bleach.blut.poisoned"));
                return false;
            }
            if (!QuincyLogic.blutUnlocked(data, mode)) {
                tell(player, Component.translatable("message.bleach.blut.locked", mode.displayName()));
                return false;
            }
            if (!QuincyLogic.trySpendReishi(data, QuincyLogic.blutActivationCost(mode))) {
                tell(player, Component.translatable("message.bleach.blut.too_weak", mode.displayName()));
                return false;
            }
        }
        data.setQuincy(data.quincy().withBlut(mode));
        data.blutSettlesAt = now + QuincyLogic.BLUT_SWITCH_TICKS;
        SpiritSenses.play(player, ModSounds.BLUT.get(), SoundSource.PLAYERS, 0.8F, mode == BlutMode.OFF ? 0.8F : 1.0F, false);
        MutableComponent shown = mode == BlutMode.OFF ? Component.translatable("message.bleach.blut.off")
                : Component.translatable("message.bleach.blut.on", mode.displayName());
        player.displayClientMessage(shown.withStyle(mode == BlutMode.ARTERIE ? ChatFormatting.RED
                : mode == BlutMode.VENE ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
        if (player instanceof ServerPlayer serverPlayer && mode != BlutMode.OFF) {
            Milestones.grant(serverPlayer, mode == BlutMode.VENE ? Milestones.BLUT_VENE : Milestones.BLUT_ARTERIE);
        }
        return true;
    }

    /** Blut lets go without a sound, with the reason on the action bar if there is one. */
    private static void endBlut(Player player, SoulData data, @Nullable String reasonKey) {
        data.setQuincy(data.quincy().withBlut(BlutMode.OFF));
        if (reasonKey != null) {
            tell(player, Component.translatable(reasonKey));
        }
    }

    // ---- Damage ----

    /**
     * Blut Arterie: every hit the Quincy causes lands harder. Not on the Quincy themself, and not the
     * Thorns of their own armour, which is no blow of theirs.
     */
    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        DamageSource source = event.getSource();
        if (event.getEntity().level().isClientSide() || event.getAmount() <= 0.0F
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypes.THORNS)
                || !(source.getEntity() instanceof Player attacker) || attacker == event.getEntity()
                || !SoulLogic.hasSoul(attacker)) {
            return;
        }
        SoulData data = SoulLogic.get(attacker);
        if (QuincyLogic.activeBlut(data) == BlutMode.ARTERIE) {
            event.setAmount(event.getAmount() * (1.0F + QuincyLogic.arterieBonus(data)));
        }
    }

    /**
     * Blut Vene: hardened veins stop part of each hit, paid for in reishi. With too little reishi it
     * stops what the reishi left pays for, and lets go. What passes through invulnerability or
     * effects passes through Vene too.
     */
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        DamageSource source = event.getSource();
        float damage = event.getNewDamage();
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide() || damage <= 0.0F
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.BYPASSES_EFFECTS)
                || !SoulLogic.hasSoul(player)) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        if (QuincyLogic.activeBlut(data) != BlutMode.VENE) {
            return;
        }
        float prevented = damage * QuincyLogic.veneReduction(data);
        if (prevented <= 0.0F) {
            return;
        }
        float cost = prevented * QuincyLogic.VENE_COST_PER_DAMAGE;
        float reishi = QuincyLogic.reishi(data);
        if (reishi < cost) {
            prevented = reishi / QuincyLogic.VENE_COST_PER_DAMAGE;
            QuincyLogic.setReishi(data, 0.0F);
            endBlut(player, data, "message.bleach.blut.dry");
        } else {
            QuincyLogic.setReishi(data, reishi - cost);
        }
        event.setNewDamage(Math.max(0.0F, damage - prevented));
    }

    /**
     * Fires only for hits that got past shields and invulnerability frames: a Hollow's blow, or a Visored's Cero,
     * poisons a Quincy ({@link #poisons}). Being devoured does not: the blow that swallows a soul would only pour
     * poison into the dying.
     */
    private static void onDamageTaken(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0.0F || !(event.getEntity() instanceof Player player) || player.level().isClientSide()
                || !SoulLogic.hasSoul(player)) {
            return;
        }
        Entity cause = event.getSource().getEntity();
        if (cause == player || !poisons(event.getSource()) || DevourLogic.isDevoured(event.getSource())) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        boolean onHit = cause instanceof Hollow hollow && hollow.kind().onHit != null;
        addPoison(player, data, QuincyLogic.poisonFrom(data, event.getNewDamage(), onHit, player.level().getDifficulty()));
    }

    /**
     * Whether this hit carries Hollow reiatsu into a Quincy: a blow, bite or beam from a Hollow source
     * ({@link QuincyLogic#isHollowSource}), or a Cero whose shooter is a Visored, which only a masked one can fire. A
     * masked Visored's melee and aura are a Shinigami's, and do not poison.
     */
    private static boolean poisons(DamageSource source) {
        Entity cause = source.getEntity();
        return QuincyLogic.isHollowSource(cause) || source.is(CeroBeam.DAMAGE) && cause instanceof Player shooter
                && SoulLogic.hasSoul(shooter) && VisoredLogic.isVisored(SoulLogic.get(shooter));
    }

    // ---- Telling the player ----

    /** A grey line on the action bar. */
    private static void tell(Player player, MutableComponent message) {
        player.displayClientMessage(message.withStyle(ChatFormatting.GRAY), true);
    }

    /**
     * A sound of the Cross in the holder's hand: heard by those who see spirits, and by the holder
     * even if they cannot yet.
     */
    private static void playToHolder(Player player, SoundEvent sound, float volume, float pitch) {
        SpiritSenses.play(player, sound, SoundSource.PLAYERS, volume, pitch, false);
        if (!SoulLogic.seesSpirits(player)) {
            player.playNotifySound(sound, SoundSource.PLAYERS, volume, pitch);
        }
    }
}
