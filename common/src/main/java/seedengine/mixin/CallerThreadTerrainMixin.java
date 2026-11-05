package seedengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import seedengine.worldgen.ChunkSteps;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class CallerThreadTerrainMixin {
    /** As {@link CallerThreadBiomesMixin}, for the terrain's noise of this mod's chunks. */
    @WrapOperation(method = "buildTerrain", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private <T> CompletableFuture<T> seedengine$onCaller(Supplier<T> work, Executor executor, Operation<CompletableFuture<T>> original) {
        return ChunkSteps.onCallerThread() ? CompletableFuture.completedFuture(work.get()) : original.call(work, executor);
    }
}
