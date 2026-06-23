package com.alonediamond.playercontrolpp.record;

import com.google.gson.JsonObject;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtLongArray;

import java.util.ArrayList;
import java.util.List;

/**
 * HP mode position keyframe — recorded every 20 ticks during high-precision recording.
 * Used during playback to correct position drift.
 *
 * <p>NBT columnar storage (since v1.4): keyframes are stored as parallel arrays
 * directly in the recording compound. No list-of-compounds wrapper.</p>
 */
public class PositionKeyframe {
    public int tick;
    public double x;
    public double y;
    public double z;

    public PositionKeyframe() {}

    public PositionKeyframe(int tick, double x, double y, double z) {
        this.tick = tick;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // ======================== NBT columnar pack / unpack ========================

    /**
     * Pack keyframes into columnar arrays stored in the given compound.
     * <pre>
     *   kfTick (IntArray)   — tick per keyframe
     *   kfX    (LongArray)  — X (Double.doubleToRawLongBits)
     *   kfY    (LongArray)  — Y
     *   kfZ    (LongArray)  — Z
     * </pre>
     */
    public static void packToNbt(NbtCompound root, List<PositionKeyframe> keyframes) {
        int n = keyframes.size();
        int[]  ticks = new int[n];
        long[] xs    = new long[n];
        long[] ys    = new long[n];
        long[] zs    = new long[n];

        for (int i = 0; i < n; i++) {
            PositionKeyframe kf = keyframes.get(i);
            ticks[i] = kf.tick;
            xs[i]    = Double.doubleToRawLongBits(kf.x);
            ys[i]    = Double.doubleToRawLongBits(kf.y);
            zs[i]    = Double.doubleToRawLongBits(kf.z);
        }

        root.put("kfTick", new NbtIntArray(ticks));
        root.put("kfX",    new NbtLongArray(xs));
        root.put("kfY",    new NbtLongArray(ys));
        root.put("kfZ",    new NbtLongArray(zs));
    }

    /**
     * Unpack keyframes from the columnar arrays in the given compound.
     * Returns an empty list if any required array is missing.
     */
    public static List<PositionKeyframe> unpackFromNbt(NbtCompound root) {
        NbtElement tickElem = root.get("kfTick");
        NbtElement xElem    = root.get("kfX");
        NbtElement yElem    = root.get("kfY");
        NbtElement zElem    = root.get("kfZ");

        if (!(tickElem instanceof NbtIntArray ticks) ||
            !(xElem instanceof NbtLongArray xs) ||
            !(yElem instanceof NbtLongArray ys) ||
            !(zElem instanceof NbtLongArray zs)) {
            return new ArrayList<>();
        }

        int n = ticks.size();
        if (xs.size() != n || ys.size() != n || zs.size() != n) {
            n = Math.min(Math.min(ticks.size(), xs.size()),
                    Math.min(ys.size(), zs.size()));
            if (n <= 0) return new ArrayList<>();
        }

        List<PositionKeyframe> keyframes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            keyframes.add(new PositionKeyframe(
                    ticks.get(i).intValue(),
                    Double.longBitsToDouble(xs.get(i).longValue()),
                    Double.longBitsToDouble(ys.get(i).longValue()),
                    Double.longBitsToDouble(zs.get(i).longValue())));
        }
        return keyframes;
    }

    // ==================== JSON backward compat (read only) ====================

    public static PositionKeyframe fromJson(JsonObject obj) {
        return new PositionKeyframe(
                obj.has("t") ? obj.get("t").getAsInt() : 0,
                obj.has("x") ? obj.get("x").getAsDouble() : 0,
                obj.has("y") ? obj.get("y").getAsDouble() : 0,
                obj.has("z") ? obj.get("z").getAsDouble() : 0);
    }
}
