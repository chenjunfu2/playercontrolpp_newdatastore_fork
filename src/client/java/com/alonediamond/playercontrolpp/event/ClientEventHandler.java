package com.alonediamond.playercontrolpp.event;

import com.alonediamond.playercontrolpp.feature.AutoForwardFeature;
import fi.dy.masa.malilib.event.TickHandler;
import fi.dy.masa.malilib.event.WorldLoadHandler;
import fi.dy.masa.malilib.interfaces.IClientTickHandler;
import fi.dy.masa.malilib.interfaces.IWorldLoadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

public class ClientEventHandler {

    public static void register() {
        TickHandler.getInstance().registerClientTickHandler(new ClientTickListener());
        WorldLoadHandler.getInstance().registerWorldLoadPreHandler(new WorldLoadListener());
    }

    private static class ClientTickListener implements IClientTickHandler {
        @Override
        public void onClientTick(MinecraftClient client) {
            if (client.player == null || client.isPaused()) {
                return;
            }
            AutoForwardFeature.tick(client);
        }
    }

    private static class WorldLoadListener implements IWorldLoadListener {
        @Override
        public void onWorldLoadPre(ClientWorld world1, ClientWorld world2, MinecraftClient client) {
            AutoForwardFeature.onWorldChange();
        }
    }
}
