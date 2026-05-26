package com.alonediamond.playercontrolpp.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class MessageUtil {

    private static final String PREFIX = "[PlayerControl++] ";

    public static void sendActionBar(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.of(PREFIX + message), true);
        }
    }
}
