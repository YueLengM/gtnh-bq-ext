package com.yuelengm.bqext.ae;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.yuelengm.bqext.ae.AeCheckScheduler.CheckKind;

import appeng.api.storage.data.IAEItemStack;

public class AeCheckSchedulerErrorTest {

    private static final UUID QUEST = new UUID(0, 1);
    private static final UUID PLAYER = new UUID(0, 2);

    @Before
    public void setUp() {
        AeCheckScheduler.clear();
    }

    @After
    public void tearDown() {
        AeCheckScheduler.clear();
    }

    @Test
    public void missingClientTypeAbortsMatchingBeforeCompletionAndAllowsNextCheck() {
        Item item = new Item() {

            @Override
            public int getDamage(ItemStack stack) {
                throw new NoClassDefFoundError("net/minecraft/client/renderer/texture/IIconRegister");
            }
        };
        ItemStack requirement = new ItemStack(item, 1, 0);
        IAEItemStack candidate = (IAEItemStack) Proxy.newProxyInstance(
            IAEItemStack.class.getClassLoader(),
            new Class<?>[] { IAEItemStack.class },
            (proxy, method, args) -> {
                if ("getItem".equals(method.getName())) return item;
                throw new AssertionError("Unexpected call after failed matching: " + method.getName());
            });
        AtomicBoolean completed = new AtomicBoolean();
        assertFalse(check(() -> {
            AeRetrievalDetector.matchesIgnoringNbt(requirement, candidate);
            completed.set(true);
            return true;
        }));
        assertFalse(completed.get());
        assertTrue(check(() -> true));
    }

    @Test
    public void isolatesRuntimeAndLinkageFailuresForBothCheckKinds() {
        assertFalse(check(() -> { throw new IllegalStateException("broken item callback"); }));
        assertFalse(
            AeCheckScheduler.executeSafely(
                () -> { throw new NoSuchMethodError("incompatible fluid callback"); },
                QUEST,
                PLAYER,
                CheckKind.FLUID));
        assertFalse(check(() -> { throw new ExceptionInInitializerError("broken optional integration"); }));
        assertTrue(check(() -> true));
    }

    @Test
    public void preservesNormalResultsWithoutReportingFailure() throws Exception {
        assertTrue(check(() -> true));
        assertFalse(check(() -> false));
        assertEquals(0, state("nextFailureLogTick"));
        assertEquals(0, state("suppressedFailures"));
    }

    @Test
    public void doesNotSwallowFatalOrUnexpectedErrors() {
        OutOfMemoryError memory = new OutOfMemoryError("test only");
        assertSame(memory, assertThrows(OutOfMemoryError.class, () -> check(() -> { throw memory; })));
        StackOverflowError stack = new StackOverflowError("test only");
        assertSame(stack, assertThrows(StackOverflowError.class, () -> check(() -> { throw stack; })));
        assertThrows(AssertionError.class, () -> check(() -> { throw new AssertionError("test only"); }));
    }

    @Test
    public void rateLimitsReportsAndResetsOnServerClear() throws Exception {
        BooleanSupplier broken = () -> { throw new NoClassDefFoundError("test/client/Only"); };
        assertFalse(check(broken));
        assertEquals(1200, state("nextFailureLogTick"));
        assertFalse(check(broken));
        assertEquals(1, state("suppressedFailures"));
        for (int tick = 0; tick < 1199; tick++) AeCheckScheduler.tick();
        assertFalse(check(broken));
        assertEquals(2, state("suppressedFailures"));
        AeCheckScheduler.tick();
        assertFalse(check(broken));
        assertEquals(2400, state("nextFailureLogTick"));
        assertEquals(0, state("suppressedFailures"));
        AeCheckScheduler.clear();
        assertEquals(0, state("nextFailureLogTick"));
        assertFalse(check(broken));
        assertEquals(1200, state("nextFailureLogTick"));
    }

    private static boolean check(BooleanSupplier action) {
        return AeCheckScheduler.executeSafely(action, QUEST, PLAYER, CheckKind.ITEM);
    }

    private static long state(String name) throws Exception {
        Field field = AeCheckScheduler.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getLong(null);
    }
}
