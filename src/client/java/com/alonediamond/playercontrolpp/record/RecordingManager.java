package com.alonediamond.playercontrolpp.record;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fi.dy.masa.malilib.util.FileUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Manages recording persistence with a lightweight index for fast GUI loading.
 *
 * <p>Since v1.4, recordings are stored in NBT (gzip-compressed) format.</p>
 *
 * <p>Storage layout (under config/playercontrolpp/recordings/):</p>
 * <pre>
 *   index.nbt        — recording metadata only (name, duration, HP flag, dimension)
 *   record_001.nbt   — full recording data (columnar segments + keyframes), loaded on demand
 *   record_002.nbt   — ...
 * </pre>
 *
 * <p>Old {@code .json} files are still readable as a compatibility fallback.
 * All new saves use NBT exclusively.</p>
 */
public class RecordingManager {
    private static final RecordingManager INSTANCE = new RecordingManager();
    private static final String RECORDINGS_DIR = "playercontrolpp/recordings";
    private static final String INDEX_FILE     = "index.nbt";
    private static final String INDEX_JSON     = "index.json";

    private final List<RecordingFile> recordings = new ArrayList<>();
    private final InputRecorder recorder = new InputRecorder();
    private final InputPlayer player = new InputPlayer();
    private boolean loaded;

    private RecordingManager() {}

    public static RecordingManager getInstance() { return INSTANCE; }

    public List<RecordingFile> getRecordings() { return Collections.unmodifiableList(recordings); }
    public InputRecorder getRecorder() { return recorder; }
    public InputPlayer getPlayer() { return player; }

    // --- Directory helpers ---

    private Path getRecordingsDir() {
        return FileUtils.getConfigDirectoryAsPath().resolve(RECORDINGS_DIR);
    }

    private Path getIndexFile() {
        return getRecordingsDir().resolve(INDEX_FILE);
    }

    private Path getIndexJsonFile() {
        return getRecordingsDir().resolve(INDEX_JSON);
    }

    private Path getRecordingFile(String id) {
        return getRecordingsDir().resolve(id + ".nbt");
    }

    private Path getRecordingJsonFile(String id) {
        return getRecordingsDir().resolve(id + ".json");
    }

    // --- Index loading (GUI only — no segment data) ---

    public void loadRecordings() {
        if (loaded) return;
        loaded = true;

        Path indexFile = getIndexFile();
        if (Files.exists(indexFile) && !Files.isDirectory(indexFile)) {
            loadIndexNbt(indexFile);
            return;
        }

        // Backward compat: try old index.json
        Path indexJson = getIndexJsonFile();
        if (Files.exists(indexJson) && !Files.isDirectory(indexJson)) {
            loadIndexJson(indexJson);
        }
    }

    /** Load index.nbt (new format since v1.4). */
    private void loadIndexNbt(Path file) {
        try {
            Optional<NbtCompound> opt = Optional.ofNullable(NbtIo.readCompressed(file, NbtSizeTracker.ofUnlimitedBytes()));
            if (opt.isEmpty()) return;
            NbtCompound root = opt.get();
            NbtElement listElem = root.get("list");
            if (listElem instanceof NbtList list) {
                for (int i = 0; i < list.size(); i++) {
                    NbtElement elem = list.get(i);
                    if (elem instanceof NbtCompound compound) {
                        recordings.add(RecordingFile.fromIndexNbt(compound));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[PlayerControl++] Failed to load recording index.nbt: " + e.getMessage());
        }
    }

    /** Fallback: load old index.json. */
    private void loadIndexJson(Path file) {
        try (Reader reader = new InputStreamReader(
                new FileInputStream(file.toFile()), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element == null || !element.isJsonObject()) return;
            JsonObject root = element.getAsJsonObject();
            if (root.has("recordings")) {
                JsonArray arr = root.getAsJsonArray("recordings");
                for (int i = 0; i < arr.size(); i++) {
                    recordings.add(RecordingFile.fromIndexJson(arr.get(i).getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            System.err.println("[PlayerControl++] Failed to load recording index.json: " + e.getMessage());
        }
    }

    /** Save index as NBT (always writes the new format). */
    private void saveIndex() {
        Path dir = getRecordingsDir();
        try { Files.createDirectories(dir); } catch (Exception ignored) { return; }

        NbtCompound root = new NbtCompound();
        NbtList list = new NbtList();
        for (RecordingFile rf : recordings) {
            list.add(rf.toIndexNbt());
        }
        root.put("list", list);

        Path file = getIndexFile();
        try {
            NbtIo.writeCompressed(root, file);
        } catch (Exception e) {
            System.err.println("[PlayerControl++] Failed to save recording index.nbt: " + e.getMessage());
        }
    }

    // --- Add / Remove ---

    public void addRecording(RecordingFile rec) {
        // Generate sequential ID
        int maxId = 0;
        for (RecordingFile r : recordings) {
            String rid = r.getId();
            if (rid != null && rid.startsWith("record_")) {
                try { maxId = Math.max(maxId, Integer.parseInt(rid.substring(7))); }
                catch (NumberFormatException ignored) {}
            }
        }
        rec.setId(String.format("record_%03d", maxId + 1));

        recordings.add(rec);
        saveRecordingFile(rec);
        saveIndex();
    }

    public void removeRecording(RecordingFile rec) {
        recordings.remove(rec);
        try {
            Files.deleteIfExists(getRecordingFile(rec.getId()));
            // Also clean up old JSON file if present
            Files.deleteIfExists(getRecordingJsonFile(rec.getId()));
        } catch (Exception ignored) {}
        saveIndex();
    }

    // --- Individual file I/O ---

    /** Save full recording data as NBT (always writes the new format). */
    public void saveRecordingFile(RecordingFile rec) {
        Path dir = getRecordingsDir();
        try { Files.createDirectories(dir); } catch (Exception ignored) { return; }

        Path file = getRecordingFile(rec.getId());
        try {
            NbtIo.writeCompressed(rec.toNbt(), file);
        } catch (Exception e) {
            System.err.println("[PlayerControl++] Failed to save recording NBT: " + e.getMessage());
        }
    }

    /**
     * Load full recording data (segments + keyframes) for playback.
     * Tries NBT first, falls back to old JSON for backward compatibility.
     */
    public RecordingFile loadRecordingFile(String id) {
        // Try NBT first
        Path nbtFile = getRecordingFile(id);
        if (Files.exists(nbtFile) && !Files.isDirectory(nbtFile)) {
            try {
                Optional<NbtCompound> opt = Optional.ofNullable(NbtIo.readCompressed(nbtFile, NbtSizeTracker.ofUnlimitedBytes()));
                if (opt.isPresent()) {
                    return RecordingFile.fromNbt(opt.get());
                }
            } catch (Exception e) {
                System.err.println("[PlayerControl++] Failed to load recording NBT " + id + ": " + e.getMessage());
            }
        }

        // Fallback: try old JSON
        Path jsonFile = getRecordingJsonFile(id);
        if (Files.exists(jsonFile) && !Files.isDirectory(jsonFile)) {
            try (Reader reader = new InputStreamReader(
                    new FileInputStream(jsonFile.toFile()), StandardCharsets.UTF_8)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (element != null && element.isJsonObject()) {
                    return RecordingFile.fromFullJson(element.getAsJsonObject());
                }
            } catch (Exception e) {
                System.err.println("[PlayerControl++] Failed to load recording JSON " + id + ": " + e.getMessage());
            }
        }

        return null;
    }

    // Backward-compatible save for RecordingListGui close
    public void saveRecordings() {
        saveIndex();
    }

    // --- Tick ---

    public void onClientTick(net.minecraft.client.MinecraftClient client) {
        recorder.tick(client);
        player.tick(client);
    }
}
