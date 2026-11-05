package seedengine;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Constants {
    public static final String MOD_ID = "seedengine";
    public static final String MOD_NAME = "Seed Engine API";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_NAME);

    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private Constants() {
    }

    /**
     * A warning logged once per kind (e.g. per feature that could not be placed): what keeps an answer from being
     * complete is seen in the log, without a line for every chunk.
     */
    public static void warnOnce(String kind, String message, Object... arguments) {
        if (WARNED.add(kind)) LOG.warn(message + " (logged once)", arguments);
    }
}
