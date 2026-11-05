package seedengine.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import seedengine.Constants;
import seedengine.api.BiomeArea;
import seedengine.api.Dungeon;
import seedengine.api.FoundBiome;
import seedengine.api.FoundPiece;
import seedengine.api.FoundStructure;
import seedengine.api.Spawner;
import seedengine.api.StructureCandidate;
import seedengine.api.StructureSpawnArea;
import seedengine.api.StructureSpawner;
import seedengine.feature.DungeonWorld;
import seedengine.locate.Candidate;
import seedengine.locate.CandidateScanner;
import seedengine.locate.Located;
import seedengine.locate.StructureEvaluator;
import seedengine.mixin.SinglePoolElementAccessor;
import seedengine.mixin.TemplateStructurePieceInvoker;
import seedengine.shape.GhostCapture;
import seedengine.worldgen.GeneratedBiome;
import seedengine.worldgen.TerrainChunks;
import seedengine.worldgen.WorldgenContext;

/**
 * One dimension of one world, answering what its generation places, and keeping what it worked out for the next
 * questions: the structures (the most recently used, and where those that decide their height as they are placed
 * go), the blocks of the last few structures, and the decorated chunks for dungeons. Thread-safe; the work runs on the
 * calling thread.
 */
public final class World {
    private static final int KEEP_STRUCTURES = 20_000;
    private static final int KEEP_CAPTURES = 8;
    /** Past this many decorated chunks the placed blocks are dropped and dungeons are worked out anew. */
    private static final int MAX_DECORATED = 6000;
    /** The largest area (in chunks) one question about dungeons may cover: 64x64. */
    public static final int MAX_DUNGEON_AREA = 64 * 64;
    /** How many worlds keep their decorated chunks at once (across worlds), the most recently asked about dungeons. */
    private static final int KEEP_DUNGEON_WORLDS = 3;
    private static final Map<World, Boolean> DUNGEON_WORLDS = new LinkedHashMap<>(8, 0.75f, true);
    /** How far (in chunks) a structure reaches from the chunk it starts in. */
    private static final int STRUCTURE_REACH = 8;
    private static final int KEEP_NEAREST_BIOMES = 256;
    private static final int KEEP_BIOME_AREAS = 4;
    /** What {@link #biomeArea} tells for a biome without an id. */
    private static final Identifier UNNAMED_BIOME = Identifier.fromNamespaceAndPath(Constants.MOD_ID, "unnamed");

    private record Key(Identifier set, ChunkPos chunk) {
    }

    /** Where a structure's pieces are once placed, and the box around them. */
    private record Placed(BoundingBox box, List<BoundingBox> pieces) {
    }

    private record NearestBiomeKey(Set<Identifier> biomes, BlockPos origin, int radius, int horizontalStep, int verticalStep) {
    }

    private record BiomeAreaKey(int x1, int z1, int x2, int z2, int y, int step) {
    }

    private final WorldgenContext context;
    private final Map<Key, Optional<Located>> structures = lru(KEEP_STRUCTURES);
    private final Map<Key, Placed> placed = lru(KEEP_STRUCTURES);
    private final Map<Key, GhostCapture.Captured> captures = lru(KEEP_CAPTURES);
    private final Map<NearestBiomeKey, Optional<FoundBiome>> nearestBiomes = lru(KEEP_NEAREST_BIOMES);
    private final Map<BiomeAreaKey, BiomeArea> biomeAreas = lru(KEEP_BIOME_AREAS);
    private final java.util.concurrent.locks.ReentrantLock dungeonLock = new java.util.concurrent.locks.ReentrantLock();
    /** Set when the decorated chunks are to be dropped while a question about dungeons holds them: dropped once it ends. */
    private volatile boolean dropDungeons;
    private DungeonWorld dungeonWorld;
    private final Set<Key> dungeonStarts = new HashSet<>();
    private volatile Map<Identifier, List<Holder<StructureSet>>> earlierSharing;

    /** Whether the decorated chunks are kept for the next question (not for a server that stopped). */
    private volatile boolean keepsDungeons;

    public World(WorldgenContext context) {
        this(context, true);
    }

    World(WorldgenContext context, boolean keepsDungeons) {
        this.context = context;
        this.keepsDungeons = keepsDungeons;
    }

    public WorldgenContext context() {
        return context;
    }

    public List<StructureCandidate> candidates(ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        List<StructureCandidate> found = new ArrayList<>();
        for (Candidate candidate : scan(from, to, sets)) {
            found.add(new StructureCandidate(idOf(candidate.set()), candidate.chunk()));
        }
        return found;
    }

    public List<FoundStructure> structures(ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        List<FoundStructure> found = new ArrayList<>();
        for (Located located : located(from, to, sets)) {
            found.add(new FoundStructure(located.id(), located.set(), located.chunk(), copy(placed(located).box())));
        }
        return found;
    }

    public List<StructureSpawnArea> spawnAreas(ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        List<StructureSpawnArea> areas = new ArrayList<>();
        for (Located located : located(from, to, sets)) {
            Map<MobCategory, StructureSpawnOverride> overrides = located.structure().value().spawnOverrides();
            if (overrides.isEmpty()) continue;
            Placed placed = placed(located);
            FoundStructure structure = new FoundStructure(located.id(), located.set(), located.chunk(), copy(placed.box()));
            // By category in the enum's order, so the answer does not depend on the map's.
            for (MobCategory category : MobCategory.values()) {
                StructureSpawnOverride override = overrides.get(category);
                if (override == null) continue;
                // As ChunkGenerator.getMobsAt checks it: inside a piece, or inside the box around all of the structure.
                boolean pieces = override.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE;
                List<BoundingBox> boxes = pieces ? placed.pieces().stream().map(World::copy).toList() : List.of(copy(placed.box()));
                List<StructureSpawnArea.Mob> mobs = override.spawns().unwrap().stream()
                        .map(mob -> new StructureSpawnArea.Mob(BuiltInRegistries.ENTITY_TYPE.getKey(mob.value().type()), mob.weight(),
                                mob.value().minCount(), mob.value().maxCount()))
                        .toList();
                areas.add(new StructureSpawnArea(structure, category, pieces, boxes, mobs));
            }
        }
        return areas;
    }

    public List<BoundingBox> pieces(FoundStructure structure) {
        return located(structure).map(located -> placed(located).pieces().stream().map(World::copy).toList()).orElse(List.of());
    }

    public List<FoundPiece> namedPieces(FoundStructure structure) {
        return located(structure).map(located -> {
            List<StructurePiece> pieces = located.start().getPieces();
            List<BoundingBox> boxes = placed(located).pieces();
            List<FoundPiece> named = new ArrayList<>(pieces.size());
            for (int i = 0; i < pieces.size(); i++) {
                StructurePiece piece = pieces.get(i);
                Identifier type = BuiltInRegistries.STRUCTURE_PIECE.getKey(piece.getType());
                named.add(new FoundPiece(type != null ? type : Identifier.fromNamespaceAndPath(Constants.MOD_ID, "unnamed"), template(piece),
                        copy(boxes.get(i))));
            }
            return List.copyOf(named);
        }).orElse(List.of());
    }

    private static Optional<Identifier> template(StructurePiece piece) {
        if (piece instanceof TemplateStructurePiece templated) return Optional.of(((TemplateStructurePieceInvoker) templated).seedengine$templateLocation());
        if (piece instanceof PoolElementStructurePiece pooled && pooled.getElement() instanceof SinglePoolElement single) {
            return ((SinglePoolElementAccessor) single).seedengine$template().left();
        }
        return Optional.empty();
    }

    public Map<BlockPos, BlockState> blocks(FoundStructure structure) {
        Optional<GhostCapture.Captured> captured = captured(structure);
        if (captured.isEmpty()) return Map.of();
        Map<BlockPos, BlockState> blocks = new HashMap<>(captured.get().blocks().size() * 2);
        captured.get().blocks().long2ObjectEntrySet().forEach(entry -> blocks.put(BlockPos.of(entry.getLongKey()), entry.getValue()));
        return blocks;
    }

    public List<Spawner> spawners(FoundStructure structure) {
        return captured(structure).map(GhostCapture.Captured::spawners).orElse(List.of());
    }

    public Map<BlockPos, Set<BlockState>> uncertainBlocks(FoundStructure structure) {
        Optional<GhostCapture.Captured> captured = captured(structure);
        if (captured.isEmpty()) return Map.of();
        Map<BlockPos, Set<BlockState>> blocks = new HashMap<>();
        captured.get().uncertain().long2ObjectEntrySet().forEach(entry -> blocks.put(BlockPos.of(entry.getLongKey()), entry.getValue()));
        return blocks;
    }

    public List<StructureSpawner> possibleSpawners(FoundStructure structure) {
        return captured(structure).map(GhostCapture.Captured::possibleSpawners).orElse(List.of());
    }

    public List<Dungeon> dungeons(ChunkPos from, ChunkPos to) {
        ChunkSquare area = ChunkSquare.of(from, to);
        long chunks = ((long) area.maxX() - area.minX() + 1) * ((long) area.maxZ() - area.minZ() + 1);
        if (chunks > MAX_DUNGEON_AREA) {
            throw new IllegalArgumentException("An area of " + chunks + " chunks for dungeons, more than " + MAX_DUNGEON_AREA + ": ask in smaller squares");
        }
        dungeonLock.lock();
        try {
            if (dungeonWorld == null || dungeonWorld.decoratedCount() > MAX_DECORATED) {
                dungeonWorld = new DungeonWorld(new TerrainChunks(context, List.of()), List.of());
                dungeonStarts.clear();
            }
            // A dimension without dungeons decorates nothing: it takes none of the places kept below.
            if (!dungeonWorld.hasDungeons()) return List.of();
            // The decorated chunks are the largest of what is kept: only the last few worlds asked keep theirs.
            List<World> older = new ArrayList<>();
            synchronized (DUNGEON_WORLDS) {
                // Checked here, where forgetting it takes it out: a world forgotten is not put back.
                if (keepsDungeons) DUNGEON_WORLDS.put(this, Boolean.TRUE);
                var iterator = DUNGEON_WORLDS.keySet().iterator();
                while (DUNGEON_WORLDS.size() > KEEP_DUNGEON_WORLDS) {
                    older.add(iterator.next());
                    iterator.remove();
                }
            }
            older.forEach(World::dropDungeons);
            if (!keepsDungeons) dropDungeons = true;
            try {
                return dungeons(area);
            } catch (RuntimeException | Error e) {
                // Chunks may be marked decorated (or their terrain built) that were not: not kept for the next question.
                dungeonWorld = null;
                dungeonStarts.clear();
                throw e;
            }
        } finally {
            dropIfAsked();
            dungeonLock.unlock();
            // A drop asked between the line above and the unlock found the lock held: done now.
            if (dropDungeons && dungeonLock.tryLock()) {
                try {
                    dropIfAsked();
                } finally {
                    dungeonLock.unlock();
                }
            }
        }
    }

    /** Drops the decorated chunks if that was asked. Holding the dungeon lock. */
    private void dropIfAsked() {
        if (dropDungeons) {
            dropDungeons = false;
            dungeonWorld = null;
            dungeonStarts.clear();
        }
    }

    /** The dungeons of an area, with the dungeon world ready. Holding the dungeon lock. */
    private List<Dungeon> dungeons(ChunkSquare area) {
        // The structures reaching the area come first: they bend the terrain and are placed before dungeons.
        List<StructureStart> starts = new ArrayList<>();
        // The terrain of the chunks next to the area is built too (decorating a chunk reads its neighbours): one more.
        int reach = STRUCTURE_REACH + 1;
        ChunkPos reachFrom = new ChunkPos(area.minX() - reach, area.minZ() - reach);
        ChunkPos reachTo = new ChunkPos(area.maxX() + reach, area.maxZ() + reach);
        for (Located located : located(reachFrom, reachTo, set -> true)) {
            if (dungeonStarts.add(new Key(located.set(), located.chunk()))) starts.add(own(located));
        }
        dungeonWorld.addStarts(starts);
        // The world decorates chunks in the order they were generated; nearest the middle first is the likeliest.
        List<ChunkPos> chunks = area.nearestMiddleFirst();
        for (ChunkPos chunk : chunks) {
            if (!dungeonWorld.isDecorated(chunk)) dungeonWorld.decorate(chunk);
        }
        List<Dungeon> dungeons = new ArrayList<>();
        for (ChunkPos chunk : chunks) dungeons.addAll(dungeonWorld.dungeons(chunk));
        // A large area may have decorated past the limit in one go: not kept for the next question then.
        if (dungeonWorld.decoratedCount() > MAX_DECORATED) {
            dungeonWorld = null;
            dungeonStarts.clear();
        }
        return dungeons;
    }

    /** No longer kept (its server stopped): what it decorated is dropped, now or once the question running ends. */
    void forgotten() {
        synchronized (DUNGEON_WORLDS) {
            keepsDungeons = false;
        }
        dropDungeons();
    }

    /**
     * Drops the decorated chunks; dungeons are worked out anew next time. Without waiting: while a question about this
     * world's dungeons runs, they are dropped once it ends.
     */
    void dropDungeons() {
        synchronized (DUNGEON_WORLDS) {
            DUNGEON_WORLDS.remove(this);
        }
        dropDungeons = true;
        if (dungeonLock.tryLock()) {
            try {
                dropIfAsked();
            } finally {
                dungeonLock.unlock();
            }
        }
    }

    public int height(Heightmap.Types type, int x, int z) {
        return context.generator().getBaseHeight(x, z, type, context.height(), context.randomState());
    }

    public Optional<Identifier> biome(int x, int y, int z) {
        return GeneratedBiome.at(context, x, y, z).unwrapKey().map(ResourceKey::identifier);
    }

    public Optional<FoundBiome> nearestBiome(Set<Identifier> biomes, BlockPos origin, int radius, int horizontalStep, int verticalStep) {
        BiomeGrid.checkSearch(radius, horizontalStep, verticalStep);
        NearestBiomeKey key = new NearestBiomeKey(Set.copyOf(biomes), origin.immutable(), radius, horizontalStep, verticalStep);
        synchronized (nearestBiomes) {
            Optional<FoundBiome> known = nearestBiomes.get(key);
            if (known != null) return known;
        }
        var found = GeneratedBiome.closest(context, key.origin(), radius, horizontalStep, verticalStep,
                biome -> biome.unwrapKey().filter(id -> key.biomes().contains(id.identifier())).isPresent());
        Optional<FoundBiome> nearest = found == null ? Optional.empty()
                : Optional.of(new FoundBiome(found.getSecond().unwrapKey().orElseThrow().identifier(), found.getFirst().immutable()));
        synchronized (nearestBiomes) {
            nearestBiomes.put(key, nearest);
        }
        return nearest;
    }

    public BiomeArea biomeArea(int x1, int z1, int x2, int z2, int y, int step) {
        BiomeAreaKey key = new BiomeAreaKey(x1, z1, x2, z2, y, step);
        synchronized (biomeAreas) {
            BiomeArea known = biomeAreas.get(key);
            if (known != null) return known;
        }
        GeneratedBiome.Sampler sampler = GeneratedBiome.sampler(context);
        BiomeArea area = BiomeGrid.sample(x1, z1, x2, z2, y, step, (x, z) -> sampler.at(x, y, z).unwrapKey().map(ResourceKey::identifier)
                .orElse(UNNAMED_BIOME));
        synchronized (biomeAreas) {
            biomeAreas.put(key, area);
        }
        return area;
    }

    public Set<Identifier> biomes() {
        Set<Identifier> biomes = new HashSet<>();
        context.generator().getBiomeSource().possibleBiomes().forEach(biome -> biome.unwrapKey().ifPresent(key -> biomes.add(key.identifier())));
        return Set.copyOf(biomes);
    }

    public Set<Identifier> structureSets() {
        Set<Identifier> sets = new HashSet<>();
        context.registries().lookupOrThrow(Registries.STRUCTURE_SET).listElementIds().forEach(key -> sets.add(key.identifier()));
        return Set.copyOf(sets);
    }

    private List<Candidate> scan(ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        ChunkSquare area = ChunkSquare.of(from, to);
        List<Candidate> candidates = CandidateScanner.scan(context, area.minX(), area.minZ(), area.maxX(), area.maxZ());
        candidates.removeIf(candidate -> !sets.test(idOf(candidate.set())));
        return candidates;
    }

    private List<Located> located(ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        List<Located> found = new ArrayList<>();
        for (Candidate candidate : scan(from, to, sets)) {
            located(candidate).ifPresent(found::add);
        }
        return found;
    }

    /** Whether the candidate's structure generates, worked out once. A candidate that cannot be worked out counts as none. */
    private Optional<Located> located(Candidate candidate) {
        Key key = new Key(idOf(candidate.set()), candidate.chunk());
        synchronized (structures) {
            Optional<Located> known = structures.get(key);
            if (known != null) return known;
        }
        Optional<Located> located;
        try {
            located = startedByEarlierSet(candidate) ? Optional.empty() : StructureEvaluator.evaluate(context, candidate);
        } catch (RuntimeException e) {
            // A broken data pack structure, say: the others are still answered.
            // Kept as none too: asked again, it would fail (and be logged) again.
            Constants.LOG.warn("Could not work out {} at chunk {} in {}: {}", key.set(), key.chunk(), context.dimension().identifier(), e.toString());
            located = Optional.empty();
        }
        synchronized (structures) {
            Optional<Located> raced = structures.putIfAbsent(key, located);
            return raced != null ? raced : located;
        }
    }

    /**
     * Whether a set before the candidate's (in the order world generation goes through them) started, in the same chunk,
     * a structure the candidate's set has too: a chunk holds one start of a structure, so world generation skips the
     * later set there. Only data packs and mods have sets sharing structures.
     */
    private boolean startedByEarlierSet(Candidate candidate) {
        ChunkPos chunk = candidate.chunk();
        for (Holder<StructureSet> earlier : earlierSharing().getOrDefault(idOf(candidate.set()), List.of())) {
            if (!earlier.value().placement().isStructureChunk(context.structureState(), chunk.x(), chunk.z())) continue;
            Optional<Located> started = located(new Candidate(earlier, chunk));
            if (started.isPresent() && candidate.set().value().structures().stream()
                    .anyMatch(entry -> entry.structure().value() == started.get().structure().value())) {
                return true;
            }
        }
        return false;
    }

    /** By structure set, the sets before it that share a structure with it; worked out once. */
    private Map<Identifier, List<Holder<StructureSet>>> earlierSharing() {
        Map<Identifier, List<Holder<StructureSet>>> known = earlierSharing;
        if (known != null) return known;
        List<Holder<StructureSet>> order = context.structureState().possibleStructureSets();
        Map<Identifier, List<Holder<StructureSet>>> sharing = new HashMap<>();
        for (int i = 0; i < order.size(); i++) {
            Set<Object> structuresOfThis = new HashSet<>();
            order.get(i).value().structures().forEach(entry -> structuresOfThis.add(entry.structure().value()));
            for (int j = 0; j < i; j++) {
                if (order.get(j).value().structures().stream().anyMatch(entry -> structuresOfThis.contains(entry.structure().value()))) {
                    sharing.computeIfAbsent(idOf(order.get(i)), id -> new ArrayList<>()).add(order.get(j));
                }
            }
        }
        earlierSharing = Map.copyOf(sharing);
        return earlierSharing;
    }

    private Optional<Located> located(FoundStructure structure) {
        ResourceKey<StructureSet> key = ResourceKey.create(Registries.STRUCTURE_SET, structure.set());
        // The structure state's own (a server's salted sets are there): a set it does not try (a flat world's outposts,
        // the end cities of the overworld) starts nothing here.
        Optional<Holder<StructureSet>> tried = context.structureState().possibleStructureSets().stream().filter(known -> known.is(key)).findFirst();
        if (tried.isEmpty()) {
            if (context.registries().lookupOrThrow(Registries.STRUCTURE_SET).get(key).isEmpty()) {
                throw new IllegalArgumentException("Unknown structure set " + structure.set() + " in " + context.source());
            }
            return Optional.empty();
        }
        Holder<StructureSet> set = tried.get();
        // A structure found in another world (another seed, or without the server's seeds) may not start here.
        ChunkPos chunk = structure.chunk();
        if (!context.generatesStructures() || !set.value().placement().isStructureChunk(context.structureState(), chunk.x(), chunk.z())) {
            return Optional.empty();
        }
        // The set's structure this world starts there, which must be the one asked about.
        return located(new Candidate(set, chunk)).filter(located -> located.id().equals(structure.id()));
    }

    /**
     * Where a structure's pieces are once placed: where they were generated, but for a structure that decides its
     * height as it is placed (an ocean ruin on the ocean floor), placed on the terrain as for its blocks. Worked out once.
     * One that cannot be placed (a broken data pack structure) keeps where it was generated, logged.
     */
    private Placed placed(Located located) {
        if (!GhostCapture.heightDecidedWhenPlaced(located.start())) return new Placed(located.box(), located.pieces());
        Key key = new Key(located.set(), located.chunk());
        synchronized (placed) {
            Placed known = placed.get(key);
            if (known != null) return known;
        }
        Placed worked;
        try {
            StructureStart own = own(located);
            List<BoundingBox> pieces = GhostCapture.placedPieces(context, own);
            // As the start's box once the world loads it again (it keeps the one from before placing until then).
            worked = new Placed(own.getStructure().adjustBoundingBox(new PiecesContainer(own.getPieces()).calculateBoundingBox()), pieces);
        } catch (RuntimeException e) {
            Constants.LOG.warn("Could not place {} at chunk {} in {} for its height: {}", located.id(), located.chunk(),
                    context.dimension().identifier(), e.toString());
            worked = new Placed(located.box(), located.pieces());
        }
        synchronized (placed) {
            Placed raced = placed.putIfAbsent(key, worked);
            return raced != null ? raced : worked;
        }
    }

    private Optional<GhostCapture.Captured> captured(FoundStructure structure) {
        // First whether it starts there in this world (kept, so quick): what is kept is by set and chunk.
        Optional<Located> located = located(structure);
        if (located.isEmpty()) return Optional.empty();
        Key key = new Key(structure.set(), structure.chunk());
        synchronized (captures) {
            GhostCapture.Captured known = captures.get(key);
            if (known != null) return Optional.of(known);
        }
        // Others of the same structure nearby (overlapping mineshafts) change its random choices.
        BoundingBox box = located.get().box();
        List<StructureStart> nearby = new ArrayList<>();
        Identifier set = structure.set();
        for (Located other : located(new ChunkPos((box.minX() >> 4) - STRUCTURE_REACH, (box.minZ() >> 4) - STRUCTURE_REACH),
                new ChunkPos((box.maxX() >> 4) + STRUCTURE_REACH, (box.maxZ() >> 4) + STRUCTURE_REACH), set::equals)) {
            if (!other.chunk().equals(structure.chunk())) nearby.add(own(other));
        }
        GhostCapture.Captured captured = GhostCapture.captureOwn(context, own(located.get()), nearby);
        synchronized (captures) {
            captures.put(key, captured);
        }
        return Optional.of(captured);
    }

    /**
     * A start of a structure that is the caller's own to place: placing pieces makes them remember what they placed (a
     * corridor's spawner), and the one kept is shared by every thread. A copy, or generated again if it cannot be copied.
     */
    private StructureStart own(Located located) {
        return GhostCapture.tryCopy(context, located.start()).orElseGet(() -> StructureEvaluator.generateAgain(context, located));
    }

    /** A box of its own for the caller: boxes can be moved, and the ones kept are shared. */
    private static BoundingBox copy(BoundingBox box) {
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    private static Identifier idOf(Holder<StructureSet> set) {
        return set.unwrapKey().map(ResourceKey::identifier).orElse(Identifier.fromNamespaceAndPath(Constants.MOD_ID, "unnamed"));
    }

    private static <K, V> Map<K, V> lru(int keep) {
        return new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > keep;
            }
        };
    }
}
