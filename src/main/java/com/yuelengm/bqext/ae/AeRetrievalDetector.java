package com.yuelengm.bqext.ae;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StringUtils;
import net.minecraftforge.oredict.OreDictionary;

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
        int[] progress = initialProgress.clone();

        for (int i = 0; i < task.requiredItems.size(); i++) {
            BigItemStack requirement = task.requiredItems.get(i);
            long remaining = Math.max(0, requirement.stackSize - AeTaskDetector.getProgress(progress, i));
            if (remaining <= 0) continue;

            addProgress(task, progress, i, countItems(task, requirement, remaining, snapshot));
        }
        return progress;
    }

    /** Each non-consuming requirement independently observes the same unchanged inventory snapshot. */
    static int countItems(TaskRetrieval task, BigItemStack requirement, long requested, AeItemSnapshot snapshot) {
        long remaining = requested;
        for (IAEItemStack stack : snapshot.getCandidates(
            requirement.getBaseStack()
                .getItem(),
            !StringUtils.isNullOrEmpty(requirement.getOreDict()))) {
            if (remaining <= 0) break;
            long amount = stack.getStackSize();
            if (amount <= 0 || !matchesItem(task, requirement, stack, snapshot)) continue;

            remaining -= Math.min(remaining, amount);
        }
        return (int) Math.min(Integer.MAX_VALUE, requested - remaining);
    }

    static void addProgress(TaskRetrieval task, int[] progress, int index, int amount) {
        int required = task.requiredItems.get(index).stackSize;
        progress[index] = Math.min(required, progress[index] + amount);
    }

    private static boolean matchesItem(TaskRetrieval task, BigItemStack requirement, IAEItemStack candidate,
        AeItemSnapshot snapshot) {
        if (task.ignoreNBT && StringUtils.isNullOrEmpty(requirement.getOreDict())) {
            return matchesIgnoringNbt(requirement.getBaseStack(), candidate);
        }

        ItemStack sample = snapshot.getComparisonStack(candidate);
        return ItemComparison.StackMatch(requirement.getBaseStack(), sample, !task.ignoreNBT, task.partialMatch)
            || ItemComparison.OreDictionaryMatch(
                requirement.getOreIngredient(),
                requirement.GetTagCompound(),
                sample,
                !task.ignoreNBT,
                task.partialMatch);
    }

    static boolean matchesIgnoringNbt(ItemStack requirement, IAEItemStack candidate) {
        Item item = requirement.getItem();
        if (item != candidate.getItem()) return false;

        int damage = requirement.getItemDamage();
        if (item.isDamageable() || damage == OreDictionary.WILDCARD_VALUE) return true;
        // Treat AE damage as equivalent to ItemStack damage; preserve constructor normalization.
        return damage == Math.max(0, candidate.getItemDamage());
    }

}
