package io.github.inkveil.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TitleGesturePolicyTest {
    @Test
    public void thirdTapWithinWindowToggles() {
        TitleGesturePolicy policy = new TitleGesturePolicy(3, 800);
        assertFalse(policy.onTap(1_000));
        assertFalse(policy.onTap(1_300));
        assertTrue(policy.onTap(1_800));
        assertFalse(policy.onTap(1_900));
    }

    @Test
    public void timeoutRestartsSequence() {
        TitleGesturePolicy policy = new TitleGesturePolicy(3, 800);
        assertFalse(policy.onTap(1_000));
        assertFalse(policy.onTap(1_801));
        assertFalse(policy.onTap(2_000));
        assertTrue(policy.onTap(2_200));
    }

    @Test
    public void adjacentIntervalsMaySpanMoreThanWholeWindow() {
        TitleGesturePolicy policy = new TitleGesturePolicy(3, 800);
        assertFalse(policy.onTap(1_000));
        assertFalse(policy.onTap(1_700));
        assertTrue(policy.onTap(2_400));
    }

    @Test
    public void exactWindowBoundaryResetsCount() {
        TitleGesturePolicy policy = new TitleGesturePolicy(3, 800);
        assertFalse(policy.onTap(1_000));
        assertFalse(policy.onTap(1_800));
        assertFalse(policy.onTap(1_900));
        assertTrue(policy.onTap(2_000));
    }

    @Test
    public void customTapCountAndClockResetAreHandled() {
        TitleGesturePolicy policy = new TitleGesturePolicy(2, 500);
        assertFalse(policy.onTap(1_000));
        assertFalse(policy.onTap(100));
        assertTrue(policy.onTap(200));
    }

    @Test
    public void longPressTogglesOnceAndSuppressesSameGestureTap() {
        TitleGesturePolicy policy = new TitleGesturePolicy(3, 800);
        assertFalse(policy.onTap(100));
        assertFalse(policy.onTap(200));
        assertTrue(policy.onLongPress());
        assertFalse(policy.onLongPress());
        assertFalse(policy.onTap(300));
        policy.onGestureStart();
        assertFalse(policy.onTap(400));
        assertFalse(policy.onTap(500));
        assertTrue(policy.onTap(600));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidTapCountIsRejected() {
        new TitleGesturePolicy(0, 800);
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidWindowIsRejected() {
        new TitleGesturePolicy(3, 0);
    }
}
