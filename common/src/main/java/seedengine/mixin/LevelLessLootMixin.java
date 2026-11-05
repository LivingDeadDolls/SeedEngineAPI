package seedengine.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.functions.SetRandomPotionFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import seedengine.trade.TradeDraws;

@Mixin({EnchantWithLevelsFunction.class, SetRandomPotionFunction.class})
abstract class LevelLessLootMixin {
    /**
     * These functions take the registries from the level only. Trades drawn without a world have none, so the
     * registries the trades are drawn with stand in; with a level (always, outside the drawing) nothing changes.
     */
    @WrapOperation(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;registryAccess()Lnet/minecraft/core/RegistryAccess;"))
    private RegistryAccess seedengine$levelLess(ServerLevel level, Operation<RegistryAccess> original) {
        if (level == null) {
            RegistryAccess drawing = TradeDraws.drawingWith();
            if (drawing != null) return drawing;
        }
        return original.call(level);
    }
}
