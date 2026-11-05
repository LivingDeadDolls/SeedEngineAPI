package seedengine;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import seedengine.client.ClientSeeds;

/**
 * The client only: keeps the seed and the world type of the world the client is in, and whether they fit its terrain,
 * and the {@code /setseed} command.
 */
public final class SeedEngineFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayConnectionEvents.JOIN.register((listener, sender, mc) -> ClientSeeds.onJoin(mc));
        ClientPlayConnectionEvents.DISCONNECT.register((listener, mc) -> ClientSeeds.onLeave());
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) ClientSeeds.onGameMessage(message);
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClientSeeds::onTick);
        ClientSeeds.listenToSeeds();
        ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> ClientSeeds.onChunkLoad(chunk));
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> SetSeedCommand.register(dispatcher));
    }
}
