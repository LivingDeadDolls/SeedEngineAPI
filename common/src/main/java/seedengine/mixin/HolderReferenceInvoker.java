package seedengine.mixin;

import net.minecraft.core.Holder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Gives a stand-alone holder its value: a structure set of a server's own that keeps its registry key. */
@Mixin(Holder.Reference.class)
public interface HolderReferenceInvoker<T> {
    @Invoker("bindValue")
    void seedengine$bindValue(T value);
}
