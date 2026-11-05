package seedengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import seedengine.shape.GhostCapture;

@Mixin(StructurePiece.class)
abstract class StructurePieceMixin {
    /**
     * Fences, torches and chains ask their chunk to fix their shape later. The block capture places structures into a
     * stand-in level that has no chunks: it is told instead, and fixes the shapes once the structure is placed.
     */
    @WrapOperation(method = "placeBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;markPosForPostprocessing(Lnet/minecraft/core/BlockPos;)V"))
    private void seedengine$ghostChunk(ChunkAccess chunk, BlockPos pos, Operation<Void> original, @Local(argsOnly = true) WorldGenLevel level) {
        if (chunk == null && GhostCapture.isGhost(level)) {
            GhostCapture.markForPostProcessing(level, pos);
            return;
        }
        original.call(chunk, pos);
    }
}
