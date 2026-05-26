package com.alonediamond.playercontrolpp.gui;

import com.alonediamond.playercontrolpp.config.Configs;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import net.minecraft.client.gui.screen.Screen;

import java.util.ArrayList;
import java.util.List;

public class PlayerControlppConfigGui extends GuiConfigsBase {

    public static final String MOD_ID = "playercontrolpp";

    public PlayerControlppConfigGui(Screen parent) {
        super(10, 50, MOD_ID, parent, "PlayerControl++ Config");
    }

    @Override
    public List<ConfigOptionWrapper> getConfigs() {
        List<ConfigOptionWrapper> wrappers = new ArrayList<>();
        wrappers.add(new ConfigOptionWrapper("Hotkeys"));
        wrappers.addAll(ConfigOptionWrapper.createFor(Configs.Hotkeys.OPTIONS));
        wrappers.add(new ConfigOptionWrapper("Settings"));
        wrappers.addAll(ConfigOptionWrapper.createFor(Configs.Settings.OPTIONS));
        return wrappers;
    }
}
