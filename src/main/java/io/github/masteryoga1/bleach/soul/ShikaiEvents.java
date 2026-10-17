package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.BleachMod;
import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.GetsugaTensho;
import io.github.masteryoga1.bleach.entity.ZabimaruLash;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.registry.ModComponents;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoKey;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import java.util.Locale;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/**
 * Shikai on the server: calling the zanpakutō's name to release it, sealing it, what a release
 * drains while it lasts, and the released blade's technique, Zangetsu's Getsuga Tenshō or
 * Zabimaru's lash. The keys reach here through {@link #onKey}; everything else takes a
 * {@link Player}, so game tests can use mock players.
 */
public final class ShikaiEvents {
    /** The reach Zabimaru gives up while its segments draw back together after a lash. */
    public static final ResourceLocation RETRACT_ID = BleachMod.id("zabimaru_retracted");
    /** Ticks after a release or a seal before the blade will change again. */
    public static final int SETTLE_TICKS = 40;

    /** Ticks a released blade can be out of its owner's hand before it seals. */
    private static final int AWAY_TICKS = 20;
    private static final int RELEASE_XP = 3;
    private static final long RELEASE_XP_COOLDOWN_TICKS = 1200L;
    private static final int RELEASE_PARTICLES = 24;
    private static final float RELEASE_PARTICLE_SIZE = 1.2F;
    private static final double SPEECH_RANGE = 24.0;
    /** A lash only comes out for a blow struck farther away than an ordinary blade could reach. */
    private static final double LASH_DISTANCE = 3.5;
    private static final float SHOUT_CHARGE = 0.75F;
    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 50;
    private static final int TITLE_FADE_OUT = 20;

    /** What came of asking to release or seal. Every one but {@link #RELEASED} is told to the player. */
    public enum Result {
        RELEASED, SEALED, NO_BLADE, NAMELESS, NOT_YOURS, LENT, SILENT, SETTLING, CONSTANT, ALREADY,
        TOO_WEAK, NOT_RELEASED, CANNOT_SEAL;

        public String messageKey() {
            return "message.bleach.shikai." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** What came of starting to gather a Getsuga Tenshō. Every one but {@link #GATHERING} is told to the player. */
    public enum GatherResult {
        GATHERING, NO_BLADE, NOT_YOURS, NO_TECHNIQUE, NOT_RELEASED, LENT, SILENT, COOLDOWN, TOO_WEAK, ALREADY_GATHERING;

        public String messageKey() {
            return "message.bleach.getsuga." + name().toLowerCase(Locale.ROOT);
        }
    }

    private ShikaiEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(ShikaiEvents::onPlayerTick);
        bus.addListener(ShikaiEvents::onDamaged);
        bus.addListener(ShikaiEvents::onDeath);
        bus.addListener(ShikaiEvents::onLoggedOut);
    }

    /** A zanpakutō key pressed on the client. Refusals are shown above the hotbar. */
    public static void onKey(ServerPlayer player, ZanpakutoKey key) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        switch (key) {
            case RELEASE -> {
                Result result = release(player);
                if (result != Result.RELEASED) {
                    tell(player, result.messageKey());
                }
            }
            case SEAL -> {
                Result result = seal(player);
                if (result != Result.SEALED) {
                    tell(player, result.messageKey());
                }
            }
            case TECHNIQUE_PRESS -> {
                GatherResult result = gather(player);
                if (result != GatherResult.GATHERING) {
                    tell(player, result.messageKey());
                }
            }
            case TECHNIQUE_RELEASE -> fire(player);
            case TECHNIQUE_CANCEL -> cancelGather(player, SoulLogic.get(player));
            default -> {
            }
        }
    }

    // ---- Release and seal ----

    /**
     * Calls out the held zanpakutō's name. The first check that fails wins; on success the soul pays
     * the release cost and the blade takes its Shikai form. A constant release, like Zangetsu's, is
     * for good: it never drains and never seals.
     */
    public static Result release(Player player) {
        SoulData data = SoulLogic.get(player);
        ItemStack stack = player.getMainHandItem();
        long now = player.level().getGameTime();
        if (!(stack.getItem() instanceof ZanpakutoItem)) {
            return Result.NO_BLADE;
        }
        ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
        ZanpakutoBond bond = data.zanpakuto();
        if (!blade.isBound() || !bond.named()) {
            return Result.NAMELESS;
        }
        if (!blade.isOwnedBy(player.getUUID())) {
            return Result.NOT_YOURS;
        }
        if (ShinigamiLogic.isPowerLent(data, now)) {
            return Result.LENT;
        }
        if (!ZanpakutoLogic.canWield(data)) {
            return Result.SILENT;
        }
        if (player.tickCount - data.lastShikaiChangeTick < SETTLE_TICKS) {
            return Result.SETTLING;
        }
        if (blade.constant() && blade.released()) {
            return Result.CONSTANT;
        }
        if (blade.released()) {
            return Result.ALREADY;
        }
        Optional<ZanpakutoSpirit> found = spiritOf(player, data, blade);
        if (found.isEmpty()) {
            return Result.SILENT;
        }
        ZanpakutoSpirit spirit = found.get();
        float cost = ZanpakutoLogic.releaseCost(data, spirit);
        if (data.reiryoku() - cost < ZanpakutoLogic.RESERVE) {
            return Result.TOO_WEAK;
        }

        SoulLogic.trySpend(player, data, cost);
        boolean constant = spirit.isConstant();
        ZanpakutoItem.setReleased(stack, spirit, true);
        ZanpakutoBlade marked = ZanpakutoItem.blade(stack);
        if (constant && !marked.constant()) {
            // A constant release is marked as one for good, even on a blade bound before its spirit changed kind.
            stack.set(ModComponents.ZANPAKUTO_BLADE.get(), new ZanpakutoBlade(marked.owner(), marked.ownerName(),
                    marked.spirit(), true, true, marked.colour()));
        }
        if (!constant) {
            data.shikaiActive = true;
            data.shikaiAwayTicks = 0;
        }
        data.lastShikaiChangeTick = player.tickCount;
        Component name = nameOf(bond, spirit);
        if (!bond.firstReleaseDone()) {
            data.setZanpakuto(data.zanpakuto().withFirstReleaseDone(true));
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT));
                serverPlayer.connection.send(new ClientboundSetSubtitleTextPacket(
                        Component.translatable("message.bleach.shikai.first.subtitle")));
                serverPlayer.connection.send(new ClientboundSetTitleTextPacket(name));
                Milestones.grant(serverPlayer, Milestones.SHIKAI);
            }
        }
        if (!constant && spirit.command().isPresent()) {
            speak(player, Component.translatable("speech.bleach.release", spirit.command().get(), name));
        }
        if (player.level() instanceof ServerLevel level) {
            SpiritSenses.particles(level, dust(blade.colour(), RELEASE_PARTICLE_SIZE), player.getX(), player.getY(0.5),
                    player.getZ(), RELEASE_PARTICLES, 0.5, 0.8, 0.5, 0.05);
        }
        SpiritSenses.play(player, ModSounds.ZANPAKUTO_RELEASE.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        if (now - data.lastShikaiXpAt >= RELEASE_XP_COOLDOWN_TICKS) {
            data.lastShikaiXpAt = now;
            SoulLogic.addStatXp(player, data, Stat.REIATSU_CONTROL, RELEASE_XP);
        }
        player.displayClientMessage(Component.translatable("message.bleach.shikai.released", name), true);
        return Result.RELEASED;
    }

    /** Seals the released zanpakutō in hand. A constant release has no sealed form any more. */
    public static Result seal(Player player) {
        SoulData data = SoulLogic.get(player);
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof ZanpakutoItem)) {
            return Result.NO_BLADE;
        }
        ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
        boolean ownReleased = blade.isOwnedBy(player.getUUID()) && blade.released();
        if (ownReleased && blade.constant()) {
            return Result.CANNOT_SEAL;
        }
        if (!ownReleased && !data.shikaiActive) {
            return Result.NOT_RELEASED;
        }
        forceSeal(player, data, "message.bleach.shikai.sealed");
        return Result.SEALED;
    }

    /**
     * Seals every one of the soul's own released blades that can seal, wherever they are carried, and
     * ends what a release brought with it: the drain, a gathering Getsuga Tenshō and Zabimaru's
     * drawn-back reach. The seal is heard, and the message shown, only if something was released.
     */
    public static void forceSeal(Player player, SoulData data, @Nullable String messageKey) {
        boolean sealed = data.shikaiActive;
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
            if (ZanpakutoLogic.isOwnBlade(stack, player) && blade.released() && !blade.constant()) {
                sealStack(stack, player.level());
                sealed = true;
            }
        }
        data.shikaiActive = false;
        data.shikaiAwayTicks = 0;
        cancelGather(player, data);
        removeRetract(player, data);
        if (sealed) {
            data.lastShikaiChangeTick = player.tickCount;
            SpiritSenses.play(player, ModSounds.ZANPAKUTO_SEAL.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
            if (messageKey != null) {
                tell(player, messageKey);
            }
        }
    }

    /** Seals one released blade that can seal: its spirit's sealed form, or the plain Asauchi's if the spirit is gone. */
    public static void sealStack(ItemStack stack, Level level) {
        ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
        if (!blade.released() || blade.constant()) {
            return;
        }
        Optional<ZanpakutoSpirit> spirit = ZanpakutoSpirits.get(level, blade.spirit());
        if (spirit.isPresent()) {
            ZanpakutoItem.setReleased(stack, spirit.get(), false);
        } else {
            ZanpakutoItem.applyForm(stack, ZanpakutoSpirit.Form.ASAUCHI);
            stack.set(ModComponents.ZANPAKUTO_BLADE.get(), blade.withReleased(false));
        }
    }

    // ---- Getsuga Tenshō ----

    /** Starts gathering reiatsu into the released blade for a Getsuga Tenshō. The first check that fails wins. */
    public static GatherResult gather(Player player) {
        SoulData data = SoulLogic.get(player);
        ItemStack stack = player.getMainHandItem();
        long now = player.level().getGameTime();
        if (!(stack.getItem() instanceof ZanpakutoItem)) {
            return GatherResult.NO_BLADE;
        }
        ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
        if (blade.isBound() && !blade.isOwnedBy(player.getUUID())) {
            return GatherResult.NOT_YOURS;
        }
        Optional<ZanpakutoSpirit> spirit = spiritOf(player, data, blade);
        if (!blade.isBound() || (spirit.isPresent() && spirit.get().ability() != ZanpakutoSpirit.Ability.GETSUGA_TENSHO)) {
            return GatherResult.NO_TECHNIQUE;
        }
        if (!blade.released()) {
            return GatherResult.NOT_RELEASED;
        }
        if (ShinigamiLogic.isPowerLent(data, now)) {
            return GatherResult.LENT;
        }
        if (!ZanpakutoLogic.canWield(data) || spirit.isEmpty()) {
            return GatherResult.SILENT;
        }
        if (now - data.lastGetsugaAt < BleachConfig.get(BleachConfig.GETSUGA_COOLDOWN_TICKS)) {
            return GatherResult.COOLDOWN;
        }
        if (ZanpakutoLogic.affordableCharge(data, 0.0F) < 0.0F) {
            return GatherResult.TOO_WEAK;
        }
        if (data.isGathering()) {
            return GatherResult.ALREADY_GATHERING;
        }
        data.gatherStartedAt = now;
        SpiritSenses.play(player, ModSounds.GETSUGA_GATHER.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        return GatherResult.GATHERING;
    }

    /**
     * Swings the gathered reiatsu out as a Getsuga Tenshō, as strong as the gathering and the soul's
     * reiryoku allow. Null, firing nothing, if nothing was gathering or the blade can no longer do it.
     */
    @Nullable
    public static GetsugaTensho fire(Player player) {
        SoulData data = SoulLogic.get(player);
        if (!data.isGathering()) {
            return null;
        }
        long now = player.level().getGameTime();
        long started = data.gatherStartedAt;
        cancelGather(player, data);
        ItemStack stack = player.getMainHandItem();
        if (!mayGetsuga(player, data, stack, now)) {
            return null;
        }
        float charge = Math.min(1.0F, (now - started) / (float) BleachConfig.get(BleachConfig.GETSUGA_FULL_CHARGE_TICKS));
        charge = ZanpakutoLogic.affordableCharge(data, charge);
        if (charge < 0.0F) {
            tell(player, GatherResult.TOO_WEAK.messageKey());
            return null;
        }
        // Never below the reserve: an affordable charge only misses it by rounding.
        data.setReiryoku(Math.max(ZanpakutoLogic.RESERVE, data.reiryoku() - ZanpakutoLogic.getsugaCost(data, charge)));
        player.swing(InteractionHand.MAIN_HAND, true);
        Vec3 look = player.getLookAngle();
        Vec3 from = player.getEyePosition().add(look).subtract(0.0, 0.4, 0.0);
        GetsugaTensho crescent = GetsugaTensho.launch(player.level(), player, from, look, charge,
                ZanpakutoLogic.getsugaDamage(data, charge),
                GetsugaTensho.lifeTicksFor(BleachConfig.get(BleachConfig.GETSUGA_RANGE)),
                stack.copy(), ZanpakutoItem.blade(stack).colour());
        SpiritSenses.play(player, ModSounds.GETSUGA_FIRE.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        data.lastGetsugaAt = now;
        noteGetsugaFired(player, data, charge);
        return crescent;
    }

    /** Lets the gathered reiatsu go without a swing. */
    public static void cancelGather(Player player, SoulData data) {
        data.gatherStartedAt = ZanpakutoBond.NEVER;
    }

    /**
     * Counts a Getsuga Tenshō. After enough of them Zangetsu names the technique, and from then on a
     * strong swing is shouted.
     *
     * @return true if it was named just now
     */
    public static boolean noteGetsugaFired(Player player, SoulData data, float charge) {
        ZanpakutoBond bond = data.zanpakuto();
        boolean wasNamed = bond.getsugaNamed();
        int fired = bond.getsugaFired() == Integer.MAX_VALUE ? Integer.MAX_VALUE : bond.getsugaFired() + 1;
        bond = bond.withGetsugaFired(fired);
        boolean namedNow = !wasNamed && fired >= BleachConfig.get(BleachConfig.GETSUGA_NAMED_AFTER);
        if (namedNow) {
            bond = bond.withGetsugaNamed(true);
        }
        data.setZanpakuto(bond);
        if (namedNow) {
            player.sendSystemMessage(Component.translatable("message.bleach.getsuga.named")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            if (player instanceof ServerPlayer serverPlayer) {
                Milestones.grant(serverPlayer, Milestones.GETSUGA_TENSHO);
            }
        } else if (wasNamed && charge >= SHOUT_CHARGE) {
            speak(player, Component.translatable("speech.bleach.getsuga"));
        }
        return namedNow;
    }

    /** Whether the held blade can still loose a Getsuga Tenshō: the soul's own, released, with the technique, power in hand. */
    private static boolean mayGetsuga(Player player, SoulData data, ItemStack stack, long now) {
        if (!ZanpakutoLogic.isOwnBlade(stack, player) || ShinigamiLogic.isPowerLent(data, now)
                || !ZanpakutoLogic.canWield(data) || data.jinzen != null) {
            return false;
        }
        ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
        return blade.released() && spiritOf(player, data, blade)
                .filter(spirit -> spirit.ability() == ZanpakutoSpirit.Ability.GETSUGA_TENSHO).isPresent();
    }

    // ---- Zabimaru's lash ----

    /**
     * A blow from beyond arm's length with Zabimaru released: the blade lashes out in segments to the
     * target, then draws back, shortening the wielder's reach for a moment.
     *
     * @return true if it lashed
     */
    public static boolean lash(Player player, LivingEntity target, ItemStack blade) {
        if (!ZanpakutoLogic.isOwnBlade(blade, player)) {
            return false;
        }
        SoulData data = SoulLogic.get(player);
        ZanpakutoBlade held = ZanpakutoItem.blade(blade);
        Optional<ZanpakutoSpirit> spirit = spiritOf(player, data, held);
        if (!held.released() || spirit.isEmpty() || spirit.get().ability() != ZanpakutoSpirit.Ability.SEGMENT_LASH) {
            return false;
        }
        if (Math.sqrt(target.getBoundingBox().distanceToSqr(player.getEyePosition())) <= LASH_DISTANCE) {
            return false;
        }
        ZabimaruLash.spawn(player.level(), player, target.getBoundingBox().getCenter());
        SpiritSenses.play(player, ModSounds.ZABIMARU_LASH.get(), SoundSource.PLAYERS, 1.0F, 1.0F, false);
        AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reach != null) {
            reach.addOrUpdateTransientModifier(new AttributeModifier(RETRACT_ID, -spirit.get().shikai().reach(),
                    AttributeModifier.Operation.ADD_VALUE));
        }
        data.lashRetractUntil = player.level().getGameTime() + BleachConfig.get(BleachConfig.LASH_RETRACT_TICKS);
        return true;
    }

    private static void removeRetract(Player player, SoulData data) {
        data.lashRetractUntil = ZanpakutoBond.NEVER;
        AttributeInstance reach = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (reach != null) {
            reach.removeModifier(RETRACT_ID);
        }
    }

    // ---- Speech ----

    /** Says something aloud in the speaker's name, heard by the speaker and every soul nearby that sees spirits. */
    public static void speak(Player speaker, Component text) {
        Component line = Component.translatable("chat.type.text", speaker.getDisplayName(), text);
        speaker.sendSystemMessage(line);
        if (speaker.level() instanceof ServerLevel level) {
            for (ServerPlayer listener : SpiritSenses.awareNear(level, speaker.position(), SPEECH_RANGE)) {
                if (listener != speaker) {
                    listener.sendSystemMessage(line);
                }
            }
        }
    }

    // ---- Upkeep ----

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) {
            return;
        }
        SoulData data = SoulLogic.get(player);
        long now = player.level().getGameTime();
        if (data.lashRetractUntil != ZanpakutoBond.NEVER && now >= data.lashRetractUntil) {
            removeRetract(player, data);
        }
        if (data.shikaiActive) {
            upkeep(player, data, now);
        }
        if (data.isGathering()) {
            if (!mayGetsuga(player, data, player.getMainHandItem(), now)) {
                cancelGather(player, data);
            } else if (now - data.gatherStartedAt >= BleachConfig.get(BleachConfig.GETSUGA_GATHER_FADE_TICKS)) {
                cancelGather(player, data);
                tell(player, "message.bleach.getsuga.faded");
            }
        }
    }

    /** One tick of a release that can seal: it seals when it must, and otherwise drains reiryoku. */
    private static void upkeep(Player player, SoulData data, long now) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()) {
            forceSeal(player, data, null);
            return;
        }
        if (ShinigamiLogic.isPowerLent(data, now) || !ZanpakutoLogic.canWield(data)) {
            forceSeal(player, data, "message.bleach.shikai.sealed");
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!ZanpakutoLogic.isOwnBlade(held, player) || !ZanpakutoItem.blade(held).released()) {
            if (++data.shikaiAwayTicks >= AWAY_TICKS) {
                forceSeal(player, data, "message.bleach.shikai.away");
                return;
            }
        } else {
            data.shikaiAwayTicks = 0;
        }
        Optional<ZanpakutoSpirit> spirit = ZanpakutoSpirits.get(player.level(), data.zanpakuto().spirit());
        float drain = spirit.map(found -> ZanpakutoLogic.shikaiDrainPerTick(data, found)).orElse(0.0F);
        if (drain > 0.0F && !SoulLogic.drain(player, data, drain)) {
            forceSeal(player, data, "message.bleach.shikai.exhausted");
        }
    }

    /** Zabimaru's lash, on a melee blow that landed. */
    private static void onDamaged(LivingDamageEvent.Post event) {
        DamageSource source = event.getSource();
        if (source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() instanceof Player player
                && source.getDirectEntity() == player && event.getEntity() != player && !player.level().isClientSide()) {
            lash(player, event.getEntity(), player.getMainHandItem());
        }
    }

    /** A soul that dies seals first, so its blades drop or are kept sealed. */
    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide()) {
            forceSeal(player, SoulLogic.get(player), null);
        }
    }

    /** A release does not outlast the session; the blades are saved sealed. */
    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        forceSeal(event.getEntity(), SoulLogic.get(event.getEntity()), null);
    }

    // ---- Helpers ----

    /**
     * The spirit in the soul's own blade: the named bond's spirit, as long as the blade holds that
     * one. Empty for a nameless blade or soul, a blade still holding a spirit the soul no longer has,
     * or a spirit missing from the registry.
     */
    private static Optional<ZanpakutoSpirit> spiritOf(Player player, SoulData data, ZanpakutoBlade blade) {
        Optional<ResourceLocation> id = data.zanpakuto().spirit();
        if (!data.zanpakuto().named() || id.isEmpty() || !blade.spirit().equals(id)) {
            return Optional.empty();
        }
        return ZanpakutoSpirits.get(player.level(), id);
    }

    private static Component nameOf(ZanpakutoBond bond, ZanpakutoSpirit spirit) {
        return bond.named() && !bond.name().getString().isEmpty() ? bond.name()
                : spirit.name().copy().withColor(spirit.colour());
    }

    private static DustParticleOptions dust(int colour, float size) {
        return new DustParticleOptions(new Vector3f(((colour >> 16) & 0xFF) / 255.0F, ((colour >> 8) & 0xFF) / 255.0F,
                (colour & 0xFF) / 255.0F), size);
    }

    private static void tell(Player player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }
}
