package seedengine;

import net.fabricmc.api.ModInitializer;

/** Both sides (the computing works the same on a client and a server). Touches no game-client classes, so a dedicated server can load it. */
public final class SeedEngineFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        SeedEngineCommon.init();
    }
}
