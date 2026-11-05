package seedengine.api;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * A biome found by {@link SeedEngineApi#nearestBiome}: which one, and the block it was found at (one of the samples
 * of the search; the biome fills the 4x4x4 cell around it).
 *
 * @param biome the biome, e.g. {@code minecraft:cherry_grove}
 * @param pos   where it was found, as {@code /locate biome} tells it (with its height)
 */
public record FoundBiome(Identifier biome, BlockPos pos) {
}
