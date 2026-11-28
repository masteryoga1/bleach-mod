package io.github.masteryoga1.bleach.entity;

import io.github.masteryoga1.bleach.config.BleachConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * Runuganga, the guardian of the white sand before Las Noches: a giant of grey sand 12 blocks tall in two hit boxes, as
 * the Gillian is ({@link Part}). His health is the config's; he is no sinner and never saved. A command brings him,
 * standing at once.
 */
public class Runuganga extends Hollow {
    public static final float WIDTH = 4.0F, HEIGHT = 12.0F, EYE_HEIGHT = 10.5F;
    /** The body part, hips to shoulders, from this high above the feet and this tall; the head part a cube above it. */
    public static final double BODY_BOTTOM = 4.0;
    public static final float BODY_HEIGHT = 5.0F, HEAD_SIZE = 3.0F;
    public static final int PART_BODY = 0, PART_HEAD = 1;

    private final Part[] parts;

    public Runuganga(EntityType<? extends Runuganga> type, Level level) {
        super(type, level);
        parts = new Part[] {new Part(this, WIDTH, BODY_HEIGHT), new Part(this, HEAD_SIZE, HEAD_SIZE)};
        // Ids for himself and both parts, as the Gillian takes them.
        setId(ENTITY_COUNTER.getAndAdd(parts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return HollowKind.RUNUGANGA.attributes();
    }

    @Override
    public HollowKind kind() {
        return HollowKind.RUNUGANGA;
    }

    /** The Gillian's navigation, which finds no path: none ever walks a body this big. */
    @Override
    protected PathNavigation createNavigation(Level level) {
        return new Gillian.Navigation(this, level);
    }

    /** He looks about him and no more for now. */
    @Override
    protected void registerGoals() {
        goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 24.0F));
        goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    /** His health is the config's; his blow is his kind's for now, as he strikes none. */
    @Override
    protected ConfigBases configBases() {
        return new ConfigBases(BleachConfig.get(BleachConfig.RUNUGANGA_HEALTH), kind().stats.attack());
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        tickParts();
    }

    /** His parts follow him on both sides, after he has moved. */
    @Override
    public void aiStep() {
        super.aiStep();
        tickParts();
    }

    private void tickParts() {
        float scale = getScale();
        parts[PART_BODY].resize(scale);
        parts[PART_HEAD].resize(scale);
        parts[PART_BODY].placeAt(getX(), getY() + BODY_BOTTOM * scale, getZ());
        parts[PART_HEAD].placeAt(getX(), getY() + (BODY_BOTTOM + BODY_HEIGHT) * scale, getZ());
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        // Null while the entity is being made; the constructor sets the ids again once the parts exist.
        if (parts != null) {
            for (int i = 0; i < parts.length; i++) {
                parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    public Part head() {
        return parts[PART_HEAD];
    }

    /** His forearms reach out of his box. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(2.0, 0.0, 2.0);
    }

    /** He goes when his chunk unloads, never because players are far. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    /** One of his two hit boxes: his body from the hips to the shoulders, and his head (see {@link GiantPart}). */
    public static final class Part extends GiantPart<Runuganga> {
        Part(Runuganga parent, float width, float height) {
            super(parent, width, height);
        }
    }
}
