package com.alonediamond.playercontrolpp.feature.automaterial;

import com.alonediamond.playercontrolpp.config.Configs;
import com.alonediamond.playercontrolpp.config.StorageMode;
import com.alonediamond.playercontrolpp.integration.QuickShulkerIntegration;
import com.alonediamond.playercontrolpp.util.MessageUtil;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

/**
 * Handles auto-storing gathered building materials into shulker boxes
 * when inventory is full during auto material gathering.
 *
 * <p>Flow:</p>
 * <pre>
 * FINDING_SHULKER → FINDING_POSITION → SWITCHING_SHULKER → PLACING
 * → OPENING → TRANSFERRING → CLOSING → MINING → WAITING_PICKUP → DONE/FAILED
 * </pre>
 */
public class ShulkerBoxStorage {

    public enum StorageState {
        IDLE, FINDING_SHULKER, FINDING_POSITION, SWITCHING_SHULKER,
        PLACING, OPENING, QUICK_OPEN, TRANSFERRING, CLOSING,
        MINING, WAITING_PICKUP, DONE
    }

    private StorageState state = StorageState.IDLE;
    private boolean active;
    private StorageResult terminalResult = StorageResult.ACTIVE; // set when reaching a terminal outcome
    private int cooldown;
    private int retryCount;
    private int miningTicks;
    private int waitTicks;
    private int openVerifyTicks;

    private final QuickShulkerIntegration quickShulker = QuickShulkerIntegration.getInstance();
    private boolean useQuickShulkerMode;
    private boolean anyItemsTransferred;
    private final java.util.Set<Integer> knownFullSlots = new java.util.HashSet<>();

    private int shulkerSlotIndex = -1;
    private int hotbarSlotIndex = -1;
    private BlockPos placedPos;           // where the shulker box was placed
    private BlockPos placeAgainst;        // the block we clicked against to place
    private Direction placeClickFace;     // which face of placeAgainst we clicked
    private int transferIndex;
    private int prevSelectedSlot;
    private int pickaxeSlot = -1;

    public boolean isActive() { return active; }

    public static boolean isEnabled() {
        return Configs.BaritoneSettings.AUTO_STORE_TO_SHULKER.getBooleanValue();
    }

    /** Whether QuickShulker storage mode is active. Requires config set to QUICKSHULKER + mod installed. */
    public static boolean isQuickShulkerModeEnabled() {
        StorageMode mode = (StorageMode) Configs.BaritoneSettings.SHULKER_STORAGE_MODE.getOptionListValue();
        return mode == StorageMode.QUICKSHULKER
                && QuickShulkerIntegration.getInstance().isLoaded();
    }

    public boolean startStorage(GatherContext ctx) {
        MinecraftClient mc = ctx.client;
        if (mc.player == null) return false;

        state = StorageState.IDLE;
        active = true;
        terminalResult = StorageResult.ACTIVE;
        cooldown = 0;
        retryCount = 0;
        miningTicks = 0;
        waitTicks = 0;
        openVerifyTicks = 0;
        shulkerSlotIndex = -1;
        hotbarSlotIndex = -1;
        placedPos = null;
        placeAgainst = null;
        placeClickFace = null;
        transferIndex = 0;
        prevSelectedSlot = mc.player.getInventory().selectedSlot;
        pickaxeSlot = -1;
        anyItemsTransferred = false;
        // NOTE: knownFullSlots is NOT cleared — full boxes stay full across cycles
        useQuickShulkerMode = isQuickShulkerModeEnabled();

        MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_store_start");
        return true;
    }

    public StorageResult tick(GatherContext ctx) {
        MinecraftClient mc = ctx.client;
        if (!active || mc.player == null) {
            return terminalResult != StorageResult.ACTIVE ? terminalResult : StorageResult.ACTIVE;
        }
        if (cooldown > 0) { cooldown--; return StorageResult.ACTIVE; }
        if (mc.player.isDead()) { abort(mc); return StorageResult.FAILED; }

        switch (state) {
            case IDLE:
                state = StorageState.FINDING_SHULKER;
                break;
            case FINDING_SHULKER:
                doFindShulker(mc, ctx);
                break;
            case FINDING_POSITION:
                doFindPosition(mc);
                break;
            case SWITCHING_SHULKER:
                doSwitchToShulker(mc);
                break;
            case PLACING:
                doPlace(mc);
                break;
            case OPENING:
                doOpen(mc);
                break;
            case QUICK_OPEN:
                doQuickOpen(mc);
                break;
            case TRANSFERRING:
                doTransfer(mc, ctx);
                break;
            case CLOSING:
                doClose(mc);
                break;
            case MINING:
                doMine(mc);
                break;
            case WAITING_PICKUP:
                return doWaitPickup(mc);
            case DONE:
                active = false;
                return StorageResult.DONE;
        }
        // If a state handler set a terminal result, propagate it immediately
        if (!active && terminalResult != StorageResult.ACTIVE) {
            return terminalResult;
        }
        return StorageResult.ACTIVE;
    }

    // ---- Phases ----

    /**
     * QuickShulker mode: send OpenShulkerPacket directly for the shulker box
     * at its current inventory position. No item swapping — compute the
     * PlayerScreenHandler slot index and send the packet.
     */
    private void doQuickOpen(MinecraftClient mc) {
        if (!quickShulker.isLoaded()) {
            fail(mc);
            return;
        }

        // Compute screen handler slot for PlayerScreenHandler:
        //   inventory index 0-8  (hotbar)       → screen slot 36-44
        //   inventory index 9-35 (main inventory) → screen slot 9-35
        int screenSlot = shulkerSlotIndex < 9 ? 36 + shulkerSlotIndex : shulkerSlotIndex;

        if (!quickShulker.openShulkerBox(screenSlot)) {
            retryCount++;
            if (retryCount < 5) {
                cooldown = 3;
                return;
            }
            MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_open_failed");
            fail(mc);
            return;
        }

        retryCount = 0;
        openVerifyTicks = 0;
        transferIndex = 0;
        state = StorageState.TRANSFERRING;
    }

    private void doFindShulker(MinecraftClient mc, GatherContext ctx) {
        for (int i = 0; i < 36; i++) {
            if (knownFullSlots.contains(i)) {
                continue;
            }
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !isShulkerBox(stack)) continue;
            if (isOnMissingList(stack, ctx)) {
                continue;
            }
            // Don't check hasSpaceInShulkerBox — NBT may be stale.
            // Fullness is determined by the actual GUI (isShulkerBoxFull) when opened.
            shulkerSlotIndex = i;
            state = useQuickShulkerMode ? StorageState.QUICK_OPEN : StorageState.FINDING_POSITION;
            return;
        }
        MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_no_box");
        terminalResult = StorageResult.FAILED;
        active = false;
    }

    /**
     * Find a valid ground-level placement position in front of the player.
     * Places on the ground (player feet Y level), never on the block the player stands on.
     */
    private void doFindPosition(MinecraftClient mc) {
        BlockPos playerFeet = mc.player.getBlockPos();

        // Determine horizontal facing direction from yaw
        float yaw = mc.player.getYaw();
        double rad = Math.toRadians(yaw);
        int facingX = (int) -Math.round(Math.sin(rad));
        int facingZ = (int) Math.round(Math.cos(rad));

        // If facing has no horizontal component, default south
        if (facingX == 0 && facingZ == 0) { facingZ = 1; }

        // Try positions at distance 1 and 2 in the facing direction
        int[][] offsets = {
            {facingX, facingZ},
            {facingX * 2, facingZ * 2},
            {-facingX, -facingZ},        // behind
            {facingZ, -facingX},         // right
            {-facingZ, facingX},         // left
        };

        for (int[] off : offsets) {
            int ox = off[0], oz = off[1];
            BlockPos ground = playerFeet.add(ox, -1, oz);
            BlockPos placeAt = playerFeet.add(ox, 0, oz);

            // Don't place on the block the player is standing on
            if (placeAt.equals(playerFeet)) continue;

            BlockState groundState = mc.world.getBlockState(ground);
            BlockState placeState = mc.world.getBlockState(placeAt);

            if (!groundState.isSolid()) continue;
            if (!placeState.isAir() && !placeState.isReplaceable()) continue;

            // Verify the player can reach this position (within ~5 blocks)
            if (playerFeet.getSquaredDistance(placeAt) > 25) continue;

            placedPos = placeAt;
            placeAgainst = ground;
            placeClickFace = Direction.UP;
            retryCount = 0;
            state = StorageState.SWITCHING_SHULKER;
            return;
        }

        // No valid position found
        retryCount++;
        if (retryCount >= 3) {
            MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_no_position");
            fail(mc);
            return;
        }
        // Look down and wait a moment, then retry
        mc.player.setPitch(90f);
        cooldown = 10;
    }

    private void doSwitchToShulker(MinecraftClient mc) {
        if (shulkerSlotIndex < 9) {
            hotbarSlotIndex = shulkerSlotIndex;
            mc.player.getInventory().selectedSlot = hotbarSlotIndex;
        } else {
            hotbarSlotIndex = mc.player.getInventory().selectedSlot;
            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    36 + hotbarSlotIndex, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    shulkerSlotIndex, 0, SlotActionType.PICKUP, mc.player);
            mc.interactionManager.clickSlot(
                    mc.player.playerScreenHandler.syncId,
                    36 + hotbarSlotIndex, 0, SlotActionType.PICKUP, mc.player);
        }
        cooldown = 3;
        state = StorageState.PLACING;
    }

    private void doPlace(MinecraftClient mc) {
        if (placedPos == null || placeAgainst == null) { fail(mc); return; }

        // Verify the shulker box actually got placed
        if (retryCount == 0) {
            faceToward(mc, Vec3d.ofCenter(placedPos));

            Vec3d hitPos = new Vec3d(
                    placeAgainst.getX() + 0.5,
                    placeAgainst.getY() + 1.0,
                    placeAgainst.getZ() + 0.5
            );
            BlockHitResult hitResult = new BlockHitResult(hitPos, placeClickFace, placeAgainst, false);

            try {
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            } catch (Exception e) {
                mc.options.useKey.setPressed(true);
                cooldown = 2;
                return;
            }
        }

        // Wait a few ticks then verify placement
        retryCount++;
        if (retryCount < 4) {
            cooldown = 2;
            return;
        }

        // Verify: the block at placedPos should no longer be air (shulker box was placed)
        BlockState placedState = mc.world.getBlockState(placedPos);
        if (placedState.isAir()) {
            // Placement failed — retry once
            if (retryCount < 10) {
                cooldown = 3;
                return;
            }
            MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_place_failed");
            fail(mc);
            return;
        }

        retryCount = 0;
        cooldown = 3;
        state = StorageState.OPENING;
    }

    private void doOpen(MinecraftClient mc) {
        if (placedPos == null) { fail(mc); return; }

        // Face and open the placed shulker box
        faceToward(mc, Vec3d.ofCenter(placedPos));

        Direction nearestFace = getNearestFace(mc, placedPos);
        Vec3d hitPos = new Vec3d(
                placedPos.getX() + 0.5 + nearestFace.getOffsetX() * 0.5,
                placedPos.getY() + 0.5 + nearestFace.getOffsetY() * 0.5,
                placedPos.getZ() + 0.5 + nearestFace.getOffsetZ() * 0.5
        );
        BlockHitResult hitResult = new BlockHitResult(hitPos, nearestFace, placedPos, false);

        try {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        } catch (Exception e) {
            mc.options.useKey.setPressed(true);
            cooldown = 2;
            return;
        }

        cooldown = 6;
        state = StorageState.TRANSFERRING;
        retryCount = 0;
        openVerifyTicks = 0;
        transferIndex = 0;
    }

    private void doTransfer(MinecraftClient mc, GatherContext ctx) {
        // Keep player facing the shulker box
        if (placedPos != null) {
            faceToward(mc, Vec3d.ofCenter(placedPos));
        }

        // Wait for GUI to open — with verification timeout
        if (!(mc.currentScreen instanceof HandledScreen<?>)) {
            openVerifyTicks++;
            if (openVerifyTicks > 30) {
                MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_open_failed");
                fail(mc);
                return;
            }
            return;
        }
        // GUI opened successfully
        openVerifyTicks = 0;

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null) {
            mc.player.closeHandledScreen();
            MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_open_failed");
            fail(mc);
            return;
        }

        if (isShulkerBoxFull()) {
            mc.player.closeHandledScreen();
            knownFullSlots.add(shulkerSlotIndex);
            if (!anyItemsTransferred) {
                // Check if any remaining candidate boxes exist before looping back
                if (!hasCandidateShulker(mc, ctx)) {
                    MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_no_box");
                    fail(mc);
                    return;
                }
                cooldown = 3;
                state = StorageState.FINDING_SHULKER;
                anyItemsTransferred = false;
                return;
            }
            state = StorageState.CLOSING;
            return;
        }

        // Scan player inventory slots (27-62) for building materials
        for (int i = 27; i <= 62 && transferIndex < 200; i++) {
            transferIndex++;
            Slot slot = handler.getSlot(i);
            if (slot == null) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            if (!isOnMissingList(stack, ctx)) continue;

            try {
                mc.interactionManager.clickSlot(handler.syncId, i, 0,
                        SlotActionType.QUICK_MOVE, mc.player);
                anyItemsTransferred = true;
                cooldown = 2;
                return;
            } catch (Exception e) {
                continue;
            }
        }

        // Done transferring — mark box full if no empty slots remain (can't accept new item types)
        if (!hasEmptySlotInShulkerBox()) {
            knownFullSlots.add(shulkerSlotIndex);
        }
        state = StorageState.CLOSING;
        cooldown = 3;
    }

    private void doClose(MinecraftClient mc) {
        if (mc.currentScreen instanceof HandledScreen) {
            mc.player.closeHandledScreen();
        }

        if (useQuickShulkerMode) {
            mc.player.getInventory().selectedSlot = prevSelectedSlot;
            terminalResult = StorageResult.DONE;
            state = StorageState.DONE;
            return;
        }

        // Manual mode: switch to pickaxe and mine
        cooldown = 3;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof PickaxeItem) {
                pickaxeSlot = i;
                mc.player.getInventory().selectedSlot = i;
                break;
            }
        }
        miningTicks = 0;
        state = StorageState.MINING;
    }

    private void doMine(MinecraftClient mc) {
        if (placedPos == null) { active = false; return; }

        // Face the placed shulker box
        faceToward(mc, Vec3d.ofCenter(placedPos));

        Direction face = getNearestFace(mc, placedPos);

        if (miningTicks == 0) {
            mc.interactionManager.attackBlock(placedPos, face);
        }

        mc.options.attackKey.setPressed(true);
        miningTicks++;

        if (miningTicks % 2 == 0) {
            mc.interactionManager.updateBlockBreakingProgress(placedPos, face);
        }

        if (mc.world.getBlockState(placedPos).isAir()) {
            mc.options.attackKey.setPressed(false);
            mc.player.getInventory().selectedSlot = prevSelectedSlot;
            waitTicks = 0;
            state = StorageState.WAITING_PICKUP;
            cooldown = 2;
            return;
        }

        if (miningTicks > 100) {
            mc.options.attackKey.setPressed(false);
            mc.player.getInventory().selectedSlot = prevSelectedSlot;
            MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_mine_failed");
            fail(mc);
        }
    }

    private StorageResult doWaitPickup(MinecraftClient mc) {
        waitTicks++;

        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && isShulkerBox(stack)) {
                MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_store_done");
                active = false;
                state = StorageState.DONE;
                return StorageResult.DONE;
            }
        }

        if (waitTicks > 100) {
            MessageUtil.sendActionBar(mc, "playercontrolpp.message.baritone.shulker_pickup_failed");
            active = false;
            state = StorageState.DONE;
            return StorageResult.FAILED;
        }

        return StorageResult.ACTIVE;
    }

    // ---- Helpers ----

    /**
     * Check if there is any usable shulker box that hasn't been marked full
     * and that isn't on the missing list. Used to avoid cycling through
     * already-known-full boxes.
     */
    private boolean hasCandidateShulker(MinecraftClient mc, GatherContext ctx) {
        if (mc.player == null) return false;
        for (int i = 0; i < 36; i++) {
            if (knownFullSlots.contains(i)) continue;
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty() || !isShulkerBox(stack)) continue;
            if (isOnMissingList(stack, ctx)) continue;
            return true;
        }
        return false;
    }

    private void abort(MinecraftClient mc) {
        mc.options.attackKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.sneakKey.setPressed(false);
        mc.options.useKey.setPressed(false);
        if (mc.player != null) {
            mc.player.getInventory().selectedSlot = prevSelectedSlot;
        }
        active = false;
    }

    /** Abort with a FAILED terminal result — signals the state machine to stop entirely. */
    private void fail(MinecraftClient mc) {
        terminalResult = StorageResult.FAILED;
        abort(mc);
    }

    public void cancel(MinecraftClient mc) { abort(mc); }

    /** Called when auto-gathering starts fresh — clears cross-cycle state. */
    public void resetKnownFullSlots() {
        knownFullSlots.clear();
    }

    private boolean isShulkerBox(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id.getPath().contains("shulker_box");
    }

    private boolean isOnMissingList(ItemStack stack, GatherContext ctx) {
        for (MaterialItemEntry entry : ctx.missingItems) {
            if (itemsMatch(stack, entry.item)) return true;
        }
        return false;
    }

    private boolean hasSpaceInShulkerBox(ItemStack shulkerBox) {
        ContainerComponent container = shulkerBox.getComponents().get(DataComponentTypes.CONTAINER);
        if (container == null) return true;
        int filledSlots = 0;
        for (ItemStack inner : container.iterateNonEmpty()) {
            filledSlots++;
            if (inner.getCount() < inner.getMaxCount()) return true;
        }
        return filledSlots < 27;
    }

    /** Check if the open shulker box has at least one completely empty slot (can accept new item types). */
    private boolean hasEmptySlotInShulkerBox() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null) return false;
        for (int i = 0; i < 27; i++) {
            Slot slot = mc.player.currentScreenHandler.getSlot(i);
            if (slot == null || !slot.hasStack()) return true;
        }
        return false;
    }

    private boolean isShulkerBoxFull() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.player.currentScreenHandler == null) return true;
        for (int i = 0; i < 27; i++) {
            Slot slot = mc.player.currentScreenHandler.getSlot(i);
            if (slot == null || !slot.hasStack()) return false;
            if (slot.getStack().getCount() < slot.getStack().getMaxCount()) return false;
        }
        return true;
    }

    private Direction getNearestFace(MinecraftClient mc, BlockPos pos) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = Vec3d.ofCenter(pos);
        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        if (ax >= ay && ax >= az) return dx > 0 ? Direction.EAST : Direction.WEST;
        if (ay >= ax && ay >= az) return dy > 0 ? Direction.UP : Direction.DOWN;
        return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    private void faceToward(MinecraftClient mc, Vec3d target) {
        Vec3d eye = mc.player.getEyePos();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double distH = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(dy, distH));
        mc.player.setYaw(yaw);
        mc.player.setHeadYaw(yaw);
        mc.player.setPitch(pitch);
    }

    private boolean itemsMatch(ItemStack stack, net.minecraft.item.Item targetItem) {
        if (stack.getItem() == targetItem) return true;
        Identifier stackId = Registries.ITEM.getId(stack.getItem());
        Identifier targetId = Registries.ITEM.getId(targetItem);
        return stackId.equals(targetId);
    }

    public enum StorageResult {
        ACTIVE, DONE, FAILED
    }
}
