package seedengine.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * The biomes of an area, sampled at one height on a grid ({@link SeedEngineApi#biomeArea}): {@link #width()} samples
 * along x by {@link #depth()} along z, {@link #step()} blocks apart, from the corner at {@link #minX()},
 * {@link #minZ()}. Read it as a map ({@link #biome(int, int)}, sample by sample) or by biome ({@link #positions},
 * {@link #byBiome()}). Immutable.
 */
public final class BiomeArea {
    private final int minX;
    private final int minZ;
    private final int y;
    private final int step;
    private final int width;
    private final int depth;
    private final List<Identifier> palette;
    private final int[] cells;

    /**
     * @param biomes the biome of every sample, row by row: z from {@code minZ} on, and x from {@code minX} on within a
     *               row ({@code width * depth} of them)
     */
    public BiomeArea(int minX, int minZ, int y, int step, int width, int depth, List<Identifier> biomes) {
        if (step < 1 || width < 1 || depth < 1 || (long) width * depth != biomes.size()) {
            throw new IllegalArgumentException("A biome area of " + width + "x" + depth + " samples " + step + " apart with " + biomes.size() + " biomes");
        }
        this.minX = minX;
        this.minZ = minZ;
        this.y = y;
        this.step = step;
        this.width = width;
        this.depth = depth;
        Map<Identifier, Integer> indices = new LinkedHashMap<>();
        cells = new int[biomes.size()];
        for (int i = 0; i < cells.length; i++) {
            cells[i] = indices.computeIfAbsent(Objects.requireNonNull(biomes.get(i)), biome -> indices.size());
        }
        palette = List.copyOf(indices.keySet());
    }

    /** The x of the first column of samples (the smaller corner's). */
    public int minX() {
        return minX;
    }

    /** The z of the first row of samples (the smaller corner's). */
    public int minZ() {
        return minZ;
    }

    /** The height every sample is at. */
    public int y() {
        return y;
    }

    /** How many blocks apart the samples are, along x and along z. */
    public int step() {
        return step;
    }

    /** How many samples along x. */
    public int width() {
        return width;
    }

    /** How many samples along z. */
    public int depth() {
        return depth;
    }

    /** The x of a column of samples, from 0 to {@code width() - 1}. */
    public int x(int column) {
        return minX + column * step;
    }

    /** The z of a row of samples, from 0 to {@code depth() - 1}. */
    public int z(int row) {
        return minZ + row * step;
    }

    /** The biome of the sample at a column and a row. Out of the grid is an {@link IndexOutOfBoundsException}. */
    public Identifier biome(int column, int row) {
        Objects.checkIndex(column, width);
        Objects.checkIndex(row, depth);
        return palette.get(cells[row * width + column]);
    }

    /** The biomes found, in the order they were first met (row by row). */
    public List<Identifier> biomes() {
        return palette;
    }

    /** Where the samples of a biome are, row by row; empty if none is of it. */
    public List<BlockPos> positions(Identifier biome) {
        int index = palette.indexOf(biome);
        if (index < 0) return List.of();
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == index) positions.add(new BlockPos(x(i % width), y, z(i / width)));
        }
        return positions;
    }

    /** {@link #positions} of every biome found, in the order of {@link #biomes()}. */
    public Map<Identifier, List<BlockPos>> byBiome() {
        List<List<BlockPos>> byIndex = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) byIndex.add(new ArrayList<>());
        for (int i = 0; i < cells.length; i++) byIndex.get(cells[i]).add(new BlockPos(x(i % width), y, z(i / width)));
        Map<Identifier, List<BlockPos>> byBiome = new LinkedHashMap<>();
        for (int i = 0; i < palette.size(); i++) byBiome.put(palette.get(i), byIndex.get(i));
        return byBiome;
    }
}
