package seedengine.api;

/**
 * Whether the vanilla calculation with the client's seed and world type ({@link SeedEngineApi#currentSeed()},
 * {@link SeedEngineApi#currentWorldType()}) fits the terrain the client was sent ({@link SeedEngineApi#currentWorldCheck()}).
 */
public enum WorldCheck {
    /** Not in a world, no seed known, or not enough of the terrain compared yet. */
    UNKNOWN,
    /** The terrain is what the seed and the world type generate. */
    MATCHES,
    /**
     * The terrain is not what the seed and the world type generate: a wrong seed, a world type the vanilla data does
     * not have, or a server whose generation differs (data packs, generator mods, chunks made by an older version).
     * Answers asked with the seed may not be where the world has them.
     */
    MISMATCH
}
