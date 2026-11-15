package com.yuelengm.bqext.ae;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;

import appeng.api.storage.data.IAEItemStack;

/** Item candidates shared by all checks using the same storage snapshot. */
final class AeItemSnapshot {

    private final List<IAEItemStack> items;
    private final Map<Item, List<IAEItemStack>> byItem = new IdentityHashMap<>();

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
}
