package seedengine.api;

/**
 * Where the client learned the seed of the world it is in, highest priority first: the seed in use is the one from the
 * highest-priority source that knows one ({@link SeedEngineApi#currentSeed()}).
 */
public enum SeedSource {
    /** Singleplayer, also a world this client opened to LAN: read from the integrated server, so always right. */
    INTEGRATED_SERVER,
    /** Typed in by the player with {@code /setseed}. Remembered per server. */
    MANUAL,
    /** Read from the result of the vanilla {@code /seed} command. Remembered per server. */
    SEED_COMMAND;

    /** Sources that are remembered per server, so the seed is known again on the next visit. */
    public boolean isRemembered() {
        return this != INTEGRATED_SERVER;
    }
}
