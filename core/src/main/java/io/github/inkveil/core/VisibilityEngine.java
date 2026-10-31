package io.github.inkveil.core;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class VisibilityEngine {
    private final AtomicReference<VisibilitySnapshot> current =
            new AtomicReference<>(VisibilitySnapshot.disabled());

    public void publish(VisibilitySnapshot snapshot) {
        current.set(Objects.requireNonNull(snapshot, "snapshot"));
    }

    public boolean shouldHide(String canonicalId, Surface surface) {
        return current.get().shouldHide(canonicalId, surface);
    }

    public VisibilitySnapshot snapshot() {
        return current.get();
    }
}
