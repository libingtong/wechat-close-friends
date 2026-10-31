package io.github.inkveil.core;

public final class TitleGesturePolicy {
    private final int requiredTaps;
    private final long windowMillis;
    private int taps;
    private long lastTap;
    private boolean longPressHandled;

    public TitleGesturePolicy(int requiredTaps, long windowMillis) {
        if (requiredTaps < 1 || windowMillis < 1) {
            throw new IllegalArgumentException("次数和时间窗口必须为正数");
        }
        this.requiredTaps = requiredTaps;
        this.windowMillis = windowMillis;
    }

    public void onGestureStart() {
        longPressHandled = false;
    }

    public boolean onTap(long uptimeMillis) {
        if (longPressHandled) {
            return false;
        }
        if (taps == 0 || uptimeMillis < lastTap || uptimeMillis - lastTap >= windowMillis) {
            taps = 1;
        } else {
            taps++;
        }
        lastTap = uptimeMillis;
        if (taps >= requiredTaps) {
            taps = 0;
            return true;
        }
        return false;
    }

    public boolean onLongPress() {
        if (longPressHandled) {
            return false;
        }
        taps = 0;
        longPressHandled = true;
        return true;
    }
}
