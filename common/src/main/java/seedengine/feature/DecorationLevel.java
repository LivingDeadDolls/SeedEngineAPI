package seedengine.feature;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import seedengine.Constants;
import seedengine.worldgen.ChunkSteps;
import seedengine.shape.GhostCapture;
import seedengine.api.Spawner;
import seedengine.worldgen.TerrainChunks;
import seedengine.worldgen.WorldgenContext;

/**
 * A stand-in {@link WorldGenLevel} for decorating chunks one after another: it reads the terrain worked out by
 * {@link TerrainChunks} and keeps what features and structures place on top of it, apart from the terrain. Like the
 * world's generation region, the chunk being decorated writes only into itself and the chunks next to it.
 * Block entities are real ones, made when asked for, so that features that write into them (spawners, chests drawing
 * their loot seed) use their random numbers as in the world.
 */
final class DecorationLevel implements GhostCapture.StandIn {
    private final WorldgenContext context;
    private final TerrainChunks terrain;
    private final Long2ObjectOpenHashMap<BlockState> placed = new Long2ObjectOpenHashMap<>();
    /** The chunk whose decoration placed each block (packed chunk position). */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap writer = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    /** When each block was placed (a count of placements). */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap placedWhen = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private long clock;
    /** Blocks more than one chunk placed: which chunk's stays depends on the order they were decorated in. */
    private final LongOpenHashSet contested = new LongOpenHashSet();
    /**
     * A trial placement, kept apart and dropped afterwards (see {@link #placeTrial}): as if the chunk being decorated
     * came after the chunks {@link #trialAfter} accepts and before the others (it sees what those placed, and of its own
     * only what came before {@link #trialBefore}).
     */
    private boolean alone;
    private java.util.function.LongPredicate trialAfter;
    private long trialBefore;
    /** The other chunks whose blocks the last trial read (shown or not): the ones its outcome may depend on. */
    private final LongOpenHashSet trialRead = new LongOpenHashSet();
    private final Long2ObjectOpenHashMap<BlockState> alonePlaced = new Long2ObjectOpenHashMap<>();
    private final Map<Long, BlockEntity> aloneBlockEntities = new HashMap<>();
    private final Map<Long, BlockEntity> blockEntities = new HashMap<>();
    /** Block entities made while a dungeon was being placed. */
    private final LongOpenHashSet dungeonBlockEntities = new LongOpenHashSet();
    private final Map<String, Boolean> unknown = new HashMap<>();
    /** The region's random of the chunk being decorated ({@code level.getRandom()}). */
    RandomSource random = RandomSource.create(0L);
    private WorldGenLevel proxy;
    boolean placingDungeon;
    /** The chunk being decorated. */
    ChunkPos center = new ChunkPos(0, 0);

    DecorationLevel(TerrainChunks terrain) {
        this.terrain = terrain;
        this.context = terrain.context();
    }

    @Override
    public WorldgenContext context() {
        return context;
    }

    WorldGenLevel level() {
        if (proxy == null) {
            proxy = (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, this);
        }
        return proxy;
    }

    /**
     * Whether a chunk other than the given one placed anything in a box (now, or before being placed over): what is
     * there then depends on the order the chunks were decorated in.
     */
    boolean touchedByOthers(ChunkPos chunk, BlockPos min, BlockPos max) {
        long own = chunk.pack();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            long key = pos.asLong();
            if (contested.contains(key) || (writer.containsKey(key) && writer.get(key) != own)) return true;
        }
        return false;
    }

    /** The count of placements so far, to tell what a chunk placed before a moment from what it placed after. */
    long now() {
        return clock;
    }

    /**
     * The spawners a placement would leave if the chunk being decorated came after the chunks {@code after} accepts
     * (packed chunk positions) and before the others; it sees what the chunk itself placed before {@code before}.
     * Nothing it places stays.
     */
    List<Spawner> placeTrial(java.util.function.LongPredicate after, long before, Runnable placement) {
        alone = true;
        trialAfter = after;
        trialBefore = before;
        trialRead.clear();
        try {
            try {
                placement.run();
            } catch (RuntimeException | LinkageError e) {
                Constants.warnOnce("dungeon trial", "Could not place a dungeon alone in chunk {}, other orders may be missed: {}", center, e.toString());
            }
            List<Spawner> spawners = new ArrayList<>();
            aloneBlockEntities.forEach((key, blockEntity) -> {
                BlockState state = alonePlaced.get(key.longValue());
                if (state == null || state.getBlock() != blockEntity.getBlockState().getBlock()) return;
                try {
                    Spawner.of(blockEntity, context.registries()).ifPresent(spawners::add);
                } catch (RuntimeException e) {
                    Constants.LOG.debug("Could not read the spawner at {}: {}", BlockPos.of(key), e.toString());
                }
            });
            return spawners;
        } finally {
            alone = false;
            alonePlaced.clear();
            aloneBlockEntities.clear();
        }
    }

    /** The spawners the dungeons placed in a chunk, still standing. */
    List<Spawner> dungeonSpawners(ChunkPos chunk) {
        List<Spawner> spawners = new ArrayList<>();
        for (long key : dungeonBlockEntities) {
            if (BlockPos.getX(key) >> 4 != chunk.x() || BlockPos.getZ(key) >> 4 != chunk.z()) continue;
            BlockEntity blockEntity = blockEntities.get(key);
            BlockState state = placed.get(key);
            if (blockEntity == null || state == null || state.getBlock() != blockEntity.getBlockState().getBlock()) continue;
            try {
                Spawner.of(blockEntity, context.registries()).ifPresent(spawners::add);
            } catch (RuntimeException e) {
                Constants.LOG.debug("Could not read the spawner at {}: {}", BlockPos.of(key), e.toString());
            }
        }
        spawners.sort(java.util.Comparator.comparingLong(spawner -> spawner.pos().asLong()));
        return spawners;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object self, Method method, Object[] args) throws Throwable {
        switch (method.getName()) {
            case "setBlock": {
                BlockPos pos = (BlockPos) args[0];
                if (!canWrite(pos)) return false;
                write(pos.asLong(), (BlockState) args[1]);
                return true;
            }
            case "removeBlock":
            case "destroyBlock":
                if (!canWrite((BlockPos) args[0])) return false;
                write(((BlockPos) args[0]).asLong(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
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
                if (args.length == 3 && args[0] instanceof Heightmap.Types type) {
                    int x = (Integer) args[1];
                    int z = (Integer) args[2];
                    return height(type, x, z);
                }
                break;
            case "getMinY":
                return context.height().getMinY();
            case "getChunk":
                if (args != null && args.length >= 2 && args[0] instanceof Integer x && args[1] instanceof Integer z) {
                    return terrain.terrain(new ChunkPos(x, z));
                }
                if (args != null && args.length == 1 && args[0] instanceof BlockPos pos) {
                    return terrain.terrain(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4));
                }
                break;
            case "hasChunk":
                return true;
            case "getBiome":
                return terrain.biomeManager().getBiome((BlockPos) args[0]);
            case "getBiomeManager":
                return terrain.biomeManager();
            case "getBlockEntity":
                // The one by type (an Optional) is the game's default, which asks this one.
                if (args.length == 1 && args[0] instanceof BlockPos pos) return blockEntityAt(pos);
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
            case "setCurrentlyGenerating":
            case "blockUpdated":
                return null;
            case "ensureCanWrite":
                return canWrite((BlockPos) args[0]);
            case "isClientSide":
                return false;
            case "hashCode":
                return System.identityHashCode(self);
            case "equals":
                return self == args[0];
            case "toString":
                return "Seed Engine decoration level";
            default:
                break;
        }
        if (method.isDefault()) return InvocationHandler.invokeDefault(self, method, args);
        if (unknown.putIfAbsent(method.getName(), Boolean.TRUE) == null) {
            Constants.LOG.debug("Decoration level does not answer {}", method.getName());
        }
        Class<?> type = method.getReturnType();
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == void.class) return null;
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        return 0;
    }

    /** As the world's generation region while decorating: the chunk and the chunks next to it. */
    private boolean canWrite(BlockPos pos) {
        return Math.abs((pos.getX() >> 4) - center.x()) <= 1 && Math.abs((pos.getZ() >> 4) - center.z()) <= 1;
    }

    private void write(long key, BlockState state) {
        if (alone) {
            alonePlaced.put(key, state);
        } else {
            placed.put(key, state);
            if (writer.containsKey(key) && writer.get(key) != center.pack()) contested.add(key);
            writer.put(key, center.pack());
            placedWhen.put(key, ++clock);
            for (Heightmap.Types type : ChunkSteps.DECORATION_HEIGHTMAPS) raise(type, key, state);
        }
    }

    /**
     * The heightmaps a chunk being decorated keeps up to date as blocks are set in it (as {@code ProtoChunk} does for
     * the final ones; the world-generation ones stay the terrain's): later features are placed by them (a tree on the
     * ground).
     */
    private final Map<Heightmap.Types, it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap> heights = new java.util.EnumMap<>(Heightmap.Types.class);

    private int height(Heightmap.Types type, int x, int z) {
        var columns = heights.get(type);
        long column = ChunkPos.pack(x, z);
        if (columns != null && columns.containsKey(column)) return columns.get(column);
        return terrain.terrain(new ChunkPos(x >> 4, z >> 4)).getHeight(type, x & 15, z & 15) + 1;
    }

    /** As {@code Heightmap.update}: a block the heightmap counts above the top raises it, the top block gone lowers it. */
    private void raise(Heightmap.Types type, long key, BlockState state) {
        int x = BlockPos.getX(key);
        int y = BlockPos.getY(key);
        int z = BlockPos.getZ(key);
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
        heights.computeIfAbsent(type, t -> new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap()).put(ChunkPos.pack(x, z), now);
    }

    /** The other chunks whose blocks the last trial read. */
    LongOpenHashSet trialRead() {
        return new LongOpenHashSet(trialRead);
    }

    /** What a block is now: placed, or the terrain. In a trial, only what the chunks it comes after placed is there. */
    private BlockState placedAt(long key) {
        if (alone) {
            BlockState state = alonePlaced.get(key);
            if (state != null) return state;
            if (!writer.containsKey(key)) return null;
            long by = writer.get(key);
            if (by == center.pack()) return placedWhen.get(key) <= trialBefore ? placed.get(key) : null;
            trialRead.add(by);
            return trialAfter.test(by) ? placed.get(key) : null;
        }
        return placed.get(key);
    }

    private BlockState stateAt(BlockPos pos) {
        BlockState state = placedAt(pos.asLong());
        if (state != null) return state;
        return terrain.terrain(new ChunkPos(pos.getX() >> 4, pos.getZ() >> 4)).getBlockState(pos);
    }

    /** A real block entity for a block placed here that has one, made once and kept. */
    private BlockEntity blockEntityAt(BlockPos pos) {
        long key = pos.asLong();
        BlockState state = placedAt(key);
        if (state == null || !(state.getBlock() instanceof EntityBlock block)) return null;
        Map<Long, BlockEntity> entities = alone ? aloneBlockEntities : blockEntities;
        BlockEntity existing = entities.get(key);
        if (existing != null && existing.getBlockState().getBlock() == state.getBlock()) return existing;
        BlockEntity made = block.newBlockEntity(pos.immutable(), state);
        if (made != null) {
            entities.put(key, made);
            if (!alone) {
                if (placingDungeon) dungeonBlockEntities.add(key);
                else dungeonBlockEntities.remove(key);
            }
        }
        return made;
    }
}
