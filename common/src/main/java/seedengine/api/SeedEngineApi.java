package seedengine.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import seedengine.engine.Worlds;
import seedengine.seed.Seeds;
import seedengine.trade.TradeDraws;
import seedengine.worldgen.VanillaWorldgen;

/**
 * What a world's seed decides, worked out with the game's own code: what its generation places, and the draws of its
 * named random sequences (the trades villagers get).
 * <p>
 * Every question about generation names the world one of two ways:
 * <ul>
 *     <li>a seed, a world type and a dimension: generation built from the vanilla data inside the game jar (with the
 *     game's own tags of blocks and the like, which a world joined sets: before joining one, at the title screen, they
 *     are empty, and such a question is an {@link IllegalStateException}), as a
 *     server running vanilla generation with that world type has it (a server's data packs and generator mods are not
 *     known). World types are world presets, e.g. {@code minecraft:normal}, {@code minecraft:large_biomes},
 *     {@code minecraft:amplified}, {@code minecraft:flat}. An unknown one, or one without the dimension, is an
 *     {@link IllegalArgumentException}.</li>
 *     <li>a server's level: that server's own generator, with its data packs and its settings (a world made without
 *     structures has none).</li>
 * </ul>
 * A Spigot or Paper server may place some structures and features with seeds of its own, which clients are not sent;
 * whoever knows them (from the server's settings) can pass them as {@link ServerSeeds} to the questions about structures
 * and dungeons, which then answer as that server generates.
 * <p>
 * Random sequences and trades do not depend on the world type or the dimension: they take a seed (the vanilla data,
 * and a world's default sequence settings) or a server's level (its data packs and its {@code /random} settings).
 * <p>
 * Areas are squares of chunks, from one corner to the other, both included.
 * <p>
 * Threads: any thread, also several at once. The work is done on the calling thread (also the terrain built for
 * dungeons, which the game's own chunk generation does on its worker threads), and some of it is heavy
 * (structures and dungeons take milliseconds to seconds, more for large areas): call from a thread of your own, not
 * the render thread or the server thread, unless the area is small. The first question about a world built from the
 * vanilla data loads that data, which takes a few seconds (once per game).
 * <p>
 * Answers are worked out once and kept for a while (structures, the blocks of the last few structures, decorated
 * chunks), so asking again, or asking for more of a structure found before, is quick. A question may still take as
 * long as the first time once the answer was dropped.
 * <p>
 * The seed and the world type of the world the client is in ({@link #currentSeed()}, {@link #currentWorldType()}) are
 * kept here too, for the mods that ask with them, and whether they fit its terrain ({@link #currentWorldCheck()}).
 */
public final class SeedEngineApi {
    private SeedEngineApi() {
    }

    /**
     * The chunks in an area where a structure may start, from the structure sets' placement only: cheap (no terrain
     * or biomes, except for the rings of strongholds, worked out once per world), but whether a structure really
     * starts there is not known. Use {@link #structures} for that. A structure set whose placement is of a kind other
     * than vanilla's (from a mod) is checked chunk by chunk, which takes longer the larger the area.
     *
     * @param sets which structure sets to list (e.g. {@code set -> true} for all)
     */
    public static List<StructureCandidate> candidates(long seed, Identifier worldType, ResourceKey<Level> dimension, ChunkPos from, ChunkPos to,
                                                      Predicate<Identifier> sets) {
        return Worlds.vanilla(seed, worldType, dimension).candidates(from, to, sets);
    }

    /** {@link #candidates(long, Identifier, ResourceKey, ChunkPos, ChunkPos, Predicate)} for a server that places some structures and features with seeds of its own. */
    public static List<StructureCandidate> candidates(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds,
                                                      ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).candidates(from, to, sets);
    }

    /** {@link #candidates(long, Identifier, ResourceKey, ChunkPos, ChunkPos, Predicate)} with a server's generator. */
    public static List<StructureCandidate> candidates(ServerLevel level, ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        return Worlds.server(level).candidates(from, to, sets);
    }

    /**
     * The structures that start in an area: where and how big. Each candidate is checked the way world generation
     * checks it (biome, terrain height and the structure's own rules), which is the heavy part. A candidate that
     * cannot be worked out (a broken data pack structure) is left out and logged.
     * <p>
     * The boxes are where the structures are placed. Some decide their height only as they are placed, from the
     * terrain (an ocean ruin, a shipwreck and a buried treasure go down to the ocean floor or the ground from the y 90
     * they are generated at; an igloo, a swamp hut, a jungle temple and a desert pyramid onto the ground): these are
     * placed on the terrain worked out as for {@link #blocks} (a few chunks of it each, tens of milliseconds), so an
     * area with many of them takes longer. Where one decides by the chunk generated first (a beached shipwreck, a
     * desert pyramid, and a swamp hut or a jungle temple across chunks), it is as if the chunks were generated row by
     * row, as for {@link #blocks}.
     *
     * @param sets which structure sets to look for (e.g. {@code set -> true} for all)
     */
    public static List<FoundStructure> structures(long seed, Identifier worldType, ResourceKey<Level> dimension, ChunkPos from, ChunkPos to,
                                                  Predicate<Identifier> sets) {
        return Worlds.vanilla(seed, worldType, dimension).structures(from, to, sets);
    }

    /** {@link #structures(long, Identifier, ResourceKey, ChunkPos, ChunkPos, Predicate)} for a server that places some structures and features with seeds of its own. */
    public static List<FoundStructure> structures(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds,
                                                  ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).structures(from, to, sets);
    }

    /** {@link #structures(long, Identifier, ResourceKey, ChunkPos, ChunkPos, Predicate)} with a server's generator. */
    public static List<FoundStructure> structures(ServerLevel level, ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        return Worlds.server(level).structures(from, to, sets);
    }

    /**
     * Where the structures that start in an area decide which mobs spawn, instead of the biome: one for each structure
     * and category of mobs it decides (a fortress's monsters inside its pieces, an outpost's monsters anywhere in its
     * box, an ocean monument's guardians, and the axolotls it keeps out). Structures that decide none are left out.
     * Worked out with {@link #structures} (as heavy), from the structures' generation data. Which blocks inside the
     * boxes a mob may spawn on (light, the block under it, room) is not known from the seed.
     *
     * @param sets which structure sets to look for (e.g. {@code set -> true} for all)
     */
    public static List<StructureSpawnArea> spawnAreas(long seed, Identifier worldType, ResourceKey<Level> dimension, ChunkPos from, ChunkPos to,
                                                      Predicate<Identifier> sets) {
        return Worlds.vanilla(seed, worldType, dimension).spawnAreas(from, to, sets);
    }

    /** {@link #spawnAreas(long, Identifier, ResourceKey, ChunkPos, ChunkPos, Predicate)} for a server that places some structures and features with seeds of its own. */
    public static List<StructureSpawnArea> spawnAreas(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds,
                                                      ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).spawnAreas(from, to, sets);
    }

    /** {@link #spawnAreas(long, Identifier, ResourceKey, ChunkPos, ChunkPos, Predicate)} with a server's generator (and its data packs' structures). */
    public static List<StructureSpawnArea> spawnAreas(ServerLevel level, ChunkPos from, ChunkPos to, Predicate<Identifier> sets) {
        return Worlds.server(level).spawnAreas(from, to, sets);
    }

    /**
     * The box of each piece of a structure (a house of a village, a room of a stronghold), where it is placed (as the
     * box {@link #structures} gives). Empty if the structure does not start there in this world. A structure set this world does not know is an {@link IllegalArgumentException}.
     */
    public static List<BoundingBox> pieces(long seed, Identifier worldType, ResourceKey<Level> dimension, FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension).pieces(structure);
    }

    /** {@link #pieces(long, Identifier, ResourceKey, FoundStructure)} for a server that places some structures and features with seeds of its own. */
    public static List<BoundingBox> pieces(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds, FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).pieces(structure);
    }

    /** {@link #pieces(long, Identifier, ResourceKey, FoundStructure)} with a server's generator. */
    public static List<BoundingBox> pieces(ServerLevel level, FoundStructure structure) {
        return Worlds.server(level).pieces(structure);
    }

    /**
     * {@link #pieces} with what each piece is: its kind and the template it places, in the same order. Where an end
     * city has its ship, for one: the piece with the template {@code minecraft:end_city/ship}. Empty if the structure
     * does not start there in this world. A structure set this world does not know is an {@link IllegalArgumentException}.
     */
    public static List<FoundPiece> namedPieces(long seed, Identifier worldType, ResourceKey<Level> dimension, FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension).namedPieces(structure);
    }

    /** {@link #namedPieces(long, Identifier, ResourceKey, FoundStructure)} for a server that places some structures and features with seeds of its own. */
    public static List<FoundPiece> namedPieces(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds, FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).namedPieces(structure);
    }

    /** {@link #namedPieces(long, Identifier, ResourceKey, FoundStructure)} with a server's generator. */
    public static List<FoundPiece> namedPieces(ServerLevel level, FoundStructure structure) {
        return Worlds.server(level).namedPieces(structure);
    }

    /**
     * The blocks a structure places, by letting the game place it into a level that only records them (air where it
     * clears the ground), on the terrain world generation builds there (with its caves) and with the randoms and
     * heightmaps of world generation: the blocks it sets in the world, as its chunks are generated row by row. Features
     * placed after structures may replace some of them later (ores in the stone of a buried structure, trees), and a
     * structure's flowers (a village's) are not placed where a chunk next to it, generated first, grew plants; the
     * flowers of that patch drawn after such a spot then move (the spot skipped skips a random draw). Where
     * the order the world generated the chunks in changes them (in a mineshaft), {@link #uncertainBlocks} tells. Heavy:
     * the terrain of every chunk the structure covers is built. Empty if the structure does not start there; a structure set this world does not know
     * is an {@link IllegalArgumentException}.
     */
    public static Map<BlockPos, BlockState> blocks(long seed, Identifier worldType, ResourceKey<Level> dimension, FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension).blocks(structure);
    }

    /** {@link #blocks(long, Identifier, ResourceKey, FoundStructure)} for a server that places some structures and features with seeds of its own. */
    public static Map<BlockPos, BlockState> blocks(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds,
                                                   FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).blocks(structure);
    }

    /** {@link #blocks(long, Identifier, ResourceKey, FoundStructure)} with a server's generator. */
    public static Map<BlockPos, BlockState> blocks(ServerLevel level, FoundStructure structure) {
        return Worlds.server(level).blocks(structure);
    }

    /**
     * The spawners a structure places (mineshafts, strongholds, fortresses, trial chambers), worked out with its
     * blocks, as its chunks are generated row by row. Heavy, as {@link #blocks}. In a mineshaft crossing chunks, where a
     * corridor puts its spawner depends on the order the world generated the chunks in, so one may be elsewhere in the
     * world: {@link #possibleSpawners} gives every place it may be.
     */
    public static List<Spawner> spawners(long seed, Identifier worldType, ResourceKey<Level> dimension, FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension).spawners(structure);
    }

    /** {@link #spawners(long, Identifier, ResourceKey, FoundStructure)} for a server that places some structures and features with seeds of its own. */
    public static List<Spawner> spawners(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds, FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).spawners(structure);
    }

    /** {@link #spawners(long, Identifier, ResourceKey, FoundStructure)} with a server's generator. */
    public static List<Spawner> spawners(ServerLevel level, FoundStructure structure) {
        return Worlds.server(level).spawners(structure);
    }

    /**
     * The blocks of a structure that depend on the order the world generated its chunks in, by position, with every
     * block the world may have there (the one {@link #blocks} gives among them; where the structure sets nothing in some
     * order, what is there then). Only a mineshaft has them: a corridor with a cave spider spawner puts it in the first
     * of its chunks generated whose draw lands inside, and the rest of that chunk's corridors come out otherwise after
     * it. Empty for a structure all of whose blocks are certain, and if the structure does not start there. Heavy, as
     * {@link #blocks} (worked out with them).
     */
    public static Map<BlockPos, Set<BlockState>> uncertainBlocks(long seed, Identifier worldType, ResourceKey<Level> dimension, FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension).uncertainBlocks(structure);
    }

    /** {@link #uncertainBlocks(long, Identifier, ResourceKey, FoundStructure)} for a server that places some structures and features with seeds of its own. */
    public static Map<BlockPos, Set<BlockState>> uncertainBlocks(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds,
                                                                 FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).uncertainBlocks(structure);
    }

    /** {@link #uncertainBlocks(long, Identifier, ResourceKey, FoundStructure)} with a server's generator. */
    public static Map<BlockPos, Set<BlockState>> uncertainBlocks(ServerLevel level, FoundStructure structure) {
        return Worlds.server(level).uncertainBlocks(structure);
    }

    /**
     * Every spawner a structure may place, whichever order the world generated its chunks in, each marked certain or
     * not: those of {@link #spawners} are among them. A mineshaft corridor's cave spider spawner is uncertain where
     * another of the corridor's chunks, generated first, may have placed it instead. Heavy, as {@link #blocks}.
     */
    public static List<StructureSpawner> possibleSpawners(long seed, Identifier worldType, ResourceKey<Level> dimension, FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension).possibleSpawners(structure);
    }

    /** {@link #possibleSpawners(long, Identifier, ResourceKey, FoundStructure)} for a server that places some structures and features with seeds of its own. */
    public static List<StructureSpawner> possibleSpawners(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds,
                                                          FoundStructure structure) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).possibleSpawners(structure);
    }

    /** {@link #possibleSpawners(long, Identifier, ResourceKey, FoundStructure)} with a server's generator. */
    public static List<StructureSpawner> possibleSpawners(ServerLevel level, FoundStructure structure) {
        return Worlds.server(level).possibleSpawners(structure);
    }

    /**
     * The dungeons (monster rooms) in an area. Whether a dungeon is there depends on the terrain, the caves and what was
     * placed before it, also by the chunks next to it, so this builds the terrain and decorates the chunks of the area
     * as world generation does, nearest the middle of the area first (the order the world most likely generated them
     * in), keeping what was decorated for the next question. Heavy: a 21x21 area of chunks takes seconds; to keep
     * nearest-first order over a large area, ask for growing squares around the same middle. Empty in dimensions
     * without dungeons. Dungeons near the edge of the area may change once the chunks next to them are decorated too.
     * An area of more than 64x64 chunks (4096) is an {@link IllegalArgumentException}.
     */
    public static List<Dungeon> dungeons(long seed, Identifier worldType, ResourceKey<Level> dimension, ChunkPos from, ChunkPos to) {
        return Worlds.vanilla(seed, worldType, dimension).dungeons(from, to);
    }

    /** {@link #dungeons(long, Identifier, ResourceKey, ChunkPos, ChunkPos)} for a server that places some structures and features with seeds of its own. */
    public static List<Dungeon> dungeons(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds, ChunkPos from, ChunkPos to) {
        return Worlds.vanilla(seed, worldType, dimension, seeds).dungeons(from, to);
    }

    /** {@link #dungeons(long, Identifier, ResourceKey, ChunkPos, ChunkPos)} with a server's generator. */
    public static List<Dungeon> dungeons(ServerLevel level, ChunkPos from, ChunkPos to) {
        return Worlds.server(level).dungeons(from, to);
    }

    /**
     * The height of the generated terrain at a column, from the terrain's noise only, as the server's generator gives it:
     * before caves and canyons are carved (where one cuts the surface, the ground is lower), structures and features.
     * One above the highest block of the kind the heightmap counts (e.g. {@code OCEAN_FLOOR_WG} for the ground).
     */
    public static int height(long seed, Identifier worldType, ResourceKey<Level> dimension, Heightmap.Types type, int x, int z) {
        return Worlds.vanilla(seed, worldType, dimension).height(type, x, z);
    }

    /** {@link #height(long, Identifier, ResourceKey, Heightmap.Types, int, int)} with a server's generator. */
    public static int height(ServerLevel level, Heightmap.Types type, int x, int z) {
        return Worlds.server(level).height(type, x, z);
    }

    /** The biome the generator puts at a block (its 4x4x4 cell). Empty for a biome without an id. */
    public static Optional<Identifier> biome(long seed, Identifier worldType, ResourceKey<Level> dimension, int x, int y, int z) {
        return Worlds.vanilla(seed, worldType, dimension).biome(x, y, z);
    }

    /** {@link #biome(long, Identifier, ResourceKey, int, int, int)} with a server's generator. */
    public static Optional<Identifier> biome(ServerLevel level, int x, int y, int z) {
        return Worlds.server(level).biome(x, y, z);
    }

    /**
     * Where the nearest of some biomes is, searched for the way {@code /locate biome} searches (the game's own
     * {@code BiomeSource.findClosestBiome3d}): columns {@code horizontalStep} blocks apart, from {@code origin} outward
     * in a square spiral, out to {@code radius} blocks along x and z; in each column, heights {@code verticalStep}
     * apart, from the origin's height up and down (within the dimension's height). The first sample of one of the
     * biomes is the answer, at that sample's block. {@code /locate biome} searches with a radius of 6400 and steps of 32
     * and 64: from the same origin, the answer is the command's. Only the samples are looked at: a biome smaller than
     * the steps may be missed, and the block found is not always the biome's nearest.
     * <p>
     * Biomes change with height: caves have their own (e.g. {@code minecraft:lush_caves}, {@code minecraft:deep_dark}).
     * For a biome at the surface, give the origin at the surface's height (e.g. from {@link #height}) and a
     * {@code verticalStep} larger than the dimension's height, which samples only the origin's height.
     * <p>
     * A radius from 0 to 6400, steps of at least 1, and at most 200 samples out from the origin along x and z
     * ({@code radius / horizontalStep}, as many as {@code /locate biome}'s), or an {@link IllegalArgumentException}. A
     * biome the dimension does not have (or an unknown id) is not found. Empty if none of the biomes is found.
     * <p>
     * Only the biome noise is read (no terrain), but a search that finds nothing near takes up to a million samples
     * with the settings of {@code /locate biome}, up to a few seconds: call from a thread of your own. The answers of
     * the last few hundred searches of each world are kept.
     *
     * @param biomes the biomes to look for (e.g. {@code minecraft:cherry_grove}); for a tag, its biomes
     */
    public static Optional<FoundBiome> nearestBiome(long seed, Identifier worldType, ResourceKey<Level> dimension, Set<Identifier> biomes,
                                                    BlockPos origin, int radius, int horizontalStep, int verticalStep) {
        return Worlds.vanilla(seed, worldType, dimension).nearestBiome(biomes, origin, radius, horizontalStep, verticalStep);
    }

    /** {@link #nearestBiome(long, Identifier, ResourceKey, Set, BlockPos, int, int, int)} with a server's generator. */
    public static Optional<FoundBiome> nearestBiome(ServerLevel level, Set<Identifier> biomes, BlockPos origin, int radius, int horizontalStep,
                                                    int verticalStep) {
        return Worlds.server(level).nearestBiome(biomes, origin, radius, horizontalStep, verticalStep);
    }

    /**
     * The biomes of an area, on a grid at one height (for a map of biomes, or to find every place of one): samples
     * {@code step} blocks apart along x and z, from the smaller corner (the corners in any order, both included; the
     * larger one is sampled where it falls on the grid). Each sample is what {@link #biome} gives at its block: the biome
     * of its 4x4x4 cell, so a step below 4 samples the same cells again.
     * <p>
     * Biomes change with height: caves have their own (e.g. {@code minecraft:lush_caves}). For the surface, give the
     * terrain's height (e.g. from {@link #height}); every sample is at {@code y}. A biome without an id is
     * {@code seedengine:unnamed}.
     * <p>
     * At most 512x512 samples (262144), and a step of at least 1, or an {@link IllegalArgumentException}. Only the
     * biome noise is read (no terrain): a few microseconds a sample, about a second for the largest area, so call from
     * a thread of your own for a large one. The last few areas asked of each world (by corners, height and step) are
     * kept.
     */
    public static BiomeArea biomeArea(long seed, Identifier worldType, ResourceKey<Level> dimension, int fromX, int fromZ, int toX, int toZ,
                                      int y, int step) {
        return Worlds.vanilla(seed, worldType, dimension).biomeArea(fromX, fromZ, toX, toZ, y, step);
    }

    /** {@link #biomeArea(long, Identifier, ResourceKey, int, int, int, int, int, int)} with a server's generator. */
    public static BiomeArea biomeArea(ServerLevel level, int fromX, int fromZ, int toX, int toZ, int y, int step) {
        return Worlds.server(level).biomeArea(fromX, fromZ, toX, toZ, y, step);
    }

    /**
     * The biomes the dimension's generator can make (its biome source's, e.g. {@code minecraft:cherry_grove} in the
     * Overworld), whether or not one is near: what {@link #nearestBiome} can find there.
     */
    public static Set<Identifier> biomes(long seed, Identifier worldType, ResourceKey<Level> dimension) {
        return Worlds.vanilla(seed, worldType, dimension).biomes();
    }

    /** {@link #biomes(long, Identifier, ResourceKey)} with a server's generator. */
    public static Set<Identifier> biomes(ServerLevel level) {
        return Worlds.server(level).biomes();
    }

    /** The structure sets the world's generation data has, for every dimension (e.g. {@code minecraft:villages}). */
    public static Set<Identifier> structureSets(long seed, Identifier worldType, ResourceKey<Level> dimension) {
        return Worlds.vanilla(seed, worldType, dimension).structureSets();
    }

    /** {@link #structureSets(long, Identifier, ResourceKey)} with a server's generator. */
    public static Set<Identifier> structureSets(ServerLevel level) {
        return Worlds.server(level).structureSets();
    }

    /**
     * A named random sequence at its start, before anything drew from it (e.g. {@code minecraft:trade_set/librarian/level_1}
     * for a librarian's first trades, or a loot table's id for its drops). A new random each call, as a world with the
     * default sequence settings starts it.
     */
    public static RandomSource randomSequence(long seed, Identifier sequence) {
        return TradeDraws.start(new RandomSequences(), seed, sequence);
    }

    /**
     * {@link #randomSequence(long, Identifier)} as a server starts it, with its {@code /random} settings. Where the
     * server's own sequence is now (how much the world drew from it) is not known: this is its start.
     */
    public static RandomSource randomSequence(ServerLevel level, Identifier sequence) {
        return TradeDraws.start(level.getServer().getRandomSequences(), level.getSeed(), sequence);
    }

    /**
     * The trades of a trade set (e.g. {@code minecraft:librarian/level_1}), draw by draw from the start of its random
     * sequence: draw 0 is what the first villager to take up the job (or reach the level) in the world gets, draw 1 the
     * next, whichever villager it is. One draw is the list of offers that villager gets. Drawn by the game's own code
     * with the vanilla data, from {@code from} to {@code from + count - 1}; the work grows with {@code from + count}
     * (a few microseconds a draw). Which draw the world is at is not known from the seed.
     * <p>
     * An unknown trade set, one drawing from the world's random instead of a sequence, one with a trade that needs
     * the world (an explorer map searching for its structure: a cartographer's), or a negative {@code from} or
     * {@code count} (or {@code from + count} past {@link Integer#MAX_VALUE}) is an {@link IllegalArgumentException}.
     * With the vanilla data, before a world is joined (the game's tags are empty then) it is an {@link IllegalStateException}.
     *
     * @param villager the one the trades are for: some trades depend on its variant (a fisherman's boats by its biome).
     *                 Not changed; it need not be in a world. Not null
     */
    public static List<List<MerchantOffer>> trades(long seed, Identifier tradeSet, int from, int count, Entity villager) {
        // The arguments first, then whether the vanilla data can be worked with.
        TradeDraws.checkRange(from, count);
        return TradeDraws.draws(VanillaWorldgen.registries(), new RandomSequences(), seed, tradeSet, from, count, villager);
    }

    /** {@link #trades(long, Identifier, int, int, Entity)} with a server's trade sets and {@code /random} settings. */
    public static List<List<MerchantOffer>> trades(ServerLevel level, Identifier tradeSet, int from, int count, Entity villager) {
        return TradeDraws.draws(level.registryAccess(), level.getServer().getRandomSequences(), level.getSeed(), tradeSet, from, count, villager);
    }

    /**
     * The seed of the world this client is in, if known: from the highest-priority {@link SeedSource} that knows one.
     * <ol>
     *     <li>{@link SeedSource#INTEGRATED_SERVER}: in singleplayer (also a world this client opened to LAN), the
     *     world's own seed, known on joining.</li>
     *     <li>{@link SeedSource#MANUAL}: entered by the player with {@code /setseed <seed>} (a number, or text, read as
     *     the world creation screen reads it), withdrawn with {@code /setseed clear}.</li>
     *     <li>{@link SeedSource#SEED_COMMAND}: the result of the vanilla {@code /seed}, when the player runs it.</li>
     * </ol>
     * The entered seed and the {@code /seed} result are remembered per server (by its address; a LAN world by its
     * name, a singleplayer world by its folder) and used again on the next visit. Empty when not in a world, when
     * nothing is known, and always on a dedicated server (whose seed is its level's {@link ServerLevel#getSeed()}).
     * The seed is not checked against the world: a wrong one entered by hand is used as given.
     * <p>
     * Any thread. Cheap.
     */
    public static OptionalLong currentSeed() {
        return Seeds.BOOK.current().map(entry -> OptionalLong.of(entry.seed())).orElse(OptionalLong.empty());
    }

    /** Where {@link #currentSeed()} comes from, or empty when it is empty. Any thread. */
    public static Optional<SeedSource> currentSeedSource() {
        return Seeds.BOOK.current().map(entry -> entry.source());
    }

    /**
     * Runs {@code listener} whenever {@link #currentSeed()} or {@link #currentSeedSource()} changes: on joining and
     * leaving a world, on {@code /setseed} and on a {@code /seed} result that changes them. It runs on the client's
     * main thread, right after the change; ask {@link #currentSeed()} there for the new seed. Keep it quick (hand
     * heavy work to a thread of your own). Never removed.
     */
    public static void addSeedListener(Runnable listener) {
        Seeds.BOOK.addListener(listener);
    }

    /**
     * The world type (world preset, e.g. {@code minecraft:normal}) of the world this client is in, if known: to ask
     * with, together with {@link #currentSeed()}.
     * <ul>
     *     <li>In singleplayer (also a world this client opened to LAN): the integrated server's, known on joining.
     *     Empty for a world that no world type makes (a customized flat world).</li>
     *     <li>On a server, which does not tell clients: worked out from the Overworld terrain the client was sent,
     *     compared with what {@code minecraft:normal}, {@code minecraft:large_biomes} and {@code minecraft:amplified}
     *     generate for {@link #currentSeed()}. Empty until the seed is known and enough chunks have arrived (a few
     *     seconds after joining, then the comparison, a few seconds the first time the vanilla data is loaded), in
     *     another dimension until the player is in the Overworld, and when none fits (a wrong seed, another world
     *     type, other generation: {@link #currentWorldCheck()} is then {@link WorldCheck#MISMATCH}). Worked out again
     *     when the seed changes.</li>
     * </ul>
     * The terrain is compared only once a mod has asked for the world type or the check (or listens to them), so the
     * first question on a server is empty: listen with {@link #addWorldTypeListener}. Empty when not in a world, and
     * always on a dedicated server (whose generator is its levels').
     * <p>
     * Any thread. Cheap.
     */
    public static Optional<Identifier> currentWorldType() {
        Seeds.WORLD.ask();
        return Seeds.WORLD.worldType();
    }

    /**
     * Runs {@code listener} whenever {@link #currentWorldType()} changes: on joining and leaving a world, when it is
     * worked out on a server, and when the seed changes there. Same thread and rules as {@link #addSeedListener}.
     */
    public static void addWorldTypeListener(Runnable listener) {
        Seeds.WORLD.addWorldTypeListener(listener);
    }

    /**
     * Whether the vanilla calculation with {@link #currentSeed()} and {@link #currentWorldType()} fits the terrain the
     * client was sent, in the dimension the player is in: the ground height and the biome at two columns of each chunk
     * that arrives. Buildings and dug holes change single columns, so it takes many columns disagreeing for a
     * {@link WorldCheck#MISMATCH}; a mismatch stays until the seed, the world type or the dimension changes, or the
     * player leaves. Notices a seed entered wrong with {@code /setseed}, and a server whose generation differs (data
     * packs, generator mods, chunks made by an older version). Also in singleplayer, where a world with such data packs
     * is a mismatch: ask with the integrated server's level there instead, which has them. A mod's dimension is not
     * checked ({@link WorldCheck#UNKNOWN}).
     * <p>
     * Compared only once a mod has asked (see {@link #currentWorldType()}): the first question is
     * {@link WorldCheck#UNKNOWN}. Any thread. Cheap.
     */
    public static WorldCheck currentWorldCheck() {
        Seeds.WORLD.ask();
        return Seeds.WORLD.check();
    }

    /**
     * Runs {@code listener} whenever {@link #currentWorldCheck()} changes. Same thread and rules as
     * {@link #addSeedListener}.
     */
    public static void addWorldCheckListener(Runnable listener) {
        Seeds.WORLD.addCheckListener(listener);
    }
}
