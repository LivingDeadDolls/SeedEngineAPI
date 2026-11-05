package seedengine;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import java.util.Locale;
import java.util.OptionalLong;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.WorldOptions;
import seedengine.api.SeedSource;
import seedengine.seed.SeedBook;
import seedengine.seed.Seeds;

/**
 * {@code /setseed <seed>} enters the seed of the server the player is on (a number, or text read as the world creation
 * screen reads it), {@code /setseed clear} withdraws it and {@code /setseed} shows the seed in use. Named apart from
 * the vanilla {@code /seed}, which still goes to the server.
 */
final class SetSeedCommand {
    static final String CLEAR = "clear";

    private SetSeedCommand() {
    }

    static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("setseed")
                .executes(SetSeedCommand::show)
                .then(ClientCommands.literal(CLEAR).executes(SetSeedCommand::clear))
                .then(ClientCommands.argument("seed", new SeedTextArgument()).executes(SetSeedCommand::set)));
    }

    private static int show(CommandContext<FabricClientCommandSource> context) {
        Seeds.BOOK.current().ifPresentOrElse(
                entry -> context.getSource().sendFeedback(Component.translatable("seedengine.command.setseed.current",
                        Long.toString(entry.seed()), sourceName(entry.source()))),
                () -> context.getSource().sendFeedback(Component.translatable("seedengine.command.setseed.unknown")));
        return 1;
    }

    private static int set(CommandContext<FabricClientCommandSource> context) {
        String input = context.getArgument("seed", String.class);
        OptionalLong parsed = WorldOptions.parseSeed(input.trim());
        if (parsed.isEmpty()) {
            context.getSource().sendError(Component.translatable("seedengine.command.setseed.invalid", input));
            return 0;
        }
        long seed = parsed.getAsLong();
        Seeds.BOOK.set(SeedSource.MANUAL, seed);
        SeedBook.Entry current = Seeds.BOOK.current().orElseThrow();
        if (current.source() == SeedSource.MANUAL) {
            context.getSource().sendFeedback(Component.translatable("seedengine.command.setseed.set", Long.toString(seed)));
        } else {
            context.getSource().sendFeedback(Component.translatable("seedengine.command.setseed.set_unused",
                    Long.toString(seed), sourceName(current.source()), Long.toString(current.seed())));
        }
        return 1;
    }

    private static int clear(CommandContext<FabricClientCommandSource> context) {
        if (!Seeds.BOOK.clear(SeedSource.MANUAL)) {
            context.getSource().sendFeedback(Component.translatable("seedengine.command.setseed.nothing_to_clear"));
            return 0;
        }
        context.getSource().sendFeedback(Component.translatable("seedengine.command.setseed.cleared"));
        return show(context);
    }

    private static Component sourceName(SeedSource source) {
        return Component.translatable("seedengine.seed_source." + source.name().toLowerCase(Locale.ROOT));
    }
}
