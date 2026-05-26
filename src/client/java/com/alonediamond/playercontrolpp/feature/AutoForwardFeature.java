package com.alonediamond.playercontrolpp.feature;

import com.alonediamond.playercontrolpp.action.MoveForwardAction;
import com.alonediamond.playercontrolpp.util.MessageUtil;
import net.minecraft.client.MinecraftClient;

public class AutoForwardFeature {

    private static boolean enabled;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle(MinecraftClient client) {
        enabled = !enabled;
        MessageUtil.sendActionBar(client, "Auto Forward: " + (enabled ? "ON" : "OFF"));
    }

    public static void tick(MinecraftClient client) {
        if (enabled && client.player != null) {
            MoveForwardAction.apply(client);
        }
    }

    public static void onWorldChange() {
        if (enabled) {
            enabled = false;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                MessageUtil.sendActionBar(client, "Auto Forward: OFF (world changed)");
            }
        }
    }
}
