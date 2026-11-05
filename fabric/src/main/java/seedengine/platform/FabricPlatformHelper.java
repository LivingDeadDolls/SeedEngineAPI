package seedengine.platform;

import seedengine.platform.services.IPlatformHelper;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class FabricPlatformHelper implements IPlatformHelper {

    @Override
    public String getPlatformName() {
        return "Fabric";
    }

    @Override
    public Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public boolean isModLoaded(String modId) {
        return FabricLoader.getInstance().isModLoaded(modId);
    }
}
