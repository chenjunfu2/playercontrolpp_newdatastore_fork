package com.alonediamond.playercontrolpp.record;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.dy.masa.malilib.util.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();
    private static final String RECORDINGS_FILE = "playercontrolpp_recordings.json";

    private final List<RecordingFile> recordings = new ArrayList<>();
    private final InputRecorder recorder = new InputRecorder();
    private final InputPlayer player = new InputPlayer();
    private boolean loaded;

    private RecordingManager() {}

    public static RecordingManager getInstance() { return INSTANCE; }

    public List<RecordingFile> getRecordings() { return Collections.unmodifiableList(recordings); }
    public InputRecorder getRecorder() { return recorder; }
    public InputPlayer getPlayer() { return player; }

    public void addRecording(RecordingFile rec) {
        recordings.add(rec);
        saveRecordings();
    }

    public void removeRecording(RecordingFile rec) {
        recordings.remove(rec);
        saveRecordings();
    }

    public void loadRecordings() {
        if (loaded) return;
        loaded = true;

        Path file = FileUtils.getConfigDirectoryAsPath().resolve(RECORDINGS_FILE);
        if (!Files.exists(file) || Files.isDirectory(file)) return;

        try (Reader reader = new InputStreamReader(
                new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) return;
            JsonObject root = element.getAsJsonObject();
            if (root.has("recordings")) {
                JsonArray arr = root.getAsJsonArray("recordings");
                for (int i = 0; i < arr.size(); i++) {
                    recordings.add(RecordingFile.fromJson(arr.get(i).getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            System.err.println("[PlayerControl++] Failed to load recordings: " + e.getMessage());
        }
    }

    public void saveRecordings() {
        Path dir = FileUtils.getConfigDirectoryAsPath();
        try { Files.createDirectories(dir); } catch (Exception ignored) { return; }

        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (RecordingFile rf : recordings) { arr.add(rf.toJson()); }
        root.add("recordings", arr);

        Path file = dir.resolve(RECORDINGS_FILE);
        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(file.toFile()), StandardCharsets.UTF_8)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(root, writer);
        } catch (Exception e) {
            System.err.println("[PlayerControl++] Failed to save recordings: " + e.getMessage());
        }
    }

    public void onClientTick(net.minecraft.client.MinecraftClient client) {
        recorder.tick(client);
        player.tick(client);
    }
}
