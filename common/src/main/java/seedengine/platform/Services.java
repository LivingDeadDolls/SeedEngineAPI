package seedengine.platform;

import seedengine.platform.services.IPlatformHelper;
import java.util.ServiceLoader;

/**
 * Finds the loader-specific implementations of common interfaces at runtime.
 * Each loader lists its implementation in META-INF/services/&lt;interface name&gt;.
 */
public final class Services {
    public static final IPlatformHelper PLATFORM = load(IPlatformHelper.class);

    private Services() {
    }

    private static <T> T load(Class<T> clazz) {
        return ServiceLoader.load(clazz, Services.class.getClassLoader())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No implementation of " + clazz.getName()));
    }
}
