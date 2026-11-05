package seedengine.client;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import seedengine.Constants;
import seedengine.api.WorldCheck;
import seedengine.engine.World;
import seedengine.engine.Worlds;
import seedengine.seed.SeedBook;
import seedengine.seed.Seeds;
import seedengine.seed.TerrainFit;

/**
 * Notices when the vanilla calculation with the seed and the world type in use does not fit the world: a wrong seed,
 * a world type the vanilla data does not have, a server's data packs or generator mods, or chunks made by an older
 * version. As chunks arrive, a few columns ({@link TerrainSample}) are compared with what the calculation gives for
 * them, in the dimension the player is in.
 */
final class TerrainCheck {
    /** The terrain comparisons, here and in {@link WorldTypeDetector}: off the client thread, one at a time. */
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Seed Engine API Terrain Check");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    /** The dimensions the vanilla world types have; a mod's dimension is not checked. */
    private static final Set<ResourceKey<Level>> VANILLA_DIMENSIONS = Set.of(Level.OVERWORLD, Level.NETHER, Level.END);

    /** What is compared: the calculation for one visit, seed, world type (null when none is known) and dimension. */
    private record Key(int visit, long seed, Identifier worldType, ResourceKey<Level> dimension) {
    }

    private static final AtomicInteger SAMPLES = new AtomicInteger();
    private static final AtomicInteger DIFFERENT = new AtomicInteger();
    private static int visit;
    private static volatile Key checking;
    private static volatile WorldCheck verdict = WorldCheck.UNKNOWN;

    private TerrainCheck() {
    }

    /** Client thread: a world joined or left. */
    static void reset() {
        visit++;
        restart(null);
    }

    /** Client tick: starts over when the calculation changes, then compares the chunks already loaded. */
    static void tick(Minecraft mc) {
        if (!Seeds.WORLD.asked()) return;
        Key key = key(mc);
        if (key == null ? checking == null : key.equals(checking)) return;
        restart(key);
        if (key == null) return;
        if (key.worldType() == null) {
            // No world type fits the terrain with this seed (or the integrated server's is none the game has).
            publish(key, WorldCheck.MISMATCH);
            return;
        }
        ChunkPos center = mc.player.chunkPosition();
        int radius = mc.options.getEffectiveRenderDistance();
        for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) onChunkLoad(chunk);
            }
        }
    }

    /** Client thread: a chunk arrived from the server. */
    static void onChunkLoad(LevelChunk chunk) {
        Key key = checking;
        if (key == null || key.worldType() == null || key.dimension() != chunk.getLevel().dimension() || verdict == WorldCheck.MISMATCH) return;
        for (TerrainSample sample : TerrainSample.of(chunk)) {
            WORKER.execute(() -> compare(key, sample));
        }
    }

    /** The calculation to compare with now, or null while there is none (no seed, the world type not worked out yet). */
    private static Key key(Minecraft mc) {
        if (mc.level == null || mc.player == null || !VANILLA_DIMENSIONS.contains(mc.level.dimension())) return null;
        Optional<SeedBook.Entry> seed = Seeds.BOOK.current();
        if (seed.isEmpty() || !WorldTypeDetector.settled(mc)) return null;
        return new Key(visit, seed.get().seed(), Seeds.WORLD.worldType().orElse(null), mc.level.dimension());
    }

    private static void restart(Key key) {
        checking = key;
        verdict = WorldCheck.UNKNOWN;
        SAMPLES.set(0);
        DIFFERENT.set(0);
        Seeds.WORLD.setCheck(WorldCheck.UNKNOWN);
    }

    private static void compare(Key key, TerrainSample sample) {
        if (key != checking) return;
        boolean differs;
        try {
            World world = Worlds.vanilla(key.seed(), key.worldType(), key.dimension());
            differs = sample.differsFrom(world);
        } catch (IllegalArgumentException e) {
            // A world type the vanilla data does not have (a data pack's, in singleplayer): it cannot be calculated.
            Constants.warnOnce("world type " + key.worldType(), "The vanilla data has no world type {}: the terrain is not what it calculates", key.worldType());
            differs = true;
        } catch (RuntimeException e) {
            Constants.warnOnce("terrain check", "Could not compare the terrain with the calculation", e);
            return;
        }
        if (key != checking) return;
        int samples = SAMPLES.incrementAndGet();
        int different = differs ? DIFFERENT.incrementAndGet() : DIFFERENT.get();
        WorldCheck now = TerrainFit.verdict(verdict, different, samples);
        if (now == verdict) return;
        verdict = now;
        if (now == WorldCheck.MISMATCH) {
            Constants.LOG.warn("Terrain does not match the calculation ({} of {} columns differ, seed {}, world type {}, {})",
                    different, samples, key.seed(), key.worldType(), key.dimension().identifier());
        }
        publish(key, now);
    }

    private static void publish(Key key, WorldCheck now) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (checking == key) Seeds.WORLD.setCheck(now);
        });
    }
}
