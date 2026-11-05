package seedengine.worldgen;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Chunks of a world worked out from the seed the way world generation makes them, up to their terrain (noise,
 * surface, carvers), without a server: the base that features are placed on. Kept for the most recently used chunks.
 * Not thread-safe: one thread uses one instance.
 * <p>
 * Structures that bend the terrain around them (villages, trial chambers, ancient cities) do so through the starts
 * given here, as world generation does through the chunks' structure references.
 */
public final class TerrainChunks {
    private static final int KEEP = 400;

    private final WorldgenContext context;
    private final StructureManager structures;
    private final BiomeManager biomeManager;
    private final PalettedContainerFactory containers;
    private final Map<Long, ProtoChunk> chunks = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ProtoChunk> eldest) {
            return size() > KEEP;
        }
    };
    private final Set<Long> built = new java.util.HashSet<>();
    /** The chunks holding structure references, for the terrain being built: the most recently used. */
    private final Map<Long, ProtoChunk> references = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, ProtoChunk> eldest) {
            return size() > KEEP * 4;
        }
    };
    /** The known starts by their chunk. */
    private final Map<Long, java.util.List<StructureStart>> startsByChunk = new java.util.HashMap<>();
    private final Set<StructureStart> knownStarts = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    public TerrainChunks(WorldgenContext context, Collection<StructureStart> starts) {
        this.context = context;
        this.containers = PalettedContainerFactory.create(context.registries());
        addStarts(starts);
        this.structures = new StructureManager(referenceLevel(), new WorldOptions(context.seed(), context.generatesStructures(), false), null);
        this.biomeManager = ChunkSteps.biomeManager(context);
    }

    public WorldgenContext context() {
        return context;
    }

    /** Starts found since: terrain built from now on bends around them too. */
    public void addStarts(Collection<StructureStart> starts) {
        boolean added = false;
        for (StructureStart start : starts) {
            if (!start.isValid() || !knownStarts.add(start)) continue;
            startsByChunk.computeIfAbsent(start.getChunkPos().pack(), k -> new java.util.ArrayList<>()).add(start);
            added = true;
        }
        if (added) references.clear();
    }

    public BiomeManager biomeManager() {
        return biomeManager;
    }

    public StructureManager structures() {
        return structures;
    }

    /** A chunk with its biomes set (no blocks yet, unless {@link #terrain} built them). */
    public ProtoChunk biomes(ChunkPos pos) {
        long key = pos.pack();
        ProtoChunk chunk = chunks.get(key);
        if (chunk == null) {
            ProtoChunk made = new ProtoChunk(pos, UpgradeData.EMPTY, context.height(), containers, null);
            ChunkSteps.onThisThread(() -> context.generator().createBiomes(context.randomState(), Blender.empty(), structures, made).join());
            chunk = made;
            chunks.put(key, chunk);
            built.remove(key);
        }
        return chunk;
    }

    /** A chunk with its terrain. */
    public ProtoChunk terrain(ChunkPos pos) {
        ProtoChunk chunk = biomes(pos);
        long key = pos.pack();
        if (built.add(key)) {
            ChunkSteps.buildTerrain(context, chunk, structures, biomeManager, possibleBiomes(pos));
        }
        return chunk;
    }

    /** The biomes of a chunk and its neighbours, as {@code ChunkStatusTasks} collects them for the surface. */
    public Set<Holder<Biome>> possibleBiomes(ChunkPos center) {
        Set<Holder<Biome>> biomes = new ObjectOpenHashSet<>();
        for (int z = center.z() - 1; z <= center.z() + 1; z++) {
            for (int x = center.x() - 1; x <= center.x() + 1; x++) {
                for (LevelChunkSection section : biomes(new ChunkPos(x, z)).getSections()) {
                    section.getBiomes().getAll(biomes::add);
                }
            }
        }
        return biomes;
    }

    /**
     * A level that only answers {@code getChunk} with chunks holding the given starts and the references to them, which
     * is all a structure manager reads to find the starts reaching a chunk.
     */
    private LevelAccessor referenceLevel() {
        Map<Long, java.util.List<StructureStart>> byChunk = startsByChunk;
        return (LevelAccessor) Proxy.newProxyInstance(LevelAccessor.class.getClassLoader(), new Class<?>[]{LevelAccessor.class}, (self, method, args) -> {
            switch (method.getName()) {
                case "getChunk":
                    if (args != null && args.length >= 2 && args[0] instanceof Integer x && args[1] instanceof Integer z) {
                        return referenceChunk(x, z, byChunk);
                    }
                    break;
                case "registryAccess":
                    return context.registries();
                case "getMinY":
                    return context.height().getMinY();
                case "getHeight":
                    if (args == null || args.length == 0) return context.height().getHeight();
                    break;
                case "hashCode":
                    return System.identityHashCode(self);
                case "equals":
                    return self == args[0];
                case "toString":
                    return "Seed Engine structure references";
                default:
                    break;
            }
            if (method.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(self, method, args);
            Class<?> type = method.getReturnType();
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            return null;
        });
    }

    private ProtoChunk referenceChunk(int chunkX, int chunkZ, Map<Long, java.util.List<StructureStart>> byChunk) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        ProtoChunk chunk = references.get(key);
        if (chunk != null) return chunk;
        chunk = new ProtoChunk(new ChunkPos(chunkX, chunkZ), UpgradeData.EMPTY, context.height(), containers, null);
        for (StructureStart start : byChunk.getOrDefault(key, java.util.List.of())) {
            chunk.setStartForStructure(start.getStructure(), start);
        }
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        // As ChunkGenerator.createReferences: the starts within 8 chunks whose box reaches this chunk.
        for (int x = chunkX - 8; x <= chunkX + 8; x++) {
            for (int z = chunkZ - 8; z <= chunkZ + 8; z++) {
                long source = ChunkPos.pack(x, z);
                for (StructureStart start : byChunk.getOrDefault(source, java.util.List.of())) {
                    if (start.getBoundingBox().intersects(minX, minZ, minX + 15, minZ + 15)) chunk.addReferenceForStructure(start.getStructure(), source);
                }
            }
        }
        references.put(key, chunk);
        return chunk;
    }
}
