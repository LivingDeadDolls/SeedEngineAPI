package seedengine;

import seedengine.platform.Services;

/** Loader-independent startup, called from each loader's entrypoint. */
public final class SeedEngineCommon {
    private SeedEngineCommon() {
    }

    /** Both sides, also on a dedicated server: touch no game-client classes here. */
    public static void init() {
        Constants.LOG.info("{} loaded on {}", Constants.MOD_NAME, Services.PLATFORM.getPlatformName());
    }
}
