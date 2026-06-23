package com.alonediamond.playercontrolpp.record;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

import java.util.ArrayList;
import java.util.List;

/**
 * Recording data model. Metadata (id, name, highPrecision, durationTicks, dimension)
 * is stored in index.nbt and always loaded. Segments and keyframes are stored as
 * columnar arrays inside individual recording NBT files and only loaded on demand
 * for playback.
 *
 * <p>NBT recording file structure (record_XXX.nbt):</p>
 * <pre>
 *   id, name, hp, ticks, dim, sX, sY, sZ, sYaw, sPitch   — key-value metadata
 *   segDur (IntArray), segFlags (ByteArray),
 *   segMove (LongArray), segRot (LongArray)                — columnar segments
 *   kfTick (IntArray), kfX, kfY, kfZ (LongArray)          — HP keyframes (optional)
 * </pre>
 */
public class RecordingFile {
    private String id;
    private String name;
    private boolean highPrecision;
    private int durationTicks;
    private String dimension;
    private double startX, startY, startZ;
    private float startYaw, startPitch;

    private List<RecordedSegment> segments = new ArrayList<>();
    private List<PositionKeyframe> keyframes = new ArrayList<>();

    public RecordingFile() {
        this.name = "Unnamed Recording";
    }

    // --- Getters / Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public boolean isHighPrecision() { return highPrecision; }
    public void setHighPrecision(boolean v) { highPrecision = v; }

    public int getDurationTicks() { return durationTicks; }
    public void setDurationTicks(int v) { durationTicks = v; }

    public String getDimension() { return dimension; }
    public void setDimension(String v) { dimension = v; }

    public double getStartX() { return startX; }
    public void setStartX(double v) { startX = v; }
    public double getStartY() { return startY; }
    public void setStartY(double v) { startY = v; }
    public double getStartZ() { return startZ; }
    public void setStartZ(double v) { startZ = v; }

    public float getStartYaw() { return startYaw; }
    public void setStartYaw(float v) { startYaw = v; }
    public float getStartPitch() { return startPitch; }
    public void setStartPitch(float v) { startPitch = v; }

    public List<RecordedSegment> getSegments() { return segments; }
    public void setSegments(List<RecordedSegment> segments) { this.segments = segments; }

    public List<PositionKeyframe> getKeyframes() { return keyframes; }
    public void setKeyframes(List<PositionKeyframe> keyframes) { this.keyframes = keyframes; }

    /** Number of segments (RLE-compressed units). */
    public int getSegmentCount() { return segments.size(); }

    // ======================== NBT Index (lightweight, for index.nbt) ========================

    public NbtCompound toIndexNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", id != null ? id : "");
        nbt.putString("name", name != null ? name : "");
        nbt.putBoolean("hp", highPrecision);
        nbt.putInt("ticks", durationTicks);
        nbt.putString("dim", dimension != null ? dimension : "");
        return nbt;
    }

    public static RecordingFile fromIndexNbt(NbtCompound nbt) {
        RecordingFile rf = new RecordingFile();
        if (nbt.contains("id"))   rf.setId(nbt.getString("id"));
        if (nbt.contains("name")) rf.setName(nbt.getString("name"));
        if (nbt.contains("hp"))   rf.setHighPrecision(nbt.getBoolean("hp"));
        if (nbt.contains("ticks")) rf.setDurationTicks(nbt.getInt("ticks"));
        if (nbt.contains("dim"))  rf.setDimension(nbt.getString("dim"));
        return rf;
    }

    // ======================= NBT Full (for individual recording files) =======================

    public NbtCompound toNbt() {
        NbtCompound nbt = toIndexNbt();

        nbt.putDouble("sX", startX);
        nbt.putDouble("sY", startY);
        nbt.putDouble("sZ", startZ);
        nbt.putFloat("sYaw", startYaw);
        nbt.putFloat("sPitch", startPitch);

        // Segments: columnar arrays inside a child compound
        if (!segments.isEmpty()) {
            nbt.put("segs", RecordedSegment.packToNbt(segments));
        }

        // Keyframes: columnar arrays directly in root (HP mode only)
        if (highPrecision && !keyframes.isEmpty()) {
            PositionKeyframe.packToNbt(nbt, keyframes);
        }

        return nbt;
    }

    public static RecordingFile fromNbt(NbtCompound nbt) {
        RecordingFile rf = fromIndexNbt(nbt);

        if (nbt.contains("sX")) rf.setStartX(nbt.getDouble("sX"));
        if (nbt.contains("sY")) rf.setStartY(nbt.getDouble("sY"));
        if (nbt.contains("sZ")) rf.setStartZ(nbt.getDouble("sZ"));
        if (nbt.contains("sYaw")) rf.setStartYaw(nbt.getFloat("sYaw"));
        if (nbt.contains("sPitch")) rf.setStartPitch(nbt.getFloat("sPitch"));

        // Segments: unpack from the "segs" child compound
        NbtElement segsElem = nbt.get("segs");
        if (segsElem instanceof NbtCompound segsCompound) {
            rf.segments = RecordedSegment.unpackFromNbt(segsCompound);
        }

        // Keyframes: unpack from root-level columnar arrays (HP mode)
        if (rf.isHighPrecision() && nbt.contains("kfTick")) {
            rf.keyframes = PositionKeyframe.unpackFromNbt(nbt);
        }

        return rf;
    }

    // ==================== JSON backward compat (read only) ====================

    /** Read index metadata from old index.json format. */
    public static RecordingFile fromIndexJson(JsonObject obj) {
        RecordingFile rf = new RecordingFile();
        if (obj.has("id")) rf.setId(obj.get("id").getAsString());
        if (obj.has("name")) rf.setName(obj.get("name").getAsString());
        if (obj.has("highPrecision")) rf.setHighPrecision(obj.get("highPrecision").getAsBoolean());
        if (obj.has("durationTicks")) rf.setDurationTicks(obj.get("durationTicks").getAsInt());
        if (obj.has("dimension")) rf.setDimension(obj.get("dimension").getAsString());
        return rf;
    }

    /** Read full recording data from old record_XXX.json format. */
    public static RecordingFile fromFullJson(JsonObject obj) {
        RecordingFile rf = fromIndexJson(obj);
        if (obj.has("startX")) rf.setStartX(obj.get("startX").getAsDouble());
        if (obj.has("startY")) rf.setStartY(obj.get("startY").getAsDouble());
        if (obj.has("startZ")) rf.setStartZ(obj.get("startZ").getAsDouble());
        if (obj.has("startYaw")) rf.setStartYaw(obj.get("startYaw").getAsFloat());
        if (obj.has("startPitch")) rf.setStartPitch(obj.get("startPitch").getAsFloat());

        if (obj.has("segments")) {
            JsonArray arr = obj.getAsJsonArray("segments");
            for (int i = 0; i < arr.size(); i++) {
                rf.segments.add(RecordedSegment.fromJson(arr.get(i).getAsJsonObject()));
            }
        }
        if (obj.has("keyframes")) {
            JsonArray arr = obj.getAsJsonArray("keyframes");
            for (int i = 0; i < arr.size(); i++) {
                rf.keyframes.add(PositionKeyframe.fromJson(arr.get(i).getAsJsonObject()));
            }
        }
        return rf;
    }
}
