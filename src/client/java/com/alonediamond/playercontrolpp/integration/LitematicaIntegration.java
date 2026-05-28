package com.alonediamond.playercontrolpp.integration;

import fi.dy.masa.malilib.util.StringUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Litematica integration via reflection to avoid compile-time dependency.
 * Only works when Litematica is loaded at runtime.
 */
public class LitematicaIntegration {

    private static boolean enabled = true;
    private static boolean showActionBar = true;

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean enabled) { LitematicaIntegration.enabled = enabled; }

    public static boolean isShowActionBar() { return showActionBar; }
    public static void setShowActionBar(boolean show) { showActionBar = show; }

    /**
     * Attempt to increment the Litematica render layer via reflection.
     *
     * @param incrementAmount number of layers to advance (positive integer)
     */
    public static boolean incrementLayer(int incrementAmount) {
        if (!enabled || incrementAmount <= 0) return false;

        try {
            // Reflectively call: DataManager.getRenderLayerRange()
            Class<?> dataManagerClass = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object range = dataManagerClass.getMethod("getRenderLayerRange").invoke(null);
            if (range == null) return false;

            // Get layer mode
            Object mode = range.getClass().getMethod("getLayerMode").invoke(range);
            // Check mode is not ALL (ordinal 0)
            if (mode != null && ((Enum<?>) mode).ordinal() == 0) return false;

            // range.moveLayer(incrementAmount)
            range.getClass().getMethod("moveLayer", int.class).invoke(range, incrementAmount);

            if (showActionBar) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player != null) {
                    String layerStr = (String) range.getClass()
                            .getMethod("getCurrentLayerString").invoke(range);
                    String msg = StringUtils.translate("playercontrolpp.message.litematica.layer", layerStr);
                    client.player.sendMessage(Text.of(msg), true);
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAvailable() {
        try {
            Class.forName("fi.dy.masa.litematica.data.DataManager");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
