package seedengine.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import seedengine.Constants;
import seedengine.engine.Worlds;
import seedengine.seed.SeedBook;
import seedengine.seed.Seeds;
import seedengine.seed.TerrainFit;

/**
 * Works out a multiplayer server's world type (Default, Large Biomes, Amplified), which servers do not tell clients,
 * from the terrain around the player: the Overworld's ground heights and biomes are compared with what each world
 * type generates for the seed, and the one that fits wins. Amplified shows in the heights, Large Biomes in the
 * biomes. Worked out again when the seed changes; in singleplayer the world type is the integrated server's instead.
 */
final class WorldTypeDetector {
    /** The world types tried, in order of preference when they fit equally. */
    private static final List<Identifier> CANDIDATES = List.of(WorldPresets.NORMAL.identifier(), WorldPresets.LARGE_BIOMES.identifier(),
            WorldPresets.AMPLIFIED.identifier());

    /** What the detection is for: one visit to a server, one seed. */
    private record Key(int visit, long seed) {
    }

    private static final List<TerrainSample> SAMPLES = new ArrayList<>();
    private static int visit;
    private static Key forKey;
    private static boolean working;
    private static volatile boolean gaveUp;

    private WorldTypeDetector() {
    }

    /** Whether the world type is final: singleplayer, or worked out (or given up on) here for the seed in use. */
    static boolean settled(Minecraft mc) {
        return mc.getSingleplayerServer() != null || Seeds.WORLD.worldType().isPresent() || gaveUp;
    }

    /** Whether no world type fits the terrain with the seed in use. */
    static boolean gaveUp() {
        return gaveUp;
    }

    /** Client thread: a world joined or left. */
    static void reset() {
        visit++;
        restart(null);
    }

    /** Client tick: collects columns from the loaded Overworld chunks and, once there are enough, decides. */
    static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() != null || !Seeds.WORLD.asked()) return;
        Optional<SeedBook.Entry> seed = Seeds.BOOK.current();
        Key key = seed.map(entry -> new Key(visit, entry.seed())).orElse(null);
        if (!Objects.equals(key, forKey)) restart(key);
        if (key == null || Seeds.WORLD.worldType().isPresent() || gaveUp || working || mc.level.dimension() != Level.OVERWORLD) return;
        if (SAMPLES.size() < TerrainFit.SAMPLES_FOR_WORLD_TYPE) {
            collect(mc);
            if (SAMPLES.size() < TerrainFit.SAMPLES_FOR_WORLD_TYPE) return;
        }
        working = true;
        List<TerrainSample> samples = List.copyOf(SAMPLES);
        TerrainCheck.WORKER.execute(() -> {
            Optional<Identifier> fits = decide(key.seed(), samples);
            mc.execute(() -> {
                if (!key.equals(forKey)) return;
                working = false;
                if (fits.isPresent()) {
                    Seeds.WORLD.setWorldType(fits);
                } else {
                    gaveUp = true;
                }
            });
        });
    }

    private static void restart(Key key) {
        forKey = key;
        gaveUp = false;
        working = false;
        SAMPLES.clear();
        Seeds.WORLD.setWorldType(Optional.empty());
    }

    private static void collect(Minecraft mc) {
        ChunkPos center = mc.player.chunkPosition();
        int radius = mc.options.getEffectiveRenderDistance();
        SAMPLES.clear();
        for (int z = center.z() - radius; z <= center.z() + radius; z++) {
            for (int x = center.x() - radius; x <= center.x() + radius; x++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false);
                if (chunk != null) SAMPLES.addAll(TerrainSample.of(chunk));
            }
        }
    }

    private static Optional<Identifier> decide(long seed, List<TerrainSample> samples) {
        Map<Identifier, Integer> different = new HashMap<>();
        StringBuilder log = new StringBuilder();
        try {
            for (Identifier type : CANDIDATES) {
                var world = Worlds.vanilla(seed, type, Level.OVERWORLD);
                int count = 0;
                for (TerrainSample sample : samples) {
                    if (sample.differsFrom(world)) count++;
                }
                different.put(type, count);
                log.append(type.getPath()).append(' ').append(count).append('/').append(samples.size()).append("  ");
            }
        } catch (RuntimeException e) {
            Constants.LOG.warn("Could not compare the terrain with the vanilla world types", e);
            return Optional.empty();
        }
        Optional<Identifier> fits = TerrainFit.bestFit(CANDIDATES, different, samples.size());
        if (fits.isPresent()) {
            Constants.LOG.info("World type of this server: {} ({})", fits.get(), log.toString().strip());
        } else {
            Constants.LOG.info("No vanilla world type fits this server's terrain with the seed in use ({})", log.toString().strip());
        }
        return fits;
    }
}
