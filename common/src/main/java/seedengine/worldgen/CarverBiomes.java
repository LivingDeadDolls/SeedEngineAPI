package seedengine.worldgen;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.RandomState;

/** The biome settings whose carvers start in each chunk, per world (its random state), as a chunk keeps its carver biome. */
public final class CarverBiomes {
    private static final int KEEP = 200_000;
    private static final Map<RandomState, Map<Long, BiomeGenerationSettings>> KNOWN = Collections.synchronizedMap(new WeakHashMap<>());

    private CarverBiomes() {
    }

    public static BiomeGenerationSettings get(RandomState world, ChunkPos chunk, Supplier<BiomeGenerationSettings> lookUp) {
        Map<Long, BiomeGenerationSettings> known = KNOWN.computeIfAbsent(world, w -> new ConcurrentHashMap<>());
        if (known.size() > KEEP) known.clear();
        return known.computeIfAbsent(chunk.pack(), key -> lookUp.get());
    }
}
