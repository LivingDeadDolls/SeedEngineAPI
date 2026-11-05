package seedengine.mixin;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What decorating a chunk needs from a generator: the features of each step, in the order and numbering it uses. */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {
    @Accessor("featuresPerStep")
    Supplier<List<FeatureSorter.StepFeatureData>> seedengine$featuresPerStep();

    @Accessor("generationSettingsGetter")
    Function<Holder<Biome>, BiomeGenerationSettings> seedengine$generationSettingsGetter();
}
