package com.simon.harmonichackernews;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.simon.harmonichackernews.data.Story;

import java.util.ArrayList;

/** Keeps a user's loaded submissions and viewport during an activity recreation. */
public class SubmissionsViewModel extends ViewModel {

    @Nullable
    private State state;

    @Nullable
    State getState() {
        return state;
    }

    void setState(@Nullable State state) {
        this.state = state;
    }

    static final class State {
        final ArrayList<Story> allSubmissions = new ArrayList<>();
        int submissionFilter;
        int submissionsHitsPerPage;
        boolean submissionsCanLoadMore;
        boolean initialLoadFinished;
        boolean submissionsLoadedSuccessfully;
        int firstVisiblePosition = -1;
        int firstVisibleTop;
        boolean appBarCollapsed;
    }
}
