package seedengine.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
abstract class WorldlessEntityMixin {
    /**
     * A structure placed into one of this mod's recording levels (no world behind them, so {@code getLevel()} is null)
     * makes no entity, as for an entity of a feature turned off: the structure goes on placing the rest of its blocks.
     * A real level is never null here, so the game's own entities are not touched.
     */
    @Inject(method = "create(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/EntitySpawnReason;)Lnet/minecraft/world/entity/Entity;",
            at = @At("HEAD"), cancellable = true)
    private void seedengine$noWorld(Level level, EntitySpawnReason reason, CallbackInfoReturnable<Entity> result) {
        if (level == null) result.setReturnValue(null);
    }
}
