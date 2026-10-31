package io.github.inkveil.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SearchEntryPolicyTest {
    @Test
    public void matchesOnlyExactCommand() {
        assertTrue(SearchEntryPolicy.isSettingsCommand("#hide"));
        assertTrue(SearchEntryPolicy.isSettingsCommand(new StringBuilder("#hide")));
        for (String ordinary : new String[] {"", "#", "hide", "#Hide",
                "abc#hide", "#hideabc", "＃hide", "#list"}) {
            assertFalse(SearchEntryPolicy.isSettingsCommand(ordinary));
        }
        assertFalse(SearchEntryPolicy.isSettingsCommand(null));
    }

    @Test
    public void allowsSurroundingWhitespaceWithoutMatchingBlankInput() {
        assertTrue(SearchEntryPolicy.isSettingsCommand(" \t#hide\n"));
        assertTrue(SearchEntryPolicy.isSettingsCommand("\u00a0#hide\u3000"));
        assertFalse(SearchEntryPolicy.isSettingsCommand("        "));
        assertFalse(SearchEntryPolicy.isSettingsCommand(" #hi de "));
    }

    @Test
    public void matchingDoesNotCallToString() {
        CharSequence command = new CharSequence() {
            @Override
            public int length() {
                return 5;
            }

            @Override
            public char charAt(int index) {
                return "#hide".charAt(index);
            }

            @Override
            public CharSequence subSequence(int start, int end) {
                throw new AssertionError("不应创建子序列");
            }

            @Override
            public String toString() {
                throw new AssertionError("不应分配字符串");
            }
        };
        assertTrue(SearchEntryPolicy.isSettingsCommand(command));
    }

    @Test
    public void attachesOnlyToKnownViewWithUniqueInput() {
        assertTrue(SearchEntryPolicy.shouldAttach(SearchEntryPolicy.TARGET_VIEW, 1));
        assertFalse(SearchEntryPolicy.shouldAttach(SearchEntryPolicy.TARGET_VIEW, 0));
        assertFalse(SearchEntryPolicy.shouldAttach(SearchEntryPolicy.TARGET_VIEW, 2));
        assertFalse(SearchEntryPolicy.shouldAttach("android.widget.EditText", 1));
        assertFalse(SearchEntryPolicy.shouldAttach(null, 1));
    }
}
