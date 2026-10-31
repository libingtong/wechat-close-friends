package io.github.inkveil.core;

public final class SearchEntryPolicy {
    public static final String TARGET_VIEW = "com.tencent.mm.ui.tools.ActionBarSearchView";

    public static boolean isSettingsCommand(CharSequence text) {
        if (text == null || text.length() < 5) {
            return false;
        }
        int start = 0;
        int end = text.length();
        while (start < end && isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end - start == 5 && text.charAt(start) == '#'
                && text.charAt(start + 1) == 'h' && text.charAt(start + 2) == 'i'
                && text.charAt(start + 3) == 'd' && text.charAt(start + 4) == 'e';
    }

    private static boolean isWhitespace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character);
    }

    public static boolean shouldAttach(String viewClassName, int editTextCount) {
        return TARGET_VIEW.equals(viewClassName) && editTextCount == 1;
    }
}
