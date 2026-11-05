package seedengine.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import seedengine.api.ServerSeeds;
import seedengine.worldgen.VanillaWorldgen;
import seedengine.worldgen.WorldgenContext;

/** The worlds asked about, kept so that the next question about the same one reuses what was built and worked out. */
public final class Worlds {
    /** How many worlds built from the vanilla data are kept, the most recently used. */
    private static final int KEEP_VANILLA = 12;

    private record Key(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds) {
    }

    private static final Map<Key, World> VANILLA = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, World> eldest) {
            return size() > KEEP_VANILLA;
        }
    };
    /** The servers that stopped (or began to), however they did: none of their levels is kept again. */
    private static final java.util.Set<MinecraftServer> STOPPED = java.util.Collections.newSetFromMap(new WeakHashMap<>());
    /** A server's levels, kept while the server keeps them. */
    private static final Map<ServerLevel, World> SERVER = new WeakHashMap<>();

    private Worlds() {
    }

    public static World vanilla(long seed, Identifier worldType, ResourceKey<Level> dimension) {
        return vanilla(seed, worldType, dimension, ServerSeeds.NONE);
    }

    public static World vanilla(long seed, Identifier worldType, ResourceKey<Level> dimension, ServerSeeds seeds) {
        Key key = new Key(seed, worldType, dimension, seeds);
        synchronized (VANILLA) {
            World known = VANILLA.get(key);
            if (known != null) return known;
        }
        World made = new World(VanillaWorldgen.context(seed, dimension, worldType, seeds));
        synchronized (VANILLA) {
            World raced = VANILLA.putIfAbsent(key, made);
            return raced != null ? raced : made;
        }
    }

    public static World server(ServerLevel level) {
        synchronized (SERVER) {
            // A server stopping or stopped (asked by a thread that has not noticed yet): answered, nothing kept for it.
            // Checked here, where forgetting the server takes its levels out, so none is put back after.
            MinecraftServer server = level.getServer();
            if (!server.isRunning() || server.isStopped() || STOPPED.contains(server)) return new World(WorldgenContext.of(level), false);
            return SERVER.computeIfAbsent(level, l -> new World(WorldgenContext.of(l)));
        }
    }

    /** Drops what was worked out for a server's levels (it stopped). */
    public static void forget(MinecraftServer server) {
        java.util.List<World> dropped = new java.util.ArrayList<>();
        synchronized (SERVER) {
            STOPPED.add(server);
            SERVER.entrySet().removeIf(entry -> {
                if (entry.getKey().getServer() != server) return false;
                dropped.add(entry.getValue());
                return true;
            });
        }
        dropped.forEach(World::forgotten);
    }
}
