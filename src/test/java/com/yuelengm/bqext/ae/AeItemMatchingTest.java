package com.yuelengm.bqext.ae;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Proxy;

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
        IAEItemStack candidate = candidate(item, 7);
        ItemStack requirement = new ItemStack(item, 1, 7);
        NBTTagCompound tags = new NBTTagCompound();
        tags.setString("ignored", "requirement-only");
        requirement.setTagCompound(tags);

        assertTrue(AeRetrievalDetector.matchesIgnoringNbt(requirement, candidate));
        assertFalse(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(item, 1, 8), candidate));
        assertFalse(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(new Item(), 1, 7), candidate));
    }

    @Test
    public void damageableAndWildcardRequirementsKeepBqRules() {
        Item damageable = new Item().setMaxDamage(100);
        IAEItemStack worn = candidate(damageable, 80);
        assertTrue(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(damageable, 1, 0), worn));

        Item item = new Item();
        IAEItemStack variant = candidate(item, 19);
        assertTrue(
            AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(item, 1, OreDictionary.WILDCARD_VALUE), variant));
        assertFalse(
            AeRetrievalDetector
                .matchesIgnoringNbt(new ItemStack(new Item(), 1, OreDictionary.WILDCARD_VALUE), variant));
    }

    @Test
    public void inheritedDefaultDamageUsesRawMetadata() {
        Item item = new Item() {};
        IAEItemStack candidate = candidate(item, 5);
        assertTrue(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(item, 1, 5), candidate));
    }

    @Test
    public void negativeRawMetadataMatchesConstructedStackBehavior() {
        Item item = new Item();
        IAEItemStack candidate = candidate(item, -1);
        assertTrue(AeRetrievalDetector.matchesIgnoringNbt(new ItemStack(item, 1, 0), candidate));
    }

    private static IAEItemStack candidate(Item item, int damage) {
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
                        throw new AssertionError("Fast matching must not create an ItemStack");
                    default:
                        throw new AssertionError("Unexpected AE method: " + method.getName());
                }
            });
    }
}
