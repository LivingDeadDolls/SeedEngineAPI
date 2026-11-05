package seedengine.seed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import seedengine.Constants;
import seedengine.api.SeedSource;

/** The remembered seeds ({@link SeedSource#isRemembered()}) of every server, in one JSON file keyed by server. */
public final class SeedStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path file;

    public SeedStore(Path file) {
        this.file = file;
    }

    Map<SeedSource, Long> load(String serverKey) {
        Map<SeedSource, Long> seeds = new EnumMap<>(SeedSource.class);
        JsonObject server = readAll().getAsJsonObject(serverKey);
        if (server == null) return seeds;
        for (SeedSource source : SeedSource.values()) {
            if (source.isRemembered() && server.has(source.name())) {
                try {
                    seeds.put(source, server.get(source.name()).getAsLong());
                } catch (RuntimeException e) {
                    Constants.LOG.warn("Ignoring an unreadable {} seed for {}", source, serverKey);
                }
            }
        }
        return seeds;
    }

    void save(String serverKey, Map<SeedSource, Long> seeds) {
        JsonObject all = readAll();
        JsonObject server = new JsonObject();
        seeds.forEach((source, seed) -> {
            if (source.isRemembered()) server.addProperty(source.name(), seed);
        });
        if (server.isEmpty()) all.remove(serverKey);
        else all.add(serverKey, server);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(all), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Constants.LOG.error("Could not save {}", file, e);
        }
    }

    private JsonObject readAll() {
        if (!Files.isRegularFile(file)) return new JsonObject();
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            Constants.LOG.error("Could not read {}; starting from an empty file", file, e);
            return new JsonObject();
        }
    }
}
