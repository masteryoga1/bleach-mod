package io.github.masteryoga1.bleach.soul;

import io.github.masteryoga1.bleach.advancement.Milestones;
import io.github.masteryoga1.bleach.config.BleachConfig;
import io.github.masteryoga1.bleach.item.ZanpakutoItem;
import io.github.masteryoga1.bleach.network.BleachNetwork;
import io.github.masteryoga1.bleach.registry.ModSounds;
import io.github.masteryoga1.bleach.zanpakuto.JinzenBackdrop;
import io.github.masteryoga1.bleach.zanpakuto.JinzenLook;
import io.github.masteryoga1.bleach.zanpakuto.JinzenPage;
import io.github.masteryoga1.bleach.zanpakuto.JinzenScene;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStep;
import io.github.masteryoga1.bleach.zanpakuto.JinzenStepKind;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoBlade;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirit;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoSpirits;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoStage;
import io.github.masteryoga1.bleach.zanpakuto.ZanpakutoType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/**
 * Jinzen: sitting with the blade across one's lap until the world inside it opens. The soul settles
 * for three seconds, then a screen shows what the spirit has for it, from silence through a stirring
 * to the meeting, where the spirit sets its test and, if the soul passes, tells it its name. The
 * server builds every page as finished text; the client only shows it and sends answers back. A soul whose
 * inner Hollow is ready to be faced meets that instead once it has settled: the Hollow steps out to fight it
 * (see {@link VisoredEvents#beginDuel}) and the sitting ends, unless the fight cannot start, when the world opens. With
 * no Hollow ready, a soul past the slow road's gates has its spirit step out of the blade for its Bankai trial instead
 * (see {@link SlowRoad}), and the sitting ends the same way; otherwise its visit says why not, or what the road still asks.
 */
public final class JinzenLogic {
    public static final int SETTLE_TICKS = 60;
    /** How long the world inside the blade stays open. */
    public static final int SESSION_TICKS = 2400;
    /** Lateness forgiven on a timed step, for the trip the answer makes to the server. */
    public static final int GRACE_TICKS = 20;
    /** How long past its time a timed step waits for an answer before it fails by itself. */
    public static final int AUTO_FAIL_TICKS = 40;
    /** The jaws cannot be struck through before this. */
    public static final int FANGS_MIN_TICKS = 30;
    public static final int RIBBON_EXTRA_PER_FAILURE = 40;
    public static final int RIBBON_EXTRA_MAX = 120;
    public static final double MAX_DRIFT = 0.25;

    private static final int MOTE_INTERVAL_TICKS = 10;
    /** The bond at which a stirring spirit tries, and fails, to say its name. */
    private static final int NAME_STIRS_AT = 750;
    private static final int STIRS_AT = ZanpakutoBond.FULL / 4;
    private static final int MIST_TINT = 0x9AA0AA;
    private static final int WHITE = 0xFFFFFF;
    /** Stands in for the name a stirring spirit cannot get across; drawn scrambled. */
    private static final String SCRAMBLED_NAME = "zanpakut";

    // Server thread only, so a plain counter is enough to tell sittings apart.
    private static int nextSession;

    private JinzenLogic() {
    }

    // ---- Sitting down ----

    /**
     * Why the soul cannot sit in Jinzen with this blade, as the last part of a
     * {@code message.bleach.jinzen.*} key, or null if it can. Reads only what the client also has,
     * so both sides agree. The first reason that applies wins.
     */
    @Nullable
    public static String refusal(Player player, SoulData data, ItemStack stack, long now) {
        if (!player.getOffhandItem().isEmpty()) {
            return "off_hand";
        }
        if (!player.onGround()) {
            return "not_grounded";
        }
        if (!ZanpakutoLogic.maySit(data)) {
            return "cannot";
        }
        if (ShinigamiLogic.isPowerLent(data, now)) {
            return "power_lent";
        }
        ZanpakutoBlade blade = ZanpakutoItem.blade(stack);
        if (blade.isBound() && !blade.isOwnedBy(player.getUUID())) {
            return "not_yours";
        }
        if (blade.isBound() && blade.stage() == ZanpakutoStage.BANKAI) {
            return "bankai";
        }
        if (blade.isBound() && blade.released() && !blade.constant()) {
            return "released";
        }
        return null;
    }

    /** The soul starts settling where it sits. Any Getsuga Tenshō it was gathering fades. */
    public static void begin(Player player, SoulData data, long now) {
        ShikaiEvents.cancelGather(player, data);
        // Seeded when the world opens, not now; see open.
        data.jinzen = new JinzenSession(++nextSession, 0L, player.position(), now);
    }

    /**
     * One tick of use with the blade, {@code elapsed} ticks in. While settling the soul must stay
     * sneaking, on the ground and in place; after that it sits until the world closes. Moving off the
     * spot ends it either way.
     */
    public static void tick(Player player, SoulData data, ItemStack stack, int elapsed, long now) {
        JinzenSession session = data.jinzen;
        if (session == null) {
            stop(player, data);
            return;
        }
        if (player.position().distanceToSqr(session.anchor) > MAX_DRIFT * MAX_DRIFT) {
            disturbed(player, data);
            return;
        }
        if (!session.opened) {
            if (!player.isShiftKeyDown() || !player.onGround()) {
                disturbed(player, data);
                return;
            }
            if (elapsed == 1) {
                SpiritSenses.play(player, ModSounds.ZANPAKUTO_JINZEN.get(), SoundSource.PLAYERS, 0.8F, 1.0F, false);
                player.displayClientMessage(Component.translatable("message.bleach.jinzen.settling")
                        .withStyle(ChatFormatting.GRAY), true);
            }
            if (elapsed % MOTE_INTERVAL_TICKS == 0) {
                motes(player, data);
            }
            if (elapsed >= SETTLE_TICKS) {
                // A Bankai trial here keeps the Hollow in, so the inner world opens, as for any refusal.
                String held = VisoredLogic.duelReady(data, now) ? BankaiTrial.duelRefusal(player) : null;
                Component road = null;
                if (held != null) {
                    player.displayClientMessage(Component.translatable(held).withStyle(ChatFormatting.GRAY), true);
                } else if (VisoredLogic.duelReady(data, now)) {
                    VisoredEvents.DuelStart start = VisoredEvents.beginDuel(player, data, now, false);
                    if (start == VisoredEvents.DuelStart.STARTED) {
                        stop(player, data);
                        return;
                    }
                    VisoredEvents.tell(player, start);
                } else {
                    // No Hollow waits, so the slow road may: its gate is read once, before the world opens.
                    SlowRoad.Gate gate = SlowRoad.gate(player.level(), data, now);
                    BankaiTrial.Start start = SlowRoad.handOver(player, gate, now);
                    if (start == BankaiTrial.Start.STARTED) {
                        stop(player, data);
                        return;
                    }
                    road = SlowRoad.pageLine(player, data, now, gate, start);
                }
                JinzenPage page = open(player, data, now, road);
                if (player instanceof ServerPlayer serverPlayer && data.jinzen != null) {
                    BleachNetwork.openJinzen(serverPlayer, data.jinzen.id, page);
                }
            }
            return;
        }
        if (now - session.openedAt >= SESSION_TICKS) {
            stop(player, data);
            return;
        }
        Optional<JinzenStep> step = session.page == null ? Optional.empty() : session.page.step();
        if (!session.finished && session.scene == JinzenScene.MEETING && step.isPresent() && step.get().kind().timed()
                && now - session.stepStartedAt >= step.get().ticks() + AUTO_FAIL_TICKS) {
            Optional<ZanpakutoSpirit> spirit = ZanpakutoSpirits.get(player.level(), Optional.ofNullable(session.spirit));
            JinzenPage page = spirit.isPresent() ? fail(data, session, spirit.get(), now) : finish(session);
            if (player instanceof ServerPlayer serverPlayer) {
                BleachNetwork.openJinzen(serverPlayer, session.id, page);
            }
        }
    }

    /**
     * The world inside the blade opens: the sitting is counted, the bond credited, the spirit locked
     * in if the bond is full, and the scene chosen. Starts a fresh sitting if none is settling, so a
     * test can open one directly. The sitting's seed is drawn now.
     */
    public static JinzenPage open(Player player, SoulData data, long now) {
        return open(player, data, now, null);
    }

    /** As above, a visit ending on this line from the slow road ({@link SlowRoad#pageLine}), if there is one. */
    private static JinzenPage open(Player player, SoulData data, long now, @Nullable Component road) {
        JinzenSession old = data.jinzen;
        boolean fresh = old == null || old.opened;
        if (fresh) {
            ShikaiEvents.cancelGather(player, data);
        }
        JinzenSession session = new JinzenSession(fresh ? ++nextSession : old.id, player.level().getRandom().nextLong(),
                fresh ? player.position() : old.anchor, fresh ? now : old.begunAt);
        session.opened = true;
        session.openedAt = now;
        data.jinzen = session;

        ZanpakutoBond bond = data.zanpakuto();
        data.setZanpakuto(bond.withJinzens(bond.jinzens() + 1));
        if (player instanceof ServerPlayer serverPlayer) {
            Milestones.grant(serverPlayer, Milestones.JINZEN);
        }
        ZanpakutoEvents.creditJinzen(player, data, now);
        ZanpakutoEvents.ensureSpirit(player, data);

        Optional<ResourceLocation> id = data.zanpakuto().spirit();
        Optional<ZanpakutoSpirit> spirit = ZanpakutoSpirits.get(player.level(), id);
        JinzenScene scene = sceneFor(data, spirit, now);
        session.scene = scene;
        session.spirit = id.orElse(null);
        session.finished = scene != JinzenScene.MEETING;
        session.page = switch (scene) {
            case MEETING -> meeting(player, data, session, spirit.orElseThrow(), now);
            case VISIT -> visit(data, spirit.orElseThrow(), road);
            default -> new JinzenPage(scene, Component.translatable("screen.bleach.jinzen"), linesFor(scene, data, spirit, now),
                    Optional.empty(), lookFor(scene, spirit), true);
        };
        return session.page;
    }

    /**
     * The soul's answer to a step of the test: which ribbon it took, which answer it chose (its
     * position on the screen), or 1 for a strike through the jaws. A right answer moves on to the next
     * step or the name; a wrong or late one ends the test. Null when the answer belongs to no step that
     * is waiting for one, such as a stale screen's.
     */
    @Nullable
    public static JinzenPage answer(Player player, SoulData data, int session, int step, int value, long now) {
        JinzenSession sitting = data.jinzen;
        if (sitting == null || sitting.id != session || !sitting.opened || sitting.finished
                || sitting.scene != JinzenScene.MEETING || sitting.step != step || sitting.page == null) {
            return null;
        }
        Optional<ZanpakutoSpirit> found = ZanpakutoSpirits.get(player.level(), Optional.ofNullable(sitting.spirit));
        if (found.isEmpty() || step < 0 || step >= found.get().tests().size()) {
            return finish(sitting);
        }
        ZanpakutoSpirit spirit = found.get();
        ZanpakutoSpirit.TestStep test = spirit.tests().get(step);
        int ticks = sitting.page.step().map(JinzenStep::ticks).orElse(stepTicks(test, data.zanpakuto().testFailures()));
        long elapsed = now - sitting.stepStartedAt;
        boolean passed;
        if (test.kind() == JinzenStepKind.RIBBONS) {
            passed = value == redRibbon(sitting.seed) && elapsed <= ticks + GRACE_TICKS;
        } else if (test.kind() == JinzenStepKind.FANGS) {
            passed = value == 1 && elapsed >= FANGS_MIN_TICKS && elapsed <= ticks + GRACE_TICKS;
        } else {
            passed = value == correctPosition(sitting.order);
        }
        if (!passed) {
            return fail(data, sitting, spirit, now);
        }
        sitting.step++;
        if (sitting.step >= spirit.tests().size()) {
            return reveal(player, data, sitting, spirit, now);
        }
        sitting.page = stepPage(data, sitting, spirit, now);
        return sitting.page;
    }

    /** The soul opens its eyes. Leaving is never a failure. */
    public static void end(Player player, SoulData data) {
        data.jinzen = null;
    }

    /** Whether the soul sits with the world inside its blade open, which counts as meditating. */
    public static boolean isSitting(Player player, SoulData data) {
        return data.jinzen != null && data.jinzen.opened && player.isUsingItem();
    }

    private static void disturbed(Player player, SoulData data) {
        stop(player, data);
        player.displayClientMessage(Component.translatable("message.bleach.jinzen.disturbed")
                .withStyle(ChatFormatting.GRAY), true);
    }

    /** Ends the sitting and lets go of the blade, which closes the screen. */
    private static void stop(Player player, SoulData data) {
        end(player, data);
        player.stopUsingItem();
    }

    /** Motes in the spirit's colour, or white before there is one, rising around the sitting soul. */
    private static void motes(Player player, SoulData data) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int colour = ZanpakutoSpirits.get(level, data.zanpakuto().spirit()).map(ZanpakutoSpirit::colour).orElse(WHITE);
        DustParticleOptions mote = new DustParticleOptions(new Vector3f(((colour >> 16) & 0xFF) / 255.0F,
                ((colour >> 8) & 0xFF) / 255.0F, (colour & 0xFF) / 255.0F), 0.8F);
        SpiritSenses.particles(level, mote, player.getX(), player.getY() + 0.4, player.getZ(), 3, 0.4, 0.3, 0.4, 0.0);
    }

    // ---- What the soul sees ----

    /** Which scene the world inside the blade shows this soul; the first that applies wins. */
    public static JinzenScene sceneFor(SoulData data, Optional<ZanpakutoSpirit> spirit, long now) {
        ZanpakutoBond bond = data.zanpakuto();
        if (bond.named()) {
            return spirit.isPresent() ? JinzenScene.VISIT : JinzenScene.UNKNOWN;
        }
        if (bond.bond() < STIRS_AT) {
            return JinzenScene.SILENT;
        }
        if (!bond.isFull()) {
            return JinzenScene.STIRRING;
        }
        if (!ShinigamiLogic.hasShinigamiPower(data)) {
            return JinzenScene.STUDENT;
        }
        if (data.maxReiryoku() < BleachConfig.get(BleachConfig.NAME_MIN_REIRYOKU)) {
            return JinzenScene.WEAK;
        }
        if (now - bond.testFailedAt() < retryTicks()) {
            return JinzenScene.RESTING;
        }
        return spirit.isPresent() ? JinzenScene.MEETING : JinzenScene.UNKNOWN;
    }

    private static List<Component> linesFor(JinzenScene scene, SoulData data, Optional<ZanpakutoSpirit> spirit, long now) {
        List<Component> lines = new ArrayList<>();
        switch (scene) {
            case SILENT -> lines.add(Component.translatable("jinzen.bleach.silent"));
            case STIRRING -> {
                lines.add(Component.translatable("jinzen.bleach.stirring"));
                ZanpakutoType leaning = ZanpakutoLogic.leaningOrder(data).get(0);
                lines.add(Component.translatable("jinzen.bleach.vision." + leaning.getSerializedName()));
                if (data.zanpakuto().bond() >= NAME_STIRS_AT) {
                    lines.add(Component.translatable("jinzen.bleach.stirring.name",
                            Component.literal(SCRAMBLED_NAME).withStyle(ChatFormatting.OBFUSCATED)));
                }
            }
            case STUDENT -> lines.add(Component.translatable("jinzen.bleach.student"));
            case WEAK -> lines.add(Component.translatable("jinzen.bleach.weak",
                    (int) Math.ceil(BleachConfig.get(BleachConfig.NAME_MIN_REIRYOKU))));
            case RESTING -> lines.add(Component.translatable("jinzen.bleach.resting",
                    duration(retryTicks() - (now - data.zanpakuto().testFailedAt()))));
            default -> lines.add(Component.translatable("jinzen.bleach.unknown"));
        }
        return lines;
    }

    /** The empty dark for a silent or lost spirit, mist in the spirit's tint while it stirs or waits, its own world once met. */
    private static JinzenLook lookFor(JinzenScene scene, Optional<ZanpakutoSpirit> spirit) {
        return switch (scene) {
            case SILENT, UNKNOWN -> JinzenLook.VOID;
            case MEETING, VISIT -> spirit.map(JinzenLogic::innerWorld).orElse(JinzenLook.VOID);
            default -> new JinzenLook(JinzenBackdrop.MIST, spirit.map(s -> s.innerWorld().tint()).orElse(MIST_TINT));
        };
    }

    private static JinzenLook innerWorld(ZanpakutoSpirit spirit) {
        return new JinzenLook(spirit.innerWorld().backdrop(), spirit.innerWorld().tint());
    }

    /** A named soul visits: the spirit's world, under its name, what it is doing today, and the slow road's word, if any. */
    private static JinzenPage visit(SoulData data, ZanpakutoSpirit spirit, @Nullable Component road) {
        Component name = nameOf(data, spirit);
        List<Component> lines = new ArrayList<>(spirit.script().visit());
        if (lines.isEmpty()) {
            lines.add(Component.translatable("jinzen.bleach.reveal.name", name));
        }
        if (road != null) {
            lines.add(road);
        }
        return new JinzenPage(JinzenScene.VISIT, name, lines, Optional.empty(), innerWorld(spirit), true);
    }

    /** The meeting: the spirit's introduction and the first step of its test, or the name at once if it sets none. */
    private static JinzenPage meeting(Player player, SoulData data, JinzenSession session, ZanpakutoSpirit spirit, long now) {
        session.step = 0;
        if (spirit.tests().isEmpty()) {
            return reveal(player, data, session, spirit, now);
        }
        return stepPage(data, session, spirit, now);
    }

    /** The current step of the test, under the spirit's introduction. Its clock starts now. */
    private static JinzenPage stepPage(SoulData data, JinzenSession session, ZanpakutoSpirit spirit, long now) {
        ZanpakutoSpirit.TestStep test = spirit.tests().get(session.step);
        int ticks = stepTicks(test, data.zanpakuto().testFailures());
        JinzenStep step;
        if (test.kind() == JinzenStepKind.RIBBONS) {
            step = new JinzenStep(test.kind(), session.step, ticks,
                    test.prompt().orElse(Component.translatable("jinzen.bleach.ribbons")), List.of(), redRibbon(session.seed));
        } else if (test.kind() == JinzenStepKind.FANGS) {
            step = new JinzenStep(test.kind(), session.step, ticks,
                    test.prompt().orElse(Component.translatable("jinzen.bleach.fangs")), List.of(), -1);
        } else {
            session.order = answerOrder(session.seed, test.answers().size());
            List<Component> answers = new ArrayList<>();
            for (int position : session.order) {
                answers.add(test.answers().get(position));
            }
            step = new JinzenStep(test.kind(), session.step, ticks, test.prompt().orElse(CommonComponents.EMPTY), answers, -1);
        }
        session.stepStartedAt = now;
        return new JinzenPage(JinzenScene.MEETING, Component.translatable("screen.bleach.jinzen.unknown_name"),
                spirit.script().intro(), Optional.of(step), innerWorld(spirit), false);
    }

    /** The test is failed: the spirit turns away, and the soul may try again after a rest. */
    private static JinzenPage fail(SoulData data, JinzenSession session, ZanpakutoSpirit spirit, long now) {
        data.setZanpakuto(data.zanpakuto().failed(now));
        session.finished = true;
        List<Component> lines = new ArrayList<>(spirit.script().fail());
        lines.add(Component.translatable("jinzen.bleach.fail.retry", duration(retryTicks())));
        session.page = new JinzenPage(JinzenScene.MEETING, Component.translatable("screen.bleach.jinzen.unknown_name"),
                lines, Optional.empty(), innerWorld(spirit), true);
        return session.page;
    }

    /** The test is passed: the spirit says its name, and the blade in hand becomes the soul's own. */
    private static JinzenPage reveal(Player player, SoulData data, JinzenSession session, ZanpakutoSpirit spirit, long now) {
        ResourceLocation id = session.spirit;
        if (id == null) {
            return finish(session);
        }
        ZanpakutoEvents.learnName(player, data, id, spirit, bladeToName(player));
        session.finished = true;
        Component name = nameOf(data, spirit);
        List<Component> lines = new ArrayList<>(spirit.script().reveal());
        lines.add(Component.translatable("jinzen.bleach.reveal.name", name));
        if (spirit.isConstant()) {
            lines.add(Component.translatable("jinzen.bleach.reveal.constant"));
        } else {
            lines.add(Component.translatable("jinzen.bleach.reveal.command", spirit.command().orElse(name), name));
        }
        session.page = new JinzenPage(JinzenScene.MEETING, name, lines, Optional.empty(), innerWorld(spirit), true);
        return session.page;
    }

    /** Nothing more to see: the same page, with only the way out. */
    private static JinzenPage finish(JinzenSession session) {
        session.finished = true;
        JinzenPage page = session.page;
        session.page = page == null
                ? new JinzenPage(JinzenScene.UNKNOWN, Component.translatable("screen.bleach.jinzen"), List.of(),
                        Optional.empty(), JinzenLook.VOID, true)
                : new JinzenPage(page.scene(), page.title(), page.lines(), Optional.empty(), page.look(), true);
        return session.page;
    }

    /** The blade the name binds: the one in hand if it can be the soul's, else the first such blade carried, else none. */
    private static ItemStack bladeToName(Player player) {
        ItemStack held = player.getMainHandItem();
        if (ZanpakutoLogic.isUnboundAsauchi(held) || ZanpakutoLogic.isOwnBlade(held, player)) {
            return held;
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (ZanpakutoLogic.isUnboundAsauchi(stack) || ZanpakutoLogic.isOwnBlade(stack, player)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static Component nameOf(SoulData data, ZanpakutoSpirit spirit) {
        Component name = data.zanpakuto().name();
        return name.getString().isEmpty() ? spirit.name().copy().withColor(spirit.colour()) : name;
    }

    // ---- The test ----

    /** Which of the {@link JinzenStep#RIBBONS} ribbons is the red one. */
    public static int redRibbon(long seed) {
        return (int) Math.floorMod(seed ^ (seed >>> 29), (long) JinzenStep.RIBBONS);
    }

    /** The order a question's answers are shown in, shuffled from the seed: position i shows answer {@code order[i]}. */
    public static int[] answerOrder(long seed, int count) {
        int[] order = new int[Math.max(0, count)];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        RandomSource random = RandomSource.create(seed);
        for (int i = order.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int swap = order[i];
            order[i] = order[j];
            order[j] = swap;
        }
        return order;
    }

    /** Where the right answer (always the spirit's first) is shown, or -1 if it is not. */
    public static int correctPosition(int[] order) {
        for (int i = 0; i < order.length; i++) {
            if (order[i] == 0) {
                return i;
            }
        }
        return -1;
    }

    /** Ticks a step allows: its seconds, plus more for the ribbons after each failure. A question has no limit (0). */
    public static int stepTicks(ZanpakutoSpirit.TestStep step, int failures) {
        if (step.kind() == JinzenStepKind.RIBBONS) {
            int extra = RIBBON_EXTRA_PER_FAILURE * Math.min(Math.max(0, failures), RIBBON_EXTRA_MAX);
            return step.seconds() * 20 + Math.min(RIBBON_EXTRA_MAX, extra);
        }
        return step.kind() == JinzenStepKind.FANGS ? step.seconds() * 20 : 0;
    }

    private static long retryTicks() {
        return Math.round(BleachConfig.get(BleachConfig.JINZEN_RETRY_SECONDS) * 20.0);
    }

    /** A wait written out as "m min s s", rounded up to the second. */
    public static Component duration(long ticks) {
        long seconds = Math.max(0L, (ticks + 19L) / 20L);
        return Component.literal(seconds / 60L + " min " + seconds % 60L + " s");
    }
}
