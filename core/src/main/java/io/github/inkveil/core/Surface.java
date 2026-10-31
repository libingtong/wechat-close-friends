package io.github.inkveil.core;

public enum Surface {
    CONVERSATION(1),
    CONTACT(2),
    SEARCH(4),
    NOTIFICATION(8);

    public static final int ALL_MASK = 15;
    public final int mask;

    Surface(int mask) {
        this.mask = mask;
    }
}
