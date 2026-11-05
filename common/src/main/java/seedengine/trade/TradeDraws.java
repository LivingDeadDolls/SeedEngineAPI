package seedengine.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Unit;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import seedengine.mixin.AbstractVillagerInvoker;
import seedengine.mixin.LootContextInvoker;
import seedengine.mixin.RandomSequencesInvoker;

/**
 * The draws of a trade set, as {@code AbstractVillager.addOffersFromTradeSet} makes them, from the start of the trade
 * set's random sequence: each villager that takes up the job (or reaches the level) in the world takes the next draw.
 * Drawn without a world, so a trade that searches the world (an explorer map) cannot be drawn.
 */
public final class TradeDraws {
    private static final ThreadLocal<RegistryAccess> DRAWING = new ThreadLocal<>();

    private TradeDraws() {
    }

    /** A random sequence at its start, as a world with these sequence settings ({@code /random}) starts it. */
    public static RandomSource start(RandomSequences sequences, long seed, Identifier sequence) {
        return ((RandomSequencesInvoker) sequences).seedengine$createSequence(sequence, seed).random();
    }

    /** A loot function that needs the level (an explorer map searching for its structure), run while drawing without a world. */
    public static final class NeedsTheWorld extends RuntimeException {
        public NeedsTheWorld(String what) {
            super(what);
        }
    }

    /** The registries trades are being drawn with on this thread, for the loot functions that ask the level; else null. */
    public static RegistryAccess drawingWith() {
        return DRAWING.get();
    }

    /**
     * Draws {@code from} to {@code from + count - 1} of a trade set.
     *
     * @param sequences how the world starts its random sequences
     */
    public static List<List<MerchantOffer>> draws(RegistryAccess registries, RandomSequences sequences, long seed, Identifier tradeSet,
                                                  int from, int count, Entity villager) {
        checkRange(from, count);
        java.util.Objects.requireNonNull(villager, "villager");
        TradeSet set = registries.lookupOrThrow(Registries.TRADE_SET).getOptional(ResourceKey.create(Registries.TRADE_SET, tradeSet))
                .orElseThrow(() -> new IllegalArgumentException("Unknown trade set " + tradeSet));
        Identifier sequence = set.randomSequence()
                .orElseThrow(() -> new IllegalArgumentException("Trade set " + tradeSet + " draws from the world's random, not a random sequence"));
        RandomSource random = start(sequences, seed, sequence);
        List<List<MerchantOffer>> draws = new ArrayList<>(Math.min(count, 64));
        RegistryAccess before = DRAWING.get();
        DRAWING.set(registries);
        try {
            for (int i = 0; i < from + count; i++) {
                MerchantOffers offers = draw(set, registries, random, villager);
                if (i >= from) draws.add(List.copyOf(offers));
            }
        } catch (NeedsTheWorld e) {
            throw new IllegalArgumentException("Trade set " + tradeSet + " has a trade that needs the world: " + e.getMessage(), e);
        } catch (NullPointerException e) {
            // A loot function or condition that failed without a level: most likely one asking it (a data pack's
            // location check, say), which is none here.
            throw new IllegalArgumentException("Trade set " + tradeSet + " has a trade that failed without a world (it may need one): " + e, e);
        } finally {
            DRAWING.set(before);
        }
        return List.copyOf(draws);
    }

    /** The draws asked for: from 0 on, and up to within the int range. */
    public static void checkRange(int from, int count) {
        if (from < 0 || count < 0 || from > Integer.MAX_VALUE - count) {
            throw new IllegalArgumentException("Draws from " + from + ", " + count + " of them");
        }
    }

    private static MerchantOffers draw(TradeSet set, RegistryAccess registries, RandomSource random, Entity villager) {
        ContextMap params = ContextMap.builder()
                .set(LootContextParams.ORIGIN, villager.position())
                .set(LootContextParams.THIS_ENTITY, villager)
                .set(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                .buildAndValidate(LootContextParamSets.VILLAGER_TRADE);
        LootContext context = LootContextInvoker.seedengine$create(new LootParams(null, params, Map.of(), 0), random, registries);
        MerchantOffers offers = new MerchantOffers();
        int count = set.calculateNumberOfTrades(context);
        if (set.allowDuplicates()) {
            AbstractVillagerInvoker.seedengine$addOffers(context, offers, set.trades(), count);
        } else {
            AbstractVillagerInvoker.seedengine$addOffersWithoutDuplicates(context, offers, set.trades(), count);
        }
        return offers;
    }
}
