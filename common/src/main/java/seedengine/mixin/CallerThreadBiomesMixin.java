package seedengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import seedengine.worldgen.ChunkSteps;

@Mixin({ChunkGenerator.class, net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator.class})
abstract class CallerThreadBiomesMixin {
    /**
     * The biomes of this mod's terrain chunks are filled on the thread that asks, not handed to the game's worker
     * threads (which the server's own chunk generation uses). Other calls are left as they are.
     */
    @WrapOperation(method = "createBiomes", at = @At(value = "INVOKE",
            target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private <T> CompletableFuture<T> seedengine$onCaller(Supplier<T> work, Executor executor, Operation<CompletableFuture<T>> original) {
        return ChunkSteps.onCallerThread() ? CompletableFuture.completedFuture(work.get()) : original.call(work, executor);
    }
}
