package com.alonediamond.playercontrolpp.feature.automaterial;

import com.alonediamond.playercontrolpp.feature.AutoMaterialGatherer.State;
import com.alonediamond.playercontrolpp.integration.BaritoneIntegration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Encapsulates Baritone pathing logic for navigating to container positions.
 */
public class BaritonePathingController {

    private final BaritoneIntegration baritone;

    public BaritonePathingController(BaritoneIntegration baritone) {
        this.baritone = baritone;
    }

    public void startPathing(BlockPos target, GatherContext ctx) {
        ctx.currentPathTarget = target;
        ctx.pathingTicks = 0;
        ctx.stuckTicks = 0;
        ctx.pathingWasActive = false;
        ctx.lastPlayerPos = ctx.client.player != null ? ctx.client.player.getPos() : Vec3d.ZERO;

        baritone.pathTo(target);
    }

    public void cancelPathing() {
        baritone.cancelPathing();
    }

    /**
     * Check pathing progress — called each tick during PATHING state.
     * When pathing completes, transitions to OPENING_CONTAINER.
     */
    public void checkProgress(GatherContext ctx, TaskStateMachine tsm, ContainerOpener opener) {
        if (ctx.client.player == null) return;

        ctx.pathingTicks++;

        if (isInventoryFull(ctx.client)) {
            tsm.onInventoryFull();
            return;
        }

        if (!ctx.pathingWasActive) {
            if (baritone.isPathing()) {
                ctx.pathingWasActive = true;
                ctx.stuckTicks = 0;
                ctx.lastPlayerPos = ctx.client.player.getPos();
            }
        }

        // Stuck detection: player not moved for 5 seconds
        if (ctx.pathingWasActive && ctx.pathingTicks > 5) {
            Vec3d currentPos = ctx.client.player.getPos();
            double moved = currentPos.squaredDistanceTo(ctx.lastPlayerPos);
            if (moved < 0.04) {
                ctx.stuckTicks++;
                if (ctx.stuckTicks >= 100) {
                    tsm.setState(State.FAILED);
                    return;
                }
            } else {
                ctx.stuckTicks = 0;
            }
            ctx.lastPlayerPos = currentPos;
        }

        // Check if Baritone has reached destination
        if (ctx.pathingWasActive && ctx.pathingTicks > 5 && !baritone.isPathing()) {
            ctx.stuckTicks = 0;
            ctx.lastPlayerPos = Vec3d.ZERO;
            ctx.pathingTicks = 0;
            ctx.pathingWasActive = false;

            if (ctx.currentPosIndex < ctx.foundPositions.size()) {
                tsm.setState(State.OPENING_CONTAINER);
                opener.openContainerAt(ctx.foundPositions.get(ctx.currentPosIndex), ctx);
            } else {
                tsm.skipCurrentItem();
            }
        }

        // Timeout: if pathing never started after 40 ticks
        if (!ctx.pathingWasActive && ctx.pathingTicks > 40) {
            tsm.setState(State.FAILED);
        }
    }

    private boolean isInventoryFull(net.minecraft.client.MinecraftClient mc) {
        if (mc.player == null) return true;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
