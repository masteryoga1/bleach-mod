package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.entity.Hollow;
import io.github.masteryoga1.bleach.entity.HollowKind;
import io.github.masteryoga1.bleach.entity.PlusSpirit;
import io.github.masteryoga1.bleach.registry.ModEntities;
import io.github.masteryoga1.bleach.registry.ModSounds;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Plus spirits in the world: the souls villagers leave when they die, those that wander near aware
 * souls, what happens when a Hollow devours one or anything else strikes one down, a Plus turning
 * Hollow when its chain runs out, and the soul that rises from a purified Hollow. The Plus itself
 * is {@link PlusSpirit}; Konsō on one is in {@link ShinigamiEvents#konsoPlus}.
 */
public final class PlusEvents {
    /** A villager's Plus stays this close to its home, workplace or the spot it died on. */
    private static final int HOME_RADIUS = 10;
    /** No villager's Plus appears while the cap of Pluses is within this many blocks. */
    private static final double VILLAGER_CAP_RANGE = 16.0;
    private static final double WANDER_MIN_DISTANCE = 16.0;
    private static final double WANDER_MAX_DISTANCE = 40.0;
    private static final int WANDER_TRIES = 8;
    private static final int WANDER_MAX_RISE = 16;
    /** A wandering Plus's chain is somewhere between this share and whole. */
    private static final float WANDER_MIN_CHAIN = 0.3F;
    private static final float BABY_SCALE = 0.5F;
    /** Ticks a Plus that pulled itself back together cannot be hurt again. */
    private static final int REFORM_INVULNERABLE_TICKS = 40;

    private PlusEvents() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(PlusEvents::onEntityTick);
        // High priority, so a Plus that pulls itself back together never reaches the other death handlers.
        bus.addListener(EventPriority.HIGH, PlusEvents::onDeath);
        bus.addListener(PlusEvents::onPlayerTickPre);
    }

    /**
     * A villager who died leaves its Plus on the first tick of its death. One a zombie turned into a
     * zombie villager is gone before that tick, so it leaves none.
     */
    private static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof AbstractVillager villager && villager.deathTime == 1
                && villager.level() instanceof ServerLevel level && BleachConfig.get(BleachConfig.VILLAGER_PLUSES)) {
            spawnVillagerPlus(level, villager);
        }
    }

    /**
     * The Plus a dead villager or wandering trader leaves: it looks as they did, keeps their name and
     * stays near their home or workplace (or where they fell), with its whole chain ahead of it. None
     * while there are already enough Pluses around.
     *
     * @return the Plus, or null if none was left
     */
    @Nullable
    public static PlusSpirit spawnVillagerPlus(ServerLevel level, AbstractVillager villager) {
        double rangeSqr = VILLAGER_CAP_RANGE * VILLAGER_CAP_RANGE;
        int nearby = level.getEntitiesOfClass(PlusSpirit.class, villager.getBoundingBox().inflate(VILLAGER_CAP_RANGE),
                plus -> plus.distanceToSqr(villager) <= rangeSqr).size();
        if (nearby >= BleachConfig.get(BleachConfig.VILLAGER_PLUS_CAP)) {
            return null;
        }
        PlusSpirit plus = ModEntities.PLUS.get().create(level);
        if (plus == null) {
            return null;
        }
        BlockPos diedAt = villager.blockPosition();
        plus.moveTo(villager.getX(), villager.getY(), villager.getZ(), villager.getYRot(), 0.0F);
        plus.setYHeadRot(villager.getYHeadRot());
        plus.setYBodyRot(villager.yBodyRot);
        EventHooks.finalizeMobSpawn(plus, level, level.getCurrentDifficultyAt(diedAt), MobSpawnType.EVENT, null);
        plus.setVillagerData(villager instanceof VillagerDataHolder holder ? holder.getVillagerData()
                : new VillagerData(VillagerType.byBiome(level.getBiome(diedAt)), VillagerProfession.NONE, 1));
        if (villager.hasCustomName()) {
            plus.setCustomName(villager.getCustomName());
        }
        if (villager.isBaby()) {
            AttributeInstance scale = plus.getAttribute(Attributes.SCALE);
            if (scale != null) {
                scale.setBaseValue(BABY_SCALE);
            }
        }
        plus.startChain(1.0F);
        plus.setPersistenceRequired();
        if (!(villager instanceof WanderingTrader)) {
            plus.bindTo(homeOf(level, villager).orElse(diedAt), HOME_RADIUS);
        }
        return level.addFreshEntity(plus) ? plus : null;
    }

    /**
     * Where a villager lived, or failing that worked, if it is in this dimension. Only a {@link Villager}
     * is asked: its brain knows both memories, and asking a brain for one it lacks throws.
     */
    private static Optional<BlockPos> homeOf(ServerLevel level, AbstractVillager villager) {
        if (!(villager instanceof Villager resident)) {
            return Optional.empty();
        }
        Optional<BlockPos> home = resident.getBrain().getMemory(MemoryModuleType.HOME)
                .filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
        if (home.isPresent()) {
            return home;
        }
        return resident.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                .filter(pos -> pos.dimension() == level.dimension()).map(GlobalPos::pos);
    }

    /**
     * A Plus, spirit or player, killed by a Hollow is devoured: the Hollow heals and its reiatsu grows.
     * Anything else that strikes a Plus spirit down only tears its soul, which pulls itself back
     * together at the cost of some of its chain. Only what kills outright, such as /kill or the void,
     * ends one without a Hollow.
     */
    private static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !isPlus(victim)) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof Hollow hollow && hollow.isAlive()) {
            devoured(level, hollow, victim);
        } else if (victim instanceof PlusSpirit plus && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            reform(level, plus, source);
        }
    }

    private static boolean isPlus(LivingEntity entity) {
        if (entity instanceof PlusSpirit) {
            return true;
        }
        return entity instanceof Player player && SoulLogic.hasSoul(player)
                && SoulLogic.get(player).state() == SoulState.PLUS;
    }

    private static void devoured(ServerLevel level, Hollow hollow, LivingEntity victim) {
        hollow.devour(victim);
        for (ServerPlayer witness : SpiritSenses.awareNear(level, victim.position(), HollowLogic.AWARE_NEAR_RANGE)) {
            if (witness != victim) {
                witness.sendSystemMessage(Component.translatable("message.bleach.plus_devoured")
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
        SpiritSenses.particles(level, ParticleTypes.SQUID_INK, victim.getX(), victim.getY(0.5), victim.getZ(),
                12, 0.3, 0.4, 0.3, 0.02);
    }

    private static void reform(ServerLevel level, PlusSpirit plus, DamageSource source) {
        plus.setHealth(plus.getMaxHealth());
        SoulData soul = plus.soul();
        int cost = BleachConfig.get(BleachConfig.PLUS_DEATH_CHAIN_COST_SECONDS) * 20;
        soul.setChainTicks(Math.max(1, soul.chainTicks() - cost));
        plus.invulnerableTime = REFORM_INVULNERABLE_TICKS;
        SpiritSenses.particles(level, ParticleTypes.SOUL, plus.getX(), plus.getY(0.5), plus.getZ(),
                6, 0.25, 0.4, 0.25, 0.02);
        if (source.getEntity() instanceof Player attacker) {
            attacker.displayClientMessage(Component.translatable("message.bleach.plus.reformed")
                    .withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Each soul is checked once a minute, on its own tick of the cycle. */
    private static void onPlayerTickPre(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player
                && (player.tickCount + player.getId()) % HollowLogic.PLUS_CHECK_TICKS == HollowLogic.PLUS_CHECK_OFFSET
                && SoulLogic.hasSoul(player)) {
            tryAmbientPlus(player, SoulLogic.get(player));
        }
    }

    /**
     * Sometimes a Plus wanders near a soul who can see it, more often in a village, unless enough are
     * near already. Not in game tests, outside the Overworld or with mob spawning off. Peaceful does
     * not stop them; there, their chain only holds at its last link.
     */
    public static void tryAmbientPlus(ServerPlayer player, SoulData data) {
        ServerLevel level = player.serverLevel();
        if (!BleachConfig.get(BleachConfig.AMBIENT_PLUSES) || level.dimension() != Level.OVERWORLD
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING) || player.server instanceof GameTestServer
                || !player.isAlive() || !player.gameMode.isSurvival() || !SoulLogic.isAware(data)) {
            return;
        }
        double range = HollowLogic.PLUS_NEARBY_RANGE;
        int nearby = level.getEntitiesOfClass(PlusSpirit.class, player.getBoundingBox().inflate(range),
                plus -> plus.distanceToSqr(player) <= range * range).size();
        if (nearby >= BleachConfig.get(BleachConfig.MAX_NEARBY_PLUSES)
                || player.getRandom().nextDouble() >= HollowLogic.plusChancePerCheck(level.isVillage(player.blockPosition()))) {
            return;
        }
        Vec3 spot = SpawnSpots.openGround(level, player, ModEntities.PLUS.get(), WANDER_MIN_DISTANCE, WANDER_MAX_DISTANCE,
                WANDER_TRIES, WANDER_MAX_RISE);
        PlusSpirit plus = spot != null ? ModEntities.PLUS.get().create(level) : null;
        if (plus == null) {
            return;
        }
        plus.moveTo(spot.x, spot.y, spot.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        EventHooks.finalizeMobSpawn(plus, level, level.getCurrentDifficultyAt(plus.blockPosition()), MobSpawnType.EVENT, null);
        plus.startChain(Mth.nextFloat(level.getRandom(), WANDER_MIN_CHAIN, 1.0F));
        plus.setAmbient(true);
        level.addFreshEntity(plus);
    }

    /**
     * A Plus whose chain ran out becomes a Hollow where it stands, its mask forming over its face,
     * screaming. Aware souls nearby are told, and Denreishinki in range beep.
     *
     * @return false, leaving the Plus as it is, on Peaceful, if there is no room where it stands for
     *         the Hollow it would become, or if the Hollow could not be added; the Plus tries again later
     */
    public static boolean hollowfy(ServerLevel level, PlusSpirit plus) {
        if (!HollowLogic.mayHollowfy(level.getDifficulty())) {
            return false;
        }
        HollowKind kind = HollowLogic.pickPlusHollow(plus.getRandom());
        Hollow hollow = ModEntities.hollowType(kind).create(level);
        if (hollow == null) {
            return false;
        }
        hollow.moveTo(plus.getX(), plus.getY(), plus.getZ(), plus.getYRot(), 0.0F);
        hollow.setYHeadRot(plus.getYHeadRot());
        hollow.setYBodyRot(plus.yBodyRot);
        EventHooks.finalizeMobSpawn(hollow, level, level.getCurrentDifficultyAt(plus.blockPosition()),
                MobSpawnType.CONVERSION, null);
        // The size it rolled is in its dimensions already, though its box only follows on its first tick.
        if (!level.noCollision(hollow, hollow.getDimensions(hollow.getPose()).makeBoundingBox(hollow.position()))) {
            return false;
        }
        hollow.setFromPlus(true);
        hollow.setFormerVillager(plus.getVillagerData());
        hollow.startMaskForming();
        if (!level.addFreshEntity(hollow)) {
            return false;
        }
        double x = plus.getX();
        double y = plus.getY(0.5);
        double z = plus.getZ();
        SpiritSenses.particles(level, ParticleTypes.SQUID_INK, x, y, z, 20, 0.4, 0.6, 0.4, 0.05);
        SpiritSenses.particles(level, ParticleTypes.SOUL, x, y, z, 10, 0.3, 0.5, 0.3, 0.03);
        SpiritSenses.play(level, x, y, z, ModSounds.HOLLOW_HURT.get(), SoundSource.HOSTILE, 1.0F, 1.0F, true);
        SpiritSenses.play(level, x, y, z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 0.6F, true);
        for (ServerPlayer witness : SpiritSenses.awareNear(level, plus.position(), HollowLogic.AWARE_NEAR_RANGE)) {
            witness.sendSystemMessage(Component.translatable("message.bleach.plus_hollowfied")
                    .withStyle(ChatFormatting.DARK_RED));
        }
        GargantaEvents.pingPagers(level, plus.position(), "message.bleach.denreishinki.hollowfication");
        plus.discard();
        return true;
    }

    /**
     * The soul a purified Hollow once was, rising and passing on: a Plus in the villager's likeness it
     * had before it fell, or a plain villager of the biome's kind.
     *
     * @return the soul, or null if it could not be added
     */
    @Nullable
    public static PlusSpirit spawnSoulEcho(ServerLevel level, Hollow hollow) {
        PlusSpirit echo = ModEntities.PLUS.get().create(level);
        if (echo == null) {
            return null;
        }
        echo.moveTo(hollow.getX(), hollow.getY(), hollow.getZ(), hollow.getYRot(), 0.0F);
        echo.setYHeadRot(hollow.getYHeadRot());
        echo.setYBodyRot(hollow.yBodyRot);
        VillagerData look = hollow.formerVillager();
        echo.setVillagerData(look != null ? look
                : new VillagerData(VillagerType.byBiome(level.getBiome(hollow.blockPosition())), VillagerProfession.NONE, 1));
        echo.startDeparture(PlusSpirit.RISING);
        return level.addFreshEntity(echo) ? echo : null;
    }
}
