package seedengine.platform.services;

import java.nio.file.Path;

/** Loader-specific lookups that common code needs. Each loader provides one through ServiceLoader. */
public interface IPlatformHelper {

    /** The name of the mod loader, for logging. */
    String getPlatformName();

    /** The loader's config directory. */
    Path getConfigDir();

    /** Whether a mod with this id is loaded, for optional links to other mods. */
    boolean isModLoaded(String modId);
}
