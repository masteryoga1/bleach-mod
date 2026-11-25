package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.soul.VisoredLogic;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * A boss's floor: the health no blow takes it below while its floor holds, and what it does once there. A boss with one
 * implements {@link Floored}. One listener, the last word on every blow ({@link EventPriority#LOWEST}, after armour, a
 * mask and every other change), cuts a blow that would take it lower to leave it on its floor, its absorption spent
 * first, and then tells it it is there. A blow that passes invulnerability ({@code /kill}, the void) is left whole, so a
 * boss can always be killed. The gate keepers' floor and the inner Hollow's duel keep their own, as they came first.
 */
public final class BossFloor {
    /** How near its floor a blow must leave it to count as there: a float's error, no more. */
    private static final float AT_FLOOR = 1.0E-3F;

    private BossFloor() {
    }

    /** A boss with a floor. Server side only: the client never asks. */
    public interface Floored {
        /** The health no blow takes it below now; 0 or less while it has none. Asked on every blow. */
        float floor();

        /**
         * A blow has left it on its floor, or found it there already: called inside that blow, before its health is set,
         * and again for each such blow, so it must do nothing twice. It may change its own state, mark the ledger, tell
         * the souls about or set others going. It must not remove, kill or heal itself: the blow is still landing, its
         * health is set from what it is now, and every listener after (the hit counted, the hurt sound, the knockback)
         * still runs on it. A boss that leaves at its floor goes on its own next tick.
         */
        void atFloor(DamageSource source);
    }

    /** Puts the floor's one listener on the game's event bus, at the lowest priority, so it has the last word on a blow. */
    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, BossFloor::onDamagePre);
    }

    /** Cuts the blow to leave a boss on its floor, and tells it once a blow leaves it there. */
    private static void onDamagePre(LivingDamageEvent.Pre event) {
        LivingEntity body = event.getEntity();
        if (!(body instanceof Floored boss) || body.level().isClientSide()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float floor = boss.floor();
        if (floor <= 0.0F) {
            return;
        }
        float absorption = body.getAbsorptionAmount();
        float blow = VisoredLogic.clampedBlow(event.getNewDamage(), body.getHealth(), absorption, floor);
        event.setNewDamage(blow);
        // As the blow will leave it: absorption takes its share first, the rest comes off its health.
        if (body.getHealth() - Math.max(0.0F, blow - absorption) <= floor + AT_FLOOR) {
            boss.atFloor(event.getSource());
        }
    }
}
