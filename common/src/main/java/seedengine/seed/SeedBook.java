package seedengine.seed;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import seedengine.api.SeedSource;

/**
 * The seeds known for the world the client is in, one per source. The seed in use is the one from the
 * highest-priority source ({@link SeedSource} order). The remembered sources are kept per server in a {@link SeedStore}.
 * Any thread may read; listeners run on the thread that made the change.
 */
public final class SeedBook {
    public record Entry(long seed, SeedSource source) {
    }

    private final SeedStore store;
    private final Map<SeedSource, Long> seeds = new EnumMap<>(SeedSource.class);
    private final List<Runnable> listeners = new ArrayList<>();
    private String serverKey;

    public SeedBook(SeedStore store) {
        this.store = store;
    }

    /** Joining a world: forgets the previous world's seeds and takes the ones remembered for this one. */
    public void onJoin(String key, OptionalLong integratedSeed) {
        Optional<Entry> before;
        synchronized (seeds) {
            before = currentLocked();
            serverKey = key;
            seeds.clear();
            seeds.putAll(store.load(key));
            if (integratedSeed.isPresent()) seeds.put(SeedSource.INTEGRATED_SERVER, integratedSeed.getAsLong());
        }
        changedFrom(before);
    }

    public void onLeave() {
        Optional<Entry> before;
        synchronized (seeds) {
            before = currentLocked();
            serverKey = null;
            seeds.clear();
        }
        changedFrom(before);
    }

    /** Records a seed from a source. Ignored while not in a world. */
    public void set(SeedSource source, long seed) {
        Optional<Entry> before;
        synchronized (seeds) {
            if (serverKey == null) return;
            before = currentLocked();
            Long old = seeds.put(source, seed);
            if (old != null && old == seed) return;
            if (source.isRemembered()) store.save(serverKey, seeds);
        }
        changedFrom(before);
    }

    /** Forgets the seed from a source; false if there was none. */
    public boolean clear(SeedSource source) {
        Optional<Entry> before;
        synchronized (seeds) {
            if (serverKey == null) return false;
            before = currentLocked();
            if (seeds.remove(source) == null) return false;
            if (source.isRemembered()) store.save(serverKey, seeds);
        }
        changedFrom(before);
        return true;
    }

    /** The seed in use and where it came from, or empty if no source knows one. */
    public Optional<Entry> current() {
        synchronized (seeds) {
            return currentLocked();
        }
    }

    public OptionalLong get(SeedSource source) {
        synchronized (seeds) {
            Long seed = seeds.get(source);
            return seed == null ? OptionalLong.empty() : OptionalLong.of(seed);
        }
    }

    /** Runs whenever the seed in use or its source changes. */
    public void addListener(Runnable listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    private Optional<Entry> currentLocked() {
        for (SeedSource source : SeedSource.values()) {
            Long seed = seeds.get(source);
            if (seed != null) return Optional.of(new Entry(seed, source));
        }
        return Optional.empty();
    }

    private void changedFrom(Optional<Entry> before) {
        if (current().equals(before)) return;
        List<Runnable> copy;
        synchronized (listeners) {
            copy = List.copyOf(listeners);
        }
        copy.forEach(Runnable::run);
    }
}
