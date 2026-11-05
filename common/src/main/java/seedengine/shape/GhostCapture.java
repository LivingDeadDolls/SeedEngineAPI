package seedengine.shape;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.structures.BuriedTreasurePieces;
import net.minecraft.world.level.levelgen.structure.structures.IglooPieces;
import net.minecraft.world.level.levelgen.structure.structures.OceanRuinPieces;
import net.minecraft.world.level.levelgen.structure.structures.ShipwreckPieces;
import seedengine.Constants;
import seedengine.api.Spawner;
import seedengine.worldgen.WorldgenContext;

/**
 * The blocks a structure places, worked out by letting vanilla place it ({@link StructureStart#placeInChunk}) into a
 * stand-in level that only records blocks. Each chunk gets the random seeds vanilla gives it (the structure's and the
 * region's), so random block choices (cracked bricks, cobwebs) come out the same.
 * <p>
 * The stand-in is a {@link Proxy} of {@link WorldGenLevel}, so it follows the interface across versions without code
 * for each method. The terrain the structure looks at (whether to lay a rail, where a path goes) is the terrain world
 * generation builds there (noise, surface, carvers, bent around the structures), with its heightmaps kept as a chunk
 * being decorated keeps them, and the blocks marked for post-processing take their neighbours' shape at the end.
 */
public final class GhostCapture {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private GhostCapture() {
    }

    /**
     * What placing a structure gave: its blocks keyed by {@link BlockPos#asLong}, and its spawners, as the chunks are
     * generated row by row. Also what depends on that order: by position, every block the world may have there in some
     * order (the one in {@code blocks} among them); and every spawner it may place, certain or not.
     */
    public record Captured(Long2ObjectOpenHashMap<BlockState> blocks, List<Spawner> spawners,
                           Long2ObjectOpenHashMap<java.util.Set<BlockState>> uncertain, List<seedengine.api.StructureSpawner> possibleSpawners) {
    }

    /** What placing the starts in one chunk gave the captured start, in one order. */
    private record ChunkResult(Long2ObjectOpenHashMap<BlockState> blocks, java.util.Set<Spawner> spawners,
                               java.util.Set<seedengine.mixin.MineShaftCorridorAccessor> placedSpiders) {
    }

    /** Places the structure chunk by chunk and returns what it set. */
    public static Captured capture(WorldgenContext context, StructureStart given) {
        return capture(context, given, List.of());
    }

    /**
     * Places the structure chunk by chunk and returns what it set. Nearby starts of the same structure (overlapping
     * mineshafts, for example) are placed too, in the order vanilla places them, as vanilla gives them all the chunk's
     * one random: the random choices of this start depend on them. Only what this start sets is returned.
     */
    public static Captured capture(WorldgenContext context, StructureStart given, java.util.Collection<StructureStart> nearby) {
        return captureOwn(context, copy(context, given), nearby.stream().map(other -> copy(context, other)).toList());
    }

    /**
     * {@link #capture(WorldgenContext, StructureStart, java.util.Collection)} with starts that are the caller's own to
     * place (copies no other thread places): placed as they are, not copied again.
     */
    public static Captured captureOwn(WorldgenContext context, StructureStart start, java.util.Collection<StructureStart> nearby) {
        Long2ObjectOpenHashMap<StructureStart> byChunk = new Long2ObjectOpenHashMap<>();
        for (StructureStart other : nearby) {
            if (other.getStructure() != start.getStructure() || !other.isValid() || other.getChunkPos().equals(start.getChunkPos())) continue;
            byChunk.put(other.getChunkPos().pack(), other);
        }
        byChunk.put(start.getChunkPos().pack(), start);
        Recorder recorder = new Recorder(context, new seedengine.worldgen.TerrainChunks(context, byChunk.values()));
        WorldGenLevel level = recorder.level();
        Structure structure = start.getStructure();
        int index = structureIndex(context, structure);
        int step = structure.step().ordinal();
        List<ChunkPos> chunks = chunksOf(start);
        for (ChunkPos chunk : chunks) {
            recorder.placing = chunk.pack();
            placeChunk(level, context, recorder, byChunk, start, chunk, index, step);
        }
        recorder.placing = Long.MIN_VALUE;
        List<Spawner> spawners = recorder.spawners(recorder.blockEntities, recorder.ownBlockEntities, recorder.placed);
        Long2ObjectOpenHashMap<java.util.Set<BlockState>> uncertain = new Long2ObjectOpenHashMap<>();
        List<seedengine.api.StructureSpawner> possible = new ArrayList<>();
        java.util.Set<Spawner> uncertainSpawners = otherOrders(level, context, recorder, byChunk, start, chunks, index, step, spawners, uncertain, possible);
        for (Spawner spawner : spawners) {
            if (!uncertainSpawners.contains(spawner)) possible.add(new seedengine.api.StructureSpawner(spawner, true));
        }
        possible.sort(java.util.Comparator.comparingLong(found -> found.spawner().pos().asLong()));
        recorder.postProcess();
        return new Captured(recorder.placed, spawners, uncertain, List.copyOf(possible));
    }

    /**
     * Whether some piece of a start decides its height only as it is placed, from the blocks and heightmaps of the
     * chunk being decorated: an ocean ruin, a shipwreck and a buried treasure (generated at y 90 and moved to the
     * ocean floor, the ground or the stone under it), an igloo, and a swamp hut, a jungle temple and a desert pyramid
     * (generated at y 64). The box of such a start is where it is placed only once it is placed.
     */
    public static boolean heightDecidedWhenPlaced(StructureStart start) {
        for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof OceanRuinPieces.OceanRuinPiece || piece instanceof BuriedTreasurePieces.BuriedTreasurePiece
                    || piece instanceof IglooPieces.IglooPiece || piece instanceof ScatteredFeaturePiece) {
                return true;
            }
            // One too big for a chunk's region is put at its height as it is generated.
            if (piece instanceof ShipwreckPieces.ShipwreckPiece ship && !ship.isTooBigToFitInWorldGenRegion()) return true;
        }
        return false;
    }

    /**
     * The boxes of a start's pieces once placed: placed chunk by chunk as {@link #captureOwn} places it (rows from the
     * corner of least x and z), on the same terrain, so the pieces that decide their height as they are placed are
     * moved where the blocks go. The start is the caller's own; its pieces are moved.
     */
    public static List<BoundingBox> placedPieces(WorldgenContext context, StructureStart start) {
        Long2ObjectOpenHashMap<StructureStart> byChunk = new Long2ObjectOpenHashMap<>();
        byChunk.put(start.getChunkPos().pack(), start);
        Recorder recorder = new Recorder(context, new seedengine.worldgen.TerrainChunks(context, List.of(start)));
        WorldGenLevel level = recorder.level();
        Structure structure = start.getStructure();
        int index = structureIndex(context, structure);
        int step = structure.step().ordinal();
        for (ChunkPos chunk : chunksOf(start)) {
            placeChunk(level, context, recorder, byChunk, start, chunk, index, step);
        }
        List<BoundingBox> boxes = new ArrayList<>();
        for (StructurePiece piece : start.getPieces()) {
            BoundingBox box = piece.getBoundingBox();
            boxes.add(new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()));
        }
        return List.copyOf(boxes);
    }

    /** The chunks a start's box reaches, row by row (as the world generates the chunks around a player, near enough). */
    private static List<ChunkPos> chunksOf(StructureStart start) {
        BoundingBox box = start.getBoundingBox();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int chunkZ = box.minZ() >> 4; chunkZ <= box.maxZ() >> 4; chunkZ++) {
            for (int chunkX = box.minX() >> 4; chunkX <= box.maxX() >> 4; chunkX++) {
                chunks.add(new ChunkPos(chunkX, chunkZ));
            }
        }
        return chunks;
    }

    /** Places the starts reaching a chunk, as decorating it does: the chunk's randoms, the start's place among the structures. */
    private static void placeChunk(WorldGenLevel level, WorldgenContext context, Recorder recorder, Long2ObjectOpenHashMap<StructureStart> byChunk,
                                   StructureStart start, ChunkPos chunk, int index, int step) {
        recorder.random = seedengine.worldgen.ChunkSteps.regionRandom(context, chunk);
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(0L));
        long decorationSeed = random.setDecorationSeed(context.seed(), chunk.getMinBlockX(), chunk.getMinBlockZ());
        random.setFeatureSeed(decorationSeed, index, step);
        BoundingBox writable = writable(context, chunk);
        for (long key : references(byChunk, chunk)) {
            StructureStart placing = byChunk.get(key);
            recorder.own = placing == start;
            placeInChunk(level, context, placing, random, writable, chunk);
        }
    }

    private static BoundingBox writable(WorldgenContext context, ChunkPos chunk) {
        return new BoundingBox(chunk.getMinBlockX(), context.height().getMinY() + 1, chunk.getMinBlockZ(),
                chunk.getMinBlockX() + 15, context.height().getMaxY(), chunk.getMinBlockZ() + 15);
    }

    /** Above this many spider corridors in one chunk, its other orders are not tried (2 to the power of it). */
    private static final int MAX_SPIDER_CORRIDORS = 6;

    /**
     * What another order of generating the chunks would change. Only a mineshaft corridor's cave spider spawner depends
     * on it (placed in the first of its chunks decorated whose draw lands inside, and once placed no longer drawn for,
     * which shifts the rest of the chunk's random): in each chunk such a corridor reaches, every combination of placed
     * or not is placed again on its own, a corridor counting as placed only where another of its chunks can place it.
     * Positions that come out otherwise in some combination are uncertain, with what each gives; so are the spawners
     * not there in every one. Returns the uncertain spawners of the order placed.
     */
    private static java.util.Set<Spawner> otherOrders(WorldGenLevel level, WorldgenContext context, Recorder recorder,
                                                       Long2ObjectOpenHashMap<StructureStart> byChunk, StructureStart start, List<ChunkPos> chunks,
                                                       int index, int step, List<Spawner> placed, Long2ObjectOpenHashMap<java.util.Set<BlockState>> uncertain,
                                                       List<seedengine.api.StructureSpawner> possible) {
        // Only the corridors reaching another chunk too may have placed their spawner before this one is decorated.
        Map<ChunkPos, List<seedengine.mixin.MineShaftCorridorAccessor>> spidersByChunk = new java.util.LinkedHashMap<>();
        Map<ChunkPos, List<seedengine.mixin.MineShaftCorridorAccessor>> onlyHere = new java.util.HashMap<>();
        for (ChunkPos chunk : chunks) {
            BoundingBox inChunk = writable(context, chunk);
            List<seedengine.mixin.MineShaftCorridorAccessor> spiders = new ArrayList<>();
            List<seedengine.mixin.MineShaftCorridorAccessor> here = new ArrayList<>();
            for (seedengine.mixin.MineShaftCorridorAccessor spider : spiderCorridors(byChunk, chunk, inChunk)) {
                BoundingBox corridor = ((StructurePiece) spider).getBoundingBox();
                boolean inside = corridor.minX() >> 4 == chunk.x() && corridor.maxX() >> 4 == chunk.x()
                        && corridor.minZ() >> 4 == chunk.z() && corridor.maxZ() >> 4 == chunk.z();
                (inside ? here : spiders).add(spider);
            }
            if (!spiders.isEmpty() && spiders.size() <= MAX_SPIDER_CORRIDORS) {
                spidersByChunk.put(chunk, spiders);
                onlyHere.put(chunk, here);
            } else if (spiders.size() > MAX_SPIDER_CORRIDORS) {
                Constants.warnOnce("spider corridors", "{} spider corridors reach chunk {} and others: its other orders are not tried", spiders.size(), chunk);
            }
        }
        java.util.Set<Spawner> uncertainSpawners = new java.util.HashSet<>();
        if (spidersByChunk.isEmpty()) return uncertainSpawners;
        Map<seedengine.mixin.MineShaftCorridorAccessor, Boolean> afterwards = new java.util.IdentityHashMap<>();
        spidersByChunk.values().forEach(spiders -> spiders.forEach(spider -> afterwards.put(spider, spider.seedengine$hasPlacedSpider())));
        onlyHere.values().forEach(spiders -> spiders.forEach(spider -> afterwards.put(spider, spider.seedengine$hasPlacedSpider())));
        // Every combination in each chunk, and which chunks can place each corridor's spawner.
        Map<ChunkPos, List<ChunkResult>> results = new java.util.LinkedHashMap<>();
        Map<seedengine.mixin.MineShaftCorridorAccessor, java.util.Set<ChunkPos>> canPlace = new java.util.IdentityHashMap<>();
        for (Map.Entry<ChunkPos, List<seedengine.mixin.MineShaftCorridorAccessor>> entry : spidersByChunk.entrySet()) {
            List<seedengine.mixin.MineShaftCorridorAccessor> spiders = entry.getValue();
            List<ChunkResult> combinations = new ArrayList<>();
            for (int combination = 0; combination < 1 << spiders.size(); combination++) {
                for (int i = 0; i < spiders.size(); i++) spiders.get(i).seedengine$setHasPlacedSpider((combination & (1 << i)) != 0);
                onlyHere.get(entry.getKey()).forEach(spider -> spider.seedengine$setHasPlacedSpider(false));
                ChunkResult result = recorder.trial(entry.getKey(), () -> placeChunk(level, context, recorder, byChunk, start, entry.getKey(), index, step));
                java.util.Set<seedengine.mixin.MineShaftCorridorAccessor> placedNow = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                for (int i = 0; i < spiders.size(); i++) {
                    if ((combination & (1 << i)) == 0 && spiders.get(i).seedengine$hasPlacedSpider()) {
                        placedNow.add(spiders.get(i));
                        canPlace.computeIfAbsent(spiders.get(i), k -> new java.util.HashSet<>()).add(entry.getKey());
                    }
                }
                combinations.add(new ChunkResult(result.blocks(), result.spawners(), placedNow));
            }
            results.put(entry.getKey(), combinations);
        }
        afterwards.forEach(seedengine.mixin.MineShaftCorridorAccessor::seedengine$setHasPlacedSpider);
        for (Map.Entry<ChunkPos, List<ChunkResult>> entry : results.entrySet()) {
            ChunkPos chunk = entry.getKey();
            List<seedengine.mixin.MineShaftCorridorAccessor> spiders = spidersByChunk.get(chunk);
            List<ChunkResult> feasible = new ArrayList<>();
            for (int combination = 0; combination < entry.getValue().size(); combination++) {
                boolean possibleOrder = true;
                for (int i = 0; i < spiders.size(); i++) {
                    if ((combination & (1 << i)) == 0) continue;
                    java.util.Set<ChunkPos> elsewhere = new java.util.HashSet<>(canPlace.getOrDefault(spiders.get(i), java.util.Set.of()));
                    elsewhere.remove(chunk);
                    if (elsewhere.isEmpty()) possibleOrder = false;
                }
                if (possibleOrder) feasible.add(entry.getValue().get(combination));
            }
            BoundingBox inChunk = writable(context, chunk);
            // The blocks of this chunk: the order placed, against each possible combination.
            it.unimi.dsi.fastutil.longs.LongOpenHashSet positions = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
            recorder.placed.keySet().forEach(key -> {
                if (inChunk.isInside(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key))) positions.add(key);
            });
            feasible.forEach(result -> positions.addAll(result.blocks().keySet()));
            for (long key : positions) {
                java.util.Set<BlockState> states = new java.util.LinkedHashSet<>();
                states.add(recorder.placed.containsKey(key) ? recorder.placed.get(key) : recorder.unplaced(key));
                for (ChunkResult result : feasible) {
                    states.add(result.blocks().containsKey(key) ? result.blocks().get(key) : recorder.unplaced(key));
                }
                if (states.size() > 1) uncertain.put(key, java.util.Set.copyOf(states));
            }
            // The spawners of this chunk: certain if every possible combination places them.
            java.util.Set<Spawner> all = new java.util.LinkedHashSet<>();
            for (Spawner spawner : placed) {
                if (inChunk.isInside(spawner.pos())) all.add(spawner);
            }
            feasible.forEach(result -> all.addAll(result.spawners()));
            for (Spawner spawner : all) {
                boolean everywhere = feasible.stream().allMatch(result -> result.spawners().contains(spawner));
                if (everywhere && placed.contains(spawner)) continue;
                uncertainSpawners.add(spawner);
                possible.add(new seedengine.api.StructureSpawner(spawner, false));
            }
        }
        return uncertainSpawners;
    }

    /** The cave spider corridors of the starts reaching a chunk that reach into it. */
    private static List<seedengine.mixin.MineShaftCorridorAccessor> spiderCorridors(Long2ObjectOpenHashMap<StructureStart> byChunk, ChunkPos chunk,
                                                                                   BoundingBox writable) {
        List<seedengine.mixin.MineShaftCorridorAccessor> spiders = new ArrayList<>();
        for (long key : references(byChunk, chunk)) {
            for (StructurePiece piece : byChunk.get(key).getPieces()) {
                if (piece instanceof seedengine.mixin.MineShaftCorridorAccessor corridor && corridor.seedengine$spiderCorridor()
                        && piece.getBoundingBox().intersects(writable)) {
                    spiders.add(corridor);
                }
            }
        }
        return spiders;
    }

    /** A block of a stand-in level whose shape is fixed by its neighbours once the structure is placed. */
    public static void markForPostProcessing(Object level, BlockPos pos) {
        if (Proxy.getInvocationHandler(level) instanceof Recorder recorder && recorder.own) recorder.toPostProcess.add(pos.asLong());
    }

    /**
     * The starts that reach a chunk, in the order vanilla places them: the chunk's reference set, filled the way
     * {@code ChunkGenerator.createReferences} fills it.
     */
    public static it.unimi.dsi.fastutil.longs.LongOpenHashSet references(Long2ObjectOpenHashMap<StructureStart> byChunk, ChunkPos chunk) {
        it.unimi.dsi.fastutil.longs.LongOpenHashSet references = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        for (int x = chunk.x() - 8; x <= chunk.x() + 8; x++) {
            for (int z = chunk.z() - 8; z <= chunk.z() + 8; z++) {
                long key = ChunkPos.pack(x, z);
                StructureStart other = byChunk.get(key);
                if (other != null && other.getBoundingBox().intersects(minX, minZ, minX + 15, minZ + 15)) references.add(key);
            }
        }
        return references;
    }

    /**
     * A fresh copy of a start, through the tag the world saves it with. Pieces keep what they placed (a corridor's
     * spawner, a stronghold's portal room spawner) and would leave it out the next time the same start is placed.
     */
    public static StructureStart copy(WorldgenContext context, StructureStart start) {
        return tryCopy(context, start).orElse(start);
    }

    /** A copy of a start through its saved form; empty (logged) if it cannot be copied. */
    public static java.util.Optional<StructureStart> tryCopy(WorldgenContext context, StructureStart start) {
        try {
            StructurePieceSerializationContext serialization = new StructurePieceSerializationContext(ResourceManager.Empty.INSTANCE,
                    context.registries(), context.templates());
            StructureStart copy = StructureStart.loadStaticStart(serialization, start.createTag(serialization, start.getChunkPos()), context.seed());
            if (copy != null && copy.isValid()) return java.util.Optional.of(copy);
            Constants.warnOnce("copy " + structureName(context, start), "Could not copy {} at chunk {}: not valid once copied",
                    structureName(context, start), start.getChunkPos());
        } catch (RuntimeException e) {
            Constants.warnOnce("copy " + structureName(context, start), "Could not copy {} at chunk {}: {}",
                    structureName(context, start), start.getChunkPos(), e.toString());
        }
        return java.util.Optional.empty();
    }

    /**
     * {@link StructureStart#placeInChunk}, piece by piece: a piece that fails in the stand-in (spawning a mob from a
     * data marker, for example) does not stop the pieces after it.
     */
    public static void placeInChunk(WorldGenLevel level, WorldgenContext context, StructureStart start, WorldgenRandom random,
                                     BoundingBox writable, ChunkPos chunk) {
        List<StructurePiece> pieces = start.getPieces();
        if (pieces.isEmpty()) return;
        BoundingBox centerBox = pieces.get(0).getBoundingBox();
        BlockPos center = centerBox.getCenter();
        BlockPos reference = new BlockPos(center.getX(), centerBox.minY(), center.getZ());
        for (StructurePiece piece : pieces) {
            if (!piece.getBoundingBox().intersects(writable)) continue;
            try {
                piece.postProcess(level, null, context.generator(), random, writable, chunk, reference);
            } catch (RuntimeException | LinkageError e) {
                Constants.warnOnce("piece " + piece.getClass().getName(), "Could not place a piece of {} in chunk {}, its blocks may lack it: {}",
                        structureName(context, start), chunk, e.toString());
            }
        }
        try {
            start.getStructure().afterPlace(level, null, context.generator(), random, writable, chunk, new PiecesContainer(pieces));
        } catch (RuntimeException | LinkageError e) {
            Constants.warnOnce("after place " + structureName(context, start), "Could not finish {} in chunk {}: {}", structureName(context, start), chunk, e.toString());
        }
    }

    /** A structure's id for logs (its object prints all it holds). */
    private static String structureName(WorldgenContext context, StructureStart start) {
        return context.registries().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).listElements()
                .filter(reference -> reference.value() == start.getStructure()).findFirst()
                .map(reference -> reference.key().identifier().toString()).orElse(start.getStructure().getClass().getSimpleName());
    }

    /** Whether a level is the stand-in used for capturing (for the CappedProcessor mixin). */
    public static boolean isGhost(Object level) {
        return level != null && Proxy.isProxyClass(level.getClass()) && Proxy.getInvocationHandler(level) instanceof StandIn;
    }

    /** The generation a stand-in level stands for. */
    public static WorldgenContext contextOf(Object level) {
        return ((StandIn) Proxy.getInvocationHandler(level)).context();
    }

    /** The seed of a stand-in level. */
    public static long seedOf(Object level) {
        return ((StandIn) Proxy.getInvocationHandler(level)).context().seed();
    }

    /** What answers for a stand-in level: this capture's, or the one features are placed in (DecorationLevel). */
    public interface StandIn extends InvocationHandler {
        WorldgenContext context();
    }

    /** The structure's position among the structures of its step, as {@code applyBiomeDecoration} counts. */
    private static int structureIndex(WorldgenContext context, Structure structure) {
        int index = 0;
        for (Structure other : context.registries().lookupOrThrow(Registries.STRUCTURE)) {
            if (other.step() != structure.step()) continue;
            if (other == structure) return index;
            index++;
        }
        return 0;
    }

    private static final class Recorder implements StandIn {
        private final WorldgenContext context;
        /** What this start set (the result). */
        private final Long2ObjectOpenHashMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
        /** What every start placed so far set, which the structure sees. */
        private final Long2ObjectOpenHashMap<BlockState> world = new Long2ObjectOpenHashMap<>();
        /** Whether the start being placed is the one captured. */
        private boolean own = true;
        private final it.unimi.dsi.fastutil.longs.LongOpenHashSet ownBlockEntities = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();
        /** Block entities handed out to the structure (it writes spawner mobs and template data into them). */
        private final Map<Long, BlockEntity> blockEntities = new HashMap<>();
        private net.minecraft.world.level.biome.BiomeManager biomes;

        private net.minecraft.world.level.biome.BiomeManager biomes() {
            if (biomes == null) biomes = seedengine.worldgen.ChunkSteps.biomeManager(context);
            return biomes;
        }

        /**
         * The final heightmaps as blocks are placed (as {@code ProtoChunk} keeps them during decoration, each counting
         * its own blocks: the ocean floor's not water), by type and column, where they changed. The world-generation
         * ones stay the terrain's.
         */
        private final Map<Heightmap.Types, Long2IntOpenHashMap> placedHeights = new java.util.EnumMap<>(Heightmap.Types.class);
        private final Map<String, Boolean> unknown = new HashMap<>();
        /** The region's random of the chunk being placed in ({@code level.getRandom()}). */
        private RandomSource random = RandomSource.create(0L);
        private WorldGenLevel proxy;
        /** The blocks marked for post-processing, in the order marked. */
        private final it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet toPostProcess = new it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet();
        /** The terrain the structure is placed on (noise, surface, carvers), where nothing was placed. */
        private final seedengine.worldgen.TerrainChunks terrain;
        /** The chunk being placed in (packed), or {@code Long.MIN_VALUE}. */
        private long placing = Long.MIN_VALUE;
        /** By chunk, the positions placing in it set: a trial of the chunk does not see them. */
        private final Long2ObjectOpenHashMap<it.unimi.dsi.fastutil.longs.LongOpenHashSet> setIn = new Long2ObjectOpenHashMap<>();
        /** A chunk placed again on its own, apart from the rest (see {@link #trial}), or null. */
        private Trial trial;

        /** What a chunk placed again sets, over what the rest of the chunks set. */
        private static final class Trial {
            final it.unimi.dsi.fastutil.longs.LongOpenHashSet hidden;
            final Long2ObjectOpenHashMap<BlockState> world = new Long2ObjectOpenHashMap<>();
            final Long2ObjectOpenHashMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
            final Map<Long, BlockEntity> blockEntities = new HashMap<>();
            final it.unimi.dsi.fastutil.longs.LongOpenHashSet ownBlockEntities = new it.unimi.dsi.fastutil.longs.LongOpenHashSet();

            Trial(it.unimi.dsi.fastutil.longs.LongOpenHashSet hidden) {
                this.hidden = hidden;
            }
        }

        /**
         * Places a chunk again on its own: what it set the first time is not seen (as if not yet decorated), and nothing
         * it sets now stays. Returns what the captured start set in it.
         */
        ChunkResult trial(ChunkPos chunk, Runnable placing) {
            trial = new Trial(setIn.getOrDefault(chunk.pack(), new it.unimi.dsi.fastutil.longs.LongOpenHashSet()));
            try {
                placing.run();
                return new ChunkResult(trial.placed, java.util.Set.copyOf(spawners(trial.blockEntities, trial.ownBlockEntities, trial.placed)), java.util.Set.of());
            } finally {
                trial = null;
            }
        }

        /** What is at a position where the captured start sets nothing: what the others set, or the terrain. */
        BlockState unplaced(long key) {
            BlockState state = world.get(key);
            if (state != null && !placed.containsKey(key)) return state;
            BlockPos pos = BlockPos.of(key);
            return terrain.terrain(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).getBlockState(pos);
        }

        private void set(BlockPos pos, BlockState state) {
            long key = pos.asLong();
            if (trial != null) {
                trial.world.put(key, state);
                if (own) trial.placed.put(key, state);
                return;
            }
            world.put(key, state);
            if (own) placed.put(key, state);
            if (placing != Long.MIN_VALUE) setIn.computeIfAbsent(placing, k -> new it.unimi.dsi.fastutil.longs.LongOpenHashSet()).add(key);
            for (Heightmap.Types type : seedengine.worldgen.ChunkSteps.DECORATION_HEIGHTMAPS) update(type, pos, state);
        }

        @Override
        public WorldgenContext context() {
            return context;
        }

        Recorder(WorldgenContext context, seedengine.worldgen.TerrainChunks terrain) {
            this.context = context;
            this.terrain = terrain;
        }

        WorldGenLevel level() {
            if (proxy == null) {
                proxy = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, this);
            }
            return proxy;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Object invoke(Object self, Method method, Object[] args) throws Throwable {
            switch (method.getName()) {
                case "setBlock":
                    set((BlockPos) args[0], (BlockState) args[1]);
                    return true;
                case "removeBlock":
                case "destroyBlock":
                    set((BlockPos) args[0], AIR);
                    return true;
                case "getBlockState":
                    return stateAt((BlockPos) args[0]);
                case "getFluidState":
                    return stateAt((BlockPos) args[0]).getFluidState();
                case "isStateAtPosition":
                    return ((Predicate<BlockState>) args[1]).test(stateAt((BlockPos) args[0]));
                case "isFluidAtPosition":
                    return ((Predicate<Object>) args[1]).test(stateAt((BlockPos) args[0]).getFluidState());
                case "getHeight":
                    if (args == null || args.length == 0) return context.height().getHeight();
                    if (args.length == 3 && args[0] instanceof Heightmap.Types type) return height(type, (Integer) args[1], (Integer) args[2]);
                    break;
                case "getBiome":
                    // As the world's generation region reads them: zoomed from the cells (a mineshaft piece in the deep
                    // dark is left out by the biome at its middle).
                    return biomes().getBiome((BlockPos) args[0]);
                case "getBiomeManager":
                    return biomes();
                case "getMinY":
                    return context.height().getMinY();
                case "getBlockEntity":
                    // The one by type (an Optional) is the game's default, which asks this one.
                    if (args.length == 1) return blockEntityAt((BlockPos) args[0]);
                    break;
                case "getSeed":
                    return context.seed();
                case "getRandom":
                    return random;
                case "registryAccess":
                    return context.registries();
                case "environmentAttributes":
                    return context.environmentAttributes();
                case "enabledFeatures":
                    return FeatureFlags.DEFAULT_FLAGS;
                case "getSeaLevel":
                    return context.generator().getSeaLevel();
                case "scheduleTick":
                    return null;
                case "ensureCanWrite":
                    return true;
                case "hashCode":
                    return System.identityHashCode(self);
                case "equals":
                    return self == args[0];
                case "toString":
                    return "Seed Engine ghost level";
                default:
                    break;
            }
            if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, args);
            if (unknown.putIfAbsent(method.getName(), Boolean.TRUE) == null) {
                Constants.LOG.debug("Ghost level does not answer {}", method.getName());
            }
            return defaultValue(method.getReturnType());
        }

        /**
         * As {@code LevelChunk.postProcessGeneration} when the chunk is finished: the marked blocks take the shape their
         * neighbours give them (a fence joins the wall next to it).
         */
        void postProcess() {
            own = true;
            WorldGenLevel level = level();
            for (long key : toPostProcess) {
                BlockState state = placed.get(key);
                if (state == null || state.getBlock() instanceof net.minecraft.world.level.block.LiquidBlock) continue;
                BlockPos pos = BlockPos.of(key);
                BlockState shaped = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(state, level, pos);
                if (shaped != state) level.setBlock(pos, shaped, 20);
            }
        }

        /** A real block entity for a block placed so far that has one, made once and kept. */
        private BlockEntity blockEntityAt(BlockPos pos) {
            long key = pos.asLong();
            Map<Long, BlockEntity> entities = trial != null ? trial.blockEntities : blockEntities;
            BlockEntity existing = entities.get(key);
            BlockState state = trial != null && trial.world.containsKey(key) ? trial.world.get(key)
                    : trial != null && trial.hidden.contains(key) ? null : world.get(key);
            if (state == null || !(state.getBlock() instanceof EntityBlock block)) return null;
            if (own) (trial != null ? trial.ownBlockEntities : ownBlockEntities).add(key);
            if (existing != null && existing.getBlockState().getBlock() == state.getBlock()) return existing;
            BlockEntity made = block.newBlockEntity(pos.immutable(), state);
            if (made != null) entities.put(key, made);
            return made;
        }

        /** The spawners among the block entities whose block is still there. */
        private List<Spawner> spawners(Map<Long, BlockEntity> blockEntities, it.unimi.dsi.fastutil.longs.LongOpenHashSet ownBlockEntities,
                                       Long2ObjectOpenHashMap<BlockState> placed) {
            List<Spawner> spawners = new ArrayList<>();
            blockEntities.forEach((key, blockEntity) -> {
                if (!ownBlockEntities.contains(key.longValue())) return;
                BlockState state = placed.get(key.longValue());
                if (state == null || state.getBlock() != blockEntity.getBlockState().getBlock()) return;
                try {
                    Spawner.of(blockEntity, context.registries()).ifPresent(spawners::add);
                } catch (RuntimeException e) {
                    Constants.LOG.debug("Could not read the spawner at {}: {}", BlockPos.of(key), e.toString());
                }
            });
            spawners.sort(java.util.Comparator.comparingLong(spawner -> spawner.pos().asLong()));
            return List.copyOf(spawners);
        }

        private BlockState stateAt(BlockPos pos) {
            long key = pos.asLong();
            BlockState state = null;
            if (trial != null) {
                state = trial.world.get(key);
                if (state == null && !trial.hidden.contains(key)) state = world.get(key);
            } else {
                state = world.get(key);
            }
            if (state != null) return state;
            if (context.height().isOutsideBuildHeight(pos.getY())) return AIR;
            return terrain.terrain(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).getBlockState(pos);
        }

        /** The heightmap value: the terrain's (bent around the structures), as the blocks placed so far changed it. */
        private int height(Heightmap.Types type, int x, int z) {
            Long2IntOpenHashMap changed = placedHeights.get(type);
            long column = ChunkPos.pack(x, z);
            if (changed != null && changed.containsKey(column)) return changed.get(column);
            return terrain.terrain(new ChunkPos(x >> 4, z >> 4)).getHeight(type, x & 15, z & 15) + 1;
        }

        /** As {@code Heightmap.update}: a block the heightmap counts above the top raises it, the top block gone lowers it. */
        private void update(Heightmap.Types type, BlockPos pos, BlockState state) {
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            int top = height(type, x, z);
            int now;
            if (type.isOpaque().test(state)) {
                if (y + 1 <= top) return;
                now = y + 1;
            } else {
                if (y + 1 != top) return;
                now = context.height().getMinY();
                for (int below = y - 1; below >= context.height().getMinY(); below--) {
                    if (type.isOpaque().test(stateAt(new BlockPos(x, below, z)))) {
                        now = below + 1;
                        break;
                    }
                }
            }
            placedHeights.computeIfAbsent(type, t -> new Long2IntOpenHashMap()).put(ChunkPos.pack(x, z), now);
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == void.class) return null;
            if (type == long.class) return 0L;
            if (type == float.class) return 0f;
            if (type == double.class) return 0d;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            return 0;
        }
    }
}
