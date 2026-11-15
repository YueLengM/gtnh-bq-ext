package com.yuelengm.bqext.ae;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.util.StringUtils;

import com.yuelengm.bqext.ae.AeTaskDetector.NetworkView;

import appeng.api.storage.data.IAEItemStack;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.BigItemStack;
import betterquesting.api.utils.ItemComparison;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.tasks.TaskRetrieval;

public final class AeRetrievalDetector {

    private AeRetrievalDetector() {}

    static void detect(TaskRetrieval task, ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest,
        List<NetworkView> networks, int[] initialProgress) {
        if (task.consume || task.isComplete(pInfo.UUID)) return;

        int[] bestProgress = initialProgress;
        for (NetworkView network : networks) {
            if (network.storage == null) continue;

            int[] candidate = planPartialItems(task, AeStorageSnapshotCache.getItemSnapshot(network), initialProgress);
            if (AeTaskDetector.isProgressComplete(task, candidate)) {
                AeTaskDetector.applyProgress(task, pInfo, quest, candidate, true);
                return;
            }
            bestProgress = AeTaskDetector.mergeMaxProgress(task, bestProgress, candidate);
        }

        AeTaskDetector.applyPartialProgress(task, pInfo, quest, initialProgress, bestProgress);
    }

    private static int[] planPartialItems(TaskRetrieval task, AeItemSnapshot snapshot, int[] initialProgress) {
        AvailableItems availableItems = createAvailableItems(snapshot);
        int[] progress = initialProgress.clone();

        if (task.requireOnlyOneItem) {
            for (int i = 0; i < task.requiredItems.size(); i++) {
                BigItemStack requirement = task.requiredItems.get(i);
                long remaining = Math.max(0, requirement.stackSize - AeTaskDetector.getProgress(progress, i));
                if (remaining <= 0) continue;

                AvailableItems workingItems = copyFreshAvailableItems(availableItems);
                addProgress(task, progress, i, countItems(task, requirement, remaining, workingItems));
            }
            return progress;
        }

        for (int i = 0; i < task.requiredItems.size(); i++) {
            BigItemStack requirement = task.requiredItems.get(i);
            long remaining = Math.max(0, requirement.stackSize - AeTaskDetector.getProgress(progress, i));
            if (remaining <= 0) continue;

            addProgress(task, progress, i, countItems(task, requirement, remaining, availableItems));
        }
        return progress;
    }

    /** Reserves counts only in this check's working inventory; never removes items from AE. */
    static int countItems(TaskRetrieval task, BigItemStack requirement, long requested, AvailableItems available) {
        long remaining = requested;
        for (IAEItemStack stack : available.snapshot.getCandidates(
            requirement.getBaseStack()
                .getItem(),
            !StringUtils.isNullOrEmpty(requirement.getOreDict()))) {
            if (remaining <= 0) break;
            AvailableItem candidate = available.items.computeIfAbsent(stack, AvailableItem::new);
            if (candidate.remaining <= 0
                || !matchesItem(task, requirement, available.snapshot.getComparisonStack(candidate.stack))) continue;

            long amount = Math.min(remaining, candidate.remaining);
            candidate.remaining -= amount;
            remaining -= amount;
        }
        return (int) Math.min(Integer.MAX_VALUE, requested - remaining);
    }

    static void addProgress(TaskRetrieval task, int[] progress, int index, int amount) {
        int required = task.requiredItems.get(index).stackSize;
        progress[index] = Math.min(required, progress[index] + amount);
    }

    private static boolean matchesItem(TaskRetrieval task, BigItemStack requirement, ItemStack sample) {
        return ItemComparison.StackMatch(requirement.getBaseStack(), sample, !task.ignoreNBT, task.partialMatch)
            || ItemComparison.OreDictionaryMatch(
                requirement.getOreIngredient(),
                requirement.GetTagCompound(),
                sample,
                !task.ignoreNBT,
                task.partialMatch);
    }

    static AvailableItems createAvailableItems(AeItemSnapshot snapshot) {
        return new AvailableItems(snapshot);
    }

    static AvailableItems copyFreshAvailableItems(AvailableItems source) {
        return new AvailableItems(source.snapshot);
    }

    static final class AvailableItems {

        private final AeItemSnapshot snapshot;
        // Each plan owns its counts, including fresh alternatives for requireOnlyOneItem.
        private final Map<IAEItemStack, AvailableItem> items = new IdentityHashMap<>();

        private AvailableItems(AeItemSnapshot snapshot) {
            this.snapshot = snapshot;
        }
    }

    private static final class AvailableItem {

        final IAEItemStack stack;
        long remaining;

        private AvailableItem(IAEItemStack stack) {
            this.stack = stack;
            this.remaining = stack.getStackSize();
        }
    }

}
