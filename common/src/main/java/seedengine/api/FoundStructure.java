package seedengine.api;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A structure world generation places: where it starts and the box around all of it. Pass it back to
 * {@link SeedEngineApi#pieces}, {@link SeedEngineApi#blocks} or {@link SeedEngineApi#spawners} (with the same seed,
 * world type and dimension) for more of it.
 *
 * @param id    the structure, e.g. {@code minecraft:village_plains}
 * @param set   the structure set it was picked from, e.g. {@code minecraft:villages}
 * @param chunk the chunk it starts in
 * @param box   the box around all of its pieces, where they are placed: for a structure that decides its height as it
 *              is placed (an ocean ruin on the ocean floor), there, not where it was generated (see
 *              {@link SeedEngineApi#structures})
 */
public record FoundStructure(Identifier id, Identifier set, ChunkPos chunk, BoundingBox box) {
}
