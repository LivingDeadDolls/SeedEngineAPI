package seedengine.seed;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import seedengine.api.WorldCheck;

/**
 * How many columns of terrain have to disagree with a calculation before it counts as not fitting. Buildings and dug
 * holes change single columns, so it takes many.
 */
public final class TerrainFit {
    /** Columns compared before a verdict. */
    public static final int MIN_SAMPLES = 48;
    /** Columns compared before choosing a world type. */
    public static final int SAMPLES_FOR_WORLD_TYPE = 64;
    private static final double MISMATCH_RATIO = 0.5;
    private static final double MATCH_RATIO = 0.2;
    private static final double WORLD_TYPE_FITS = 0.25;

    private TerrainFit() {
    }

    /**
     * The verdict after one more column: a mismatch stays, too few columns are unknown, and between the two ratios the
     * verdict so far stays.
     */
    public static WorldCheck verdict(WorldCheck before, int different, int samples) {
        if (before == WorldCheck.MISMATCH) return before;
        if (samples < MIN_SAMPLES) return WorldCheck.UNKNOWN;
        double ratio = (double) different / samples;
        if (ratio > MISMATCH_RATIO) return WorldCheck.MISMATCH;
        if (ratio < MATCH_RATIO) return WorldCheck.MATCHES;
        return before;
    }

    /**
     * The candidate with the fewest disagreeing columns, the earlier one when they tie, if few enough disagree.
     *
     * @param different how many of the {@code samples} columns disagree, per candidate
     */
    public static <T> Optional<T> bestFit(List<T> candidates, Map<T, Integer> different, int samples) {
        T best = null;
        int fewest = Integer.MAX_VALUE;
        for (T candidate : candidates) {
            int count = different.get(candidate);
            if (count < fewest) {
                fewest = count;
                best = candidate;
            }
        }
        return best != null && fewest <= samples * WORLD_TYPE_FITS ? Optional.of(best) : Optional.empty();
    }
}
