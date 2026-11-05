package seedengine.mixin;

import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Fills a tag of the vanilla data that only its data names, which loading leaves empty. */
@Mixin(HolderSet.Named.class)
public interface NamedHolderSetInvoker<T> {
    @Invoker("bind")
    void seedengine$bind(List<Holder<T>> contents);
}
