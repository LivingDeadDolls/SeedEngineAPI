package seedengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.CappedProcessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import seedengine.shape.GhostCapture;

@Mixin(CappedProcessor.class)
abstract class CappedProcessorMixin {
    /**
     * The processor seeds its random from the server level's seed. The block capture places structures
     * into a stand-in level that has no server level, so give it the stand-in's seed (the same value).
     */
    @WrapOperation(method = "finalizeProcessing", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getSeed()J"))
    private long seedengine$ghostSeed(ServerLevel serverLevel, Operation<Long> original, @Local(argsOnly = true) ServerLevelAccessor level) {
        if (serverLevel == null && GhostCapture.isGhost(level)) return GhostCapture.seedOf(level);
        return original.call(serverLevel);
    }
}
