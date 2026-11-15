package com.yuelengm.bqext.ae;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraftforge.fluids.FluidStack;

import com.yuelengm.bqext.ae.AeTaskDetector.NetworkView;

import appeng.api.storage.data.IAEFluidStack;
import betterquesting.api.questing.IQuest;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.tasks.TaskFluid;

public final class AeFluidDetector {

    private AeFluidDetector() {}

    static void detect(TaskFluid task, ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest,
        List<NetworkView> networks) {
        if (task.consume || task.isComplete(pInfo.UUID)) return;

        for (NetworkView network : networks) {
            if (network.storage == null) continue;
            if (network.storage.getFluidInventory() == null) continue;

            int[] completedProgress = planFluids(
                task,
                AeStorageSnapshotCache.getFluidSnapshot(network),
                task.getUsersProgress(pInfo.UUID));
            if (completedProgress == null) continue;

            AeTaskDetector.completeTask(task, pInfo, quest, completedProgress);
            return;
        }
    }

    private static int[] planFluids(TaskFluid task, List<IAEFluidStack> snapshot, int[] progress) {
        if (task.requiredFluids.isEmpty()) return progress.clone();

        List<AvailableFluid> available = createAvailableFluids(snapshot);
        for (int i = 0; i < task.requiredFluids.size(); i++) {
            FluidStack requirement = task.requiredFluids.get(i);
            long remaining = Math.max(0, requirement.amount - AeTaskDetector.getProgress(progress, i));
            if (!allocateFluid(requirement, remaining, available)) return null;
        }

        int[] completedProgress = progress.clone();
        for (int i = 0; i < task.requiredFluids.size(); i++) {
            completedProgress[i] = task.requiredFluids.get(i).amount;
        }
        return completedProgress;
    }

    private static boolean allocateFluid(FluidStack requirement, long remaining, List<AvailableFluid> available) {
        for (AvailableFluid candidate : available) {
            if (remaining <= 0) break;
            if (candidate.remaining <= 0 || requirement.getFluid() != candidate.stack.getFluid()) {
                continue;
            }

            long amount = Math.min(remaining, candidate.remaining);
            candidate.remaining -= amount;
            remaining -= amount;
        }
        return remaining <= 0;
    }

    private static List<AvailableFluid> createAvailableFluids(List<IAEFluidStack> snapshot) {
        List<AvailableFluid> available = new ArrayList<>(snapshot.size());
        for (IAEFluidStack stack : snapshot) {
            if (stack != null && stack.getStackSize() > 0) available.add(new AvailableFluid(stack));
        }
        return available;
    }

    private static final class AvailableFluid {

        private final IAEFluidStack stack;
        private long remaining;

        private AvailableFluid(IAEFluidStack stack) {
            this.stack = stack;
            this.remaining = stack.getStackSize();
        }
    }

}
