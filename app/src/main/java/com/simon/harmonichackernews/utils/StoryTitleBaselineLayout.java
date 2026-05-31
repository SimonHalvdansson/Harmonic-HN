package com.simon.harmonichackernews.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

import com.simon.harmonichackernews.R;

public class StoryTitleBaselineLayout extends LinearLayout {

    public StoryTitleBaselineLayout(Context context) {
        super(context);
    }

    public StoryTitleBaselineLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StoryTitleBaselineLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public int getBaseline() {
        View title = findViewById(R.id.story_title);
        if (title == null || title.getVisibility() != VISIBLE) {
            return -1;
        }

        int titleBaseline = title.getBaseline();
        if (titleBaseline == -1) {
            return -1;
        }

        return getMeasuredChildTop(title) + titleBaseline;
    }

    private int getMeasuredChildTop(View target) {
        int childTop = getPaddingTop();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) child.getLayoutParams();
            if (child == target) {
                return childTop + params.topMargin;
            }
            if (child.getVisibility() != GONE) {
                childTop += params.topMargin + child.getMeasuredHeight() + params.bottomMargin;
            }
        }
        return target.getTop();
    }
}
