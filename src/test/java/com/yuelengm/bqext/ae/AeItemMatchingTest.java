package com.yuelengm.bqext.ae;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import org.junit.Test;

import appeng.api.storage.data.IAEItemStack;

public class AeItemMatchingTest {

    @Test
    public void ordinaryMetadataMatchingDoesNotMaterializeStacks() {
        Item item = new Item();
        IAEItemStack candidate = candidate(item, 7, null, new AtomicInteger());
        AeItemSnapshot snapshot = snapshot(candidate);
        ItemStack requirement = new ItemStack(item, 1, 7);
        NBTTagCompound tags = new NBTTagCompound();
        tags.setString("ignored", "requirement-only");
        requirement.setTagCompound(tags);

        assertTrue(AeRetrievalDetector.matchesIgnoringNbt(requirement, candidate, snapshot));
        assertFalse(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(item, 1, 8), candidate, snapshot));
        assertFalse(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(new Item(), 1, 7), candidate, snapshot));
    }

    @Test
    public void damageableAndWildcardRequirementsKeepBqRules() {
        Item damageable = new Item().setMaxDamage(100);
        IAEItemStack worn = candidate(damageable, 80, null, new AtomicInteger());
        assertTrue(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(damageable, 1, 0), worn, snapshot(worn)));

        Item item = new Item();
        IAEItemStack variant = candidate(item, 19, null, new AtomicInteger());
        assertTrue(
            AeRetrievalDetector
                .matchesIgnoringNbt(new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE), variant, snapshot(variant)));
        assertFalse(
            AeRetrievalDetector.matchesIgnoringNbt(
                new ItemStack(new Item(), 1, OreDictionary.WILDCARD_VALUE),
                variant,
                snapshot(variant)));
    }

    @Test
    public void inheritedDefaultDamageUsesRawMetadata() {
        Item item = new Item() {};
        IAEItemStack candidate = candidate(item, 5, null, new AtomicInteger());
        assertTrue(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(item, 1, 5), candidate, snapshot(candidate)));
    }

    @Test
    public void negativeRawMetadataMatchesConstructedStackBehavior() {
        Item item = new Item();
        IAEItemStack candidate = candidate(item, -1, null, new AtomicInteger());
        assertTrue(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(item, 1, 0), candidate, snapshot(candidate)));
    }

    @Test
    public void customNbtBasedMetadataFallsBackToCachedSample() {
        Item item = new Item() {

            @Override
            public int getDamage(ItemStack stack) {
                return stack.hasTagCompound() ? stack.getTagCompound()
                    .getInteger("meta") : 0;
            }
        };
        ItemStack requirement = withMetadata(item, 42);
        ItemStack sample = withMetadata(item, 42);
        AtomicInteger conversions = new AtomicInteger();
        IAEItemStack candidate = candidate(item, 7, sample, conversions);
        AeItemSnapshot snapshot = snapshot(candidate);

        assertTrue(AeRetrievalDetector.matchesIgnoringNbt(requirement, candidate, snapshot));
        assertFalse(AeRetrievalDetector.matchesIgnoringNbt(withMetadata(item, 43), candidate, snapshot));
        assertEquals(1, conversions.get());
    }

    private static ItemStack withMetadata(Item item, int meta) {
        ItemStack stack = new ItemStack(item, 1, 7);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("meta", meta);
        stack.setTagCompound(tag);
        return stack;
    }

    private static AeItemSnapshot snapshot(IAEItemStack candidate) {
        return new AeItemSnapshot(Collections.singletonList(candidate));
    }

    private static IAEItemStack candidate(Item item, int damage, ItemStack sample, AtomicInteger conversions) {
        return (IAEItemStack) Proxy.newProxyInstance(
            IAEItemStack.class.getClassLoader(),
            new Class<?>[] { IAEItemStack.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getItem":
                        return item;
                    case "getItemDamage":
                        return damage;
                    case "getItemStack":
                        if (sample == null) throw new AssertionError("Fast matching must not create an ItemStack");
                        conversions.incrementAndGet();
                        return sample.copy();
                    default:
                        throw new AssertionError("Unexpected AE method: " + method.getName());
                }
            });
    }
}
