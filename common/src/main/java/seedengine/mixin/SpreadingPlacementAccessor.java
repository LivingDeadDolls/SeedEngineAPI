package seedengine.mixin;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What a structure set's placement is made of, to make the same one with another salt. */
@Mixin(AbstractSpreadingStructurePlacement.class)
public interface SpreadingPlacementAccessor {
    @Invoker("frequencyReductionMethod")
    AbstractSpreadingStructurePlacement.FrequencyReductionMethod seedengine$frequencyReductionMethod();

    @Invoker("frequency")
    float seedengine$frequency();

    @Invoker("salt")
    int seedengine$salt();

    @Invoker("exclusionZone")
    Optional<AbstractSpreadingStructurePlacement.ExclusionZone> seedengine$exclusionZone();
}
