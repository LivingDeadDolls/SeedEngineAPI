package seedengine.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.world.level.levelgen.structure.structures.EndCityPieces$EndCityPiece")
abstract class WorldlessEndCityMixin {
    /**
     * An end city's shulkers and its elytra's item frame are entities only (the frame made directly, which needs a world):
     * in this mod's recording levels (no world) they are left out, the rest of the marker's handling stays.
     */
    @Inject(method = "handleDataMarker", at = @At("HEAD"), cancellable = true)
    private void seedengine$noWorld(String markerId, BlockPos position, ServerLevelAccessor level, RandomSource random, BoundingBox chunkBB,
                                    CallbackInfo info) {
        if ((markerId.startsWith("Sentry") || markerId.startsWith("Elytra")) && level.getLevel() == null) info.cancel();
    }
}
