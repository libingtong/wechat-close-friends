package io.github.inkveil.data;

public final class HiddenContact {
    public final String identifier;
    public final String label;

    public HiddenContact(String identifier, String label) {
        this.identifier = identifier.trim();
        this.label = label == null ? "" : label.trim();
    }
}
