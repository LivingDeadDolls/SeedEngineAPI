package seedengine.mixin;

import net.minecraft.resources.Identifier;
import net.minecraft.world.RandomSequence;
import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A sequence at its start as the world would start it (with its salt and flags), without keeping it. */
@Mixin(RandomSequences.class)
public interface RandomSequencesInvoker {
    @Invoker("createSequence")
    RandomSequence seedengine$createSequence(Identifier id, long worldSeed);
}
