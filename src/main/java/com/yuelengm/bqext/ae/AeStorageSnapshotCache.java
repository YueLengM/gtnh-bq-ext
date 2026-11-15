package com.yuelengm.bqext.ae;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.yuelengm.bqext.ae.AeTaskDetector.NetworkView;

import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;

final class AeStorageSnapshotCache {

    private static final int SNAPSHOT_LIFETIME_TICKS = 20;
    private static final Map<UUID, Snapshot<AeItemSnapshot>> itemSnapshots = new HashMap<>();
    private static final Map<UUID, Snapshot<List<IAEFluidStack>>> fluidSnapshots = new HashMap<>();
    private static long currentTick;

    private AeStorageSnapshotCache() {}

    static AeItemSnapshot getItemSnapshot(NetworkView network) {
        Snapshot<AeItemSnapshot> snapshot = itemSnapshots.get(network.networkId);
        if (snapshot == null || snapshot.storage != network.storage || snapshot.isExpired()) {
            snapshot = new Snapshot<>(
                network.storage,
                new AeItemSnapshot(captureItems(network.storage.getItemInventory())));
            itemSnapshots.put(network.networkId, snapshot);
        }
        return snapshot.value;
    }

    static List<IAEFluidStack> getFluidSnapshot(NetworkView network) {
        Snapshot<List<IAEFluidStack>> snapshot = fluidSnapshots.get(network.networkId);
        if (snapshot == null || snapshot.storage != network.storage || snapshot.isExpired()) {
            snapshot = new Snapshot<>(network.storage, captureFluids(network.storage.getFluidInventory()));
            fluidSnapshots.put(network.networkId, snapshot);
        }
        return snapshot.value;
    }

    static void tick() {
        currentTick++;
        // Expire from capture time, not last access, even when there are no pending checks.
        itemSnapshots.values()
            .removeIf(Snapshot::isExpired);
        fluidSnapshots.values()
            .removeIf(Snapshot::isExpired);
    }

    static void clear() {
        itemSnapshots.clear();
        fluidSnapshots.clear();
        currentTick = 0;
    }

    private static final class Snapshot<T> {

        private final IStorageGrid storage;
        private final T value;
        private final long capturedAt = currentTick;

        private Snapshot(IStorageGrid storage, T value) {
            this.storage = storage;
            this.value = value;
        }

        private boolean isExpired() {
            return currentTick - capturedAt >= SNAPSHOT_LIFETIME_TICKS;
        }
    }

    private static List<IAEItemStack> captureItems(IMEMonitor<IAEItemStack> inventory) {
        if (inventory == null) return Collections.emptyList();

        List<IAEItemStack> snapshot = new ArrayList<>();
        for (IAEItemStack stack : inventory.getStorageList()) {
            if (stack != null && stack.getStackSize() > 0) snapshot.add(stack.copy());
        }
        return snapshot;
    }

    private static List<IAEFluidStack> captureFluids(IMEMonitor<IAEFluidStack> inventory) {
        if (inventory == null) return Collections.emptyList();

        List<IAEFluidStack> snapshot = new ArrayList<>();
        for (IAEFluidStack stack : inventory.getStorageList()) {
            if (stack != null && stack.getStackSize() > 0) snapshot.add(stack.copy());
        }
        return snapshot;
    }
}
