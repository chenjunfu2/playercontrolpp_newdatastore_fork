package com.alonediamond.playercontrolpp.input;

import com.alonediamond.playercontrolpp.feature.AutoForwardFeature;
import com.alonediamond.playercontrolpp.feature.QuickTurnFeature;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeyAction;
import net.minecraft.client.MinecraftClient;

import static com.alonediamond.playercontrolpp.config.Configs.Hotkeys.AUTO_FORWARD;
import static com.alonediamond.playercontrolpp.config.Configs.Hotkeys.QUICK_TURN;

public class KeybindCallbacks {

    public static void register() {
        AUTO_FORWARD.getKeybind().setCallback(new AutoForwardCallback());
        QUICK_TURN.getKeybind().setCallback(new QuickTurnCallback());
    }

    private static class AutoForwardCallback implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            if (action != KeyAction.PRESS) {
                return false;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return false;
            }
            AutoForwardFeature.toggle(client);
            return true;
        }
    }

    private static class QuickTurnCallback implements IHotkeyCallback {
        @Override
        public boolean onKeyAction(KeyAction action, IKeybind key) {
            if (action != KeyAction.PRESS) {
                return false;
            }
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) {
                return false;
            }
            QuickTurnFeature.execute(client);
            return true;
        }
    }
}
