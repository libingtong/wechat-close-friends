package io.github.inkveil.core;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class CareModeEntryPolicyTest {
    @Test
    public void selectsOnlyUniqueEnableButton() {
        assertEquals(1, CareModeEntryPolicy.uniqueCandidateIndex(CareModeEntryPolicy.TARGET_ACTIVITY,
                List.of(new CareModeEntryPolicy.Candidate(false, 10, "开启"),
                        new CareModeEntryPolicy.Candidate(true, 11, "开启"),
                        new CareModeEntryPolicy.Candidate(true, 12, "返回"))));
    }

    @Test
    public void supportsTraditionalLabel() {
        assertEquals(0, CareModeEntryPolicy.uniqueCandidateIndex(CareModeEntryPolicy.TARGET_ACTIVITY,
                List.of(new CareModeEntryPolicy.Candidate(true, 11, "開啟"))));
    }

    @Test
    public void ambiguousButtonsFailOpen() {
        assertEquals(-1, CareModeEntryPolicy.uniqueCandidateIndex(CareModeEntryPolicy.TARGET_ACTIVITY,
                List.of(new CareModeEntryPolicy.Candidate(true, 11, "开启"),
                        new CareModeEntryPolicy.Candidate(true, 12, "開啟"))));
    }

    @Test
    public void rejectsNonButtonsAndInvalidIdentifiers() {
        assertEquals(-1, CareModeEntryPolicy.uniqueCandidateIndex(CareModeEntryPolicy.TARGET_ACTIVITY,
                Arrays.asList(null, new CareModeEntryPolicy.Candidate(false, 11, "开启"),
                        new CareModeEntryPolicy.Candidate(true, 0, "开启"),
                        new CareModeEntryPolicy.Candidate(true, -1, "开启"),
                        new CareModeEntryPolicy.Candidate(true, 12, null))));
    }

    @Test
    public void otherActivitiesFailOpen() {
        assertEquals(-1, CareModeEntryPolicy.uniqueCandidateIndex("example.OtherActivity",
                List.of(new CareModeEntryPolicy.Candidate(true, 11, "开启"))));
    }

    @Test
    public void noCandidatesAndUnknownLabelsFailOpen() {
        assertEquals(-1, CareModeEntryPolicy.uniqueCandidateIndex(CareModeEntryPolicy.TARGET_ACTIVITY, null));
        assertEquals(-1, CareModeEntryPolicy.uniqueCandidateIndex(CareModeEntryPolicy.TARGET_ACTIVITY, List.of()));
        assertEquals(-1, CareModeEntryPolicy.uniqueCandidateIndex(CareModeEntryPolicy.TARGET_ACTIVITY,
                List.of(new CareModeEntryPolicy.Candidate(true, 11, "关闭"))));
    }
}
