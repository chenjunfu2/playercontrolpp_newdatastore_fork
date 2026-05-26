package com.alonediamond.playercontrolpp.feature;

import com.alonediamond.playercontrolpp.util.MessageUtil;
import net.minecraft.client.MinecraftClient;

public class AutoForwardFeature {

    private static boolean enabled;

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle(MinecraftClient client) {
        enabled = !enabled;
        MessageUtil.sendActionBar(client, enabled
                ? "playercontrolpp.message.auto_forward.on"
                : "playercontrolpp.message.auto_forward.off");
    }

    public static void onWorldChange() {
        if (enabled) {
            enabled = false;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                MessageUtil.sendActionBar(client, "playercontrolpp.message.auto_forward.world_change");
            }
        }
    }
}
