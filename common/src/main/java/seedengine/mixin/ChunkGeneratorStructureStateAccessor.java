package seedengine.mixin;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A structure state with structure sets and a rings seed of a server's own (see {@code ServerSeedStructures}). */
@Mixin(ChunkGeneratorStructureState.class)
public interface ChunkGeneratorStructureStateAccessor {
    @Invoker("<init>")
    static ChunkGeneratorStructureState seedengine$create(RandomState randomState, BiomeSource biomeSource, long levelSeed, ChunkPos origin,
                                                          long concentricRingsSeed, List<Holder<StructureSet>> possibleStructureSets) {
        throw new AssertionError();
    }

    @Accessor("biomeSource")
    BiomeSource seedengine$biomeSource();

    @Accessor("concentricRingsSeed")
    long seedengine$concentricRingsSeed();
}
