package seedengine.client;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import seedengine.api.SeedSource;
import seedengine.seed.Seeds;
import seedengine.worldgen.WorldTypes;

/**
 * The client's connection events, messages, ticks and chunks, forwarded from the loader's client entrypoint, feeding
 * {@link Seeds}: the seed, the world type and whether they fit the terrain.
 */
public final class ClientSeeds {
    private static final String SEED_SUCCESS = "commands.seed.success";
    private static final Pattern NUMBER = Pattern.compile("-?\\d{1,20}");

    private ClientSeeds() {
    }

    public static void onJoin(Minecraft mc) {
        OptionalLong integratedSeed = OptionalLong.empty();
        IntegratedServer integrated = mc.getSingleplayerServer();
        if (integrated != null) {
            ServerLevel overworld = integrated.overworld();
            if (overworld != null) integratedSeed = OptionalLong.of(overworld.getSeed());
        }
        WorldTypeDetector.reset();
        TerrainCheck.reset();
        // The world type first: a mod hearing of the seed may ask for both.
        Seeds.WORLD.setWorldType(integrated != null ? WorldTypes.of(integrated) : Optional.empty());
        Seeds.BOOK.onJoin(ServerKey.of(mc), integratedSeed);
    }

    public static void onLeave() {
        WorldTypeDetector.reset();
        TerrainCheck.reset();
        Seeds.BOOK.onLeave();
    }

    /** Every client tick (end): the world type from the terrain and the check, once a mod asked for them. */
    public static void onTick(Minecraft mc) {
        WorldTypeDetector.tick(mc);
        TerrainCheck.tick(mc);
    }

    /**
     * When the seed changes, the world type and the check start over at once (not at the next tick): a mod hearing of
     * the new seed does not read the old seed's verdict.
     */
    public static void listenToSeeds() {
        Seeds.BOOK.addListener(() -> onTick(Minecraft.getInstance()));
    }

    /** A chunk the client was sent. */
    public static void onChunkLoad(LevelChunk chunk) {
        TerrainCheck.onChunkLoad(chunk);
    }

    /** Every system message: picks up the seed from a {@code /seed} result. */
    public static void onGameMessage(Component message) {
        fromSeedCommand(message).ifPresent(seed -> Seeds.BOOK.set(SeedSource.SEED_COMMAND, seed));
    }

    /** The seed in a {@code /seed} result ("Seed: [123]"), or empty for any other message. */
    static OptionalLong fromSeedCommand(Component message) {
        if (!(message.getContents() instanceof TranslatableContents contents) || !SEED_SUCCESS.equals(contents.getKey())) {
            return OptionalLong.empty();
        }
        for (Object arg : contents.getArgs()) {
            OptionalLong seed = firstNumber(arg instanceof Component component ? component.getString() : String.valueOf(arg));
            if (seed.isPresent()) return seed;
        }
        return firstNumber(message.getString());
    }

    private static OptionalLong firstNumber(String text) {
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            try {
                return OptionalLong.of(Long.parseLong(matcher.group()));
            } catch (NumberFormatException ignored) {
                // Too large for a long; keep looking.
            }
        }
        return OptionalLong.empty();
    }
}
