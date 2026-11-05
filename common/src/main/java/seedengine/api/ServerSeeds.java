package seedengine.api;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import net.minecraft.resources.Identifier;

/**
 * Seeds a server uses for parts of its generation in place of what the world seed gives, which clients are not sent:
 * a Spigot or Paper server's world settings ({@code seed-village} and the rest in {@code spigot.yml}, and Paper's
 * {@code feature-seeds}, which {@code generate-random-seeds-for-all} fills with random ones). Asked with them, the
 * questions about structures and dungeons answer as that server generates; the terrain and biomes do not depend on them.
 *
 * @param structureSalts  by structure set (e.g. {@code minecraft:villages}), the salt its placement uses in place of its
 *                        own; for {@code minecraft:mineshafts} and {@code minecraft:buried_treasures}, the salt their
 *                        frequency is drawn with. Only the vanilla sets that spread at random take one
 * @param strongholdSeed  the seed the strongholds' rings are drawn with in place of the world seed
 * @param featureSeeds    by feature (e.g. {@code minecraft:monster_room}), the seed it is placed with in place of the
 *                        world seed
 */
public record ServerSeeds(Map<Identifier, Integer> structureSalts, OptionalLong strongholdSeed, Map<Identifier, Long> featureSeeds) {
    /** None: everything from the world seed, as a vanilla server (and a Spigot or Paper server left at its defaults). */
    public static final ServerSeeds NONE = new ServerSeeds(Map.of(), OptionalLong.empty(), Map.of());

    /** The structure sets of a Spigot world setting ({@code seed-village} is {@code minecraft:villages}). */
    private static final Map<String, String> SPIGOT_SETS = Map.ofEntries(
            Map.entry("seed-village", "villages"),
            Map.entry("seed-desert", "desert_pyramids"),
            Map.entry("seed-igloo", "igloos"),
            Map.entry("seed-jungle", "jungle_temples"),
            Map.entry("seed-swamp", "swamp_huts"),
            Map.entry("seed-monument", "ocean_monuments"),
            Map.entry("seed-shipwreck", "shipwrecks"),
            Map.entry("seed-ocean", "ocean_ruins"),
            Map.entry("seed-outpost", "pillager_outposts"),
            Map.entry("seed-endcity", "end_cities"),
            Map.entry("seed-nether", "nether_complexes"),
            Map.entry("seed-mansion", "woodland_mansions"),
            Map.entry("seed-fossil", "nether_fossils"),
            Map.entry("seed-portal", "ruined_portals"),
            Map.entry("seed-ancientcity", "ancient_cities"),
            Map.entry("seed-trailruins", "trail_ruins"),
            Map.entry("seed-trialchambers", "trial_chambers"),
            Map.entry("seed-abandonedcamp", "abandoned_camp"),
            Map.entry("seed-buriedtreasure", "buried_treasures"),
            Map.entry("seed-mineshaft", "mineshafts"));

    public ServerSeeds {
        java.util.Objects.requireNonNull(strongholdSeed, "strongholdSeed (OptionalLong.empty() for none)");
        structureSalts = Map.copyOf(structureSalts);
        featureSeeds = Map.copyOf(featureSeeds);
    }

    /**
     * The seeds of a Spigot (or Paper) world's settings, as written in {@code spigot.yml} under {@code world-settings}
     * (e.g. {@code "seed-village" -> "10387312"}, {@code "seed-stronghold" -> "default"}). Settings left at
     * {@code default} (or without a value: null), and settings of other things, are left out. No feature seeds: add them
     * with {@link #withFeatureSeeds}.
     *
     * @throws NumberFormatException for a seed that is not a number
     */
    public static ServerSeeds fromSpigot(Map<String, String> settings) {
        Map<Identifier, Integer> salts = new HashMap<>();
        OptionalLong stronghold = OptionalLong.empty();
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            if (setting.getValue() == null) continue;
            String value = setting.getValue().trim();
            if (value.equals("default")) continue;
            if (setting.getKey().equals("seed-stronghold")) {
                stronghold = OptionalLong.of(Long.parseLong(value));
                continue;
            }
            String set = SPIGOT_SETS.get(setting.getKey());
            if (set != null) salts.put(Identifier.withDefaultNamespace(set), Integer.parseInt(value));
        }
        return new ServerSeeds(salts, stronghold, Map.of());
    }

    /** These seeds with the features' seeds of a Paper world's {@code feature-seeds} (by feature, e.g. {@code minecraft:monster_room}). */
    public ServerSeeds withFeatureSeeds(Map<Identifier, Long> features) {
        return new ServerSeeds(structureSalts, strongholdSeed, features);
    }
}
