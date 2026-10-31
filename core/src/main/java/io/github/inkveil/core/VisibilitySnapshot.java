package io.github.inkveil.core;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class VisibilitySnapshot {
    private static final VisibilitySnapshot DISABLED = new VisibilitySnapshot(false, 0,
            Collections.emptySet(), Collections.emptySet());
    private final boolean enabled;
    private final int surfaceMask;
    private final Set<String> hiddenIds;
    private final Set<String> temporarilyVisibleIds;

    private VisibilitySnapshot(boolean enabled, int surfaceMask, Set<String> hiddenIds,
            Set<String> temporarilyVisibleIds) {
        this.enabled = enabled;
        this.surfaceMask = surfaceMask & Surface.ALL_MASK;
        this.hiddenIds = hiddenIds;
        this.temporarilyVisibleIds = temporarilyVisibleIds;
    }

    public static VisibilitySnapshot disabled() {
        return DISABLED;
    }

    public static VisibilitySnapshot create(boolean enabled, int surfaceMask,
            Collection<String> hiddenIds, Collection<String> temporarilyVisibleIds) {
        return new VisibilitySnapshot(enabled, surfaceMask, normalizedCopy(hiddenIds),
                normalizedCopy(temporarilyVisibleIds));
    }

    private static Set<String> normalizedCopy(Collection<String> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) {
            return Collections.emptySet();
        }
        HashSet<String> normalized = new HashSet<>();
        for (String identifier : identifiers) {
            if (identifier != null) {
                String canonical = identifier.trim();
                if (!canonical.isEmpty()) {
                    normalized.add(canonical);
                }
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    public boolean shouldHide(String canonicalId, Surface surface) {
        return enabled && surface != null && (surfaceMask & surface.mask) != 0
                && canonicalId != null && hiddenIds.contains(canonicalId)
                && !temporarilyVisibleIds.contains(canonicalId);
    }

    public int hiddenCount() {
        return hiddenIds.size();
    }
}
