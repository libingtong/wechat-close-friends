package io.github.inkveil.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

public final class VisibilityEngineTest {
    @Test
    public void defaultsToVisible() {
        assertFalse(new VisibilityEngine().shouldHide("wxid_alice", Surface.CONVERSATION));
    }

    @Test
    public void hidesOnlyListedCanonicalIdentifiers() {
        VisibilitySnapshot snapshot = VisibilitySnapshot.create(true, Surface.ALL_MASK,
                Arrays.asList(" wxid_alice ", "wxid_alice", "", null), List.of());
        assertTrue(snapshot.shouldHide("wxid_alice", Surface.CONVERSATION));
        assertFalse(snapshot.shouldHide("wxid_bob", Surface.CONVERSATION));
        assertFalse(snapshot.shouldHide("wxid_alice@chatroom", Surface.CONVERSATION));
        assertEquals(1, snapshot.hiddenCount());
    }

    @Test
    public void disabledAndUnknownInputsRemainVisible() {
        VisibilitySnapshot disabled = VisibilitySnapshot.create(false, Surface.ALL_MASK,
                List.of("wxid_alice"), List.of());
        VisibilitySnapshot enabled = VisibilitySnapshot.create(true, Surface.ALL_MASK,
                List.of("wxid_alice"), List.of());
        assertFalse(disabled.shouldHide("wxid_alice", Surface.CONVERSATION));
        assertFalse(enabled.shouldHide(null, Surface.CONVERSATION));
        assertFalse(enabled.shouldHide("", Surface.CONVERSATION));
        assertFalse(enabled.shouldHide("wxid_alice", null));
    }

    @Test
    public void surfacesAreIndependent() {
        VisibilitySnapshot snapshot = VisibilitySnapshot.create(true, Surface.SEARCH.mask,
                List.of("wxid_alice"), List.of());
        assertTrue(snapshot.shouldHide("wxid_alice", Surface.SEARCH));
        assertFalse(snapshot.shouldHide("wxid_alice", Surface.CONTACT));
        assertFalse(snapshot.shouldHide("wxid_alice", Surface.CONVERSATION));
        assertFalse(snapshot.shouldHide("wxid_alice", Surface.NOTIFICATION));
    }

    @Test
    public void temporaryVisibilityOverridesEverySurface() {
        VisibilitySnapshot snapshot = VisibilitySnapshot.create(true, Surface.ALL_MASK,
                List.of("wxid_alice"), List.of(" wxid_alice "));
        for (Surface surface : Surface.values()) {
            assertFalse(snapshot.shouldHide("wxid_alice", surface));
        }
    }

    @Test
    public void clearingTemporaryVisibilityRehides() {
        VisibilityEngine engine = new VisibilityEngine();
        engine.publish(VisibilitySnapshot.create(true, Surface.ALL_MASK,
                List.of("wxid_alice"), List.of("wxid_alice")));
        assertFalse(engine.shouldHide("wxid_alice", Surface.SEARCH));
        engine.publish(VisibilitySnapshot.create(true, Surface.ALL_MASK,
                List.of("wxid_alice"), List.of()));
        assertTrue(engine.shouldHide("wxid_alice", Surface.SEARCH));
    }

    @Test
    public void snapshotsDoNotRetainMutableInputs() {
        ArrayList<String> hidden = new ArrayList<>(List.of("wxid_alice"));
        ArrayList<String> temporary = new ArrayList<>();
        VisibilitySnapshot snapshot = VisibilitySnapshot.create(true, Surface.ALL_MASK, hidden, temporary);
        hidden.clear();
        temporary.add("wxid_alice");
        assertTrue(snapshot.shouldHide("wxid_alice", Surface.CONVERSATION));
    }

    @Test
    public void emptyAndNullCollectionsRemainVisible() {
        VisibilitySnapshot snapshot = VisibilitySnapshot.create(true, Surface.ALL_MASK, null, null);
        assertFalse(snapshot.shouldHide("wxid_alice", Surface.CONVERSATION));
        assertEquals(0, snapshot.hiddenCount());
    }

    @Test
    public void unknownSurfaceBitsDoNotEnableFiltering() {
        VisibilitySnapshot snapshot = VisibilitySnapshot.create(true, 16, List.of("wxid_alice"), List.of());
        assertFalse(snapshot.shouldHide("wxid_alice", Surface.SEARCH));
    }

    @Test
    public void concurrentPublicationNeverExposesPartialSnapshot() throws Exception {
        VisibilityEngine engine = new VisibilityEngine();
        VisibilitySnapshot hidden = VisibilitySnapshot.create(true, Surface.ALL_MASK,
                List.of("wxid_alice"), List.of());
        VisibilitySnapshot visible = VisibilitySnapshot.create(false, Surface.ALL_MASK,
                List.of("wxid_alice"), List.of());
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread writer = new Thread(() -> {
            try {
                start.await();
                for (int iteration = 0; iteration < 100_000; iteration++) {
                    engine.publish((iteration & 1) == 0 ? hidden : visible);
                }
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        writer.start();
        start.countDown();
        for (int iteration = 0; iteration < 100_000; iteration++) {
            VisibilitySnapshot snapshot = engine.snapshot();
            assertFalse(snapshot.shouldHide("wxid_bob", Surface.CONTACT));
            snapshot.shouldHide("wxid_alice", Surface.CONVERSATION);
        }
        writer.join();
        if (failure.get() != null) {
            throw new AssertionError(failure.get());
        }
        engine.publish(hidden);
        assertTrue(engine.shouldHide("wxid_alice", Surface.CONVERSATION));
    }
}
