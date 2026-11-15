package com.yuelengm.bqext.ae;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yuelengm.bqext.ae.AeTaskDetector.NetworkView;

import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IItemList;

public class AeStorageSnapshotCacheTest {

    @Before
    public void setUp() {
        AeStorageSnapshotCache.clear();
    }

    @After
    public void tearDown() {
        AeStorageSnapshotCache.clear();
    }

    @Test
    public void reusesAcrossTicksButReadsDoNotExtendLifetime() throws Exception {
        Inventory inventory = new Inventory();
        NetworkView network = network(UUID.randomUUID(), inventory.storage);
        Object items = AeStorageSnapshotCache.getItemSnapshot(network);
        Object fluids = AeStorageSnapshotCache.getFluidSnapshot(network);

        for (int tick = 1; tick < 20; tick++) {
            AeStorageSnapshotCache.tick();
            assertSame(items, AeStorageSnapshotCache.getItemSnapshot(network));
            assertSame(fluids, AeStorageSnapshotCache.getFluidSnapshot(network));
        }
        assertEquals(1, inventory.itemReads.get());
        assertEquals(1, inventory.fluidReads.get());

        AeStorageSnapshotCache.tick();
        assertNotSame(items, AeStorageSnapshotCache.getItemSnapshot(network));
        assertNotSame(fluids, AeStorageSnapshotCache.getFluidSnapshot(network));
        assertEquals(2, inventory.itemReads.get());
        assertEquals(2, inventory.fluidReads.get());
    }

    @Test
    public void expiresIdleEntriesWithoutReadingStorage() throws Exception {
        Inventory inventory = new Inventory();
        NetworkView network = network(UUID.randomUUID(), inventory.storage);
        AeStorageSnapshotCache.getItemSnapshot(network);
        AeStorageSnapshotCache.getFluidSnapshot(network);

        advance(20);
        assertEquals(0, entryCount("itemSnapshots"));
        assertEquals(0, entryCount("fluidSnapshots"));
        assertEquals(1, inventory.itemReads.get());
        assertEquals(1, inventory.fluidReads.get());
    }

    @Test
    public void itemAndFluidLifetimesStartWhenEachIsCaptured() throws Exception {
        Inventory inventory = new Inventory();
        NetworkView network = network(UUID.randomUUID(), inventory.storage);
        Object items = AeStorageSnapshotCache.getItemSnapshot(network);
        advance(10);
        Object fluids = AeStorageSnapshotCache.getFluidSnapshot(network);
        advance(10);

        assertNotSame(items, AeStorageSnapshotCache.getItemSnapshot(network));
        assertSame(fluids, AeStorageSnapshotCache.getFluidSnapshot(network));
        advance(10);
        assertNotSame(fluids, AeStorageSnapshotCache.getFluidSnapshot(network));
    }

    @Test
    public void isolatesNetworksAndReplacesChangedStorage() throws Exception {
        Inventory first = new Inventory();
        Inventory second = new Inventory();
        UUID id = UUID.randomUUID();
        NetworkView original = network(id, first.storage);
        Object items = AeStorageSnapshotCache.getItemSnapshot(original);
        Object fluids = AeStorageSnapshotCache.getFluidSnapshot(original);

        NetworkView otherNetwork = network(UUID.randomUUID(), first.storage);
        assertNotSame(items, AeStorageSnapshotCache.getItemSnapshot(otherNetwork));
        assertNotSame(fluids, AeStorageSnapshotCache.getFluidSnapshot(otherNetwork));
        assertSame(items, AeStorageSnapshotCache.getItemSnapshot(network(id, first.storage)));
        assertSame(fluids, AeStorageSnapshotCache.getFluidSnapshot(network(id, first.storage)));

        NetworkView replacement = network(id, second.storage);
        assertNotSame(items, AeStorageSnapshotCache.getItemSnapshot(replacement));
        assertNotSame(fluids, AeStorageSnapshotCache.getFluidSnapshot(replacement));
        assertEquals(1, second.itemReads.get());
        assertEquals(1, second.fluidReads.get());
    }

    @Test
    public void clearDiscardsSnapshotsAndRestartsLifetime() throws Exception {
        Inventory inventory = new Inventory();
        NetworkView network = network(UUID.randomUUID(), inventory.storage);
        Object previous = AeStorageSnapshotCache.getItemSnapshot(network);
        AeStorageSnapshotCache.getFluidSnapshot(network);
        advance(8);
        AeStorageSnapshotCache.clear();
        assertEquals(0, entryCount("itemSnapshots"));
        assertEquals(0, entryCount("fluidSnapshots"));

        Object current = AeStorageSnapshotCache.getItemSnapshot(network);
        assertNotSame(previous, current);
        advance(19);
        assertSame(current, AeStorageSnapshotCache.getItemSnapshot(network));
        advance(1);
        assertNotSame(current, AeStorageSnapshotCache.getItemSnapshot(network));
    }

    private static void advance(int ticks) {
        for (int i = 0; i < ticks; i++) AeStorageSnapshotCache.tick();
    }

    private static int entryCount(String name) throws Exception {
        Field field = AeStorageSnapshotCache.class.getDeclaredField(name);
        field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).size();
    }

    private static NetworkView network(UUID id, IStorageGrid storage) throws Exception {
        Constructor<NetworkView> constructor = NetworkView.class.getDeclaredConstructor(UUID.class, IStorageGrid.class);
        constructor.setAccessible(true);
        return constructor.newInstance(id, storage);
    }

    private static final class Inventory {

        private final AtomicInteger itemReads = new AtomicInteger();
        private final AtomicInteger fluidReads = new AtomicInteger();
        private final IMEMonitor<?> items = monitor(itemReads);
        private final IMEMonitor<?> fluids = monitor(fluidReads);
        private final IStorageGrid storage = (IStorageGrid) Proxy.newProxyInstance(
            IStorageGrid.class.getClassLoader(),
            new Class<?>[] { IStorageGrid.class },
            (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getItemInventory":
                        return items;
                    case "getFluidInventory":
                        return fluids;
                    default:
                        throw new AssertionError("Unexpected storage call: " + method.getName());
                }
            });

        private static IMEMonitor<?> monitor(AtomicInteger reads) {
            // Empty inventories exercise cache lifetime without a world or Forge initialization.
            IItemList<?> list = (IItemList<?>) Proxy.newProxyInstance(
                IItemList.class.getClassLoader(),
                new Class<?>[] { IItemList.class },
                (proxy, method, args) -> {
                    if ("iterator".equals(method.getName())) return Collections.emptyList()
                        .iterator();
                    throw new AssertionError("Unexpected list call: " + method.getName());
                });
            return (IMEMonitor<?>) Proxy.newProxyInstance(
                IMEMonitor.class.getClassLoader(),
                new Class<?>[] { IMEMonitor.class },
                (proxy, method, args) -> {
                    if ("getStorageList".equals(method.getName())) {
                        reads.incrementAndGet();
                        return list;
                    }
                    throw new AssertionError("Unexpected monitor call: " + method.getName());
                });
        }
    }
}
