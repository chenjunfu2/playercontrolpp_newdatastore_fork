package com.alonediamond.playercontrolpp.integration;

import fi.dy.masa.malilib.util.StringUtils;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import java.util.Collections;
import java.util.Set;

/**
 * Litematica integration via reflection.
 * Calls moveLayer() exactly as Litematica's PageUp/PageDown hotkeys do.
 */
public class LitematicaIntegration implements ModIntegration {

    private static final LitematicaIntegration INSTANCE = new LitematicaIntegration();
    private boolean loaded;
    private boolean showActionBar = true;

    private LitematicaIntegration() {}

    public static LitematicaIntegration getInstance() { return INSTANCE; }

    @Override
    public boolean isLoaded() { return loaded; }

    @Override
    public void initialize() {
        loaded = FabricLoader.getInstance().isModLoaded("litematica");
    }

    public static boolean isShowActionBar() { return INSTANCE.showActionBar; }
    public static void setShowActionBar(boolean show) { INSTANCE.showActionBar = show; }

    /**
     * Get Litematica's MaterialList via reflection.
     */
    public Object getMaterialList() {
        try {
            Class<?> dmClass = Class.forName("fi.dy.masa.litematica.data.DataManager");
            return dmClass.getMethod("getMaterialList").invoke(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get Litematica's internal ignored-entries set via reflection.
     */
    @SuppressWarnings("unchecked")
    public Set<Object> getIgnoredSet(Object materialList) {
        try {
            return (Set<Object>) materialList.getClass().getField("ignored").get(materialList);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    public boolean incrementLayer(int amount) {
        if (amount == 0) return false;

        try {
            Class<?> dmClass = Class.forName("fi.dy.masa.litematica.data.DataManager");
            Object range = dmClass.getMethod("getRenderLayerRange").invoke(null);
            if (range == null) return false;

            Object mode = range.getClass().getMethod("getLayerMode").invoke(range);
            if (!"SINGLE_LAYER".equals(((Enum<?>) mode).name())) return false;

            boolean ok = (Boolean) range.getClass()
                    .getMethod("moveLayer", int.class).invoke(range, amount);
            if (!ok) return false;

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
}
