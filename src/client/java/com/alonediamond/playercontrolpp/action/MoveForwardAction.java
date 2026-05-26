package com.alonediamond.playercontrolpp.action;

import net.minecraft.client.MinecraftClient;

public class MoveForwardAction {

    public static void apply(MinecraftClient client) {
        if (client.player != null && client.player.input != null) {
            client.player.input.movementForward = 1.0F;
        }
    }
}
