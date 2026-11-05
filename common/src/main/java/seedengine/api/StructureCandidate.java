package seedengine.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;

/**
 * A chunk where a structure set's placement lets a structure start. Whether one really does depends on the biome and
 * the terrain there: {@link SeedEngineApi#structures} works that out.
 *
 * @param set   the structure set, e.g. {@code minecraft:villages}
 * @param chunk the chunk the structure would start in
 */
public record StructureCandidate(Identifier set, ChunkPos chunk) {
    /** The square of the distance in chunks, for sorting by distance. */
    public long distanceSquared(ChunkPos from) {
        long dx = chunk.x() - from.x();
        long dz = chunk.z() - from.z();
        return dx * dx + dz * dz;
    }
}
