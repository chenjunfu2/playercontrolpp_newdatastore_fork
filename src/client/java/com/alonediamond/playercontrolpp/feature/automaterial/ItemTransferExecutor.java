package com.alonediamond.playercontrolpp.feature.automaterial;

import com.alonediamond.playercontrolpp.feature.AutoMaterialGatherer.State;
import com.alonediamond.playercontrolpp.feature.ItemTransferStrategy;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * Handles item transfer from containers: loose items first, then shulker boxes.
 */
public class ItemTransferExecutor {

    /**
     * Check whether enough of the current target item has been gathered,
     * counting items in the player's inventory AND inside shulker boxes.
     * Used after shulker box auto-store to decide whether to continue.
     */
    public boolean isCurrentItemSatisfied(GatherContext ctx) {
        if (ctx.currentTargetItem == null) return false;
        int total = countItemInInventory(ctx.currentTargetItem, ctx.client);
        total += countItemInShulkerBoxes(ctx.currentTargetItem, ctx.client);
        ctx.currentlyGathered = total;
        return total >= ctx.targetNeededTotal;
    }

    /**
     * Set up the next item to gather.
     */
    public void nextItem(GatherContext ctx, TaskStateMachine tsm) {
        ctx.justTookShulkerBox = false;

        if (ctx.currentItemIndex >= ctx.missingItems.size()) {
            tsm.setState(State.COMPLETED);
            return;
        }

        MaterialItemEntry entry = ctx.missingItems.get(ctx.currentItemIndex);
        ctx.currentTargetItem = entry.item;
        ctx.targetNeededTotal = entry.neededCount;
        ctx.currentlyGathered = countItemInInventory(entry.item, ctx.client)
                + countItemInShulkerBoxes(entry.item, ctx.client);
        ctx.currentPosIndex = 0;
        ctx.chestRetryCount = 0;
        ctx.foundPositions.clear();

        if (ctx.currentlyGathered >= ctx.targetNeededTotal) {
            ctx.currentItemIndex++;
            tsm.setState(State.NEXT_ITEM);
            return;
        }

        int stillNeeded = ctx.targetNeededTotal - ctx.currentlyGathered;
        ctx.currentTransferPlan = ItemTransferStrategy.calculate(stillNeeded, entry.maxStackSize);
        ctx.stacksTakenThisContainer.clear();
        ctx.shulkerBoxesTakenThisContainer.clear();

        tsm.setState(State.SEARCHING);
    }

    /**
     * Handle item transfer from an open container.
     */
    public void transfer(GatherContext ctx, TaskStateMachine tsm) {
        MinecraftClient mc = ctx.client;

        if (ctx.transferCooldown > 0) return;

        if (!(mc.currentScreen instanceof HandledScreen<?>)) {
            tsm.setState(State.VERIFYING);
            return;
        }

        if (isInventoryFull(mc)) {
            mc.player.closeHandledScreen();
            tsm.onInventoryFull();
            return;
        }

        ScreenHandler handler = mc.player.currentScreenHandler;
        if (handler == null) {
            mc.player.closeHandledScreen();
            tsm.setState(State.VERIFYING);
            return;
        }

        java.util.List<Slot> slots = handler.slots;
        boolean transferred = false;

        // Phase 1a: Loose items first
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.inventory == mc.player.getInventory()) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || isShulkerBox(stack)) continue;

            MaterialItemEntry matchedEntry = findMatchingMissingItem(stack, ctx);
            if (matchedEntry == null) continue;

            if (tryTransferLoose(mc, handler, slot, matchedEntry, ctx)) {
                ctx.transferCooldown = 4;
                return;
            }
        }

        // Phase 1b: Shulker boxes — only if verified to contain needed items
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.inventory == mc.player.getInventory()) continue;
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || !isShulkerBox(stack)) continue;

            if (!shulkerBoxContainsAnyMissingItem(stack, ctx)) continue;

            MaterialItemEntry bestEntry = findBestMissingItemForShulker(stack, ctx);
            if (bestEntry == null) continue;

            if (tryTransferShulker(mc, handler, slot, bestEntry, ctx)) {
                ctx.justTookShulkerBox = true;
                ctx.transferCooldown = 4;
                return;
            }
        }

        if (!transferred) {
            mc.player.closeHandledScreen();
            ctx.transferCooldown = 8;
            tsm.setState(State.VERIFYING);
        }
    }

    /**
     * Verify if enough items have been gathered for the current target.
     */
    public void verify(GatherContext ctx, TaskStateMachine tsm,
                        ContainerOpener opener, BaritonePathingController pathing) {
        ctx.currentlyGathered = countItemInInventory(ctx.currentTargetItem, ctx.client);
        ctx.currentlyGathered += countItemInShulkerBoxes(ctx.currentTargetItem, ctx.client);

        if (ctx.currentlyGathered >= ctx.targetNeededTotal) {
            ctx.currentItemIndex++;
            tsm.setState(State.NEXT_ITEM);
        } else {
            ctx.stacksTakenThisContainer.clear();
            ctx.shulkerBoxesTakenThisContainer.clear();

            ctx.currentPosIndex++;
            ctx.adjacentContainerTargets = null;
            ctx.adjacentTryIndex = 0;
            if (ctx.currentPosIndex >= ctx.foundPositions.size()) {
                ctx.stacksTakenThisContainer.clear();
                ctx.shulkerBoxesTakenThisContainer.clear();
                tsm.setState(State.SEARCHING);
            } else {
                navigateToContainer(ctx.foundPositions.get(ctx.currentPosIndex), ctx, tsm, opener, pathing);
            }
        }
    }

    private void navigateToContainer(BlockPos pos, GatherContext ctx, TaskStateMachine tsm,
                                      ContainerOpener opener, BaritonePathingController pathing) {
        if (ctx.client.player == null) return;
        if (ctx.client.player.getBlockPos().getSquaredDistance(pos) <= 25.0) {
            tsm.setState(State.OPENING_CONTAINER);
            opener.openContainerAt(pos, ctx);
        } else {
            tsm.setState(State.PATHING);
            pathing.startPathing(pos, ctx);
        }
    }

    // --- Helper methods ---

    private MaterialItemEntry findMatchingMissingItem(ItemStack stack, GatherContext ctx) {
        for (MaterialItemEntry entry : ctx.missingItems) {
            if (itemsMatch(stack, entry.item)) {
                return entry;
            }
        }
        return null;
    }

    private MaterialItemEntry findBestMissingItemForShulker(ItemStack shulkerBox, GatherContext ctx) {
        MaterialItemEntry best = null;
        int bestNeeded = 0;
        for (MaterialItemEntry entry : ctx.missingItems) {
            int have = countItemInInventory(entry.item, ctx.client) + countItemInShulkerBoxes(entry.item, ctx.client);
            int needed = entry.neededCount - have;
            if (needed > 128 && shulkerBoxContainsItem(shulkerBox, entry.item)) {
                if (needed > bestNeeded) {
                    bestNeeded = needed;
                    best = entry;
                }
            }
        }
        return best;
    }

    private boolean tryTransferLoose(MinecraftClient mc, ScreenHandler handler, Slot slot, MaterialItemEntry entry, GatherContext ctx) {
        int have = countItemInInventory(entry.item, ctx.client);
        int needed = entry.neededCount - have;
        if (needed <= 0) return false;

        int taken = ctx.stacksTakenThisContainer.getOrDefault(entry.item, 0);
        int stackSize = entry.maxStackSize > 0 ? entry.maxStackSize : 64;
        int maxStacks = (needed + stackSize - 1) / stackSize;

        if (taken >= maxStacks) return false;

        try {
            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0,
                    SlotActionType.QUICK_MOVE, mc.player);
            ctx.stacksTakenThisContainer.put(entry.item, taken + 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean tryTransferShulker(MinecraftClient mc, ScreenHandler handler, Slot slot, MaterialItemEntry entry, GatherContext ctx) {
        int have = countItemInInventory(entry.item, ctx.client) + countItemInShulkerBoxes(entry.item, ctx.client);
        int needed = entry.neededCount - have;
        if (needed <= 0) return false;

        int taken = ctx.shulkerBoxesTakenThisContainer.getOrDefault(entry.item, 0);
        int stackSize = entry.maxStackSize > 0 ? entry.maxStackSize : 64;
        int shulkerCap = 27 * stackSize;
        int maxBoxes = (needed + shulkerCap - 1) / shulkerCap;

        if (taken >= maxBoxes) return false;

        try {
            mc.interactionManager.clickSlot(handler.syncId, slot.id, 0,
                    SlotActionType.QUICK_MOVE, mc.player);
            ctx.shulkerBoxesTakenThisContainer.put(entry.item, taken + 1);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int countItemInInventory(Item item, MinecraftClient mc) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (itemsMatch(stack, item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private int countItemInShulkerBoxes(Item targetItem, MinecraftClient mc) {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isShulkerBox(stack)) {
                count += countItemInShulkerBox(stack, targetItem);
            }
        }
        return count;
    }

    private int countItemInShulkerBox(ItemStack shulkerBox, Item targetItem) {
        ContainerComponent container = shulkerBox.getComponents().get(DataComponentTypes.CONTAINER);
        if (container == null) return 0;
        int count = 0;
        for (ItemStack inner : container.iterateNonEmpty()) {
            if (itemsMatch(inner, targetItem)) {
                count += inner.getCount();
            }
        }
        return count;
    }

    private boolean isShulkerBox(ItemStack stack) {
        Identifier id = Registries.ITEM.getId(stack.getItem());
        return id.getPath().contains("shulker_box");
    }

    private boolean itemsMatch(ItemStack stack, Item targetItem) {
        if (stack.getItem() == targetItem) return true;
        Identifier stackId = Registries.ITEM.getId(stack.getItem());
        Identifier targetId = Registries.ITEM.getId(targetItem);
        return stackId.equals(targetId);
    }

    private boolean shulkerBoxContainsItem(ItemStack shulkerBox, Item targetItem) {
        ContainerComponent container = shulkerBox.getComponents().get(DataComponentTypes.CONTAINER);
        if (container == null) return false;
        for (ItemStack inner : container.iterateNonEmpty()) {
            if (itemsMatch(inner, targetItem)) return true;
        }
        return false;
    }

    private boolean shulkerBoxContainsAnyMissingItem(ItemStack shulkerBox, GatherContext ctx) {
        ContainerComponent container = shulkerBox.getComponents().get(DataComponentTypes.CONTAINER);
        if (container == null) return false;
        for (ItemStack inner : container.iterateNonEmpty()) {
            for (MaterialItemEntry entry : ctx.missingItems) {
                if (itemsMatch(inner, entry.item)) return true;
            }
        }
        return false;
    }

    private boolean isInventoryFull(MinecraftClient mc) {
        if (mc.player == null) return true;
        for (int i = 0; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }
}
