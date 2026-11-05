package seedengine;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import java.util.List;

/**
 * The rest of the input as the text of a seed, except the word {@code clear}, which is the subcommand beside it in
 * {@code /setseed}. Rejecting it keeps the two from overlapping (Fabric warns in the log of each overlap on joining).
 */
final class SeedTextArgument implements ArgumentType<String> {
    private static final List<String> EXAMPLES = List.of("8675309", "-4242424242424242424", "a seed in words");
    private static final SimpleCommandExceptionType CLEAR = new SimpleCommandExceptionType(
            new LiteralMessage("\"" + SetSeedCommand.CLEAR + "\" is the subcommand, not a seed"));

    @Override
    public String parse(StringReader reader) throws CommandSyntaxException {
        String text = reader.getRemaining();
        if (text.equals(SetSeedCommand.CLEAR)) {
            throw CLEAR.createWithContext(reader);
        }
        reader.setCursor(reader.getTotalLength());
        return text;
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
