package seedengine.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import seedengine.engine.World;

/**
 * What a chunk the client was sent says about how the world was generated, at a few columns: the height of the
 * natural ground and the biome there. Compared with what a generator gives, it tells whether the generator (seed,
 * world type) is right.
 *
 * @param ground one above the highest block of natural ground
 * @param biome  the biome stored in the chunk at the ground (exact 4x4x4 cell, not the blended one used for colors)
 */
record TerrainSample(int x, int z, int ground, Identifier biome) {
    /** Columns compared in each chunk, as local coordinates. */
    private static final int[][] COLUMNS = {{4, 4}, {12, 12}};
    /** Heights may differ this much without counting (erosion details the base height leaves out). */
    private static final int TOLERANCE = 4;

    /** Client thread. */
    static List<TerrainSample> of(LevelChunk chunk) {
        List<TerrainSample> samples = new ArrayList<>(COLUMNS.length);
        ChunkPos pos = chunk.getPos();
        for (int[] column : COLUMNS) {
            int ground = groundHeight(chunk, column[0], column[1]);
            if (ground == Integer.MIN_VALUE) continue;
            int x = pos.getMinBlockX() + column[0];
            int z = pos.getMinBlockZ() + column[1];
            var biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(Math.max(ground - 1, chunk.getMinY())), QuartPos.fromBlock(z));
            biome.unwrapKey().ifPresent(key -> samples.add(new TerrainSample(x, z, ground, key.identifier())));
        }
        return samples;
    }

    /** Whether a generator disagrees with this column: the ground height, or the biome. Any thread. */
    boolean differsFrom(World world) {
        int expected = world.height(Heightmap.Types.OCEAN_FLOOR_WG, x, z);
        if (Math.abs(expected - ground) > TOLERANCE) return true;
        return world.biome(x, ground - 1, z).map(id -> !id.equals(biome)).orElse(true);
    }

    /**
     * One above the highest block of natural ground: down from the top, past air, plants, snow layers, leaves, logs
     * and liquids. This is the height {@code OCEAN_FLOOR_WG} gives on a freshly generated column.
     */
    private static int groundHeight(LevelChunk chunk, int localX, int localZ) {
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(chunk.getPos().getMinBlockX() + localX, top, chunk.getPos().getMinBlockZ() + localZ);
        for (int y = top; y >= chunk.getMinY(); y--) {
            pos.setY(y);
            BlockState state = chunk.getBlockState(pos);
            if (state.isAir() || !state.isSolid() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || !state.getFluidState().isEmpty()) {
                continue;
            }
            return y + 1;
        }
        return Integer.MIN_VALUE;
    }
}
