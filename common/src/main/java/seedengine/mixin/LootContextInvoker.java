package seedengine.mixin;

import net.minecraft.core.HolderGetter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A loot context without a server (its builder takes the random and the registries from the server). */
@Mixin(LootContext.class)
public interface LootContextInvoker {
    @Invoker("<init>")
    static LootContext seedengine$create(LootParams params, RandomSource random, HolderGetter.Provider resolver) {
        throw new AssertionError();
    }
}
