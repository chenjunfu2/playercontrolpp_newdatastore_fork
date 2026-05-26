package com.alonediamond.playercontrolpp.feature;

import com.alonediamond.playercontrolpp.action.RotateAction;
import com.alonediamond.playercontrolpp.config.Configs;
import net.minecraft.client.MinecraftClient;

public class QuickTurnFeature {

    public static void execute(MinecraftClient client) {
        int angle = Configs.Settings.TURN_ANGLE.getIntegerValue();
        RotateAction.apply(client, angle);
    }
}
