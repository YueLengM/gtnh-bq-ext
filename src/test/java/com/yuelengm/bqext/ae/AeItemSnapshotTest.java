package com.yuelengm.bqext.ae;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.item.Item;

import org.junit.Test;

import appeng.api.storage.data.IAEItemStack;

public class AeItemSnapshotTest {

    @Test
    public void ordinaryRequirementSkipsThousandsOfUnrelatedEntries() {
        Item required = new Item();
        Item unrelated = new Item();
        AtomicInteger conversions = new AtomicInteger();
        List<IAEItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 2048; i++) {
            stacks.add(stack(unrelated, conversions));
        }
        IAEItemStack target = stack(required, conversions);
        stacks.add(target);
        AeItemSnapshot snapshot = new AeItemSnapshot(stacks);

        List<IAEItemStack> candidates = snapshot.getCandidates(required, false);
        assertEquals(1, candidates.size());
        assertSame(target, candidates.get(0));
        assertEquals(0, conversions.get());
    }

    @Test
    public void keepsAllVariantsOfAnItemInOriginalOrder() {
        Item required = new Item();
        IAEItemStack firstVariant = stack(required);
        IAEItemStack secondVariant = stack(required);
        IAEItemStack unrelated = stack(new Item());
        AeItemSnapshot snapshot = new AeItemSnapshot(Arrays.asList(firstVariant, unrelated, secondVariant));

        List<IAEItemStack> candidates = snapshot.getCandidates(required, false);
        assertEquals(2, candidates.size());
        assertSame(firstVariant, candidates.get(0));
        assertSame(secondVariant, candidates.get(1));
    }

    @Test
    public void oreFallbackKeepsOtherItemsAndOriginalOrder() {
        Item baseItem = new Item();
        IAEItemStack alternative = stack(new Item());
        IAEItemStack base = stack(baseItem);
        IAEItemStack anotherAlternative = stack(new Item());
        AeItemSnapshot snapshot = new AeItemSnapshot(Arrays.asList(alternative, base, anotherAlternative));

        List<IAEItemStack> candidates = snapshot.getCandidates(baseItem, true);
        assertEquals(3, candidates.size());
        assertSame(alternative, candidates.get(0));
        assertSame(base, candidates.get(1));
        assertSame(anotherAlternative, candidates.get(2));
        assertEquals(
            3,
            snapshot.getCandidates(new Item(), true)
                .size());
    }

    @Test
    public void missingItemsAndEmptySnapshotsHaveNoCandidates() {
        Item missing = new Item();
        AeItemSnapshot snapshot = new AeItemSnapshot(Collections.singletonList(stack(new Item())));
        assertTrue(
            snapshot.getCandidates(missing, false)
                .isEmpty());
        AeItemSnapshot empty = new AeItemSnapshot(Collections.emptyList());
        assertTrue(
            empty.getCandidates(missing, false)
                .isEmpty());
        assertTrue(
            empty.getCandidates(missing, true)
                .isEmpty());
    }

    @Test
    public void sourceListChangesDoNotChangeCapturedCandidates() {
        Item required = new Item();
        IAEItemStack original = stack(required);
        List<IAEItemStack> source = new ArrayList<>(Collections.singletonList(original));
        AeItemSnapshot snapshot = new AeItemSnapshot(source);
        source.clear();
        source.add(stack(new Item()));

        assertSame(
            original,
            snapshot.getCandidates(required, false)
                .get(0));
        assertSame(
            original,
            snapshot.getCandidates(required, true)
                .get(0));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void checksCannotRemoveSharedCandidates() {
        Item required = new Item();
        AeItemSnapshot snapshot = new AeItemSnapshot(Collections.singletonList(stack(required)));
        snapshot.getCandidates(required, false)
            .clear();
    }

    private static IAEItemStack stack(Item item) {
        return stack(item, new AtomicInteger());
    }

    private static IAEItemStack stack(Item item, AtomicInteger conversions) {
        // Indexing must only inspect the Item identity, never materialize an ItemStack.
        return (IAEItemStack) Proxy.newProxyInstance(
            IAEItemStack.class.getClassLoader(),
            new Class<?>[] { IAEItemStack.class },
            (proxy, method, args) -> {
                if ("getItem".equals(method.getName())) return item;
                if ("getItemStack".equals(method.getName())) conversions.incrementAndGet();
                throw new AssertionError("Unexpected AE method during indexing: " + method.getName());
            });
    }
}
