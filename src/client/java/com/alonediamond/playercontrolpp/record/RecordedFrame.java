package com.alonediamond.playercontrolpp.record;

import com.google.gson.JsonObject;

public class RecordedFrame {
    public float movementForward;
    public float movementSideways;
    public boolean jump;
    public boolean sneak;
    public boolean leftClick;
    public boolean rightClick;
    public float yaw;
    public float pitch;

    public RecordedFrame() {}

    public RecordedFrame(float mf, float ms, boolean jump, boolean sneak,
                         boolean leftClick, boolean rightClick, float yaw, float pitch) {
        this.movementForward = mf;
        this.movementSideways = ms;
        this.jump = jump;
        this.sneak = sneak;
        this.leftClick = leftClick;
        this.rightClick = rightClick;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("mf", movementForward);
        obj.addProperty("ms", movementSideways);
        obj.addProperty("j", jump);
        obj.addProperty("s", sneak);
        obj.addProperty("lc", leftClick);
        obj.addProperty("rc", rightClick);
        obj.addProperty("y", yaw);
        obj.addProperty("p", pitch);
        return obj;
    }

    public static RecordedFrame fromJson(JsonObject obj) {
        return new RecordedFrame(
                obj.has("mf") ? obj.get("mf").getAsFloat() : 0,
                obj.has("ms") ? obj.get("ms").getAsFloat() : 0,
                obj.has("j") && obj.get("j").getAsBoolean(),
                obj.has("s") && obj.get("s").getAsBoolean(),
                obj.has("lc") && obj.get("lc").getAsBoolean(),
                obj.has("rc") && obj.get("rc").getAsBoolean(),
                obj.has("y") ? obj.get("y").getAsFloat() : 0,
                obj.has("p") ? obj.get("p").getAsFloat() : 0);
    }
}
