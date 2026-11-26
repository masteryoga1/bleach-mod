package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The rules of the Asauchi and the zanpakutō it becomes, with no side effects: who bonds, how much
 * each source is worth, the hidden leaning and the spirit it picks, which stacks count, and what
 * Shikai and Getsuga Tenshō cost (an Arrancar's Resurrección drains by the same Reiatsu Control share).
 * The events that apply them are in {@link ZanpakutoEvents} and {@link ShikaiEvents}.
 */
public final class ZanpakutoLogic {
    /** Reiryoku a technique always leaves behind; spending below it exhausts the soul. */
    public static final float RESERVE = 0.5F;

    /** How far an affordable charge may miss the reserve by float rounding. */
    private static final double AFFORD_EPSILON = 1.0E-4;
    private static final int QUARTER = ZanpakutoBond.FULL / 4;
    private static final int CLOSE_CALL_LEAN = 5;
    private static final int SUBSTITUTE_CLOSE_CALL_LEAN = 10;
    /** Base temperatures at or past which a biome is cold or hot enough to count as an elemental place. */
    private static final float COLD_TEMPERATURE = 0.15F;
    private static final float HOT_TEMPERATURE = 1.5F;
    /** Tie order of the leaning: power first, then constant release, then kidō. */
    private static final List<ZanpakutoType> TIE_ORDER =
            List.of(ZanpakutoType.POWER, ZanpakutoType.CONSTANT_RELEASE, ZanpakutoType.KIDO);
    /** A Substitute's tie order: constant release first (Ichigo's way leads to Zangetsu). */
    private static final List<ZanpakutoType> SUBSTITUTE_TIE_ORDER =
            List.of(ZanpakutoType.CONSTANT_RELEASE, ZanpakutoType.POWER, ZanpakutoType.KIDO);

    private ZanpakutoLogic() {
    }

    // ---- Who takes part ----

    /** Whether this soul's Asauchi can grow a bond with it: one that may sit in Jinzen, power not lent away. */
    public static boolean canBond(SoulData data, long now) {
        return maySit(data) && !ShinigamiLogic.isPowerLent(data, now);
    }

    /**
     * Whether this soul can reach a blade's spirit at all: not a Plus, Hollow or Arrancar, and either
     * holding a Shinigami's power or a soul of the Rukongai (a student, or one who could become one).
     */
    public static boolean maySit(SoulData data) {
        return !barred(data.state())
                && (ShinigamiLogic.hasShinigamiPower(data) || data.state() == SoulState.RUKONGAI);
    }

    /** Whether this soul can release a zanpakutō and use its techniques: a Shinigami's power, in a soul that keeps it. */
    public static boolean canWield(SoulData data) {
        return ShinigamiLogic.hasShinigamiPower(data) && !barred(data.state());
    }

    /** Whether the spirit will tell this soul its name: a full bond, a Shinigami's power of its own, and enough reiryoku. */
    public static boolean canLearnName(SoulData data, long now) {
        return data.zanpakuto().isFull() && ShinigamiLogic.hasShinigamiPower(data)
                && !ShinigamiLogic.isPowerLent(data, now)
                && data.maxReiryoku() >= BleachConfig.get(BleachConfig.NAME_MIN_REIRYOKU);
    }

    private static boolean barred(SoulState state) {
        return state == SoulState.HOLLOW || state == SoulState.ARRANCAR || state == SoulState.PLUS;
    }

    // ---- The bond ----

    /** A source's bond after the server's multiplier, rounded. */
    public static int scaledBond(int raw) {
        return (int) Math.round(raw * BleachConfig.get(BleachConfig.BOND_MULTIPLIER));
    }

    /** Which quarter of the bond this is, from 0 (nothing yet) to 4 (full). */
    public static int quarterOf(int bond) {
        return Mth.clamp(bond / QUARTER, 0, 4);
    }

    /** The bond for purifying a Hollow with this much reiatsu: a share of it, up to the cap. */
    public static int purificationBond(float hollowReiatsu) {
        double share = Math.round(Math.max(0.0F, hollowReiatsu) * BleachConfig.get(BleachConfig.BOND_PER_PURIFIED_REIATSU));
        double cap = BleachConfig.get(BleachConfig.BOND_PURIFICATION_CAP);
        return (int) Math.min(cap, share);
    }

    /** The bond for surviving a close call. A Substitute, whose body is at stake, gains more. */
    public static int closeCallBond(SoulData data) {
        double bond = BleachConfig.get(BleachConfig.BOND_PER_CLOSE_CALL);
        if (ShinigamiLogic.isSubstitute(data)) {
            bond *= BleachConfig.get(BleachConfig.SUBSTITUTE_CLOSE_CALL_MULTIPLIER);
        }
        return (int) Math.round(bond);
    }

    /** How far a close call leans the blade towards constant release. */
    public static int closeCallLean(SoulData data) {
        return ShinigamiLogic.isSubstitute(data) ? SUBSTITUTE_CLOSE_CALL_LEAN : CLOSE_CALL_LEAN;
    }

    // ---- The hidden leaning and the spirit ----

    /**
     * The leaning with the soul's training and reiryoku added: Zanjutsu and Hakuda lean to power,
     * Reiatsu Control to kidō, and raw reiryoku without the control to hold it to constant release.
     * A Substitute leans to constant release more. Never shown to players.
     */
    public static Map<ZanpakutoType, Integer> effectiveAffinity(SoulData data) {
        ZanpakutoBond bond = data.zanpakuto();
        int control = data.level(Stat.REIATSU_CONTROL);
        Map<ZanpakutoType, Integer> effective = new EnumMap<>(ZanpakutoType.class);
        effective.put(ZanpakutoType.POWER, bond.affinity(ZanpakutoType.POWER)
                + 2 * data.level(Stat.ZANJUTSU) + data.level(Stat.HAKUDA));
        effective.put(ZanpakutoType.KIDO, bond.affinity(ZanpakutoType.KIDO) + 2 * control);
        int constant = bond.affinity(ZanpakutoType.CONSTANT_RELEASE)
                + Math.max(0, Mth.floor(data.maxReiryoku() / 10.0F) - 2 * control);
        if (ShinigamiLogic.isSubstitute(data)) {
            constant += (int) Math.round((double) BleachConfig.get(BleachConfig.SUBSTITUTE_CONSTANT_LEAN));
        }
        effective.put(ZanpakutoType.CONSTANT_RELEASE, constant);
        return Collections.unmodifiableMap(effective);
    }

    /** Every kind of spirit, the one this soul leans to most first. Ties go as {@link #TIE_ORDER}, or {@link #SUBSTITUTE_TIE_ORDER}. */
    public static List<ZanpakutoType> leaningOrder(SoulData data) {
        Map<ZanpakutoType, Integer> effective = effectiveAffinity(data);
        List<ZanpakutoType> ties = ShinigamiLogic.isSubstitute(data) ? SUBSTITUTE_TIE_ORDER : TIE_ORDER;
        List<ZanpakutoType> order = new ArrayList<>(ties);
        order.sort(Comparator.<ZanpakutoType>comparingInt(type -> effective.getOrDefault(type, 0)).reversed()
                .thenComparingInt(ties::indexOf));
        return List.copyOf(order);
    }

    /** Which of {@code count} equally fitting spirits this soul meets: always the same one for the same soul. */
    public static int tieBreakIndex(UUID id, int count) {
        return count <= 0 ? 0 : Math.floorMod(id.hashCode(), count);
    }

    /**
     * The spirit this soul's blade becomes: among the spirits of the first kind in its leaning order
     * that has any, the one whose weights fit its leaning best. Empty if the registry has none.
     */
    public static Optional<ResourceLocation> chooseSpirit(SoulData data, UUID id, Registry<ZanpakutoSpirit> registry) {
        Map<ZanpakutoType, Integer> effective = effectiveAffinity(data);
        for (ZanpakutoType type : leaningOrder(data)) {
            List<ResourceLocation> candidates = new ArrayList<>();
            for (ResourceLocation key : registry.keySet()) {
                ZanpakutoSpirit spirit = registry.get(key);
                if (spirit != null && spirit.type() == type) {
                    candidates.add(key);
                }
            }
            if (candidates.isEmpty()) {
                continue; // no spirit of this kind ships: the next-closest kind answers
            }
            Collections.sort(candidates);
            double best = Double.NEGATIVE_INFINITY;
            List<ResourceLocation> tied = new ArrayList<>();
            for (ResourceLocation key : candidates) {
                double score = fit(registry.get(key).weights(), effective);
                if (score > best) {
                    best = score;
                    tied.clear();
                    tied.add(key);
                } else if (score == best) {
                    tied.add(key);
                }
            }
            return Optional.of(tied.get(tieBreakIndex(id, tied.size())));
        }
        return Optional.empty();
    }

    private static double fit(ZanpakutoSpirit.Weights weights, Map<ZanpakutoType, Integer> effective) {
        double score = 0.0;
        for (ZanpakutoType type : ZanpakutoType.values()) {
            score += (double) weights.of(type) * effective.getOrDefault(type, 0);
        }
        return score;
    }

    // ---- Which stacks count ----

    /** A nameless Asauchi: the zanpakutō item, bound to nobody. */
    public static boolean isUnboundAsauchi(ItemStack stack) {
        return stack.getItem() instanceof ZanpakutoItem && !ZanpakutoItem.isBound(stack);
    }

    /** A zanpakutō bound to this player. */
    public static boolean isOwnBlade(ItemStack stack, Player player) {
        return stack.getItem() instanceof ZanpakutoItem && ZanpakutoItem.isOwnedBy(stack, player.getUUID());
    }

    /** Whether a nameless Asauchi is anywhere in the player's own inventory, the off hand and armour included. */
    public static boolean carriesUnboundAsauchi(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (isUnboundAsauchi(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    /** Whether the player carries a blade of their own or one that could become theirs; someone else's does not count. */
    public static boolean carriesOwnOrUnboundBlade(Player player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isUnboundAsauchi(stack) || isOwnBlade(stack, player)) {
                return true;
            }
        }
        return false;
    }

    /** The player's own blade, the one in the main hand first, or an empty stack. Reads only the stacks, so either side may ask. */
    public static ItemStack findOwnBlade(Player player) {
        if (isOwnBlade(player.getMainHandItem(), player)) {
            return player.getMainHandItem();
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isOwnBlade(stack, player)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Rain falling on the player, water around them, or a biome of snow or desert heat. */
    public static boolean isElementalPlace(Player player) {
        if (player.isInWaterOrRain()) {
            return true;
        }
        float temperature = player.level().getBiome(player.blockPosition()).value().getBaseTemperature();
        return temperature <= COLD_TEMPERATURE || temperature >= HOT_TEMPERATURE;
    }

    // ---- Shikai and Getsuga Tenshō ----

    /** Reiryoku a release costs up front: the spirit's share of the soul's maximum. */
    public static float releaseCost(SoulData data, ZanpakutoSpirit spirit) {
        return data.maxReiryoku() * spirit.releaseCost();
    }

    /** Reiryoku a Shikai drains each tick. Reiatsu Control shrinks it, down to the configured floor. */
    public static float shikaiDrainPerTick(SoulData data, ZanpakutoSpirit spirit) {
        return (float) (data.maxReiryoku() * (double) spirit.drain()
                * BleachConfig.get(BleachConfig.SHIKAI_DRAIN_MULTIPLIER) * drainFraction(data) / 20.0);
    }

    /** The form a blade takes at this stage: its spirit's sealed or Shikai form, or its Bankai's. */
    public static ZanpakutoSpirit.Form form(ZanpakutoSpirit spirit, ZanpakutoStage stage) {
        return switch (stage) {
            case SEALED -> spirit.sealed();
            case SHIKAI -> spirit.shikai();
            case BANKAI -> bankaiForm(spirit);
        };
    }

    /**
     * Bankai's form: the one its spirit's file gives, or else Shikai's look, speed and reach with its damage made
     * stronger by the server's config.
     */
    private static ZanpakutoSpirit.Form bankaiForm(ZanpakutoSpirit spirit) {
        ZanpakutoSpirit.Form shikai = spirit.shikai();
        float damage = (float) (shikai.damage() * BleachConfig.get(BleachConfig.BANKAI_DAMAGE_MULTIPLIER));
        return spirit.bankai().flatMap(ZanpakutoSpirit.Bankai::form)
                .orElse(new ZanpakutoSpirit.Form(shikai.model(), damage, shikai.speed(), shikai.reach()));
    }

    /**
     * Reiryoku a Bankai release costs up front: its file's share of the soul's maximum, or else its Shikai release's
     * share made dearer by the server's config, and never more than the whole.
     */
    public static float bankaiReleaseCost(SoulData data, ZanpakutoSpirit spirit) {
        double share = spirit.bankai().flatMap(ZanpakutoSpirit.Bankai::releaseCost).map(Float::doubleValue)
                .orElse(spirit.releaseCost() * BleachConfig.get(BleachConfig.BANKAI_RELEASE_COST_RATIO));
        return (float) (data.maxReiryoku() * Math.min(1.0, share));
    }

    /**
     * The share of a release's drain this soul pays: Reiatsu Control shrinks it, down to the configured
     * floor. Shikai and an Arrancar's Resurrección both drain by it.
     */
    public static double drainFraction(SoulData data) {
        double control = 1.0 - BleachConfig.get(BleachConfig.DRAIN_REDUCTION_PER_REIATSU_CONTROL_LEVEL)
                * data.level(Stat.REIATSU_CONTROL);
        return Math.max(BleachConfig.get(BleachConfig.MIN_DRAIN_FRACTION), control);
    }

    /**
     * A Getsuga Tenshō's damage at this charge (0 to 1): it grows with the root of maximum reiryoku up
     * to a cap, and is weaker until Zangetsu has named it; and, from behind a Visored's mask,
     * {@code black_getsuga_multiplier} times that, after the cap ({@link VisoredLogic#getsugaFactor}).
     */
    public static float getsugaDamage(SoulData data, float charge) {
        double base = Math.min(BleachConfig.get(BleachConfig.GETSUGA_MAX_DAMAGE),
                BleachConfig.get(BleachConfig.GETSUGA_BASE_DAMAGE)
                        + BleachConfig.get(BleachConfig.GETSUGA_DAMAGE_PER_ROOT_REIRYOKU) * Math.sqrt(data.maxReiryoku()));
        double named = data.zanpakuto().getsugaNamed() ? 1.0 : BleachConfig.get(BleachConfig.GETSUGA_UNNAMED_MULTIPLIER);
        return (float) (base * (0.6 + 0.6 * Mth.clamp(charge, 0.0F, 1.0F)) * named * VisoredLogic.getsugaFactor(data));
    }

    /** What a Getsuga Tenshō of this charge costs: a share of maximum reiryoku from a tap to a full swing, never below the minimum. */
    public static float getsugaCost(SoulData data, float charge) {
        return (float) cost(data, Mth.clamp(charge, 0.0F, 1.0F));
    }

    private static double cost(SoulData data, double charge) {
        double share = Mth.lerp(charge, BleachConfig.get(BleachConfig.GETSUGA_TAP_COST_FRACTION),
                BleachConfig.get(BleachConfig.GETSUGA_FULL_COST_FRACTION));
        return Math.max(BleachConfig.get(BleachConfig.GETSUGA_MIN_COST), data.maxReiryoku() * share);
    }

    /**
     * The strongest charge up to {@code wanted} the soul can pay for and keep {@link #RESERVE}
     * reiryoku, or -1 if not even a tap is affordable.
     */
    public static float affordableCharge(SoulData data, float wanted) {
        double want = Mth.clamp(wanted, 0.0F, 1.0F);
        double budget = data.reiryoku() - RESERVE;
        if (cost(data, want) <= budget + AFFORD_EPSILON) {
            return (float) want;
        }
        if (cost(data, 0.0) > budget + AFFORD_EPSILON) {
            return -1.0F;
        }
        // The minimum cost fits, so the charge is where the share alone meets the budget.
        double tap = BleachConfig.get(BleachConfig.GETSUGA_TAP_COST_FRACTION);
        double full = BleachConfig.get(BleachConfig.GETSUGA_FULL_COST_FRACTION);
        if (full <= tap || data.maxReiryoku() <= 0.0F) {
            return 0.0F;
        }
        double charge = (budget / data.maxReiryoku() - tap) / (full - tap);
        return (float) Mth.clamp(charge, 0.0, want);
    }
}
