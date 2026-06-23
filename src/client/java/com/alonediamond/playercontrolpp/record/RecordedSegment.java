package com.alonediamond.playercontrolpp.record;

import com.google.gson.JsonObject;
import net.minecraft.nbt.NbtByteArray;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIntArray;
import net.minecraft.nbt.NbtLong;
import net.minecraft.nbt.NbtLongArray;

import java.util.ArrayList;
import java.util.List;

/**
 * RLE-compressed recording segment. Represents a contiguous range of ticks
 * where all input fields were identical.
 *
 * <p>NBT columnar storage (since v1.4): segments are stored as parallel arrays
 * in the recording compound. All arrays share the same index for the same segment.</p>
 */
public class RecordedSegment {
    public int duration;
    public float forward;
    public float sideways;
    public boolean jump;
    public boolean sneak;
    public boolean sprint;
    public float yaw;
    public float pitch;
    public boolean attack;
    public boolean use;

    // ---- Bit layout for segFlags (1 byte per segment) ----
    private static final int FLAG_JUMP   = 0x01;  // bit 0
    private static final int FLAG_SNEAK  = 0x02;  // bit 1
    private static final int FLAG_SPRINT = 0x04;  // bit 2
    private static final int FLAG_ATTACK = 0x08;  // bit 3
    private static final int FLAG_USE    = 0x10;  // bit 4
    // bits 5-7 reserved

    public RecordedSegment() {}

    public RecordedSegment(int duration, float forward, float sideways,
                           boolean jump, boolean sneak, boolean sprint,
                           float yaw, float pitch, boolean attack, boolean use) {
        this.duration = duration;
        this.forward = forward;
        this.sideways = sideways;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;
        this.yaw = yaw;
        this.pitch = pitch;
        this.attack = attack;
        this.use = use;
    }

    public boolean matches(float fw, float sw, boolean j, boolean sn, boolean sp,
                           float y, float p, boolean at, boolean us) {
        return forward == fw && sideways == sw
                && jump == j && sneak == sn && sprint == sp
                && yaw == y && pitch == p
                && attack == at && use == us;
    }

    // ======================== NBT columnar pack / unpack ========================

    /**
     * Pack segments into a columnar NBT compound with four parallel arrays.
     * <pre>
     *   segDur   (IntArray)   — duration per segment
     *   segFlags (ByteArray)  — 5 boolean bits per segment
     *   segMove  (LongArray)  — fw (hi32) | sw (lo32)
     *   segRot   (LongArray)  — yaw (hi32) | pitch (lo32)
     * </pre>
     */
    public static NbtCompound packToNbt(List<RecordedSegment> segments) {
        int n = segments.size();
        int[] dur = new int[n];
        byte[] flags = new byte[n];
        long[] move = new long[n];
        long[] rot = new long[n];

        for (int i = 0; i < n; i++) {
            RecordedSegment seg = segments.get(i);
            dur[i] = seg.duration;

            byte f = 0;
            if (seg.jump)   f |= FLAG_JUMP;
            if (seg.sneak)  f |= FLAG_SNEAK;
            if (seg.sprint) f |= FLAG_SPRINT;
            if (seg.attack) f |= FLAG_ATTACK;
            if (seg.use)    f |= FLAG_USE;
            flags[i] = f;

            move[i] = packTwoFloats(seg.forward, seg.sideways);
            rot[i]  = packTwoFloats(seg.yaw, seg.pitch);
        }

        NbtCompound compound = new NbtCompound();
        compound.put("segDur",   new NbtIntArray(dur));
        compound.put("segFlags", new NbtByteArray(flags));
        compound.put("segMove",  new NbtLongArray(move));
        compound.put("segRot",   new NbtLongArray(rot));
        return compound;
    }

    /**
     * Unpack the columnar NBT compound back into a list of segments.
     * Returns an empty list if any of the required arrays is missing or malformed.
     */
    public static List<RecordedSegment> unpackFromNbt(NbtCompound segCompound) {
        NbtElement durElem   = segCompound.get("segDur");
        NbtElement flagsElem = segCompound.get("segFlags");
        NbtElement moveElem  = segCompound.get("segMove");
        NbtElement rotElem   = segCompound.get("segRot");

        if (!(durElem instanceof NbtIntArray durArray) ||
            !(flagsElem instanceof NbtByteArray flagsArray) ||
            !(moveElem instanceof NbtLongArray moveArray) ||
            !(rotElem instanceof NbtLongArray rotArray)) {
            return new ArrayList<>();
        }

        int n = durArray.size();
        if (flagsArray.size() != n || moveArray.size() != n || rotArray.size() != n) {
            n = Math.min(Math.min(durArray.size(), flagsArray.size()),
                    Math.min(moveArray.size(), rotArray.size()));
            if (n <= 0) return new ArrayList<>();
        }

        List<RecordedSegment> segments = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int   d   = durArray.get(i).intValue();
            byte  f   = flagsArray.get(i).byteValue();
            long  mv  = moveArray.get(i).longValue();
            long  rt  = rotArray.get(i).longValue();

            segments.add(new RecordedSegment(
                    d,
                    unpackHighFloat(mv),   // forward
                    unpackLowFloat(mv),    // sideways
                    (f & FLAG_JUMP)   != 0,
                    (f & FLAG_SNEAK)  != 0,
                    (f & FLAG_SPRINT) != 0,
                    unpackHighFloat(rt),   // yaw
                    unpackLowFloat(rt),    // pitch
                    (f & FLAG_ATTACK) != 0,
                    (f & FLAG_USE)    != 0));
        }
        return segments;
    }

    // ---- float packing helpers ----

    /** Pack two floats into one long: {@code hi} in the upper 32 bits, {@code lo} in the lower 32 bits. */
    static long packTwoFloats(float hi, float lo) {
        return ((long) Float.floatToRawIntBits(hi) << 32)
             | (Float.floatToRawIntBits(lo) & 0xFFFF_FFFFL);
    }

    /** Extract the upper 32 bits of a packed long and interpret as float. */
    static float unpackHighFloat(long packed) {
        return Float.intBitsToFloat((int) (packed >>> 32));
    }

    /** Extract the lower 32 bits of a packed long and interpret as float. */
    static float unpackLowFloat(long packed) {
        return Float.intBitsToFloat((int) packed);
    }

    // ==================== JSON backward compat (read only) ====================

    public static RecordedSegment fromJson(JsonObject obj) {
        return new RecordedSegment(
                obj.has("d") ? obj.get("d").getAsInt() : 1,
                obj.has("fw") ? obj.get("fw").getAsFloat() : 0,
                obj.has("sw") ? obj.get("sw").getAsFloat() : 0,
                obj.has("j") && obj.get("j").getAsBoolean(),
                obj.has("sn") && obj.get("sn").getAsBoolean(),
                obj.has("sp") && obj.get("sp").getAsBoolean(),
                obj.has("y") ? obj.get("y").getAsFloat() : 0,
                obj.has("p") ? obj.get("p").getAsFloat() : 0,
                obj.has("at") && obj.get("at").getAsBoolean(),
                obj.has("us") && obj.get("us").getAsBoolean());
    }
}
