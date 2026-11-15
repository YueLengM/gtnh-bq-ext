package com.yuelengm.bqext.ae;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;

/** Item candidates shared by all checks using the same storage snapshot. */
final class AeItemSnapshot {

    private final List<IAEItemStack> items;
    private final Map<Item, List<IAEItemStack>> byItem = new IdentityHashMap<>();
    private final Map<IAEItemStack, ItemStack> comparisonStacks = new IdentityHashMap<>();

    AeItemSnapshot(List<IAEItemStack> items) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        for (IAEItemStack stack : items) {
            byItem.computeIfAbsent(stack.getItem(), item -> new ArrayList<>())
                .add(stack);
        }
        byItem.replaceAll((item, candidates) -> Collections.unmodifiableList(candidates));
    }

    List<IAEItemStack> getCandidates(Item item, boolean matchOreDictionary) {
        // Ore matches may use a different Item. Keep their original traversal order.
        if (matchOreDictionary) return items;
        return byItem.getOrDefault(item, Collections.emptyList());
    }

    /** Read-only matching sample, discarded with this snapshot on the next tick. */
    ItemStack getComparisonStack(IAEItemStack stack) {
        return comparisonStacks.computeIfAbsent(stack, IAEItemStack::getItemStack);
    }
}
