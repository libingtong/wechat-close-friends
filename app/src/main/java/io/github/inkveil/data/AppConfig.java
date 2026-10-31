package io.github.inkveil.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import io.github.inkveil.core.Surface;

public final class AppConfig {
    public final boolean enabled;
    public final int surfaceMask;
    public final boolean rehideOnBackground;
    public final List<HiddenContact> contacts;

    public AppConfig(boolean enabled, int surfaceMask, boolean rehideOnBackground,
            List<HiddenContact> contacts) {
        this.enabled = enabled;
        this.surfaceMask = surfaceMask & Surface.ALL_MASK;
        this.rehideOnBackground = rehideOnBackground;
        this.contacts = Collections.unmodifiableList(new ArrayList<>(contacts));
    }

    public static AppConfig defaults() {
        return new AppConfig(false, Surface.ALL_MASK, true, Collections.emptyList());
    }

    public AppConfig withEnabled(boolean value) {
        return new AppConfig(value, surfaceMask, rehideOnBackground, contacts);
    }

    public AppConfig withSurface(Surface surface, boolean value) {
        return new AppConfig(enabled, value ? surfaceMask | surface.mask : surfaceMask & ~surface.mask,
                rehideOnBackground, contacts);
    }

    public AppConfig withRehide(boolean value) {
        return new AppConfig(enabled, surfaceMask, value, contacts);
    }

    public AppConfig withContacts(List<HiddenContact> value) {
        return new AppConfig(enabled, surfaceMask, rehideOnBackground, value);
    }
}
