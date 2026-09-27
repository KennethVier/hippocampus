package com.hippocampus.learning.domain.policy;

import com.hippocampus.learning.domain.LearningActionType;
import com.hippocampus.learning.domain.LearningState;
import com.hippocampus.learning.domain.RecentLearningActivity;
import java.util.ArrayList;
import java.util.List;

final class PolicyHistory {

    private PolicyHistory() {
    }

    static List<RecentLearningActivity> recentForConcept(LearningState state) {
        ArrayList<RecentLearningActivity> recent = new ArrayList<>();
        List<RecentLearningActivity> history = state.recentActivityHistory();
        for (int index = history.size() - 1; index >= 0; index--) {
            RecentLearningActivity activity = history.get(index);
            if (activity.conceptKey().equals(state.conceptKey())) {
                recent.add(activity);
            }
        }
        return List.copyOf(recent);
    }

    static boolean represents(RecentLearningActivity activity, LearningActionType actionType) {
        String normalized = activity.activityType().toUpperCase();
        return normalized.equals(actionType.name()) || normalized.contains(actionType.name());
    }
}
