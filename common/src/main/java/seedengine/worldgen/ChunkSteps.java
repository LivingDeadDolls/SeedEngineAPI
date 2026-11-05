package seedengine.worldgen;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.feature.MonsterRoomFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** The steps of chunk generation that differ between Minecraft versions (this is 26.2). */
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
        return new BiomeManager((x, y, z) -> context.generator().getBiomeSource().getNoiseBiome(x, y, z, context.randomState().sampler()),
                BiomeManager.obfuscateSeed(context.seed()));
    }

    /**
     * Fills a chunk whose biomes are set with its terrain as {@code ChunkStatusTasks} does: noise, surface, then carvers
     * (as {@code NoiseBasedChunkGenerator.applyCarvers}, with the generator's biomes where it reads a generation region).
     */
    public static void buildTerrain(WorldgenContext context, ProtoChunk chunk, StructureManager structures, BiomeManager biomes,
                                    Set<Holder<Biome>> possibleBiomes) {
        onThisThread(() -> context.generator().fillFromNoise(Blender.empty(), context.randomState(), structures, chunk).join());
        if (context.generator() instanceof NoiseBasedChunkGenerator generator) {
            WorldGenerationContext generation = new WorldGenerationContext(generator, chunk.getHeightAccessorForGeneration());
            generator.buildSurface(chunk, generation, context.randomState(), structures, biomes, Blender.empty(), possibleBiomes);
            carve(context, generator, chunk, biomes);
        }
        // The world's chunk keeps its world-generation heightmaps up to date through the surface and the carvers (its
        // status says which); this one's status does not: worked out again from its blocks.
        net.minecraft.world.level.levelgen.Heightmap.primeHeightmaps(chunk, java.util.EnumSet.of(
                net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR_WG, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG));
    }

    private static void carve(WorldgenContext context, NoiseBasedChunkGenerator generator, ProtoChunk chunk, BiomeManager biomes) {
        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(c -> {
            throw new IllegalStateException("No noise chunk for " + c.getPos());
        });
        CarvingContext carving = new CarvingContext(generator, context.registries(), chunk.getHeightAccessorForGeneration(), noiseChunk,
                context.randomState(), generator.generatorSettings().value().surfaceRule());
        var mask = chunk.getOrCreateCarvingMask();
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        ChunkPos pos = chunk.getPos();
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                ChunkPos source = new ChunkPos(pos.x() + dx, pos.z() + dz);
                BiomeGenerationSettings settings = CarverBiomes.get(context.randomState(), source, () -> generator.getBiomeGenerationSettings(
                        generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(source.getMinBlockX()), 0, QuartPos.fromBlock(source.getMinBlockZ()),
                                context.randomState().sampler())));
                int index = 0;
                for (Holder<ConfiguredWorldCarver<?>> carver : settings.getCarvers()) {
                    random.setLargeFeatureSeed(context.seed() + index, source.x(), source.z());
                    if (carver.value().isStartChunk(random)) {
                        carver.value().carve(carving, chunk, biomes::getBiome, random, noiseChunk.aquifer(), source, mask);
                    }
                    index++;
                }
            }
        }
    }

    /** Places a feature as chunk decoration does, biome check included. */
    public static boolean placeFeature(WorldGenLevel level, WorldgenContext context, PlacedFeature feature, RandomSource random, BlockPos origin) {
        return feature.placeWithBiomeCheck(level, context.generator(), random, origin);
    }

    /** Whether a placed feature is a dungeon (a monster room). */
    public static boolean isDungeon(PlacedFeature feature) {
        return feature.feature().value().feature() instanceof MonsterRoomFeature;
    }
}
