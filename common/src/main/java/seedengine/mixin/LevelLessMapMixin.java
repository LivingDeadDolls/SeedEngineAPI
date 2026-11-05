package seedengine.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import seedengine.trade.TradeDraws;

@Mixin(ExplorationMapFunction.class)
abstract class LevelLessMapMixin {
    /**
     * An explorer map searches the world for its structure: drawn without a world, that is told apart from the other
     * failures. With a level (always, outside the drawing) nothing changes.
     */
    @Inject(method = "run", at = @At("HEAD"))
    private void seedengine$levelLess(ItemStack stack, LootContext context, CallbackInfoReturnable<ItemStack> result) {
        if (context.getLevel() == null && TradeDraws.drawingWith() != null) throw new TradeDraws.NeedsTheWorld("an explorer map");
    }
}
