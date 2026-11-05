package seedengine.worldgen;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** The steps of chunk generation that differ between Minecraft versions (this is 26.3). */
public final class ChunkSteps {
    private ChunkSteps() {
    }

    private static final ThreadLocal<Boolean> ON_CALLER = ThreadLocal.withInitial(() -> false);

    /** Whether this thread is building this mod's terrain chunks, which the game's own steps then do on this thread. */
    public static boolean onCallerThread() {
        return ON_CALLER.get();
    }

    /** Runs the game's step on this thread (see {@link #onCallerThread}). */
    static <T> T onThisThread(java.util.function.Supplier<T> step) {
        boolean before = ON_CALLER.get();
        ON_CALLER.set(true);
        try {
            return step.get();
        } finally {
            ON_CALLER.set(before);
        }
    }

    /**
     * The random a generation region gives out ({@code level.getRandom()}) while a chunk is decorated, as
     * {@code WorldGenRegion} makes it from the chunk's position (a desert pyramid's collapsed roof draws from it).
     */
    public static net.minecraft.util.RandomSource regionRandom(WorldgenContext context, net.minecraft.world.level.ChunkPos chunk) {
        return context.randomState().getOrCreateRandomFactory(net.minecraft.resources.Identifier.withDefaultNamespace("worldgen_region_random"))
                .at(chunk.getWorldPosition());
    }

    /**
     * The heightmaps a chunk keeps up to date while features and structures are placed in it (its status's, then
     * terrain: the final ones). The world-generation ones ({@code _WG}) stay as the terrain left them.
     */
    public static final java.util.Set<net.minecraft.world.level.levelgen.Heightmap.Types> DECORATION_HEIGHTMAPS = java.util.EnumSet.of(
            net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES);

    /** The biomes of the world as a generation region reads them (zoomed from the generator's noise biomes). */
    public static BiomeManager biomeManager(WorldgenContext context) {
        return new BiomeManager(context.generator().getBiomeSource().createUncachedResolver(context.randomState()), BiomeManager.obfuscateSeed(context.seed()));
    }

    /** Fills a chunk whose biomes are set with its terrain: noise, surface and carvers, as {@code ChunkStatusTasks.buildTerrain}. */
    public static void buildTerrain(WorldgenContext context, ProtoChunk chunk, StructureManager structures, BiomeManager biomes,
                                    Set<Holder<Biome>> possibleBiomes) {
        onThisThread(() -> context.generator().buildTerrain(chunk, Blender.empty(), context.randomState(), structures, biomes, null, possibleBiomes).join());
        // The world's chunk keeps its world-generation heightmaps up to date through the surface and the carvers (its
        // status says which); this one's status does not: worked out again from its blocks.
        net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(
                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG));
    }

    /** Places a feature as chunk decoration does, biome check included. */
    public static boolean placeFeature(WorldGenLevel level, WorldgenContext context, PlacedFeature feature, RandomSource random, BlockPos origin) {
        return new FeaturePlacer(level, context.generator()).placeWithBiomeCheck(feature, random, origin);
    }

    /** Whether a placed feature is a dungeon (a monster room). */
    public static boolean isDungeon(PlacedFeature feature) {
        return feature.feature().value() instanceof MonsterRoomFeature;
    }
}
