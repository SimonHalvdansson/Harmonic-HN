package com.simon.harmonichackernews;

import android.content.Intent;
import android.content.res.Configuration;

import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;

import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentTransaction;

import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

public class MainActivity extends AppCompatActivity implements StoriesFragment.StoryClickListener {

    int lastPosition = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isFirstAppStart(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
        }

        ThemeUtils.setupTheme(this);

        if (Utils.shouldUseTransparentStatusBar(this)) {
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.statusBarColorTransparent));
        }

        setContentView(R.layout.activity_main);

        updateFragmentLayout();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateFragmentLayout();
    }

    @Override
    public void openStory(Story story, int pos, boolean showWebsite) {
        Bundle bundle = story.toBundle();

        bundle.putInt(CommentsFragment.EXTRA_FORWARD, pos - lastPosition);
        bundle.putBoolean(CommentsFragment.EXTRA_SHOW_WEBSITE, showWebsite);

        lastPosition = pos;

        if (Utils.isTablet(this)) {
            CommentsFragment fragment = new CommentsFragment();
            fragment.setArguments(bundle);
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.main_fragment_comments_container, fragment);
            transaction.commit();
        } else {
            Intent intent = new Intent(getApplicationContext(), CommentsActivity.class);
            intent.putExtras(bundle);
            startActivity(intent);

            overridePendingTransition(R.anim.activity_in_animation, 0);
        }
    }

    @Override
    public void onBackPressed() {
        final StoriesFragment fragment = (StoriesFragment) getSupportFragmentManager().findFragmentById(R.id.main_fragment_stories_container);

        if (fragment == null || !fragment.exitSearch()) {
            super.onBackPressed();
        }
    }

    private void updateFragmentLayout() {
        if (Utils.isTablet(getApplicationContext()) && findViewById(R.id.main_fragments_container) != null) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    getResources().getInteger(R.integer.stories_pane_weight));
            findViewById(R.id.main_fragment_stories_container).setLayoutParams(params);

            int extraPadding = getResources().getDimensionPixelSize(R.dimen.extra_pane_padding);
            if (Utils.shouldUseTransparentStatusBar(this)) {
                findViewById(R.id.main_fragments_container).setPadding(extraPadding, 0, 0, 0);
            } else {
                findViewById(R.id.main_fragments_container).setPadding(extraPadding, Utils.getStatusBarHeight(getResources()), 0, 0);
            }
        }
    }
}