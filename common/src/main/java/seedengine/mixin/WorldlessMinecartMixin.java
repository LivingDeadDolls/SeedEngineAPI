package seedengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.MineshaftPieces$MineShaftCorridor")
abstract class WorldlessMinecartMixin {
    /**
     * A mineshaft's chest minecart draws its loot seed from the structure's random: in this mod's recording levels (no
     * world, so no minecart: see {@link WorldlessEntityMixin}) the draw is made all the same, so that the rest of the
     * mineshaft comes out as in the world.
     */
    @WrapOperation(method = "createChest", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/EntityType;create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;"))
    private Entity seedengine$chestMinecart(EntityType<?> type, Level level, EntitySpawnReason reason, Operation<Entity> original,
                                           @Local(argsOnly = true) RandomSource random) {
        Entity entity = original.call(type, level, reason);
        if (entity == null && level == null) random.nextLong();
        return entity;
    }
}
