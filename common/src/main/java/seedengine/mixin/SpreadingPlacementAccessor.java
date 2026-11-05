package seedengine.mixin;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What a structure set's placement is made of, to make the same one with another salt. */
@Mixin(StructurePlacement.class)
public interface SpreadingPlacementAccessor {
    @Invoker("locateOffset")
    net.minecraft.core.Vec3i seedengine$locateOffset();

    @Invoker("frequencyReductionMethod")
    StructurePlacement.FrequencyReductionMethod seedengine$frequencyReductionMethod();

    @Invoker("frequency")
    float seedengine$frequency();

    @Invoker("salt")
    int seedengine$salt();

    @Invoker("exclusionZone")
    Optional<StructurePlacement.ExclusionZone> seedengine$exclusionZone();
}
