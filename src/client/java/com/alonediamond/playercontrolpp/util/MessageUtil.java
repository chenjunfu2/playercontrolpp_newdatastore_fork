package com.alonediamond.playercontrolpp.util;

import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class MessageUtil {

    public static void sendActionBar(MinecraftClient client, String translationKey) {
        if (client.player != null) {
            client.player.sendMessage(Text.of(StringUtils.translate(translationKey)), true);
        }
    }
}
