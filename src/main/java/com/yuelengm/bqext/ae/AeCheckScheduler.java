package com.yuelengm.bqext.ae;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.tasks.ITask;
import betterquesting.api2.utils.ParticipantInfo;
import bq_standard.tasks.TaskFluid;
import bq_standard.tasks.TaskRetrieval;

public final class AeCheckScheduler {

    private static final int DEBOUNCE_TICKS = 20;
    private static final int COOLDOWN_TICKS = 20;
    private static final int MAX_CHECKS_PER_TICK = 16;
    private static final long MAX_CHECK_TIME_NANOS = 2_000_000L;

    // Keep deferred checks ahead of new requests, including requests made while running a check.
    private static final Map<CheckKey, PendingCheck> pending = new LinkedHashMap<>();
    private static final Map<CheckKey, Long> nextAllowed = new HashMap<>();
    private static long currentTick;

    private AeCheckScheduler() {}

    public static boolean request(ITask task, ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, CheckKind kind) {
        CheckKey key = new CheckKey(task, pInfo.UUID, quest.getKey(), kind);
        if (consumesResources(task) || task.isComplete(pInfo.UUID) || !AeTaskDetector.hasAnchors(pInfo)) {
            pending.remove(key);
            nextAllowed.remove(key);
            return false;
        }

        PendingCheck check = pending.get(key);
        if (check == null) {
            check = new PendingCheck(task, pInfo, quest, kind, currentTick);
            pending.put(key, check);
        } else {
            check.update(pInfo, quest, currentTick);
        }
        return true;
    }

    public static void tick() {
        currentTick++;
        AeStorageSnapshotCache.clear();
        if (pending.isEmpty()) return;

        long startedAt = System.nanoTime();
        List<CheckKey> dueKeys = new ArrayList<>(MAX_CHECKS_PER_TICK);
        for (Map.Entry<CheckKey, PendingCheck> entry : pending.entrySet()) {
            if (isDue(entry.getKey(), entry.getValue())) {
                dueKeys.add(entry.getKey());
                if (dueKeys.size() >= MAX_CHECKS_PER_TICK) break;
            }
        }

        int executed = 0;
        for (CheckKey key : dueKeys) {
            // Always allow one check to make progress. A running check cannot be interrupted safely.
            if (executed > 0 && System.nanoTime() - startedAt >= MAX_CHECK_TIME_NANOS) break;
            PendingCheck check = pending.get(key);
            // Earlier checks may have cancelled or refreshed a request in this batch.
            if (check == null || !isDue(key, check)) continue;

            pending.remove(key);
            if (consumesResources(check.task) || check.task.isComplete(check.pInfo.UUID)) {
                nextAllowed.remove(key);
                continue;
            }
            nextAllowed.put(key, currentTick + COOLDOWN_TICKS);
            run(check.task, check.pInfo, check.quest, check.kind);
            executed++;
            if (check.task.isComplete(check.pInfo.UUID)) {
                nextAllowed.remove(key);
            }
        }
    }

    private static boolean consumesResources(ITask task) {
        return (task instanceof TaskRetrieval && ((TaskRetrieval) task).consume)
            || (task instanceof TaskFluid && ((TaskFluid) task).consume);
    }

    private static boolean isDue(CheckKey key, PendingCheck check) {
        Long allowedAt = nextAllowed.get(key);
        return currentTick - check.lastRequestTick >= DEBOUNCE_TICKS && (allowedAt == null || currentTick >= allowedAt);
    }

    public static void clear() {
        pending.clear();
        nextAllowed.clear();
        AeStorageSnapshotCache.clear();
        currentTick = 0;
    }

    private static void run(ITask task, ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, CheckKind kind) {
        if (kind == CheckKind.ITEM && task instanceof TaskRetrieval) {
            AeTaskDetector.detectItems((TaskRetrieval) task, pInfo, quest);
        } else if (kind == CheckKind.FLUID && task instanceof TaskFluid) {
            AeTaskDetector.detectFluids((TaskFluid) task, pInfo, quest);
        }
    }

    public enum CheckKind {
        ITEM,
        FLUID
    }

    private static final class PendingCheck {

        private final ITask task;
        private ParticipantInfo pInfo;
        private Map.Entry<UUID, IQuest> quest;
        private final CheckKind kind;
        private long lastRequestTick;

        private PendingCheck(ITask task, ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, CheckKind kind,
            long lastRequestTick) {
            this.task = task;
            this.pInfo = pInfo;
            this.quest = quest;
            this.kind = kind;
            this.lastRequestTick = lastRequestTick;
        }

        private void update(ParticipantInfo pInfo, Map.Entry<UUID, IQuest> quest, long lastRequestTick) {
            this.pInfo = pInfo;
            this.quest = quest;
            this.lastRequestTick = lastRequestTick;
        }
    }

    private static final class CheckKey {

        private final ITask task;
        private final UUID playerId;
        private final UUID questId;
        private final CheckKind kind;

        private CheckKey(ITask task, UUID playerId, UUID questId, CheckKind kind) {
            this.task = task;
            this.playerId = playerId;
            this.questId = questId;
            this.kind = kind;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CheckKey)) return false;

            CheckKey other = (CheckKey) obj;
            return this.task == other.task && this.playerId.equals(other.playerId)
                && this.questId.equals(other.questId)
                && this.kind == other.kind;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(this.task);
            result = 31 * result + this.playerId.hashCode();
            result = 31 * result + this.questId.hashCode();
            result = 31 * result + this.kind.hashCode();
            return result;
        }
    }
}
