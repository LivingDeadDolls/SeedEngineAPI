package seedengine.mixin;

import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import seedengine.engine.Worlds;

@Mixin(MinecraftServer.class)
abstract class ServerStopMixin {
    /**
     * What was worked out for a server's levels is dropped when it stops, not when the next question comes. At the
     * start of stopping (the server no longer runs then): also when stopping fails half way.
     */
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void seedengine$forget(CallbackInfo info) {
        Worlds.forget((MinecraftServer) (Object) this);
    }
}
