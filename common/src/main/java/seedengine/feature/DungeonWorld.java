package seedengine.feature;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import seedengine.Constants;
import seedengine.api.Dungeon;
import seedengine.mixin.ChunkGeneratorAccessor;
import seedengine.shape.GhostCapture;
import seedengine.api.Spawner;
import seedengine.worldgen.ChunkSteps;
import seedengine.worldgen.TerrainChunks;
import seedengine.worldgen.WorldgenContext;

/**
 * The dungeons (monster rooms) of a world and their spawners, worked out from the seed. A dungeon goes where its
 * random tries find a closed room with one to five openings, so whether it is there depends on the terrain, the caves
 * and what was placed before it, also by the chunks next to it. So this decorates chunks as
 * {@code ChunkGenerator.applyBiomeDecoration} does, one after another into one shared level, on the terrain from
 * {@link TerrainChunks}, up to the step dungeons are in: the structures and features of the earlier steps, then those
 * of the dungeons' step in their order.
 * <p>
 * The world decorates chunks in the order they were generated, which depends on how players moved; decorate them here
 * in the order the world most likely did (nearest the player first). Not thread-safe: one thread uses one instance.
 */
public final class DungeonWorld {
    private static final int LAST_STEP = GenerationStep.Decoration.UNDERGROUND_STRUCTURES.ordinal();
    /** How far (in chunks) what other chunks place can reach a chunk's dungeon. */
    private static final int REACH = 2;
    /** How far a dungeon's room goes from its spawner, sideways (it goes from 1 below to 4 above). */
    private static final int ROOM = 4;
    /** Up to how many neighbours whose blocks a dungeon looks at have every order tried (2 to the power of it). */
    private static final int MAX_ORDERED = 8;

    private final TerrainChunks terrain;
    private final WorldgenContext context;
    private final DecorationLevel decoration;
    private final List<FeatureSorter.StepFeatureData> featureList;
    private final Function<Holder<Biome>, BiomeGenerationSettings> settings;
    private final Map<Integer, List<Structure>> structuresByStep;
    private final Map<Structure, Long2ObjectOpenHashMap<StructureStart>> startsByStructure = new HashMap<>();
    private final LongOpenHashSet decorated = new LongOpenHashSet();
    /** Per decorated chunk, the dungeon spawners it would have if it were decorated before the chunks next to it. */
    private final Long2ObjectOpenHashMap<List<Spawner>> alone = new Long2ObjectOpenHashMap<>();
    /**
     * Per chunk whose surroundings are all decorated, the dungeon spawners it would have if it were decorated after them,
     * or after only some of those that placed blocks where its dungeons look.
     */
    private final Long2ObjectOpenHashMap<List<Spawner>> last = new Long2ObjectOpenHashMap<>();
    /** How each decorated chunk placed its dungeons, to place them again once its neighbours are decorated. */
    private final Long2ObjectOpenHashMap<List<Runnable>> dungeonPlacements = new Long2ObjectOpenHashMap<>();
    /** When each decorated chunk began placing dungeons. */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap dungeonsBegan = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();

    /** Starts are the structures known around the chunks to decorate. */
    public DungeonWorld(TerrainChunks terrain, Collection<StructureStart> starts) {
        this.terrain = terrain;
        this.context = terrain.context();
        this.decoration = new DecorationLevel(terrain);
        ChunkGeneratorAccessor generator = (ChunkGeneratorAccessor) context.generator();
        this.featureList = generator.seedengine$featuresPerStep().get();
        this.settings = generator.seedengine$generationSettingsGetter();
        this.structuresByStep = context.registries().lookupOrThrow(Registries.STRUCTURE).stream()
                .collect(Collectors.groupingBy(structure -> structure.step().ordinal()));
        addStarts(starts);
    }

    /** Structures found since: chunks decorated from now on place them too. */
    public void addStarts(Collection<StructureStart> starts) {
        terrain.addStarts(starts);
        for (StructureStart start : starts) {
            if (!start.isValid()) continue;
            startsByStructure.computeIfAbsent(start.getStructure(), s -> new Long2ObjectOpenHashMap<>()).putIfAbsent(start.getChunkPos().pack(), start);
        }
    }

    /** How many chunks were decorated (they all keep what was placed in them). */
    public int decoratedCount() {
        return decorated.size();
    }

    /** Whether this world's generation places dungeons at all (the Nether and the End do not). */
    public boolean hasDungeons() {
        for (int step = 0; step <= LAST_STEP && step < featureList.size(); step++) {
            for (PlacedFeature feature : featureList.get(step).features()) {
                if (ChunkSteps.isDungeon(feature)) return true;
            }
        }
        return false;
    }

    public boolean isDecorated(ChunkPos chunk) {
        return decorated.contains(chunk.pack());
    }

    /**
     * The spawners of the dungeons in a decorated chunk, as they stand in the order the chunks were decorated here (a
     * later chunk may have built over one).
     */
    public List<Spawner> standing(ChunkPos chunk) {
        return decoration.dungeonSpawners(chunk);
    }

    /**
     * The dungeons of a decorated chunk: those standing in the order decorated here, and those it would have if it came
     * before its neighbours or (once they are all decorated) after them, or after only some of them. A dungeon is certain when it stands and would
     * stand first too, and no other chunk places anything where its room goes: then no order changes it. Otherwise
     * what is there depends on the order (and so does the outcome of placing it last, which is why that one only adds
     * candidates).
     */
    public List<Dungeon> dungeons(ChunkPos chunk) {
        List<Spawner> standing = standing(chunk);
        List<Spawner> first = alone.getOrDefault(chunk.pack(), List.of());
        java.util.LinkedHashSet<Spawner> all = new java.util.LinkedHashSet<>(standing);
        all.addAll(first);
        all.addAll(last.getOrDefault(chunk.pack(), List.of()));
        List<Dungeon> dungeons = new java.util.ArrayList<>();
        for (Spawner spawner : all) {
            boolean untouched = !decoration.touchedByOthers(chunk, spawner.pos().offset(-ROOM, -1, -ROOM), spawner.pos().offset(ROOM, 4, ROOM));
            dungeons.add(new Dungeon(spawner, untouched && standing.contains(spawner) && first.contains(spawner)));
        }
        return dungeons;
    }

    /** Decorates a chunk, once, up to the step of dungeons. */
    public void decorate(ChunkPos chunk) {
        if (!decorated.add(chunk.pack())) return;
        decorateOnly(chunk);
        // The chunks around whose surroundings are now all decorated: their dungeons as if they had come last. A room
        // reaches into the chunks next to its own, which chunks next to those write into: two chunks around.
        for (int z = chunk.z() - REACH; z <= chunk.z() + REACH; z++) {
            for (int x = chunk.x() - REACH; x <= chunk.x() + REACH; x++) {
                ChunkPos around = new ChunkPos(x, z);
                if (last.containsKey(around.pack()) || !surrounded(around)) continue;
                decoration.center = around;
                List<Spawner> after = new java.util.ArrayList<>();
                // Of its own blocks, only what it placed before its dungeons: those are placed again here.
                long began = dungeonsBegan.containsKey(around.pack()) ? dungeonsBegan.get(around.pack()) : decoration.now();
                List<Runnable> placements = dungeonPlacements.getOrDefault(around.pack(), List.of());
                Runnable placing = () -> placements.forEach(Runnable::run);
                after.addAll(decoration.placeTrial(other -> true, began, placing));
                // After only some of the neighbours whose blocks its dungeons looked at: every such order (the world's
                // depends on how players moved, and a server may decorate chunks side by side).
                long[] read = decoration.trialRead().toLongArray();
                if (!placements.isEmpty() && read.length > 0 && read.length <= MAX_ORDERED) {
                    for (int subset = 1; subset < (1 << read.length) - 1; subset++) {
                        LongOpenHashSet before = new LongOpenHashSet();
                        for (int i = 0; i < read.length; i++) {
                            if ((subset & (1 << i)) != 0) before.add(read[i]);
                        }
                        for (Spawner spawner : decoration.placeTrial(before::contains, began, placing)) {
                            if (!after.contains(spawner)) after.add(spawner);
                        }
                    }
                }
                last.put(around.pack(), after);
            }
        }
    }

    private boolean surrounded(ChunkPos chunk) {
        for (int z = chunk.z() - REACH; z <= chunk.z() + REACH; z++) {
            for (int x = chunk.x() - REACH; x <= chunk.x() + REACH; x++) {
                if (!decorated.contains(ChunkPos.pack(x, z))) return false;
            }
        }
        return true;
    }

    private void decorateOnly(ChunkPos chunk) {
        decoration.center = chunk;
        decoration.random = ChunkSteps.regionRandom(context, chunk);
        WorldGenLevel level = decoration.level();
        SectionPos section = SectionPos.of(chunk, SectionPos.blockToSectionCoord(context.height().getMinY()));
        BlockPos origin = section.origin();
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(context.seed(), origin.getX(), origin.getZ());
        Set<Holder<Biome>> possibleBiomes = new ObjectArraySet<>();
        for (int z = chunk.z() - 1; z <= chunk.z() + 1; z++) {
            for (int x = chunk.x() - 1; x <= chunk.x() + 1; x++) {
                for (LevelChunkSection chunkSection : terrain.terrain(new ChunkPos(x, z)).getSections()) {
                    chunkSection.getBiomes().getAll(possibleBiomes::add);
                }
            }
        }
        possibleBiomes.retainAll(context.generator().getBiomeSource().possibleBiomes());
        BoundingBox writable = new BoundingBox(chunk.getMinBlockX(), context.height().getMinY() + 1, chunk.getMinBlockZ(),
                chunk.getMinBlockX() + 15, context.height().getMaxY(), chunk.getMinBlockZ() + 15);

        for (int step = 0; step <= LAST_STEP; step++) {
            int index = 0;
            if (context.generatesStructures()) {
                for (Structure structure : structuresByStep.getOrDefault(step, Collections.emptyList())) {
                    random.setFeatureSeed(decorationSeed, index, step);
                    Long2ObjectOpenHashMap<StructureStart> byChunk = startsByStructure.get(structure);
                    if (byChunk != null) {
                        for (long key : GhostCapture.references(byChunk, chunk)) {
                            GhostCapture.placeInChunk(level, context, byChunk.get(key), random, writable, chunk);
                        }
                    }
                    index++;
                }
            }
            if (step >= featureList.size()) continue;
            FeatureSorter.StepFeatureData stepData = featureList.get(step);
            IntSet possibleFeatures = new IntArraySet();
            for (Holder<Biome> biome : possibleBiomes) {
                List<HolderSet<PlacedFeature>> featuresInBiome = settings.apply(biome).features();
                if (step < featuresInBiome.size()) {
                    featuresInBiome.get(step).stream().map(Holder::value).forEach(feature -> possibleFeatures.add(stepData.indexMapping().applyAsInt(feature)));
                }
            }
            int[] indices = possibleFeatures.toIntArray();
            Arrays.sort(indices);
            for (int featureIndex : indices) {
                PlacedFeature feature = stepData.features().get(featureIndex);
                // A server may place a feature with a seed of its own (Paper's feature-seeds): the chunk's seed from it.
                Long ownSeed = context.featureSeed(feature);
                long featureSeed = ownSeed == null ? decorationSeed : random.setDecorationSeed(ownSeed, origin.getX(), origin.getZ());
                random.setFeatureSeed(featureSeed, featureIndex, step);
                decoration.placingDungeon = ChunkSteps.isDungeon(feature);
                try {
                    if (decoration.placingDungeon) {
                        // Once as if this chunk came before its neighbours, with the same random numbers, to see whether
                        // the dungeon depends on what they placed; kept to place again as if it came after them.
                        int dungeonIndex = featureIndex;
                        int dungeonStep = step;
                        Runnable placement = () -> {
                            random.setFeatureSeed(featureSeed, dungeonIndex, dungeonStep);
                            ChunkSteps.placeFeature(level, context, feature, random, origin);
                        };
                        if (!dungeonsBegan.containsKey(chunk.pack())) dungeonsBegan.put(chunk.pack(), decoration.now());
                        dungeonPlacements.computeIfAbsent(chunk.pack(), k -> new java.util.ArrayList<>()).add(placement);
                        // Before its neighbours, it still sees all it placed itself so far (an earlier dungeon of its own too).
                        List<Spawner> first = decoration.placeTrial(other -> false, decoration.now(), placement);
                        if (!first.isEmpty()) {
                            List<Spawner> known = new java.util.ArrayList<>(alone.getOrDefault(chunk.pack(), List.of()));
                            known.addAll(first);
                            alone.put(chunk.pack(), known);
                        }
                        random.setFeatureSeed(featureSeed, featureIndex, step);
                    }
                    ChunkSteps.placeFeature(level, context, feature, random, origin);
                } catch (RuntimeException | LinkageError e) {
                    String name = feature.feature().unwrapKey().map(key -> key.identifier().toString())
                            .orElse(feature.feature().value().getClass().getSimpleName());
                    // An unnamed feature is told apart by the object, not by its kind alone.
                    String kind = feature.feature().unwrapKey().isPresent() ? name : name + "@" + System.identityHashCode(feature);
                    Constants.warnOnce("feature " + kind, "Could not place {} in chunk {}, answers near it may lack what it places: {}",
                            name, chunk, e.toString());
                } finally {
                    decoration.placingDungeon = false;
                }
            }
        }
    }
}
