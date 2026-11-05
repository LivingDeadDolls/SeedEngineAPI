package seedengine.mixin;

import net.minecraft.core.HolderSet;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The two ways a villager picks the offers of a trade set, for drawing them without a villager in a world. */
@Mixin(AbstractVillager.class)
public interface AbstractVillagerInvoker {
    @Invoker("addOffersFromItemListings")
    static void seedengine$addOffers(LootContext context, MerchantOffers offers, HolderSet<VillagerTrade> trades, int count) {
        throw new AssertionError();
    }

    @Invoker("addOffersFromItemListingsWithoutDuplicates")
    static void seedengine$addOffersWithoutDuplicates(LootContext context, MerchantOffers offers, HolderSet<VillagerTrade> trades, int count) {
        throw new AssertionError();
    }
}
