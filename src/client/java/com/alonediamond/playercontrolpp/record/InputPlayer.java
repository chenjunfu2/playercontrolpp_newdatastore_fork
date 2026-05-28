package com.alonediamond.playercontrolpp.record;

import com.alonediamond.playercontrolpp.util.MessageUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;

public class InputPlayer {

    public enum State { IDLE, PLAYING, COMPLETED }

    private RecordingFile recording;
    private State state = State.IDLE;
    private int frameIndex;
    private int playCount;       // configured play count
    private int currentPlay;     // current play number (0-based)
    private RecordedFrame currentFrame;

    // Cached playback input for the current frame (read by mixin)
    private float playForward;
    private float playSideways;
    private boolean playJump;
    private boolean playSneak;
    private boolean playLeftClick;
    private boolean playRightClick;
    private float playYaw;
    private float playPitch;

    public State getState() { return state; }
    public boolean isPlaying() { return state == State.PLAYING; }

    // Current-frame input getters (read by MixinKeyboardInput)
    public float getForward() { return playForward; }
    public float getSideways() { return playSideways; }
    public boolean getJump() { return playJump; }
    public boolean getSneak() { return playSneak; }
    public boolean getLeftClick() { return playLeftClick; }
    public boolean getRightClick() { return playRightClick; }
    public float getYaw() { return playYaw; }
    public float getPitch() { return playPitch; }

    public void start(RecordingFile rec, int playCount) {
        if (rec == null || rec.getFrames().isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        this.recording = rec;
        this.playCount = playCount;
        this.currentPlay = 0;
        this.frameIndex = 0;
        this.state = State.PLAYING;
        loadFrame(0);

        // Teleport to recording start position for first play
        player.setPosition(rec.getStartX(), rec.getStartY(), rec.getStartZ());
        player.setYaw(rec.getStartYaw());
        player.setHeadYaw(rec.getStartYaw());
        player.setPitch(rec.getStartPitch());

        MessageUtil.sendActionBar(client, "playercontrolpp.message.playback.started");
    }

    public void stop() {
        state = State.IDLE;
        playForward = 0;
        playSideways = 0;
        playJump = false;
        playSneak = false;
        playLeftClick = false;
        playRightClick = false;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            MessageUtil.sendActionBar(client, "playercontrolpp.message.playback.stopped");
        }
    }

    public void tick(MinecraftClient client) {
        if (state != State.PLAYING) return;

        ClientPlayerEntity player = client.player;
        if (player == null || recording == null) {
            state = State.IDLE;
            return;
        }

        // Advance frame
        frameIndex++;
        if (frameIndex >= recording.getFrames().size()) {
            // Completed one playback
            currentPlay++;
            if (playCount == 0 || currentPlay < playCount) {
                // Restart: teleport back to start
                player.setPosition(
                        recording.getStartX(), recording.getStartY(), recording.getStartZ());
                player.setYaw(recording.getStartYaw());
                player.setHeadYaw(recording.getStartYaw());
                player.setPitch(recording.getStartPitch());
                frameIndex = 0;
                loadFrame(0);
            } else {
                state = State.COMPLETED;
                playForward = 0;
                playSideways = 0;
                playJump = false;
                playSneak = false;
                playLeftClick = false;
                playRightClick = false;
                MessageUtil.sendActionBar(client, "playercontrolpp.message.playback.completed");
            }
            return;
        }

        loadFrame(frameIndex);
    }

    private void loadFrame(int idx) {
        if (recording == null || idx >= recording.getFrames().size()) return;
        RecordedFrame f = recording.getFrames().get(idx);
        this.currentFrame = f;
        this.playForward = f.movementForward;
        this.playSideways = f.movementSideways;
        this.playJump = f.jump;
        this.playSneak = f.sneak;
        this.playLeftClick = f.leftClick;
        this.playRightClick = f.rightClick;
        this.playYaw = f.yaw;
        this.playPitch = f.pitch;
    }

    /**
     * Apply yaw/pitch from the current frame to the player entity.
     * Handled separately from keyboard input since yaw is not keyboard-based.
     */
    public void applyYaw(MinecraftClient client) {
        if (state != State.PLAYING) return;
        ClientPlayerEntity player = client.player;
        if (player == null || currentFrame == null) return;
        player.setYaw(MathHelper.wrapDegrees(playYaw));
        player.setHeadYaw(MathHelper.wrapDegrees(playYaw));
        player.setPitch(playPitch);
    }
}
