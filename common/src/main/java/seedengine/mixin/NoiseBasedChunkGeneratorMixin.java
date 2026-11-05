package seedengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import seedengine.worldgen.CarverBiomes;

@Mixin(NoiseBasedChunkGenerator.class)
abstract class NoiseBasedChunkGeneratorMixin {
    /**
     * Without a generation region (terrain worked out off the server), carving looks up the biome of
     * each of the 17x17 chunks around anew for every chunk; a region's chunks keep it. Keep it here the same way.
     * World generation passes a region and looks it up elsewhere, so it is not touched.
     */
    @WrapOperation(method = "generateCarvers", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseBasedChunkGenerator;getBiomeGenerationSettingsForCarver(Lnet/minecraft/world/level/biome/BiomeResolver;Lnet/minecraft/world/level/ChunkPos;)Lnet/minecraft/world/level/biome/BiomeGenerationSettings;"))
    private BiomeGenerationSettings seedengine$keptCarverBiome(NoiseBasedChunkGenerator self, BiomeResolver resolver, ChunkPos source,
                                                                   Operation<BiomeGenerationSettings> original,
                                                                   @Local(argsOnly = true) RandomState randomState) {
        return CarverBiomes.get(randomState, source, () -> original.call(self, resolver, source));
    }
}
