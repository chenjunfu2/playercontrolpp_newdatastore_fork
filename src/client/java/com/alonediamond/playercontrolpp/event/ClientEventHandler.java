package com.alonediamond.playercontrolpp.event;

import com.alonediamond.playercontrolpp.feature.AutoForwardFeature;
import fi.dy.masa.malilib.event.WorldLoadHandler;
import fi.dy.masa.malilib.interfaces.IWorldLoadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

public class ClientEventHandler {

    public static void register() {
        WorldLoadHandler.getInstance().registerWorldLoadPreHandler(new WorldLoadListener());
    }

    private static class WorldLoadListener implements IWorldLoadListener {
        @Override
        public void onWorldLoadPre(ClientWorld world1, ClientWorld world2, MinecraftClient client) {
            AutoForwardFeature.onWorldChange();
        }
    }
}
