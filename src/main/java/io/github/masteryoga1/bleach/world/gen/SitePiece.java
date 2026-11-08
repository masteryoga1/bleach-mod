package io.github.masteryoga1.bleach.world.gen;

import io.github.masteryoga1.bleach.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** {@code bleach:site}'s one piece: a template set whole at its corner, turned about it, saved with its turn. */
public final class SitePiece extends TemplateStructurePiece {
    public SitePiece(StructureTemplateManager templates, ResourceLocation template, BlockPos corner, Rotation rotation) {
        super(ModWorldgen.SITE_PIECE.get(), 0, templates, template, template.toString(), settings(rotation), corner);
    }

    /** As saved in a chunk: the template, its corner and its turn. */
    public SitePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(ModWorldgen.SITE_PIECE.get(), tag, templates, template -> settings(tag.contains("Rot")
                ? Rotation.valueOf(tag.getString("Rot")) : Rotation.NONE));
    }

    /** Turned about the corner, which is the settings' own pivot; structure blocks are left out. */
    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", placeSettings.getRotation().name());
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random,
                                    BoundingBox box) {
    }
}
