package io.github.inkveil.core;

import java.util.List;

public final class CareModeEntryPolicy {
    public static final String TARGET_ACTIVITY =
            "com.tencent.mm.plugin.setting.ui.setting.SettingsCareModeIntro";

    public static boolean isEnableButton(boolean isButton, int viewId, CharSequence text) {
        return isButton && viewId > 0 && text != null
                && ("开启".contentEquals(text) || "開啟".contentEquals(text));
    }

    public static int uniqueCandidateIndex(String activityClassName, List<Candidate> candidates) {
        if (!TARGET_ACTIVITY.equals(activityClassName) || candidates == null) {
            return -1;
        }
        int selected = -1;
        for (int index = 0; index < candidates.size(); index++) {
            Candidate candidate = candidates.get(index);
            if (candidate != null && isEnableButton(candidate.button, candidate.viewId, candidate.text)) {
                if (selected >= 0) {
                    return -1;
                }
                selected = index;
            }
        }
        return selected;
    }

    public static final class Candidate {
        private final boolean button;
        private final int viewId;
        private final CharSequence text;

        public Candidate(boolean button, int viewId, CharSequence text) {
            this.button = button;
            this.viewId = viewId;
            this.text = text;
        }
    }
}
