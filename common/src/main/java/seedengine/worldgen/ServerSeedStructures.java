package seedengine.worldgen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import seedengine.api.ServerSeeds;
import seedengine.mixin.ChunkGeneratorStructureStateAccessor;
import seedengine.mixin.HolderReferenceInvoker;
import seedengine.mixin.SpreadingPlacementAccessor;

/**
 * The structure state of a server that places structures with seeds of its own ({@link ServerSeeds}), made the way a
 * Spigot server makes it: the vanilla structure sets that spread at random get the server's salt (mineshafts and buried
 * treasures the salt of their frequency), and the strongholds' rings are drawn with the server's seed. Other structure
 * sets' placements stay as they are, also where one set keeps away from another (outposts from villages): as on such a
 * server, that looks at the other set's own placement.
 */
final class ServerSeedStructures {
    private static final Identifier MINESHAFTS = Identifier.withDefaultNamespace("mineshafts");
    private static final Identifier BURIED_TREASURES = Identifier.withDefaultNamespace("buried_treasures");

    private ServerSeedStructures() {
    }

    static ChunkGeneratorStructureState apply(ChunkGeneratorStructureState state, RegistryAccess registries, ServerSeeds seeds) {
        if (seeds.structureSalts().isEmpty() && seeds.strongholdSeed().isEmpty()) return state;
        var owner = registries.lookupOrThrow(Registries.STRUCTURE_SET);
        List<Holder<StructureSet>> sets = new ArrayList<>();
        for (Holder<StructureSet> set : state.possibleStructureSets()) {
            ResourceKey<StructureSet> key = set.unwrapKey().orElse(null);
            Integer salt = key == null || !key.identifier().getNamespace().equals("minecraft") ? null : seeds.structureSalts().get(key.identifier());
            if (salt == null || !(set.value().placement() instanceof RandomSpreadStructurePlacement spread)) {
                sets.add(set);
                continue;
            }
            boolean frequencySalt = key.identifier().equals(MINESHAFTS) || key.identifier().equals(BURIED_TREASURES);
            SpreadingPlacementAccessor parts = (SpreadingPlacementAccessor) spread;
            RandomSpreadStructurePlacement placement = new Salted(spread, frequencySalt ? parts.seedengine$salt() : salt, frequencySalt ? salt : null);
            // Keyed like the registry's, so that it is told apart the same way.
            Holder.Reference<StructureSet> salted = Holder.Reference.createStandAlone(owner, key);
            ((HolderReferenceInvoker<StructureSet>) salted).seedengine$bindValue(new StructureSet(set.value().structures(), placement));
            sets.add(salted);
        }
        ChunkGeneratorStructureStateAccessor parts = (ChunkGeneratorStructureStateAccessor) state;
        long rings = seeds.strongholdSeed().orElse(parts.seedengine$concentricRingsSeed());
        return ChunkGeneratorStructureStateAccessor.seedengine$create(state.randomState(), parts.seedengine$biomeSource(), state.getLevelSeed(),
                state.getDimensionOrigin(), rings, sets);
    }

    /** A placement that spreads with another salt, or draws its frequency with another salt (as Spigot does for mineshafts and buried treasures). */
    private static final class Salted extends RandomSpreadStructurePlacement {
        private final Integer frequencySalt;

        Salted(RandomSpreadStructurePlacement spread, int salt, Integer frequencySalt) {
            super(spread.locateOffset(), ((SpreadingPlacementAccessor) spread).seedengine$frequencyReductionMethod(),
                    ((SpreadingPlacementAccessor) spread).seedengine$frequency(), salt, ((SpreadingPlacementAccessor) spread).seedengine$exclusionZone(),
                    spread.spacing(), spread.separation(), spread.spreadType());
            this.frequencySalt = frequencySalt;
        }

        @Override
        public boolean applyAdditionalChunkRestrictions(int sourceX, int sourceZ, long levelSeed) {
            if (frequencySalt == null || frequency() >= 1.0F) return super.applyAdditionalChunkRestrictions(sourceX, sourceZ, levelSeed);
            AbstractSpreadingStructurePlacement.FrequencyReductionMethod method = frequencyReductionMethod();
            if (method != AbstractSpreadingStructurePlacement.FrequencyReductionMethod.LEGACY_TYPE_2
                    && method != AbstractSpreadingStructurePlacement.FrequencyReductionMethod.LEGACY_TYPE_3) {
                return super.applyAdditionalChunkRestrictions(sourceX, sourceZ, levelSeed);
            }
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureWithSalt(levelSeed, sourceX, sourceZ, frequencySalt);
            return method == AbstractSpreadingStructurePlacement.FrequencyReductionMethod.LEGACY_TYPE_3
                    ? random.nextDouble() < frequency()
                    : random.nextFloat() < frequency();
        }
    }
}
