package com.alonediamond.playercontrolpp.feature.automaterial;

import com.alonediamond.playercontrolpp.feature.AutoMaterialGatherer.State;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.item.ItemStack;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages container interaction: opening, retry logic, adjacent position fallback.
 */
public class ContainerOpener {

    /**
     * Open container at the given position. Called from OPENING_CONTAINER state transition.
     */
    public void openContainerAt(BlockPos target, GatherContext ctx) {
        openContainerWithRetry(target, false, 0, ctx);
    }

    /**
     * Opens a container by sending an explicit BlockHitResult via interactBlock().
     * This bypasses client-side raycasting, so adjacent containers do not interfere.
     */
    public void openContainerWithRetry(BlockPos target, boolean jumpBeforeClick, int attemptNumber, GatherContext ctx) {
        ctx.currentContainerTarget = target;
        ctx.openAttemptCount = attemptNumber;

        if (jumpBeforeClick && ctx.client.player != null) {
            ctx.client.player.jump();
        }

        try {
            Vec3d playerEye = ctx.client.player.getEyePos();
            double dx = target.getX() + 0.5 - playerEye.x;
            double dy = target.getY() + 0.5 - playerEye.y;
            double dz = target.getZ() + 0.5 - playerEye.z;
            double distH = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) Math.toDegrees(-Math.atan2(dy, distH));
            ctx.client.player.setYaw(yaw);
            ctx.client.player.setHeadYaw(yaw);
            ctx.client.player.setPitch(pitch);

            Direction face = getNearestContainerFace(target, ctx);
            Vec3d hitPos = new Vec3d(
                    target.getX() + 0.5 + face.getOffsetX() * 0.5,
                    target.getY() + 0.5 + face.getOffsetY() * 0.5,
                    target.getZ() + 0.5 + face.getOffsetZ() * 0.5
            );

            BlockHitResult hitResult = new BlockHitResult(hitPos, face, target, false);
            ctx.client.interactionManager.interactBlock(ctx.client.player, Hand.MAIN_HAND, hitResult);

            ctx.transferCooldown = 10;
            ctx.containerJustOpened = true;

        } catch (Exception e) {
            try {
                ctx.client.options.useKey.setPressed(true);
                ctx.transferCooldown = 6;
                ctx.containerJustOpened = true;
            } catch (Exception ignored) {}
        }
    }

    /**
     * Called when the transfer cooldown expires after attempting to open a container.
     * Checks if the container opened successfully and contains matching items.
     */
    public void checkOpenResult(GatherContext ctx, TaskStateMachine tsm, BaritonePathingController pathing) {
        MinecraftClient mc = ctx.client;

        if (mc.currentScreen instanceof HandledScreen<?>) {
            if (containerHasAnyMissingItem(ctx)) {
                tsm.setState(State.TRANSFERRING_ITEM);
                ctx.transferCooldown = 4;
                ctx.openAttemptCount = 0;
            } else {
                mc.player.closeHandledScreen();
                ctx.transferCooldown = 8;
                retryAdjacentOrFail(ctx, tsm, pathing);
            }
        } else {
            ctx.openAttemptCount++;
            if (ctx.openAttemptCount < 3) {
                boolean jump = (ctx.openAttemptCount == 2);
                openContainerWithRetry(ctx.currentContainerTarget, jump, ctx.openAttemptCount, ctx);
            } else {
                retryAdjacentOrFail(ctx, tsm, pathing);
            }
        }
    }

    /**
     * Called when the current container target fails to open or has wrong contents.
     */
    public void retryAdjacentOrFail(GatherContext ctx, TaskStateMachine tsm,
                                     BaritonePathingController pathing) {
        if (ctx.adjacentContainerTargets == null) {
            ctx.adjacentContainerTargets = getAdjacentContainerTargets(ctx.currentContainerTarget);
            ctx.adjacentTryIndex = 0;
        }

        while (ctx.adjacentTryIndex < ctx.adjacentContainerTargets.size()) {
            BlockPos adjPos = ctx.adjacentContainerTargets.get(ctx.adjacentTryIndex);
            ctx.adjacentTryIndex++;
            ctx.openAttemptCount = 0;
            openContainerAt(adjPos, ctx);
            return;
        }

        ctx.adjacentContainerTargets = null;
        ctx.adjacentTryIndex = 0;
        ctx.openAttemptCount = 0;
        ctx.chestRetryCount++;
        if (ctx.chestRetryCount >= 3) {
            ctx.chestRetryCount = 0;
            ctx.currentPosIndex++;
            if (ctx.currentPosIndex >= ctx.foundPositions.size()) {
                tsm.skipCurrentItem();
            } else {
                navigateToNextContainer(ctx.foundPositions.get(ctx.currentPosIndex), ctx, tsm, pathing);
            }
        } else {
            tsm.setState(State.SEARCHING);
        }
    }

    private void navigateToNextContainer(BlockPos pos, GatherContext ctx, TaskStateMachine tsm,
                                          BaritonePathingController pathing) {
        if (ctx.client.player == null) return;
        if (ctx.client.player.getBlockPos().getSquaredDistance(pos) <= 25.0) {
            tsm.setState(State.OPENING_CONTAINER);
            openContainerAt(pos, ctx);
        } else {
            tsm.setState(State.PATHING);
            pathing.startPathing(pos, ctx);
        }
    }

    /**
     * Close any currently open container screen.
     */
    public void closeAnyContainer(MinecraftClient mc) {
        if (mc.player != null && mc.currentScreen instanceof HandledScreen) {
            mc.player.closeHandledScreen();
        }
    }

    private Direction getNearestContainerFace(BlockPos target, GatherContext ctx) {
        if (ctx.client.player == null) return Direction.UP;
        Vec3d playerEye = ctx.client.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(target);

        double dx = playerEye.x - center.x;
        double dy = playerEye.y - center.y;
        double dz = playerEye.z - center.z;

        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);

        if (ax >= ay && ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private List<BlockPos> getAdjacentContainerTargets(BlockPos target) {
        List<BlockPos> adj = new ArrayList<>();
        adj.add(target);
        adj.add(target.west());
        adj.add(target.east());
        adj.add(target.north());
        adj.add(target.south());
        adj.add(target.up());
        adj.add(target.down());
        return adj;
    }

    private boolean containerHasAnyMissingItem(GatherContext ctx) {
        if (ctx.client.player == null || ctx.client.player.currentScreenHandler == null) return false;
        List<Slot> slots = ctx.client.player.currentScreenHandler.slots;
        for (Slot slot : slots) {
            if (slot.inventory == ctx.client.player.getInventory()) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            for (MaterialItemEntry entry : ctx.missingItems) {
                if (itemsMatch(stack, entry.item)) return true;
            }
            if (isShulkerBox(stack) && shulkerBoxContainsAnyMissingItem(stack, ctx)) return true;
        }
        return false;
    }

    public boolean shulkerBoxContainsAnyMissingItem(ItemStack shulkerBox, GatherContext ctx) {
        ContainerComponent container = shulkerBox.getComponents().get(DataComponentTypes.CONTAINER);
        if (container == null) return false;
        for (ItemStack inner : container.iterateNonEmpty()) {
            for (MaterialItemEntry entry : ctx.missingItems) {
                if (itemsMatch(inner, entry.item)) return true;
            }
        }
        return false;
    }

    private boolean itemsMatch(ItemStack stack, net.minecraft.item.Item targetItem) {
        if (stack.getItem() == targetItem) return true;
        net.minecraft.util.Identifier stackId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
        net.minecraft.util.Identifier targetId = net.minecraft.registry.Registries.ITEM.getId(targetItem);
        return stackId.equals(targetId);
    }

    private boolean isShulkerBox(ItemStack stack) {
        net.minecraft.util.Identifier id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem());
        return id.getPath().contains("shulker_box");
    }
}
