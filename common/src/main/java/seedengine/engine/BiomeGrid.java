package seedengine.engine;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.Identifier;
import seedengine.api.BiomeArea;

/** The grids biomes are sampled on, and the limits of the questions about biomes. */
final class BiomeGrid {
    /** The most samples one area may take: 512x512. */
    static final int MAX_AREA_SAMPLES = 512 * 512;
    /** The farthest a search for the nearest biome may reach, in blocks: as far as {@code /locate biome} does. */
    static final int MAX_SEARCH_RADIUS = 6400;
    /** The most samples a search may go out from its origin, along x and along z: as many as {@code /locate biome}'s (6400 / 32). */
    static final int MAX_SEARCH_STEPS = 200;

    /** The biome at a column of the area's height. */
    interface Sampler {
        Identifier at(int x, int z);
    }

    private BiomeGrid() {
    }

    /** The samples from one corner to the other (in any order, both included), {@code step} apart from the smaller one. */
    static BiomeArea sample(int x1, int z1, int x2, int z2, int y, int step, Sampler sampler) {
        if (step < 1) throw new IllegalArgumentException("A step of " + step + " between biome samples: at least 1");
        int minX = Math.min(x1, x2);
        int minZ = Math.min(z1, z2);
        long width = ((long) Math.max(x1, x2) - minX) / step + 1;
        long depth = ((long) Math.max(z1, z2) - minZ) / step + 1;
        if (width * depth > MAX_AREA_SAMPLES) {
            throw new IllegalArgumentException("An area of " + width + "x" + depth + " biome samples, more than " + MAX_AREA_SAMPLES
                    + ": ask in smaller areas or with a larger step");
        }
        List<Identifier> biomes = new ArrayList<>((int) (width * depth));
        for (int row = 0; row < depth; row++) {
            for (int column = 0; column < width; column++) biomes.add(sampler.at(minX + column * step, minZ + row * step));
        }
        return new BiomeArea(minX, minZ, y, step, (int) width, (int) depth, biomes);
    }

    /** Refuses a search for the nearest biome past the limits. */
    static void checkSearch(int radius, int horizontalStep, int verticalStep) {
        if (horizontalStep < 1 || verticalStep < 1) {
            throw new IllegalArgumentException("Steps of " + horizontalStep + " and " + verticalStep + " between biome samples: at least 1");
        }
        if (radius < 0 || radius > MAX_SEARCH_RADIUS) {
            throw new IllegalArgumentException("A search radius of " + radius + " blocks for a biome: from 0 to " + MAX_SEARCH_RADIUS);
        }
        if (radius / horizontalStep > MAX_SEARCH_STEPS) {
            throw new IllegalArgumentException("A search " + radius / horizontalStep + " samples out for a biome, more than " + MAX_SEARCH_STEPS
                    + ": take a larger step or a smaller radius");
        }
    }
}
