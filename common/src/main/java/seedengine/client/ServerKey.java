package seedengine.client;

import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Names the world the player is in, for keeping seeds apart per server. The same server reached through another
 * address (IP instead of DNS name, an alternative domain) gets another key.
 */
final class ServerKey {
    private static final String UNKNOWN = "unknown";

    private ServerKey() {
    }

    static String of(Minecraft mc) {
        IntegratedServer integrated = mc.getSingleplayerServer();
        if (integrated != null) {
            Path folder = integrated.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
            return "sp_" + sanitize(folder == null ? UNKNOWN : folder.toString());
        }
        ServerData data = mc.getCurrentServer();
        if (data == null) return UNKNOWN;
        // A LAN world gets a new port each time it is opened, so its announced name identifies it instead.
        if (data.isLan()) return "lan_" + sanitize(data.name);
        String prefix = data.isRealm() ? "realms_" : "";
        return prefix + forAddress(data.ip);
    }

    /** "Play.Example.net" becomes "play.example.net_25565"; the default port is filled in. */
    private static String forAddress(String address) {
        ServerAddress parsed = ServerAddress.parseString(address.trim());
        String host = parsed.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        return sanitize(host + "_" + parsed.getPort());
    }

    private static String sanitize(String text) {
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
        return cleaned.isEmpty() ? UNKNOWN : cleaned;
    }
}
