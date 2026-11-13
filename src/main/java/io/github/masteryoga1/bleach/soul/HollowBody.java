package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.registry.BleachTags;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

/**
 * What being a Hollow does to a player's body: claws on a bare hand, a hide that gives armour and bears none,
 * a leap that falls further unharmed, sight in the dark, and soul hunger in place of food, which drains with
 * time and effort, pays for a fast regeneration, and starves the Hollow when it runs out. A Hollow that stops
 * being one gets its body and its food back. An Arrancar's Resurrección puts the claws and leap of the form
 * it rose from back on, but none of the rest (its {@link Hierro} stands in for the hide). The numbers are in
 * {@link HollowPlayerLogic}; a Menos form's size and the rest of its body are in {@link MenosBody}, and its
 * rise and fall in {@link MenosEvolution}; the Arrancar's own tick is in {@link ArrancarEvents}. Server side
 * unless said otherwise; everything takes a {@link Player}, so game tests can use mock players, and only
 * messages and sounds need a real one.
 *
 * <p>The hunger spent is gathered as a debt and taken off the saved value now and then (see
 * {@link #flushDebt}), so the soul is not re-sent to its owner every tick.
 */
public final class HollowBody {
    public static final ResourceLocation CLAWS_ID = BleachMod.id("hollow_claws");
    public static final ResourceLocation CLAW_SPEED_ID = BleachMod.id("hollow_claw_speed");
    public static final ResourceLocation HIDE_ID = BleachMod.id("hollow_hide");
    public static final ResourceLocation LEAP_ID = BleachMod.id("hollow_leap");
    /** Starving for souls: no armour, shield or effect softens it, and it scales with no difficulty. */
    public static final ResourceKey<DamageType> SOUL_HUNGER =
            ResourceKey.create(Registries.DAMAGE_TYPE, BleachMod.id("soul_hunger"));

    /** How long the Hollow sight lasts, and when it is topped up: well before vanilla starts to flicker it at 200. */
    public static final int NIGHT_VISION_TICKS = 400;
    public static final int NIGHT_VISION_REFRESH_BELOW = 300;
    /** Ticks between looks for armour the hide has not yet shed, and between two messages about it. */
    public static final int SHED_INTERVAL_TICKS = 20;
    public static final int SHED_MESSAGE_INTERVAL_TICKS = 200;
    /** Ticks between two heartbeats of hunger, between regenerations, and between flushes of the hunger spent. */
    public static final int PANG_INTERVAL_TICKS = 600;
    public static final int REGEN_INTERVAL_TICKS = 10;
    public static final int FLUSH_INTERVAL_TICKS = 600;
    /** Ticks between two refusal-type messages (see {@link #actionBar}). */
    public static final int MESSAGE_INTERVAL_TICKS = 20;
    /**
     * Ticks between two looks at whether a soul holds a place among the server's Vasto Lorde, and between two
     * checks of an Arrancar's body and blades (see {@link ArrancarEvents#reconcile}).
     */
    private static final int VASTO_NOTE_INTERVAL_TICKS = 20;

    private static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    /** The vanilla food bar while the hunger holds: full, so food cannot even be started. */
    private static final int FULL_FOOD_LEVEL = 20;
    /** What a fresh body's food is, given back on leaving the Hollow state. */
    private static final float FRESH_SATURATION = 5.0F;
    /** The pending hunger is taken off the saved value once it reaches this much. */
    private static final float FLUSH_AT = 1.0F;
    private static final long SCENT_DISTANCE_STEP = 10L;

    private HollowBody() {
    }

    // ---- Soul hunger ----

    /** Starts the hunger of a Hollow that has none yet: on its first tick, after a command reset, or from an old save. */
    public static void ensureHunger(SoulData data) {
        if (!data.hollow().hungerStarted()) {
            data.setHollow(data.hollow().withSatiety((float) BleachConfig.get(BleachConfig.SATIETY_START)));
        }
    }

    /** The soul hunger left, the debt not yet taken off included, or {@link HollowProgress#UNSET} before it starts. */
    public static float satiety(SoulData data) {
        HollowProgress hollow = data.hollow();
        return hollow.hungerStarted() ? Math.max(0.0F, hollow.satiety() - data.hollowSatietyDebt) : HollowProgress.UNSET;
    }

    /** Spends soul hunger, to be taken off the saved value later. */
    public static void addDebt(SoulData data, float amount) {
        if (amount > 0.0F) {
            data.hollowSatietyDebt += amount;
        }
    }

    /** Takes the hunger spent off the saved value, which marks the soul to be sent to its owner. */
    public static void flushDebt(SoulData data) {
        HollowProgress hollow = data.hollow();
        if (data.hollowSatietyDebt > 0.0F && hollow.hungerStarted()) {
            data.setHollow(hollow.withSatiety(Math.max(0.0F, hollow.satiety() - data.hollowSatietyDebt)));
        }
        data.hollowSatietyDebt = 0.0F;
    }

    /** A meal, or Peaceful's refill: the debt is settled first, and the hunger never goes past full. */
    public static void addSatiety(SoulData data, float amount) {
        flushDebt(data);
        ensureHunger(data);
        if (amount > 0.0F) {
            HollowProgress hollow = data.hollow();
            data.setHollow(hollow.withSatiety(Math.min(HollowProgress.MAX_SATIETY, hollow.satiety() + amount)));
        }
    }

    /** Sets the hunger outright, from 0 to full, forgetting any debt (the command). */
    public static void setSatiety(SoulData data, float value) {
        data.hollowSatietyDebt = 0.0F;
        data.setHollow(data.hollow().withSatiety(Math.max(0.0F, Math.min(HollowProgress.MAX_SATIETY, value))));
    }

    /**
     * The FoodDataMixin hook, in place of vanilla's food tick: false, changing nothing, for anything
     * but a Hollow player on the server. A Hollow's exhaustion (sprinting, jumping, fighting, being hurt,
     * the Hunger effect) is spent as soul hunger instead, under survival's rules, off Peaceful and while
     * the game runs; the food bar is held full, or at the sprint limit while starving, with no saturation,
     * so vanilla never heals, starves or feeds it.
     */
    public static boolean feedsOnSouls(Player player, FoodData food) {
        if (player.level().isClientSide() || !HollowPlayerLogic.isHollowPlayer(player)) {
            return false;
        }
        SoulData data = SoulLogic.get(player);
        ensureHunger(data);
        boolean survival = HollowPlayerLogic.survivalRules(player);
        if (survival && player.level().getDifficulty() != Difficulty.PEACEFUL
                && player.level().tickRateManager().runsNormally()) {
            addDebt(data, (float) (HollowPlayerLogic.satietyFromExhaustion(food.getExhaustionLevel())
                    * HollowPlayerLogic.hungerShare(player.level().dimension())));
        }
        food.setExhaustion(0.0F);
        boolean starving = survival && HollowPlayerLogic.band(satiety(data)) == HollowPlayerLogic.Band.STARVING;
        food.setFoodLevel(starving ? HollowPlayerLogic.STARVING_FOOD_LEVEL : FULL_FOOD_LEVEL);
        food.setSaturation(0.0F);
        return true;
    }

    // ---- The body ----

    /**
     * A Hollow player's tick, from HollowPlayerEvents: the hunger starts, a Menos grows, shrinks or
     * squeezes, the body is put right, the road as a Menos is kept (and an unfed Adjuchas's clock runs),
     * a Cero gathers and a tear at the mask goes on (see {@link ArrancarEvents#tickTear}), which ends the
     * tick if the mask came away; then, alive, under survival's rules and while the game runs, the armour is
     * shed, the hunger drains (or refills on Peaceful), the bands are told, the body regenerates or starves,
     * and a hungry Hollow smells for souls. An Arrancar has its own tick instead (see
     * {@link ArrancarEvents#tick}). Any other soul gets its body back, any Cero it was gathering fizzles (but a
     * masked Visored's, which goes on) and any tear is let go of; a Gran Rey Cero fizzles on any soul but an
     * Arrancar. Every soul's place among the Vasto Lorde, and its Arrancar body and blades, are kept right every
     * second.
     */
    public static void tickBody(Player player, SoulData data, long now) {
        if (player.tickCount % VASTO_NOTE_INTERVAL_TICKS == 0) {
            MenosEvolution.noteVasto(player, data);
            ArrancarEvents.reconcile(player, data, now);
        }
        if (ArrancarLogic.isArrancar(data)) {
            ArrancarEvents.tick(player, data, now);
            return;
        }
        // Only an Arrancar gathers a Gran Rey Cero: one left on a soul that is no longer one (a Hollow again by
        // command, too) fizzles.
        if (GranReyCero.charging(data)) {
            GranReyCero.cancel(player, data);
        }
        if (!HollowPlayerLogic.isHollow(data)) {
            // A command, a purification or any other road out of the Hollow state lets go of the mask.
            ArrancarEvents.clearTear(data);
            if (HollowCero.charging(data)) {
                // Settled as a Hollow's is: a masked Visored's keeps gathering, any other soul's fizzles (HollowCero#tick).
                HollowCero.tick(player, data, now);
            }
            leaveBodyIfWorn(player, data);
            return;
        }
        ensureHunger(data);
        // The size first, so the reach, steadiness and fall below follow the body as it now is.
        MenosBody.tick(player, data, now);
        // The one place claws grow: vanilla has just taken off the modifiers of whatever left the hand.
        reconcile(player, data, true);
        MenosEvolution.tick(player, data, now);
        HollowCero.tick(player, data, now);
        if (ArrancarEvents.tickTear(player, data, now)) {
            return;
        }
        boolean alive = player.isAlive();
        if (alive && data.hollow().purifiedOnDeath()) {
            // Marked by a purifying blow whose death did not happen after all.
            data.setHollow(data.hollow().withPurifiedOnDeath(false));
        }
        if (!alive || !player.level().tickRateManager().runsNormally()) {
            return;
        }
        if (!HollowPlayerLogic.survivalRules(player)) {
            data.hollowStarveTicks = 0;
            return;
        }
        if (player.tickCount % SHED_INTERVAL_TICKS == 0) {
            shedArmour(player, data, now);
        }
        boolean peaceful = player.level().getDifficulty() == Difficulty.PEACEFUL;
        if (peaceful) {
            if (player.tickCount % 20 == 0) {
                addSatiety(data, (float) BleachConfig.get(BleachConfig.PEACEFUL_SATIETY_PER_SECOND));
            }
        } else {
            addDebt(data, (float) (HollowPlayerLogic.drainPerTick()
                    * HollowPlayerLogic.hungerShare(player.level().dimension())));
        }
        if (data.hollowSatietyDebt >= FLUSH_AT || player.tickCount % FLUSH_INTERVAL_TICKS == 0) {
            flushDebt(data);
        }
        HollowPlayerLogic.Band band = HollowPlayerLogic.band(satiety(data));
        band(player, data, band, now);
        if (player.tickCount % REGEN_INTERVAL_TICKS == 0) {
            regenerate(player, data, now, REGEN_INTERVAL_TICKS / 20.0F);
        }
        if (band == HollowPlayerLogic.Band.STARVING && !peaceful) {
            if (++data.hollowStarveTicks >= BleachConfig.get(BleachConfig.STARVING_INTERVAL_TICKS)) {
                data.hollowStarveTicks = 0;
                starve(player, data);
            }
        } else {
            data.hollowStarveTicks = 0;
        }
        long scentTicks = BleachConfig.get(BleachConfig.SCENT_INTERVAL_SECONDS) * 20L;
        if (BleachConfig.get(BleachConfig.SCENT_RANGE) > 0 && satiety(data) < BleachConfig.get(BleachConfig.SCENT_BELOW)
                && now - data.lastScentAt >= scentTicks) {
            data.lastScentAt = now;
            scent(player, data);
        }
    }

    /**
     * Puts the body right, and does nothing when it already is: for a Hollow, claws and their speed on a bare
     * main hand only, the hide, the leap, the sight and its Menos form's body (see
     * {@link MenosBody#reconcile}); for an Arrancar in Resurrección, the claws and leap of the form it rose
     * from (see {@link HollowPlayerLogic#bodyForm}), with no hide, as it has Hierro instead; for anyone else,
     * none of them. First, for every soul, an Arrancar's health, speed and damage are put right (see
     * {@link ArrancarEvents#reconcileBody}). The modifiers are transient but for a Menos's and an Arrancar's
     * health, so this runs again on logging in and respawning (see {@link SoulLogic#applyStatModifiers}), and
     * each is written only when its amount changes, so the attributes are not re-sent every tick. Server
     * side.
     * <p>
     * The claws come off a hand that holds something at once, but grow only on the player's tick (see
     * {@link #tickBody}), after vanilla's equipment check. Between ticks, a hand just emptied by a hotbar
     * swap still lends the weapon it held its own damage, and the claws would stack on it.
     */
    public static void reconcile(Player player, SoulData data) {
        reconcile(player, data, false);
    }

    /**
     * {@link #reconcile(Player, SoulData)}, letting the claws grow on a bare hand when {@code growClaws}: only
     * from a body tick, a Hollow's here or a released Arrancar's (see {@link ResurreccionEvents#upkeep}).
     */
    static void reconcile(Player player, SoulData data, boolean growClaws) {
        if (player.level().isClientSide()) {
            return;
        }
        ArrancarEvents.reconcileBody(player, data);
        if (!HollowPlayerLogic.wearsHollowBody(data)) {
            removeBody(player);
            data.hollowBodyApplied = false;
            return;
        }
        // Grown is either claw modifier on: a server may turn the claws' damage off and keep their speed.
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
        boolean grown = damage != null && damage.getModifier(CLAWS_ID) != null
                || speed != null && speed.getModifier(CLAW_SPEED_ID) != null;
        boolean clawed = player.getMainHandItem().isEmpty() && (growClaws || grown);
        setModifier(player, Attributes.ATTACK_DAMAGE, CLAWS_ID, clawed ? HollowPlayerLogic.clawBonus(data) : 0.0,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.ATTACK_SPEED, CLAW_SPEED_ID, clawed ? HollowPlayerLogic.clawSpeedModifier(data) : 0.0,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        setModifier(player, Attributes.ARMOR, HIDE_ID, HollowPlayerLogic.hideArmour(data),
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.SAFE_FALL_DISTANCE, LEAP_ID, BleachConfig.get(BleachConfig.HOLLOW_SAFE_FALL),
                AttributeModifier.Operation.ADD_VALUE);
        if (!HollowPlayerLogic.isHollow(data)) {
            // A released Arrancar: no sight and a person's size, and never marked as wearing the Hollow's body,
            // so sealing it again never gives it a fresh body's food.
            return;
        }
        sight(player, data);
        MenosBody.reconcile(player, data);
        data.hollowBodyApplied = true;
    }

    /**
     * Gives a soul that has stopped being a Hollow its body back, if it ever wore the Hollow's. A soul
     * about to be replaced outright (a reset) needs this first: its replacement never wore the body, so
     * it would have nothing to take off and the food would stay pinned.
     */
    public static void leaveBodyIfWorn(Player player, SoulData data) {
        if (data.hollowBodyApplied || data.hollow().hungerStarted()) {
            leaveBody(player, data);
        }
    }

    /**
     * The soul stopped being a Hollow: the claws, hide, leap, the devourer's slowness, the Hollow sight
     * and a Menos's body come off (it is a person's size at once), the food comes back as a fresh body's,
     * and the hunger is unset.
     */
    public static void leaveBody(Player player, SoulData data) {
        if (!player.level().isClientSide()) {
            removeBody(player);
            FoodData food = player.getFoodData();
            food.setFoodLevel(FULL_FOOD_LEVEL);
            food.setSaturation(FRESH_SATURATION);
            food.setExhaustion(0.0F);
        }
        data.hollowSatietyDebt = 0.0F;
        data.hollowStarveTicks = 0;
        data.lastHollowBand = -1;
        data.setHollow(data.hollow().withSatiety(HollowProgress.UNSET));
        data.hollowBodyApplied = false;
    }

    /**
     * Heals a Hollow for {@code seconds} of its regeneration, if it may now: the game rule allows it, it
     * is hurt and not starving, nothing has hurt it for a while and no blow to its mask holds it back,
     * and it is not woven by Ransōtengai, which lets nothing heal it. The hunger it costs is charged on
     * the health actually gained, so a heal that is cut short costs only what it gave.
     *
     * @return the health gained
     */
    public static float regenerate(Player player, SoulData data, long now, float seconds) {
        if (!HollowPlayerLogic.isHollow(data) || seconds <= 0.0F
                || !player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION) || !player.isHurt()
                || HollowPlayerLogic.band(satiety(data)) == HollowPlayerLogic.Band.STARVING
                || !HollowLogic.mayRegenerate(now, data.hollowHurtAt, data.hollowRegenPausedUntil)
                || QuincyRansotengai.woven(player)) {
            return 0.0F;
        }
        float before = player.getHealth();
        player.heal(HollowPlayerLogic.regenPerSecond(data) * seconds);
        float gained = Math.max(0.0F, player.getHealth() - before);
        addDebt(data, gained * (float) BleachConfig.get(BleachConfig.REGEN_SATIETY_PER_HEALTH));
        return gained;
    }

    /**
     * One starving hurt, if the difficulty allows it at this health (see {@link HollowPlayerLogic#starves}).
     *
     * @return whether it hurt
     */
    public static boolean starve(Player player, SoulData data) {
        if (!HollowPlayerLogic.isHollow(data)
                || !HollowPlayerLogic.starves(player.level().getDifficulty(), player.getHealth())) {
            return false;
        }
        float amount = HollowPlayerLogic.starvingHurt(player.level().getDifficulty(), player.getHealth(),
                (float) BleachConfig.get(BleachConfig.STARVING_DAMAGE));
        return player.hurt(soulHunger(player.level()), amount);
    }

    /**
     * Takes off whatever a Hollow's hide will not bear, into the inventory or, when it is full, onto the
     * ground at its feet. Curse of Binding does not hold it on. The armour slots refuse it in the first
     * place (see LivingEntityMixin); this catches what still gets in, such as armour from an old save or
     * put on by a command. Under survival's rules only, as the armour rule is.
     *
     * @return the pieces shed
     */
    public static int shedArmour(Player player, SoulData data, long now) {
        if (player.level().isClientSide() || !HollowPlayerLogic.isHollow(data) || !HollowPlayerLogic.survivalRules(player)
                || BleachConfig.get(BleachConfig.HOLLOW_WEARS_ARMOR)) {
            return 0;
        }
        int shed = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || stack.is(BleachTags.HOLLOW_WEARABLE)) {
                continue;
            }
            player.setItemSlot(slot, ItemStack.EMPTY);
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
            shed++;
        }
        if (shed > 0 && now - data.lastShedMessageAt >= SHED_MESSAGE_INTERVAL_TICKS) {
            data.lastShedMessageAt = now;
            player.displayClientMessage(Component.translatable("message.bleach.hollow_player.shed")
                    .withStyle(ChatFormatting.GRAY), true);
        }
        return shed;
    }

    /** Starving for souls, with no one to blame: see {@link #SOUL_HUNGER}. */
    public static DamageSource soulHunger(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SOUL_HUNGER));
    }

    /**
     * Whether this is the Hollow sight's own night vision, ambient, hidden and short, rather than a
     * potion's or a beacon's, which are never taken away.
     */
    public static boolean isOwnNightVision(MobEffectInstance effect) {
        return effect.is(MobEffects.NIGHT_VISION) && effect.isAmbient() && !effect.isVisible() && !effect.showIcon()
                && effect.getAmplifier() == 0 && !effect.isInfiniteDuration() && effect.getDuration() <= NIGHT_VISION_TICKS;
    }

    /**
     * A refusal-type message in the action bar (devouring, food, villagers), at most once every
     * {@link #MESSAGE_INTERVAL_TICKS}, so holding a key or a click does not flood it.
     */
    public static void actionBar(Player player, SoulData data, Component message, long now) {
        if (now - data.lastHollowMessageAt < MESSAGE_INTERVAL_TICKS) {
            return;
        }
        data.lastHollowMessageAt = now;
        if (player instanceof ServerPlayer server) {
            server.displayClientMessage(message, true);
        }
    }

    // ---- Hurt and the mask ----

    /** Damage above zero landed on a Hollow: it waits a while before it regenerates. */
    public static void onHurt(SoulData data, long now) {
        data.hollowHurtAt = now;
    }

    /**
     * A blow landed on a Hollow player's mask: it is drawn cracked for a while, and the Hollow does not
     * regenerate until the mask pause is over, as for a Hollow mob. A fresh crack is noted, as it breaks
     * a Cero's gather (see {@link HollowCero}); a blow to a mask already cracked does not.
     *
     * @return whether the mask was whole until now (a fresh crack)
     */
    public static boolean onMaskStruck(SoulData data, long now) {
        boolean fresh = now >= data.hollowCrackedUntil;
        if (fresh) {
            data.hollowFreshCrackAt = now;
        }
        data.hollowCrackedUntil = now + Hollow.CRACK_TICKS;
        data.hollowRegenPausedUntil = Math.max(data.hollowRegenPausedUntil,
                now + BleachConfig.get(BleachConfig.MASK_REGEN_PAUSE_SECONDS) * 20L);
        return fresh;
    }

    // ---- Purification and death ----

    /**
     * The mask is broken and the Hollow a soul again: a Rukongai soul, as one sent on by Konsō, with
     * its Hollow tier and its hunger gone. What it devoured stays on its record, and its reiryoku and
     * stats are its own.
     */
    public static void purify(SoulData data) {
        SoulLogic.changeState(data, SoulState.RUKONGAI);
        data.setTier(PowerPath.HOLLOW, 0);
        data.setReiatsuReleased(false);
        data.setHollow(data.hollow().restarted());
        data.hollowSatietyDebt = 0.0F;
    }

    /** A purifying blow is killing this Hollow: it comes back as a soul, if the server lets it (see {@link #afterDeath}). */
    public static void markPurifiedOnDeath(SoulData victim) {
        if (HollowPlayerLogic.isHollow(victim) && BleachConfig.get(BleachConfig.PURIFICATION_FREES)) {
            victim.setHollow(victim.hollow().withPurifiedOnDeath(true));
        }
    }

    /**
     * What a Hollow's death leaves, from SoulLogic#afterDeath: purified by the blow that killed it, it
     * comes back as a soul; otherwise it stays a Hollow, its hunger at least the respawn floor and a
     * share of its souls lost. What it devoured, its sight and its last meal are kept.
     */
    public static void afterDeath(SoulData old, SoulData next) {
        HollowProgress hollow = old.hollow();
        if (hollow.purifiedOnDeath() && BleachConfig.get(BleachConfig.PURIFICATION_FREES)) {
            purify(next);
            next.hollowCleansed = true;
            return;
        }
        next.setHollow(hollow.withPurifiedOnDeath(false)
                .withSatiety(HollowPlayerLogic.respawnSatiety(hollow))
                .withSouls(hollow.souls() - HollowPlayerLogic.soulsLostOnDeath(hollow.souls())));
    }

    /** Whether this respawned soul was a Hollow that a purifying blow freed. Server side only. */
    public static boolean wasCleansed(SoulData data) {
        return data.hollowCleansed;
    }

    /** Whether this respawned soul was freed by a purifying blow; forgets it either way. */
    public static boolean consumeCleansed(SoulData data) {
        boolean cleansed = data.hollowCleansed;
        data.hollowCleansed = false;
        return cleansed;
    }

    // ---- Helpers ----

    /** Sets a transient modifier to this amount, or takes it off at zero, writing only when it changes. */
    static void setModifier(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount,
                            AttributeModifier.Operation operation) {
        setModifier(player, attribute, id, amount, operation, false);
    }

    /**
     * Sets a modifier to this amount, or takes it off at zero, writing only when it changes: a saved one
     * goes into the player's save with its attributes, a transient one never does.
     */
    static void setModifier(Player player, Holder<Attribute> attribute, ResourceLocation id, double amount,
                            AttributeModifier.Operation operation, boolean saved) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (amount == 0.0 || !Double.isFinite(amount)) {
            instance.removeModifier(id);
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (current != null && current.amount() == amount && current.operation() == operation) {
            return;
        }
        if (saved) {
            // Takes the old one off first, under whatever operation it was filed.
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, amount, operation));
            return;
        }
        if (current != null && current.operation() != operation) {
            // An update would leave the old one filed under its old operation.
            instance.removeModifier(id);
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
    }

    /**
     * Takes off everything the Hollow body puts on: the four modifiers, the devourer's slowness, the
     * sight and every Menos modifier.
     */
    private static void removeBody(Player player) {
        removeModifier(player, Attributes.ATTACK_DAMAGE, CLAWS_ID);
        removeModifier(player, Attributes.ATTACK_SPEED, CLAW_SPEED_ID);
        removeModifier(player, Attributes.ARMOR, HIDE_ID);
        removeModifier(player, Attributes.SAFE_FALL_DISTANCE, LEAP_ID);
        removeModifier(player, Attributes.MOVEMENT_SPEED, DevourLogic.DEVOURING_ID);
        MenosBody.remove(player);
        MobEffectInstance sight = player.getEffect(MobEffects.NIGHT_VISION);
        if (sight != null && isOwnNightVision(sight)) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    private static void removeModifier(Player player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }

    /**
     * The Hollow sight: night vision that is ambient, without particles or an icon, topped up before
     * vanilla would make it flicker, while the server and the soul want it. Taken off when not, but
     * only if it is the sight's own. A stronger or longer night vision is left to run out first.
     */
    private static void sight(Player player, SoulData data) {
        boolean wanted = BleachConfig.get(BleachConfig.HOLLOW_NIGHT_SIGHT) && data.hollow().sight();
        MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
        if (wanted) {
            boolean fading = current != null && current.getAmplifier() == 0 && !current.isInfiniteDuration()
                    && current.getDuration() < NIGHT_VISION_REFRESH_BELOW;
            if (player.isAlive() && (current == null || fading)) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
            }
        } else if (current != null && isOwnNightVision(current)) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    /**
     * Tells a Hollow when it sinks into a hungrier band (rising out of one says nothing), sounds the
     * heartbeat of hunger now and then while it is not fed, and puts the claws right when the band
     * changes, the hunger spent settled first so the claws and the saved hunger agree.
     */
    private static void band(Player player, SoulData data, HollowPlayerLogic.Band band, long now) {
        int last = data.lastHollowBand;
        if (last >= 0 && band.ordinal() > last && player instanceof ServerPlayer server) {
            String key = band == HollowPlayerLogic.Band.STARVING
                    ? "message.bleach.hollow_player.starving" : "message.bleach.hollow_player.ravenous";
            server.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        }
        if (band != HollowPlayerLogic.Band.FED && now - data.lastHungerPangAt >= PANG_INTERVAL_TICKS) {
            data.lastHungerPangAt = now;
            if (player instanceof ServerPlayer server) {
                server.playNotifySound(ModSounds.HOLLOW_PANG.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
            }
        }
        data.lastHollowBand = band.ordinal();
        if (last != band.ordinal()) {
            flushDebt(data);
            reconcile(player, data);
        }
    }

    /**
     * A hungry Hollow smells the nearest soul worth devouring a little way off, too far to see for
     * itself: a Plus that is not passing on, a Hollow, or an aware player who is not a Hollow. It learns
     * which way and roughly how far. A stand-in for Pesquisa.
     */
    private static void scent(Player player, SoulData data) {
        if (!(player instanceof ServerPlayer server)) {
            return;
        }
        double range = BleachConfig.get(BleachConfig.SCENT_RANGE);
        double nearestSqr = range * range;
        double minSqr = HollowPlayerLogic.SCENT_MIN_DISTANCE * HollowPlayerLogic.SCENT_MIN_DISTANCE;
        LivingEntity nearest = null;
        for (LivingEntity soul : server.level().getEntitiesOfClass(LivingEntity.class, server.getBoundingBox().inflate(range),
                entity -> entity != server && smellable(entity))) {
            double distanceSqr = soul.distanceToSqr(server);
            if (distanceSqr >= minSqr && distanceSqr <= nearestSqr) {
                nearestSqr = distanceSqr;
                nearest = soul;
            }
        }
        if (nearest == null) {
            return;
        }
        double dx = nearest.getX() - server.getX();
        double dz = nearest.getZ() - server.getZ();
        server.displayClientMessage(Component.translatable("message.bleach.hollow_player.scent",
                        Component.translatable(ShinigamiLogic.compassKey(dx, dz)),
                        ShinigamiLogic.roundedDistance(Math.sqrt(nearestSqr), SCENT_DISTANCE_STEP))
                .withStyle(ChatFormatting.GRAY), true);
        server.playNotifySound(ModSounds.HOLLOW_SCENT.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Whether a hungry Hollow smells this creature: a soul it could devour. */
    private static boolean smellable(LivingEntity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        if (entity instanceof PlusSpirit plus) {
            return !plus.isDeparting();
        }
        if (entity instanceof Hollow hollow) {
            return !hollow.isPurifying() && !hollow.isDeparting();
        }
        return entity instanceof Player other && HollowPlayerLogic.survivalRules(other) && SoulLogic.hasSoul(other)
                && SoulLogic.isAware(SoulLogic.get(other)) && !HollowPlayerLogic.isHollow(SoulLogic.get(other));
    }
}
