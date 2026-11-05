package seedengine.seed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import seedengine.api.WorldCheck;

/**
 * The world type of the world the client is in, and whether the seed and the world type fit its terrain. Set on the
 * client's main thread; any thread may read. Touches no game-client classes: on a dedicated server it stays empty.
 */
public final class WorldBook {
    private final List<Runnable> typeListeners = new ArrayList<>();
    private final List<Runnable> checkListeners = new ArrayList<>();
    private volatile Identifier worldType;
    private volatile WorldCheck check = WorldCheck.UNKNOWN;
    private volatile boolean asked;

    public Optional<Identifier> worldType() {
        return Optional.ofNullable(worldType);
    }

    public WorldCheck check() {
        return check;
    }

    /** A mod asked for the world type or the check: from now on the terrain is compared. */
    public void ask() {
        asked = true;
    }

    /**
     * Whether a mod has asked for the world type or the check (or listens): the terrain is compared only then, as it
     * takes the vanilla data (loaded once, a few seconds) and some work for each chunk.
     */
    public boolean asked() {
        return asked;
    }

    /** Client thread. */
    public void setWorldType(Optional<Identifier> type) {
        Identifier now = type.orElse(null);
        if (Objects.equals(worldType, now)) return;
        worldType = now;
        run(typeListeners);
    }

    /** Client thread. */
    public void setCheck(WorldCheck now) {
        if (check == now) return;
        check = now;
        run(checkListeners);
    }

    public void addWorldTypeListener(Runnable listener) {
        add(typeListeners, listener);
    }

    public void addCheckListener(Runnable listener) {
        add(checkListeners, listener);
    }

    private void add(List<Runnable> listeners, Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        asked = true;
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    private static void run(List<Runnable> listeners) {
        List<Runnable> copy;
        synchronized (listeners) {
            copy = List.copyOf(listeners);
        }
        copy.forEach(Runnable::run);
    }
}
