package seedengine.seed;

import java.nio.file.Path;
import seedengine.Constants;
import seedengine.platform.Services;

/**
 * The client's seeds, remembered in config/seedengine/seeds.json. Touches no game-client classes: on a dedicated
 * server it stays empty.
 */
public final class Seeds {
    public static final SeedBook BOOK = new SeedBook(new SeedStore(file()));
    /** The world type and whether the seed fits the terrain, not remembered. */
    public static final WorldBook WORLD = new WorldBook();

    private Seeds() {
    }

    public static Path file() {
        return Services.PLATFORM.getConfigDir().resolve(Constants.MOD_ID).resolve("seeds.json");
    }
}
