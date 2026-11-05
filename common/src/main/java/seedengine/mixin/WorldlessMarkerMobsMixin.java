package seedengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where a structure, after making an entity, sets a block, the same is done without the entity in this mod's recording
 * levels (no world, so no entity: see {@link WorldlessEntityMixin}), so that the rest of the structure comes out as in
 * the world. Only when there is no world; the game's own generation is not touched.
 */
@Mixin(targets = {
        "net.minecraft.world.level.levelgen.structure.structures.OceanRuinPieces$OceanRuinPiece",
        "net.minecraft.world.level.levelgen.structure.structures.WoodlandMansionPieces$WoodlandMansionPiece"
})
abstract class WorldlessMarkerMobsMixin {
    /**
     * An ocean ruin's drowned leaves water (or air above the sea) at its marker, a mansion's mobs leave air there.
     */
    @WrapOperation(method = "handleDataMarker", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;"))
    private Entity seedengine$markerMob(EntityType<?> type, Level level, EntitySpawnReason reason, Operation<Entity> original,
                                        @Local(argsOnly = true) BlockPos position, @Local(argsOnly = true) ServerLevelAccessor accessor) {
        Entity entity = original.call(type, level, reason);
        if (entity == null && level == null) {
            boolean drowned = type == net.minecraft.world.entity.EntityTypes.DROWNED;
            accessor.setBlock(position, drowned && position.getY() <= accessor.getSeaLevel() ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        return entity;
    }
}
