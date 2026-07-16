package com.simon.harmonichackernews.adapters;

import android.animation.ArgbEvaluator;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.LoadMoreButtonBinding;
import com.simon.harmonichackernews.databinding.StoryListItemBinding;
import com.simon.harmonichackernews.databinding.StoryListItemCardBinding;
import com.simon.harmonichackernews.databinding.StoryListItemCardLeftBinding;
import com.simon.harmonichackernews.databinding.StoryListItemLeftBinding;
import com.simon.harmonichackernews.databinding.SubmissionsCommentBinding;
import com.simon.harmonichackernews.databinding.SubmissionsCommentCardBinding;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.StoryPreviewImageLoader;
import com.simon.harmonichackernews.utils.AccessibilityTextUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.PreviewImageLayoutUtils;
import com.simon.harmonichackernews.utils.PreviewImageFailureAnimator;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryPreviewImageMemoryCache;
import com.simon.harmonichackernews.utils.TextSizeImageSpan;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import coil.Coil;
import coil.request.Disposable;
import coil.request.ImageRequest;
import coil.target.ImageViewTarget;
import coil.target.Target;
import coil.util.CoilUtils;

public class StoryRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Story> stories;
    private ClickListener linkClickListener;
    private ClickListener commentClickListener;
    private ClickListener commentRepliesClickListener;
    private ClickListener commentStoryClickListener;
    private View.OnClickListener loadMoreClickListener;
    private LongClickCoordinateListener longClickListener;
    private final boolean atSubmissions;
    private final String submitter;

    private static final int TYPE_STORY = 2;
    private static final int TYPE_COMMENT = 3;
    private static final int TYPE_LOAD_MORE_BUTTON = 4;
    private static final int TYPE_STORY_LEFT = 5;
    private static final int TYPE_STORY_CARD = 6;
    private static final int TYPE_STORY_CARD_LEFT = 7;
    private static final int TYPE_COMMENT_CARD = 8;
    private static final float CLICKED_PREVIEW_IMAGE_ALPHA = 0.6f;
    private static final long PREVIEW_IMAGE_FADE_IN_DURATION_MS = 160;
    private static final long COMMENT_HEADER_RESOLVE_FADE_DURATION_MS = 160;
    private static final long CLICKED_STATE_ANIMATION_DURATION_MS = 180;
    private static final long CARD_TINT_ANIMATION_DURATION_MS = 180;
    private static final int FAVICON_TINT_SIZE_DP = 64;
    private static final int LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP = 176;
    private static final String PAYLOAD_CLICKED_STATE = "clicked_state";
    private static final String PAYLOAD_STORY_INDEX = "story_index";
    private static final String PAYLOAD_LOAD_MORE_STATE = "load_more_state";
    private static final long LOAD_MORE_FADE_OUT_DURATION_MS = 90;
    private static final long LOAD_MORE_FADE_IN_DURATION_MS = 140;
    private static final PathInterpolator LOAD_MORE_TRANSITION_INTERPOLATOR =
            new PathInterpolator(0.2f, 0f, 0f, 1f);

    public boolean showPoints;
    public boolean compactPoints;
    public boolean includeTopLevelDomain;
    public boolean showCommentsCount;
    public boolean compactView;
    public boolean thumbnails;
    public String previewImageMode;
    public boolean borderlessLargePreviewImage;
    public float storyTextSize;
    public boolean showIndex;
    public boolean compactHeader;
    public boolean leftAlign;
    public boolean cardStyle;
    public boolean tintCardUsingPreview;
    public String paletteTintMode;
    public String faviconProvider;
    public int hotness;
    public int type;
    public String font;
    public float commentTextSize;
    public boolean allowCommentRows;
    public boolean disableClickedEffects;
    public boolean grayOutClicked;

    public boolean paginationMode = false;
    public boolean showLoadMoreButton = false;
    private boolean loadMoreLoading = false;
    public static final int PAGINATION_PAGE_SIZE = 30;
    public int visibleStoryCount = 30;
    private final Map<Story, StoryPreviewImageLoader.PreviewImageRequest> previewImageUrlRequests = new IdentityHashMap<>();
    @Nullable
    private RecyclerView recyclerView;

    public StoryRecyclerViewAdapter(List<Story> items,
                                    boolean shouldShowPoints,
                                    boolean shouldUseCompactPoints,
                                    boolean shouldIncludeTopLevelDomain,
                                    boolean shouldShowCommentsCount,
                                    boolean shouldUseCompactView,
                                    boolean shouldShowThumbnails,
                                    String preferredPreviewImageMode,
                                    boolean shouldUseBorderlessLargePreviewImage,
                                    float preferredStoryTextSize,
                                    boolean shouldShowIndex,
                                    boolean shouldUseCompactHeader,
                                    boolean shouldLeftAlign,
                                    boolean shouldUseCardStyle,
                                    boolean shouldTintCardUsingPreview,
                                    String preferredPaletteTintMode,
                                    boolean shouldGrayOutClicked,
                                    int preferredHotness,
                                    String faviconProv,
                                    String prefFont,
                                    float preferredCommentTextSize,
                                    String submissionsUserName,
                                    int wantedType) {
        stories = items;
        showPoints = shouldShowPoints;
        compactPoints = shouldUseCompactPoints;
        includeTopLevelDomain = shouldIncludeTopLevelDomain;
        showCommentsCount = shouldShowCommentsCount;
        compactView = shouldUseCompactView;
        thumbnails = shouldShowThumbnails;
        previewImageMode = preferredPreviewImageMode;
        borderlessLargePreviewImage = shouldUseBorderlessLargePreviewImage;
        storyTextSize = SettingsUtils.clampStoryTextSize(preferredStoryTextSize);
        showIndex = shouldShowIndex;
        compactHeader = shouldUseCompactHeader;
        leftAlign = shouldLeftAlign;
        cardStyle = shouldUseCardStyle;
        tintCardUsingPreview = shouldTintCardUsingPreview;
        paletteTintMode = SettingsUtils.getPaletteTintConfigKey(preferredPaletteTintMode);
        grayOutClicked = shouldGrayOutClicked;
        hotness = preferredHotness;
        faviconProvider = faviconProv;
        font = prefFont;
        commentTextSize = SettingsUtils.clampCommentTextSize(preferredCommentTextSize);
        type = wantedType;

        atSubmissions = !TextUtils.isEmpty(submissionsUserName);
        submitter = submissionsUserName;
        setHasStableIds(true);
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (isStoryViewType(viewType)) {
            if (viewType == TYPE_STORY_CARD) {
                return new StoryViewHolder(StoryListItemCardBinding.inflate(inflater, parent, false));
            }
            if (viewType == TYPE_STORY_CARD_LEFT) {
                return new StoryViewHolder(StoryListItemCardLeftBinding.inflate(inflater, parent, false));
            }
            if (viewType == TYPE_STORY_LEFT) {
                return new StoryViewHolder(StoryListItemLeftBinding.inflate(inflater, parent, false));
            }
            return new StoryViewHolder(StoryListItemBinding.inflate(inflater, parent, false));
        } else if (viewType == TYPE_LOAD_MORE_BUTTON) {
            return new LoadMoreViewHolder(LoadMoreButtonBinding.inflate(inflater, parent, false));
        } else {
            if (viewType == TYPE_COMMENT_CARD) {
                return new CommentViewHolder(SubmissionsCommentCardBinding.inflate(inflater, parent, false));
            }
            return new CommentViewHolder(SubmissionsCommentBinding.inflate(inflater, parent, false));
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NotNull final RecyclerView.ViewHolder holder, int position, @NotNull List<Object> payloads) {
        if (holder instanceof LoadMoreViewHolder && payloads.contains(PAYLOAD_LOAD_MORE_STATE)) {
            bindLoadMoreHolder((LoadMoreViewHolder) holder, true);
            return;
        }

        if (holder instanceof StoryViewHolder && !payloads.isEmpty()) {
            StoryViewHolder storyViewHolder = (StoryViewHolder) holder;
            Story story = stories.get(position);
            storyViewHolder.story = story;
            boolean handled = false;

            if (payloads.contains(PAYLOAD_STORY_INDEX)) {
                bindStoryIndex(storyViewHolder, position, story);
                handled = true;
            }

            if (payloads.contains(PAYLOAD_CLICKED_STATE)) {
                applyStoryClickedState(storyViewHolder, story, true);
                handled = true;
            }

            if (handled) {
                return;
            }
        }

        onBindViewHolder(holder, position);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NotNull final RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadMoreViewHolder) {
            LoadMoreViewHolder loadMoreHolder = (LoadMoreViewHolder) holder;

            bindLoadMoreHolder(loadMoreHolder, false);
            loadMoreHolder.loadMoreButton.setOnClickListener(v -> {
                if (!loadMoreLoading && loadMoreClickListener != null) {
                    loadMoreClickListener.onClick(v);
                }
            });
            return;
        }

        if (holder instanceof StoryViewHolder) {
            final StoryViewHolder storyViewHolder = (StoryViewHolder) holder;
            final Context ctx = storyViewHolder.itemView.getContext();

            storyViewHolder.story = stories.get(position);
            boolean useClickedEffects = shouldUseClickedEffects(storyViewHolder.story);
            resetPreviewImages(storyViewHolder);
            configureLargePreviewImageAppearance(storyViewHolder);
            configureStoryCardAppearance(storyViewHolder);
            hydrateCachedPreviewState(ctx, storyViewHolder.story);
            applyStoryCardBackground(storyViewHolder, storyViewHolder.story, false);
            setPreviewImageAlpha(storyViewHolder, useClickedEffects);

            bindStoryIndex(storyViewHolder, position, storyViewHolder.story);

            if (storyViewHolder.story.loaded || storyViewHolder.story.loadingFailed) {
                setStoryTitleText(storyViewHolder, storyViewHolder.story, useClickedEffects);

                final String commentCountText;
                if (showCommentsCount) {
                    commentCountText = Integer.toString(storyViewHolder.story.descendants);
                } else if (storyViewHolder.story.descendants > 0) {
                    commentCountText = "•";
                } else {
                    commentCountText = "";
                }
                storyViewHolder.commentsView.setText(commentCountText);
                storyViewHolder.commentsView.setContentDescription(AccessibilityTextUtils.commentCountDescription(storyViewHolder.story.descendants));
                if (!showCommentsCount) {
                    storyViewHolder.commentsView.setText(null);
                    storyViewHolder.commentsView.setContentDescription(null);
                }
                storyViewHolder.commentLayoutView.setContentDescription(AccessibilityTextUtils.commentCountDescription(storyViewHolder.story.descendants));

                String host = "";
                try {
                    if (storyViewHolder.story.url != null) {
                        host = Utils.getDomainName(storyViewHolder.story.url);
                        host = Utils.formatDomainNameForDisplay(host, includeTopLevelDomain);
                    }
                } catch (Exception e) {
                    host = "Unknown";
                }

                if (showPoints && !storyViewHolder.story.isComment) {
                    String pointsText = compactPoints
                            ? "+" + storyViewHolder.story.score
                            : AccessibilityTextUtils.pointCountDescription(storyViewHolder.story.score);
                    storyViewHolder.metaView.setText(pointsText + " • " + host + " • " + storyViewHolder.story.getTimeFormatted());
                    storyViewHolder.metaView.setContentDescription(
                            AccessibilityTextUtils.pointCountDescription(storyViewHolder.story.score) + ", "
                                    + host + ", "
                                    + storyViewHolder.story.getTimeFormatted());
                } else {
                    storyViewHolder.metaView.setText(host + " • " + storyViewHolder.story.getTimeFormatted());
                    storyViewHolder.metaView.setContentDescription(host + ", " + storyViewHolder.story.getTimeFormatted());
                }

                if (thumbnails) {
                    FaviconLoader.loadFavicon(storyViewHolder.story.url, storyViewHolder.metaFavicon, ctx, faviconProvider, true);
                }

                bindPreviewImage(storyViewHolder, storyViewHolder.story);
                bindStoryCardTintFallback(storyViewHolder, storyViewHolder.story);

                storyViewHolder.commentsIcon.setImageResource(hotness > 0 && storyViewHolder.story.score + storyViewHolder.story.descendants > hotness ? R.drawable.ic_whatshot : R.drawable.ic_comment);

                applyStoryTextSizes(storyViewHolder);

                storyViewHolder.titleShimmer.setVisibility(View.GONE);
                storyViewHolder.metaShimmer.setVisibility(View.GONE);
                storyViewHolder.titleView.setVisibility(View.VISIBLE);
                storyViewHolder.metaContainer.setVisibility(compactView ? View.GONE : View.VISIBLE);
                storyViewHolder.commentsView.setVisibility(compactView || !showCommentsCount ? View.GONE : View.VISIBLE);
                storyViewHolder.metaFavicon.setVisibility(thumbnails ? View.VISIBLE : View.GONE);

                if (storyViewHolder.story.loadingFailed) {
                    storyViewHolder.titleView.setText("Loading failed, click to retry");
                    storyViewHolder.metaContainer.setVisibility(View.GONE);
                    storyViewHolder.commentsView.setVisibility(View.GONE);
                    storyViewHolder.commentsView.setContentDescription(null);
                    storyViewHolder.commentLayoutView.setContentDescription(null);
                }

                storyViewHolder.linkLayoutView.setEnabled(true);
                storyViewHolder.linkLayoutView.setClickable(true);
                storyViewHolder.commentLayoutView.setEnabled(!storyViewHolder.story.loadingFailed);
                storyViewHolder.commentLayoutView.setClickable(!storyViewHolder.story.loadingFailed);
            } else {
                storyViewHolder.commentsIcon.setImageResource(R.drawable.ic_comment);
                storyViewHolder.titleShimmer.setVisibility(View.VISIBLE);
                storyViewHolder.metaShimmer.setVisibility(compactView ? View.GONE : View.VISIBLE);
                storyViewHolder.titleView.setVisibility(View.GONE);
                storyViewHolder.metaContainer.setVisibility(View.GONE);
                storyViewHolder.commentsView.setText(null);
                storyViewHolder.commentsView.setContentDescription(null);
                storyViewHolder.metaView.setContentDescription(null);
                storyViewHolder.commentLayoutView.setContentDescription(null);
                storyViewHolder.linkLayoutView.setEnabled(false);
                storyViewHolder.linkLayoutView.setClickable(false);
                storyViewHolder.commentLayoutView.setEnabled(false);
                storyViewHolder.commentLayoutView.setClickable(false);
                storyViewHolder.commentsIcon.setAlpha(useClickedEffects ? 0.6f : 1.0f);
            }

            storyViewHolder.commentLayoutView.setVisibility(storyViewHolder.story.isFrontpageLink ? View.GONE : View.VISIBLE);
            applyStoryClickedState(storyViewHolder, storyViewHolder.story);
        } else if (holder instanceof CommentViewHolder) {
            final CommentViewHolder commentViewHolder = (CommentViewHolder) holder;

            Story story = stories.get(position);

            bindCommentRowHeader(commentViewHolder, story);
            commentViewHolder.headerTime.setText(Utils.getTimeAgo(story.time));
            commentViewHolder.storyButton.setEnabled(story.commentMasterId > 0 || story.parentId > 0);
            bindCommentRowText(commentViewHolder, story);
            applyCommentRowTypefaces(commentViewHolder);

            commentViewHolder.bodyText.post(new Runnable() {
                @Override
                public void run() {
                    commentViewHolder.scrim.setVisibility(ViewUtils.isTextTruncated(commentViewHolder.bodyText) ? View.VISIBLE : View.GONE);
                }
            });

        }
    }

    private void bindLoadMoreHolder(@NotNull LoadMoreViewHolder holder, boolean animate) {
        if (!animate || holder.showingLoading == null || holder.showingLoading == loadMoreLoading) {
            holder.applyLoadingState(loadMoreLoading);
            return;
        }

        holder.animateLoadingState(loadMoreLoading);
    }

    @SuppressLint("SetTextI18n")
    private void bindStoryIndex(StoryViewHolder storyViewHolder, int position, Story story) {
        if (showIndex) {
            Context ctx = storyViewHolder.itemView.getContext();
            int displayIndex = position + 1;
            storyViewHolder.indexTextView.setText(displayIndex + ".");
            storyViewHolder.indexTextView.setContentDescription("Story " + displayIndex);

            if (shouldUseClickedEffects(story)) {
                storyViewHolder.indexTextView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.storyColorDisabled));
            } else {
                storyViewHolder.indexTextView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.storyColorNormal));
            }

            if (displayIndex < 100) {
                storyViewHolder.indexTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            } else {
                storyViewHolder.indexTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            }
            storyViewHolder.indexTextView.setPadding(0, 0, 0, 0);
        }

        storyViewHolder.indexTextView.setVisibility(showIndex ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onViewRecycled(@NotNull RecyclerView.ViewHolder holder) {
        if (holder instanceof StoryViewHolder) {
            StoryViewHolder storyViewHolder = (StoryViewHolder) holder;
            storyViewHolder.story = null;
            storyViewHolder.cancelClickedStateAnimator();
            resetPreviewImages(storyViewHolder);
            resetStoryCardBackground(storyViewHolder);
        } else if (holder instanceof CommentViewHolder) {
            ((CommentViewHolder) holder).resetHeaderState();
        }
        super.onViewRecycled(holder);
    }

    @Override
    public void onAttachedToRecyclerView(@NotNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        this.recyclerView = recyclerView;
    }

    @Override
    public void onDetachedFromRecyclerView(@NotNull RecyclerView recyclerView) {
        cancelPreviewImageUrlRequests();
        if (this.recyclerView == recyclerView) {
            this.recyclerView = null;
        }
        super.onDetachedFromRecyclerView(recyclerView);
    }

    public void updateStoryClickedState(int position) {
        if (position == RecyclerView.NO_POSITION
                || position < 0
                || position >= stories.size()
                || isLoadMorePosition(position)) {
            return;
        }

        if (applyVisibleStoryClickedState(position)) {
            return;
        }

        notifyItemChanged(position, PAYLOAD_CLICKED_STATE);
    }

    public void updateStoryIndicesFromPosition(int position) {
        if (!showIndex) {
            return;
        }

        int visibleItemCount = getVisibleItemCount();
        if (position < 0 || position >= visibleItemCount) {
            return;
        }

        updateVisibleStoryIndicesFromPosition(position, visibleItemCount);
        notifyItemRangeChanged(position, visibleItemCount - position, PAYLOAD_STORY_INDEX);
    }

    private void updateVisibleStoryIndicesFromPosition(int position, int visibleItemCount) {
        if (recyclerView == null) {
            return;
        }

        for (int i = position; i < visibleItemCount; i++) {
            RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(i);
            if (holder instanceof StoryViewHolder) {
                StoryViewHolder storyViewHolder = (StoryViewHolder) holder;
                Story story = stories.get(i);
                storyViewHolder.story = story;
                bindStoryIndex(storyViewHolder, i, story);
            }
        }
    }

    private boolean applyVisibleStoryClickedState(int position) {
        if (recyclerView == null) {
            return false;
        }

        RecyclerView.ViewHolder holder = recyclerView.findViewHolderForAdapterPosition(position);
        if (!(holder instanceof StoryViewHolder)) {
            return false;
        }

        StoryViewHolder storyViewHolder = (StoryViewHolder) holder;
        Story story = stories.get(position);
        storyViewHolder.story = story;
        applyStoryClickedState(storyViewHolder, story, true);
        return true;
    }

    private boolean shouldUseClickedEffects(Story story) {
        return story != null && story.clicked && grayOutClicked && !disableClickedEffects;
    }

    private void applyStoryClickedState(StoryViewHolder storyViewHolder, Story story) {
        applyStoryClickedState(storyViewHolder, story, false);
    }

    private void applyStoryClickedState(StoryViewHolder storyViewHolder, Story story, boolean animate) {
        if (story == null) {
            return;
        }

        Context ctx = storyViewHolder.itemView.getContext();
        boolean useClickedEffects = shouldUseClickedEffects(story);
        int storyColor = Utils.getColorViaAttr(
                ctx,
                useClickedEffects ? R.attr.storyColorDisabled : R.attr.storyColorNormal);
        int textColor = Utils.getColorViaAttr(
                ctx,
                useClickedEffects ? R.attr.textColorDisabled : R.attr.textColorDefault);
        float iconAlpha = useClickedEffects ? 0.6f : 1.0f;
        float previewImageAlpha = useClickedEffects ? CLICKED_PREVIEW_IMAGE_ALPHA : 1.0f;

        if (story.loaded && !story.loadingFailed) {
            setStoryTitleText(storyViewHolder, story, useClickedEffects);
        }

        if (animate && isVisibleOnScreen(storyViewHolder.itemView)) {
            animateStoryClickedState(storyViewHolder, storyColor, textColor, iconAlpha, previewImageAlpha);
            return;
        }

        storyViewHolder.cancelClickedStateAnimator();

        if (showIndex) {
            storyViewHolder.indexTextView.setTextColor(storyColor);
        }

        storyViewHolder.titleView.setTextColor(storyColor);
        storyViewHolder.commentsIcon.setAlpha(iconAlpha);
        storyViewHolder.metaFavicon.setAlpha(iconAlpha);
        storyViewHolder.commentsView.setTextColor(textColor);
        storyViewHolder.metaView.setTextColor(textColor);
        setPreviewImageAlpha(storyViewHolder, previewImageAlpha);
    }

    private void animateStoryClickedState(
            StoryViewHolder storyViewHolder,
            int targetStoryColor,
            int targetTextColor,
            float targetIconAlpha,
            float targetPreviewImageAlpha) {
        storyViewHolder.cancelClickedStateAnimator();
        cancelViewAnimation(storyViewHolder.smallPreviewImage);
        cancelViewAnimation(storyViewHolder.largePreviewImage);

        final int startTitleColor = storyViewHolder.titleView.getCurrentTextColor();
        final int startIndexColor = storyViewHolder.indexTextView.getCurrentTextColor();
        final int startCommentsColor = storyViewHolder.commentsView.getCurrentTextColor();
        final int startMetaColor = storyViewHolder.metaView.getCurrentTextColor();
        final float startCommentsIconAlpha = storyViewHolder.commentsIcon.getAlpha();
        final float startFaviconAlpha = storyViewHolder.metaFavicon.getAlpha();
        final float startSmallPreviewAlpha = getViewAlpha(storyViewHolder.smallPreviewImage);
        final float startLargePreviewAlpha = getViewAlpha(storyViewHolder.largePreviewImage);
        final ArgbEvaluator argbEvaluator = new ArgbEvaluator();

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        storyViewHolder.clickedStateAnimator = animator;
        animator.setDuration(CLICKED_STATE_ANIMATION_DURATION_MS);
        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            storyViewHolder.titleView.setTextColor((int) argbEvaluator.evaluate(fraction, startTitleColor, targetStoryColor));
            if (showIndex) {
                storyViewHolder.indexTextView.setTextColor((int) argbEvaluator.evaluate(fraction, startIndexColor, targetStoryColor));
            }
            storyViewHolder.commentsView.setTextColor((int) argbEvaluator.evaluate(fraction, startCommentsColor, targetTextColor));
            storyViewHolder.metaView.setTextColor((int) argbEvaluator.evaluate(fraction, startMetaColor, targetTextColor));
            storyViewHolder.commentsIcon.setAlpha(lerp(startCommentsIconAlpha, targetIconAlpha, fraction));
            storyViewHolder.metaFavicon.setAlpha(lerp(startFaviconAlpha, targetIconAlpha, fraction));
            setViewAlpha(storyViewHolder.smallPreviewImage, lerp(startSmallPreviewAlpha, targetPreviewImageAlpha, fraction));
            setViewAlpha(storyViewHolder.largePreviewImage, lerp(startLargePreviewAlpha, targetPreviewImageAlpha, fraction));
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (storyViewHolder.clickedStateAnimator == animation) {
                    storyViewHolder.clickedStateAnimator = null;
                }
            }
        });
        animator.start();
    }

    private static float lerp(float start, float end, float fraction) {
        return start + ((end - start) * fraction);
    }

    private static void cancelViewAnimation(@Nullable View view) {
        if (view != null) {
            view.animate().cancel();
        }
    }

    private static float getViewAlpha(@Nullable View view) {
        return view == null ? 1.0f : view.getAlpha();
    }

    private static void setViewAlpha(@Nullable View view, float alpha) {
        if (view != null) {
            view.setAlpha(alpha);
        }
    }

    private void setStoryTitleText(StoryViewHolder storyViewHolder, Story story, boolean useClickedEffects) {
        if (!TextUtils.isEmpty(story.pdfTitle)) {
            storyViewHolder.titleView.setText(TextSizeImageSpan.createWithTrailingBadge(
                    storyViewHolder.itemView.getContext(),
                    story.pdfTitle,
                    useClickedEffects ? R.drawable.ic_action_pdf_clicked : R.drawable.ic_action_pdf));
            return;
        }

        if (!TextUtils.isEmpty(story.videoTitle)) {
            storyViewHolder.titleView.setText(TextSizeImageSpan.createWithTrailingBadge(
                    storyViewHolder.itemView.getContext(),
                    story.videoTitle,
                    useClickedEffects ? R.drawable.ic_action_video_clicked : R.drawable.ic_action_video));
            return;
        }

        storyViewHolder.titleView.setText(story.title);
    }

    private void bindPreviewImage(final StoryViewHolder storyViewHolder, final Story story) {
        if (!shouldLoadPreviewImage(story)) {
            return;
        }

        if (!TextUtils.isEmpty(story.previewImageUrl)) {
            loadPreviewImage(storyViewHolder, story);
            refreshPreviewImageUrlIfNeeded(storyViewHolder.itemView.getContext(), story);
            return;
        }

        if (story.previewImageUrlLoaded) {
            refreshPreviewImageUrlIfNeeded(storyViewHolder.itemView.getContext(), story);
            return;
        }

        reservePendingPreviewImageSpace(storyViewHolder);
        loadPreviewImageUrl(storyViewHolder.itemView.getContext(), story);
    }

    public void prefetchPreviewImage(Context context, Story story) {
        hydrateCachedPreviewState(context, story);
        if (!shouldLoadPreviewImage(story)) {
            return;
        }

        if (!TextUtils.isEmpty(story.previewImageUrl)) {
            prefetchPreviewImageDrawable(
                    context == null ? null : context.getApplicationContext(),
                    story,
                    getDefaultStoryCardBackgroundColor(context));
            refreshPreviewImageUrlIfNeeded(context, story);
            return;
        }

        if (story.previewImageUrlLoaded && story.previewImageUrlNeedsRefresh) {
            refreshPreviewImageUrlIfNeeded(context, story);
            return;
        }

        loadPreviewImageUrl(context, story);
    }

    private boolean shouldLoadPreviewImage(Story story) {
        return !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)
                && story.loaded
                && !story.loadingFailed
                && !story.isComment
                && !TextUtils.isEmpty(story.url)
                && (!story.previewImageLoadFailed || story.previewImageUrlNeedsRefresh);
    }

    private void loadPreviewImageUrl(@Nullable Context context, Story story) {
        if (story.previewImageUrlLoaded || story.previewImageUrlLoading) {
            return;
        }

        story.previewImageUrlLoading = true;
        Context appContext = context == null ? null : context.getApplicationContext();
        int storyCardBackgroundColor = getDefaultStoryCardBackgroundColor(context);
        StoryPreviewImageLoader.PreviewImageRequest request = StoryPreviewImageLoader.loadPreviewImageUrl(appContext, story.id, story.url, imageUrl -> {
            previewImageUrlRequests.remove(story);
            story.previewImageUrlLoading = false;
            story.previewImageUrlLoaded = true;
            if (TextUtils.isEmpty(imageUrl)) {
                story.previewImageLoadFailed = true;
                PreviewImageTintUtils.clearStoryPreviewImageTintColor(story);
                cachePreviewState(appContext, story);
                showPreviewImageFailure(story);
                return;
            }

            setPreviewImageUrl(story, imageUrl);
            story.previewImageLoadFailed = false;
            cachePreviewState(appContext, story);
            prefetchPreviewImageDrawable(appContext, story, storyCardBackgroundColor);
            int index = stories.indexOf(story);
            if (index >= 0 && !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)) {
                notifyItemChanged(index);
            }
        });
        previewImageUrlRequests.put(story, request);
    }

    private void refreshPreviewImageUrlIfNeeded(@Nullable Context context, Story story) {
        if (context == null
                || story == null
                || !story.previewImageUrlNeedsRefresh
                || story.previewImageUrlLoading
                || TextUtils.isEmpty(story.url)) {
            return;
        }

        story.previewImageUrlNeedsRefresh = false;
        story.previewImageUrlLoading = true;
        Context appContext = context.getApplicationContext();
        int storyCardBackgroundColor = getDefaultStoryCardBackgroundColor(context);
        final String oldImageUrl = story.previewImageUrl;
        final boolean oldPreviewImageLoadFailed = story.previewImageLoadFailed;
        final boolean oldPreviewImageTintColorLoaded = story.previewImageTintColorLoaded;
        StoryPreviewImageLoader.PreviewImageRequest request = StoryPreviewImageLoader.loadPreviewImageUrl(
                appContext,
                story.id,
                story.url,
                true,
                imageUrl -> {
                    previewImageUrlRequests.remove(story);
                    story.previewImageUrlLoading = false;
                    story.previewImageUrlLoaded = true;

                    if (TextUtils.isEmpty(imageUrl)) {
                        if (TextUtils.isEmpty(oldImageUrl)) {
                            story.previewImageLoadFailed = true;
                            PreviewImageTintUtils.clearStoryPreviewImageTintColor(story);
                            cachePreviewState(appContext, story);
                            if (!oldPreviewImageLoadFailed || oldPreviewImageTintColorLoaded) {
                                notifyStoryChangedIfPreviewEnabled(story);
                            }
                        }
                        return;
                    }

                    if (!TextUtils.equals(oldImageUrl, imageUrl)) {
                        setPreviewImageUrl(story, imageUrl);
                        story.previewImageLoadFailed = false;
                        cachePreviewState(appContext, story);
                        prefetchPreviewImageDrawable(appContext, story, storyCardBackgroundColor);
                        notifyStoryChangedIfPreviewEnabled(story);
                        return;
                    }

                    story.previewImageLoadFailed = false;
                    cachePreviewState(appContext, story);
                });
        previewImageUrlRequests.put(story, request);
    }

    public void hydrateCachedPreviewState(@Nullable Context context, Story story) {
        if (context == null
                || story == null
                || SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)
                || !story.loaded
                || story.loadingFailed
                || story.isComment
                || TextUtils.isEmpty(story.url)) {
            return;
        }

        if (TextUtils.isEmpty(story.previewImageUrl) && !story.previewImageUrlLoaded) {
            String cachedPreviewImageUrl = StoryPreviewImageLoader.getCachedPreviewImageUrl(
                    context,
                    story.id,
                    story.url);
            boolean cachedPreviewImageUrlLoaded = StoryPreviewImageLoader.isCachedPreviewImageUrlLoaded(
                    context,
                    story.id,
                    story.url);
            if (cachedPreviewImageUrlLoaded) {
                setPreviewImageUrl(story, cachedPreviewImageUrl);
                story.previewImageUrlLoaded = true;
                story.previewImageUrlNeedsRefresh = true;
                story.previewImageLoadFailed = TextUtils.isEmpty(cachedPreviewImageUrl);
            }
        }

        hydrateCachedPreviewTintColor(context, story);
        hydrateCachedFaviconTintColor(context, story);
    }

    private void hydrateCachedPreviewTintColor(Context context, Story story) {
        if (!shouldTintStoryCards() || story == null || TextUtils.isEmpty(story.previewImageUrl)) {
            return;
        }

        int baseColor = getDefaultStoryCardBackgroundColor(context);
        if (PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(story, baseColor, paletteTintMode)) {
            return;
        }

        Integer tintColor = StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                context,
                story.id,
                story.previewImageUrl,
                baseColor,
                paletteTintMode);
        if (tintColor != null) {
            PreviewImageTintUtils.applyCachedStoryPreviewImageTintColor(
                    story,
                    story.previewImageUrl,
                    baseColor,
                    paletteTintMode,
                    tintColor);
        }
    }

    private void hydrateCachedFaviconTintColor(Context context, Story story) {
        if (!shouldUseFaviconTint(story)) {
            return;
        }

        String faviconUrl = getFaviconTintSourceUrl(story);
        if (TextUtils.isEmpty(faviconUrl)) {
            return;
        }

        int baseColor = getDefaultStoryCardBackgroundColor(context);
        if (isFaviconTintColorCurrent(story, faviconUrl, baseColor)) {
            return;
        }

        Integer tintColor = StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                context,
                story.id,
                faviconUrl,
                baseColor,
                paletteTintMode);
        if (tintColor == null) {
            return;
        }

        story.faviconTintSourceUrl = faviconUrl;
        story.faviconTintColor = tintColor;
        story.faviconTintColorLoaded = true;
        story.faviconTintColorLoading = false;
        story.faviconTintColorLoadFailed = false;
        story.faviconTintBaseColor = baseColor;
        story.faviconTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
    }

    private void cachePreviewState(@Nullable Context context, Story story) {
        if (context == null || story == null) {
            return;
        }

        Utils.cacheStoryPreviewState(context, story);
        if (story.previewImageTintColorLoaded && !TextUtils.isEmpty(story.previewImageTintSourceUrl)) {
            StoryPreviewImageLoader.saveCachedPreviewImageTintColor(
                    context,
                    story.id,
                    story.previewImageTintSourceUrl,
                    story.previewImageTintBaseColor,
                    story.previewImageTintMode,
                    story.previewImageTintColor);
        }
        if (story.faviconTintColorLoaded && !TextUtils.isEmpty(story.faviconTintSourceUrl)) {
            StoryPreviewImageLoader.saveCachedPreviewImageTintColor(
                    context,
                    story.id,
                    story.faviconTintSourceUrl,
                    story.faviconTintBaseColor,
                    story.faviconTintMode,
                    story.faviconTintColor);
        }
    }

    private void notifyStoryChangedIfPreviewEnabled(Story story) {
        int index = stories.indexOf(story);
        if (index >= 0 && !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)) {
            notifyItemChanged(index);
        }
    }

    private void cancelPreviewImageUrlRequests() {
        for (Map.Entry<Story, StoryPreviewImageLoader.PreviewImageRequest> entry : previewImageUrlRequests.entrySet()) {
            entry.getValue().cancel();
            entry.getKey().previewImageUrlLoading = false;
        }
        previewImageUrlRequests.clear();
    }

    private void prefetchPreviewImageDrawable(@Nullable Context context, Story story, int storyCardBackgroundColor) {
        if (context == null
                || TextUtils.isEmpty(story.previewImageUrl)
                || story.previewImageLoaded
                || story.previewImageLoading) {
            return;
        }

        story.previewImageLoading = true;
        final String imageUrl = story.previewImageUrl;
        int previewWidth = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? context.getResources().getDisplayMetrics().widthPixels
                : Utils.pxFromDpInt(context.getResources(), 72);
        int previewHeight = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? Utils.pxFromDpInt(context.getResources(), LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP)
                : Utils.pxFromDpInt(context.getResources(), 54);
        ImageRequest request = new ImageRequest.Builder(context)
                .data(imageUrl)
                .setHeader("User-Agent", NetworkComponent.USER_AGENT)
                .size(previewWidth, previewHeight)
                .allowHardware(!shouldTintStoryCards())
                .target(new Target() {
                    @Override
                    public void onStart(Drawable placeholder) {
                        story.previewImageLoading = true;
                    }

                    @Override
                    public void onError(Drawable error) {
                        story.previewImageLoading = false;
                    }

                    @Override
                    public void onSuccess(Drawable result) {
                        story.previewImageLoading = false;
                        story.previewImageLoaded = true;
                        StoryPreviewImageMemoryCache.put(story.id, imageUrl, result);
                        updatePreviewImageTintColor(story, imageUrl, result, storyCardBackgroundColor);
                        cachePreviewState(context, story);
                    }
                })
                .build();

        Coil.imageLoader(context).enqueue(request);
    }

    private void loadPreviewImage(final StoryViewHolder storyViewHolder, final Story story) {
        final ImageView previewImage = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? storyViewHolder.largePreviewImage
                : storyViewHolder.smallPreviewImage;
        final String imageUrl = story.previewImageUrl;
        if (previewImage == null) {
            return;
        }

        storyViewHolder.cancelPreviewImageRequest(previewImage);
        PreviewImageFailureAnimator.cancel(previewImage);
        previewImage.setTag(imageUrl);
        boolean hasMemoryPreviewImage = bindCachedPreviewImage(storyViewHolder, story, previewImage, imageUrl);
        final boolean fadeInWhenLoaded = !hasMemoryPreviewImage && !story.previewImageLoaded;

        int previewWidth = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? previewImage.getResources().getDisplayMetrics().widthPixels
                : Utils.pxFromDpInt(previewImage.getResources(), 72);
        int previewHeight = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? Utils.pxFromDpInt(previewImage.getResources(), LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP)
                : Utils.pxFromDpInt(previewImage.getResources(), 54);
        ImageRequest request = new ImageRequest.Builder(previewImage.getContext())
                .data(imageUrl)
                .setHeader("User-Agent", NetworkComponent.USER_AGENT)
                .size(previewWidth, previewHeight)
                .allowHardware(!shouldTintStoryCards())
                .target(new ImageViewTarget(previewImage) {
                    @Override
                    public void onStart(Drawable placeholder) {
                        story.previewImageLoading = true;
                        if (!hasMemoryPreviewImage && isCurrentPreviewTarget(previewImage, imageUrl)) {
                            previewImage.animate().cancel();
                            super.onStart((Drawable) null);
                            previewImage.setAlpha(getPreviewImageTargetAlpha(story));
                            setPreviewImageVisibility(storyViewHolder, previewImage, View.INVISIBLE);
                        }
                    }

                    @Override
                    public void onError(Drawable error) {
                        story.previewImageLoading = false;
                        if (isCurrentPreviewTarget(previewImage, imageUrl)) {
                            if (hasMemoryPreviewImage || hasCurrentPreviewTint(previewImage.getContext(), story)) {
                                story.previewImageLoadFailed = false;
                                applyStoryCardBackground(storyViewHolder, story, false);
                                return;
                            }
                            story.previewImageLoadFailed = true;
                            PreviewImageTintUtils.clearStoryPreviewImageTintColor(story);
                            previewImage.animate().cancel();
                            super.onError(null);
                            animatePreviewImageFailure(storyViewHolder, story, previewImage);
                        }
                    }

                    @Override
                    public void onSuccess(Drawable result) {
                        story.previewImageLoading = false;
                        story.previewImageLoaded = true;
                        story.previewImageLoadFailed = false;
                        StoryPreviewImageMemoryCache.put(story.id, imageUrl, result);
                        updatePreviewImageTintColor(previewImage.getContext(), story, imageUrl, result);
                        cachePreviewState(previewImage.getContext(), story);
                        if (isCurrentPreviewTarget(previewImage, imageUrl)) {
                            super.onSuccess(result);
                            if (previewImage == storyViewHolder.largePreviewImage) {
                                PreviewImageLayoutUtils.applyWideImageHeight(
                                        previewImage,
                                        result,
                                        LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP);
                            }
                            applyStoryCardBackground(
                                    storyViewHolder,
                                    story,
                                    fadeInWhenLoaded && isVisibleOnScreen(storyViewHolder.itemView));
                            if (fadeInWhenLoaded) {
                                fadeInPreviewImage(storyViewHolder, previewImage, getPreviewImageTargetAlpha(story));
                            } else {
                                previewImage.animate().cancel();
                                previewImage.setAlpha(getPreviewImageTargetAlpha(story));
                                setPreviewImageVisibility(storyViewHolder, previewImage, View.VISIBLE);
                            }
                        }
                    }
                })
                .build();

        storyViewHolder.setPreviewImageRequest(
                previewImage,
                story,
                Coil.imageLoader(previewImage.getContext()).enqueue(request));
    }

    private boolean bindCachedPreviewImage(
            StoryViewHolder storyViewHolder,
            Story story,
            ImageView previewImage,
            String imageUrl) {
        Drawable cachedPreviewImage = StoryPreviewImageMemoryCache.get(story.id, imageUrl);
        if (cachedPreviewImage == null) {
            return false;
        }

        previewImage.setImageDrawable(cachedPreviewImage);
        if (previewImage == storyViewHolder.largePreviewImage) {
            PreviewImageLayoutUtils.applyWideImageHeight(
                    previewImage,
                    cachedPreviewImage,
                    LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP);
        }
        previewImage.setAlpha(getPreviewImageTargetAlpha(story));
        setPreviewImageVisibility(storyViewHolder, previewImage, View.VISIBLE);
        story.previewImageLoaded = true;
        story.previewImageLoadFailed = false;
        updatePreviewImageTintColor(previewImage.getContext(), story, imageUrl, cachedPreviewImage);
        cachePreviewState(previewImage.getContext(), story);
        applyStoryCardBackground(storyViewHolder, story, false);
        return true;
    }

    private void reservePendingPreviewImageSpace(StoryViewHolder storyViewHolder) {
        ImageView previewImage = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? storyViewHolder.largePreviewImage
                : storyViewHolder.smallPreviewImage;
        if (previewImage != null) {
            previewImage.setImageDrawable(null);
            previewImage.setAlpha(1.0f);
            setPreviewImageVisibility(storyViewHolder, previewImage, View.INVISIBLE);
        }
    }

    private static void resetPreviewImages(StoryViewHolder storyViewHolder) {
        storyViewHolder.cancelPreviewImageRequests();
        boolean changed = resetPreviewImage(storyViewHolder.smallPreviewImage, false)
                | resetPreviewImage(storyViewHolder.largePreviewImage, true);
        if (changed) {
            storyViewHolder.itemView.requestLayout();
        }
    }

    private static boolean resetPreviewImage(ImageView previewImage, boolean large) {
        if (previewImage == null) {
            return false;
        }

        boolean changed = previewImage.getVisibility() != View.GONE
                || previewImage.getDrawable() != null
                || previewImage.getTag() != null
                || previewImage.getAlpha() != 1.0f;
        PreviewImageFailureAnimator.cancel(previewImage);
        CoilUtils.dispose(previewImage);
        previewImage.animate().cancel();
        previewImage.clearAnimation();
        previewImage.setTag(null);
        previewImage.setAlpha(1.0f);
        previewImage.setImageDrawable(null);
        previewImage.setVisibility(View.GONE);
        if (large) {
            PreviewImageLayoutUtils.resetHeight(
                    previewImage,
                    LARGE_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP);
        }
        return changed;
    }

    private void showPreviewImageFailure(Story story) {
        int index = stories.indexOf(story);
        if (index < 0 || SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)) {
            return;
        }

        RecyclerView.ViewHolder holder = recyclerView == null
                ? null : recyclerView.findViewHolderForAdapterPosition(index);
        if (holder instanceof StoryViewHolder
                && ((StoryViewHolder) holder).story == story) {
            StoryViewHolder storyViewHolder = (StoryViewHolder) holder;
            ImageView previewImage = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                    ? storyViewHolder.largePreviewImage
                    : storyViewHolder.smallPreviewImage;
            animatePreviewImageFailure(storyViewHolder, story, previewImage);
            return;
        }

        notifyItemChanged(index);
    }

    private void animatePreviewImageFailure(
            StoryViewHolder storyViewHolder,
            Story story,
            @Nullable ImageView previewImage) {
        boolean visible = isVisibleOnScreen(storyViewHolder.itemView);
        applyStoryCardBackground(storyViewHolder, story, visible);
        loadFaviconTintColor(
                storyViewHolder.itemView.getContext(),
                story,
                storyViewHolder,
                visible);
        if (previewImage == null || previewImage.getVisibility() == View.GONE) {
            return;
        }

        PreviewImageFailureAnimator.Axis axis =
                previewImage == storyViewHolder.largePreviewImage
                        ? PreviewImageFailureAnimator.Axis.VERTICAL
                        : PreviewImageFailureAnimator.Axis.HORIZONTAL;
        PreviewImageFailureAnimator.collapse(previewImage, axis);
    }

    private void applyStoryCardBackground(StoryViewHolder storyViewHolder, Story story, boolean animate) {
        if (storyViewHolder.storyCard == null) {
            return;
        }

        int targetColor = resolveStoryCardBackgroundColor(storyViewHolder.storyCard.getContext(), story);
        setStoryCardBackgroundColor(storyViewHolder, targetColor, animate);
    }

    public int resolveStoryCardBackgroundColor(@Nullable Context context, Story story) {
        int targetColor = getDefaultStoryCardBackgroundColor(context);
        if (shouldUsePreviewTint(story, targetColor)) {
            targetColor = story.previewImageTintColor;
        } else if (shouldUseFaviconTint(story)
                && story.faviconTintColorLoaded
                && isFaviconTintColorCurrent(story, getFaviconTintSourceUrl(story), targetColor)) {
            targetColor = story.faviconTintColor;
        }
        return targetColor;
    }

    private void resetStoryCardBackground(StoryViewHolder storyViewHolder) {
        if (storyViewHolder.storyCard != null) {
            setStoryCardBackgroundColor(
                    storyViewHolder,
                    getDefaultStoryCardBackgroundColor(storyViewHolder.storyCard),
                    false);
        }
    }

    private void setStoryCardBackgroundColor(StoryViewHolder storyViewHolder, int targetColor, boolean animate) {
        MaterialCardView card = storyViewHolder.storyCard;
        if (card == null) {
            return;
        }

        storyViewHolder.cancelStoryCardTintAnimator();
        int currentColor = storyViewHolder.currentStoryCardBackgroundColor != null
                ? storyViewHolder.currentStoryCardBackgroundColor
                : card.getCardBackgroundColor().getDefaultColor();

        if (!animate || currentColor == targetColor) {
            card.setCardBackgroundColor(targetColor);
            storyViewHolder.currentStoryCardBackgroundColor = targetColor;
            return;
        }

        ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), currentColor, targetColor);
        storyViewHolder.storyCardTintAnimator = animator;
        animator.setDuration(CARD_TINT_ANIMATION_DURATION_MS);
        animator.addUpdateListener(animation -> {
            int color = (int) animation.getAnimatedValue();
            card.setCardBackgroundColor(color);
            storyViewHolder.currentStoryCardBackgroundColor = color;
        });
        animator.start();
    }

    private static int getDefaultStoryCardBackgroundColor(View view) {
        return PreviewImageTintUtils.getTintBaseColor(view.getContext());
    }

    private static int getDefaultStoryCardBackgroundColor(@Nullable Context context) {
        if (context == null) {
            return Color.TRANSPARENT;
        }

        return PreviewImageTintUtils.getTintBaseColor(context);
    }

    private void updatePreviewImageTintColor(Context context, Story story, Drawable drawable) {
        updatePreviewImageTintColor(
                context,
                story,
                story == null ? null : story.previewImageUrl,
                drawable);
    }

    private void updatePreviewImageTintColor(Context context, Story story, String imageUrl, Drawable drawable) {
        if (!shouldTintStoryCards() || context == null || story == null || drawable == null) {
            return;
        }

        updatePreviewImageTintColor(
                story,
                imageUrl,
                drawable,
                getDefaultStoryCardBackgroundColor(context));
    }

    private void updatePreviewImageTintColor(Story story, Drawable drawable, int storyCardBackgroundColor) {
        updatePreviewImageTintColor(
                story,
                story == null ? null : story.previewImageUrl,
                drawable,
                storyCardBackgroundColor);
    }

    private void updatePreviewImageTintColor(Story story, String imageUrl, Drawable drawable, int storyCardBackgroundColor) {
        if (!shouldTintStoryCards() || story == null || drawable == null) {
            return;
        }

        PreviewImageTintUtils.updateStoryPreviewImageTintColor(
                story,
                imageUrl,
                drawable,
                storyCardBackgroundColor,
                paletteTintMode);
    }

    private void bindStoryCardTintFallback(StoryViewHolder storyViewHolder, Story story) {
        if (!shouldUseFaviconTint(story)) {
            return;
        }

        int baseColor = getDefaultStoryCardBackgroundColor(storyViewHolder.itemView.getContext());
        if (shouldUsePreviewTint(story, baseColor)) {
            return;
        }

        loadFaviconTintColor(
                storyViewHolder.itemView.getContext(),
                story,
                storyViewHolder,
                isVisibleOnScreen(storyViewHolder.itemView));
    }

    private void loadFaviconTintColor(
            @Nullable Context context,
            Story story,
            @Nullable StoryViewHolder storyViewHolder,
            boolean animate) {
        if (context == null || !shouldUseFaviconTint(story)) {
            return;
        }

        String faviconUrl = getFaviconTintSourceUrl(story);
        if (TextUtils.isEmpty(faviconUrl)) {
            return;
        }

        if (!TextUtils.equals(story.faviconTintSourceUrl, faviconUrl)) {
            story.faviconTintSourceUrl = faviconUrl;
            story.faviconTintColorLoaded = false;
            story.faviconTintColorLoading = false;
            story.faviconTintColorLoadFailed = false;
        }

        int baseColor = getDefaultStoryCardBackgroundColor(context);
        if (story.faviconTintColorLoaded && !isFaviconTintColorCurrent(story, faviconUrl, baseColor)) {
            story.faviconTintColorLoaded = false;
            story.faviconTintColorLoading = false;
            story.faviconTintColorLoadFailed = false;
        }

        if (story.faviconTintColorLoaded) {
            if (isCurrentStoryHolder(storyViewHolder, story)) {
                applyStoryCardBackground(storyViewHolder, story, animate);
            }
            return;
        }

        if (story.faviconTintColorLoading || story.faviconTintColorLoadFailed) {
            return;
        }

        story.faviconTintColorLoading = true;
        int faviconSize = Utils.pxFromDpInt(context.getResources(), FAVICON_TINT_SIZE_DP);
        ImageRequest request = new ImageRequest.Builder(context)
                .data(faviconUrl)
                .size(faviconSize, faviconSize)
                .allowHardware(false)
                .target(new Target() {
                    @Override
                    public void onStart(Drawable placeholder) {
                        if (TextUtils.equals(story.faviconTintSourceUrl, faviconUrl)) {
                            story.faviconTintColorLoading = true;
                        }
                    }

                    @Override
                    public void onError(Drawable error) {
                        if (!TextUtils.equals(story.faviconTintSourceUrl, faviconUrl)) {
                            return;
                        }

                        story.faviconTintColorLoading = false;
                        story.faviconTintColorLoadFailed = true;
                        if (isCurrentStoryHolder(storyViewHolder, story)) {
                            applyStoryCardBackground(storyViewHolder, story, animate);
                        }
                    }

                    @Override
                    public void onSuccess(Drawable result) {
                        if (!TextUtils.equals(story.faviconTintSourceUrl, faviconUrl)) {
                            return;
                        }

                        story.faviconTintColorLoading = false;
                        updateFaviconTintColor(context, story, result);
                        if (story.faviconTintColorLoaded) {
                            cachePreviewState(context, story);
                            if (isCurrentStoryHolder(storyViewHolder, story)) {
                                applyStoryCardBackground(storyViewHolder, story, animate);
                            } else {
                                notifyStoryChanged(story);
                            }
                        }
                    }
                })
                .build();

        Coil.imageLoader(context).enqueue(request);
    }

    private void updateFaviconTintColor(Context context, Story story, Drawable drawable) {
        if (context == null || story == null || drawable == null) {
            return;
        }

        try {
            int baseColor = getDefaultStoryCardBackgroundColor(context);
            story.faviconTintColor = PreviewImageTintUtils.calculateCardTint(baseColor, drawable, paletteTintMode);
            story.faviconTintColorLoaded = true;
            story.faviconTintBaseColor = baseColor;
            story.faviconTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
            story.faviconTintColorLoadFailed = false;
        } catch (RuntimeException e) {
            story.faviconTintColorLoaded = false;
            story.faviconTintColorLoadFailed = true;
        }
    }

    private boolean shouldUsePreviewTint(Story story, int baseColor) {
        PreviewImageTintUtils.syncStoryPreviewImageTintColorFromCache(story, baseColor, paletteTintMode);
        return shouldTintStoryCards()
                && story != null
                && !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)
                && !story.previewImageLoadFailed
                && PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(story, baseColor, paletteTintMode);
    }

    private boolean shouldUseFaviconTint(Story story) {
        return shouldTintStoryCards()
                && story != null
                && thumbnails
                && story.loaded
                && !story.loadingFailed
                && !story.isComment
                && !TextUtils.isEmpty(story.url);
    }

    private boolean isFaviconTintColorCurrent(Story story, String faviconUrl, int baseColor) {
        return story != null
                && story.faviconTintColorLoaded
                && TextUtils.equals(story.faviconTintSourceUrl, faviconUrl)
                && story.faviconTintBaseColor == baseColor
                && SettingsUtils.getPaletteTintConfigKey(paletteTintMode)
                .equals(SettingsUtils.getPaletteTintConfigKey(story.faviconTintMode));
    }

    private String getFaviconTintSourceUrl(Story story) {
        try {
            return story == null ? null : FaviconLoader.getFaviconUrl(story.url, faviconProvider);
        } catch (Exception e) {
            return null;
        }
    }

    private void setPreviewImageUrl(Story story, String imageUrl) {
        if (!TextUtils.equals(story.previewImageUrl, imageUrl)) {
            PreviewImageTintUtils.clearStoryPreviewImageTintColor(story);
            story.previewImageLoaded = false;
        }
        story.previewImageUrl = imageUrl;
    }

    private boolean hasCurrentPreviewTint(Context context, Story story) {
        if (context == null || story == null) {
            return false;
        }

        int baseColor = getDefaultStoryCardBackgroundColor(context);
        return PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(story, baseColor, paletteTintMode)
                || PreviewImageTintUtils.syncStoryPreviewImageTintColorFromCache(story, baseColor, paletteTintMode);
    }

    private boolean isCurrentStoryHolder(@Nullable StoryViewHolder storyViewHolder, Story story) {
        return storyViewHolder != null && storyViewHolder.story == story;
    }

    private void notifyStoryChanged(Story story) {
        int index = stories.indexOf(story);
        if (index >= 0) {
            notifyItemChanged(index);
        }
    }

    private boolean shouldTintStoryCards() {
        return tintCardUsingPreview;
    }

    private boolean shouldUseStoryCardShell() {
        return cardStyle || tintCardUsingPreview;
    }

    private void configureStoryCardAppearance(StoryViewHolder storyViewHolder) {
        MaterialCardView card = storyViewHolder.storyCard;
        if (card == null) {
            return;
        }

        if (cardStyle) {
            int oneDp = Utils.pxFromDpInt(card.getResources(), 1);
            card.setStrokeWidth(oneDp);
            card.setStrokeColor(MaterialColors.getColor(card, R.attr.commentDividerColor, Color.TRANSPARENT));
            card.setCardElevation(oneDp);
            return;
        }

        card.setStrokeWidth(0);
        card.setStrokeColor(Color.TRANSPARENT);
        card.setCardElevation(0f);
    }

    private void configureLargePreviewImageAppearance(StoryViewHolder storyViewHolder) {
        ImageView previewImage = storyViewHolder.largePreviewImage;
        if (previewImage == null) {
            return;
        }

        boolean useBorderless = borderlessLargePreviewImage
                && SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode);
        int inset = useBorderless
                ? 0
                : previewImage.getResources().getDimensionPixelSize(
                        R.dimen.story_large_preview_image_inset);
        int bottomMargin = useBorderless
                ? 0
                : previewImage.getResources().getDimensionPixelSize(
                        R.dimen.story_large_preview_image_bottom_margin);
        setLargePreviewImageMargins(previewImage, inset, inset, inset, bottomMargin);

        if (useBorderless) {
            previewImage.setBackground(null);
            previewImage.setClipToOutline(false);
        } else {
            previewImage.setBackgroundResource(R.drawable.story_preview_image_background);
            previewImage.setClipToOutline(true);
        }
    }

    private static void setLargePreviewImageMargins(
            ImageView previewImage,
            int start,
            int top,
            int end,
            int bottom) {
        ViewGroup.LayoutParams layoutParams = previewImage.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        ViewGroup.MarginLayoutParams marginParams = (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginParams.getMarginStart() == start
                && marginParams.topMargin == top
                && marginParams.getMarginEnd() == end
                && marginParams.bottomMargin == bottom) {
            return;
        }

        marginParams.setMarginStart(start);
        marginParams.topMargin = top;
        marginParams.setMarginEnd(end);
        marginParams.bottomMargin = bottom;
        previewImage.setLayoutParams(marginParams);
    }

    private static boolean isVisibleOnScreen(View view) {
        return view != null
                && view.isShown()
                && ViewCompat.isAttachedToWindow(view)
                && view.getGlobalVisibleRect(new Rect());
    }

    private static void setPreviewImageAlpha(StoryViewHolder storyViewHolder, boolean useClickedEffects) {
        setPreviewImageAlpha(storyViewHolder, useClickedEffects ? CLICKED_PREVIEW_IMAGE_ALPHA : 1.0f);
    }

    private static void setPreviewImageAlpha(StoryViewHolder storyViewHolder, float alpha) {
        setPreviewImageAlpha(storyViewHolder.smallPreviewImage, alpha);
        setPreviewImageAlpha(storyViewHolder.largePreviewImage, alpha);
    }

    private static void setPreviewImageAlpha(ImageView previewImage, float alpha) {
        if (previewImage != null) {
            previewImage.animate().cancel();
            previewImage.setAlpha(alpha);
        }
    }

    private float getPreviewImageTargetAlpha(Story story) {
        return story.clicked && grayOutClicked && !disableClickedEffects
                ? CLICKED_PREVIEW_IMAGE_ALPHA
                : 1.0f;
    }

    private static void fadeInPreviewImage(StoryViewHolder storyViewHolder, ImageView previewImage, float targetAlpha) {
        previewImage.animate().cancel();
        previewImage.setAlpha(0f);
        setPreviewImageVisibility(storyViewHolder, previewImage, View.VISIBLE);
        previewImage.animate()
                .alpha(targetAlpha)
                .setDuration(PREVIEW_IMAGE_FADE_IN_DURATION_MS)
                .start();
    }

    private void applyStoryTextSizes(StoryViewHolder storyViewHolder) {
        FontUtils.setStoryTitleTypeface(storyViewHolder.titleView, storyTextSize);
        FontUtils.setStoryMetaTypeface(storyViewHolder.metaView, storyTextSize);
        FontUtils.setStoryCommentCountTypeface(storyViewHolder.commentsView, storyTextSize);
    }

    private void bindCommentRowText(CommentViewHolder commentViewHolder, Story story) {
        String commentText = story.text == null ? "" : story.text;
        String bodyHtml = Utils.expandShortenedAnchorText(commentText);
        if (TextUtils.isEmpty(bodyHtml)) {
            commentViewHolder.bodyText.setText("");
        } else if (story.spannedText != null) {
            commentViewHolder.bodyText.setHtml(story.spannedText);
        } else {
            commentViewHolder.bodyText.setHtml(bodyHtml);
            if (story.loaded) {
                story.spannedText = (Spanned) commentViewHolder.bodyText.getText();
            }
        }
    }

    private void bindCommentRowHeader(CommentViewHolder commentViewHolder, Story story) {
        if (TextUtils.isEmpty(story.commentMasterTitle)) {
            commentViewHolder.cancelHeaderAnimation();
            commentViewHolder.headerText.setText(null);
            commentViewHolder.headerText.setAlpha(1.0f);
            commentViewHolder.headerText.setVisibility(View.GONE);
            commentViewHolder.headerLoading.setAlpha(1.0f);
            commentViewHolder.headerLoading.setVisibility(View.VISIBLE);
            commentViewHolder.headerLoading.setContentDescription("On post title loading");
            commentViewHolder.headerBoundStoryId = story.id;
            commentViewHolder.headerShowingLoading = true;
            return;
        }

        boolean animateResolvedTitle = commentViewHolder.headerBoundStoryId == story.id
                && commentViewHolder.headerShowingLoading
                && isVisibleOnScreen(commentViewHolder.itemView);

        commentViewHolder.cancelHeaderAnimation();
        commentViewHolder.headerText.setText("On \"" + story.commentMasterTitle + "\"");
        commentViewHolder.headerLoading.setContentDescription(null);

        if (animateResolvedTitle) {
            commentViewHolder.headerText.setAlpha(0.0f);
            commentViewHolder.headerText.setVisibility(View.VISIBLE);
            commentViewHolder.headerLoading.setAlpha(1.0f);
            commentViewHolder.headerLoading.setVisibility(View.VISIBLE);
            commentViewHolder.headerText.animate()
                    .alpha(1.0f)
                    .setDuration(COMMENT_HEADER_RESOLVE_FADE_DURATION_MS)
                    .start();
            commentViewHolder.headerLoading.animate()
                    .alpha(0.0f)
                    .setDuration(COMMENT_HEADER_RESOLVE_FADE_DURATION_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            commentViewHolder.headerLoading.setVisibility(View.GONE);
                            commentViewHolder.headerLoading.setAlpha(1.0f);
                            commentViewHolder.headerLoading.animate().setListener(null);
                        }
                    })
                    .start();
        } else {
            commentViewHolder.headerText.setAlpha(1.0f);
            commentViewHolder.headerText.setVisibility(View.VISIBLE);
            commentViewHolder.headerLoading.setAlpha(1.0f);
            commentViewHolder.headerLoading.setVisibility(View.GONE);
        }

        commentViewHolder.headerBoundStoryId = story.id;
        commentViewHolder.headerShowingLoading = false;
    }

    private void applyCommentRowTypefaces(CommentViewHolder commentViewHolder) {
        FontUtils.setTypefaceForFont(commentViewHolder.headerText, font, true, 13);
        FontUtils.setTypefaceForFont(commentViewHolder.headerLoadingPrefix, font, true, 13);
        FontUtils.setTypefaceForFont(commentViewHolder.headerTime, font, true, 12);
        FontUtils.setCommentTextTypefaceForFont(commentViewHolder.bodyText, font, commentTextSize);
    }

    private static boolean isCurrentPreviewTarget(ImageView previewImage, String imageUrl) {
        return imageUrl.equals(previewImage.getTag());
    }

    private static void setPreviewImageVisibility(StoryViewHolder storyViewHolder, ImageView previewImage, int visibility) {
        if (previewImage.getVisibility() != visibility) {
            previewImage.setVisibility(visibility);
            storyViewHolder.itemView.requestLayout();
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (isLoadMorePosition(position)) {
            return TYPE_LOAD_MORE_BUTTON;
        }

        if (atSubmissions) {
            return stories.get(position).isComment ? getCommentViewType() : getStoryViewType();
        }

        if (allowCommentRows && stories.get(position).isComment) {
            return getCommentViewType();
        }

        return getStoryViewType();
    }

    @Override
    public int getItemCount() {
        int visibleItemCount = getVisibleItemCount();
        return visibleItemCount + (hasLoadMoreButton() ? 1 : 0);
    }

    @Override
    public long getItemId(int position) {
        if (isLoadMorePosition(position)) {
            return Long.MAX_VALUE;
        }

        return stories.get(position).id;
    }

    private int getVisibleItemCount() {
        if (paginationMode) {
            return Math.min(visibleStoryCount, stories.size());
        }

        return stories.size();
    }

    private boolean hasLoadMoreButton() {
        return loadMoreLoading || showLoadMoreButton || (paginationMode && visibleStoryCount < stories.size());
    }

    private boolean isLoadMorePosition(int position) {
        return hasLoadMoreButton() && position == getVisibleItemCount();
    }

    public class StoryViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView titleView;
        public final TextView metaView;
        public final TextView commentsView;
        public final LinearLayout linkLayoutView;
        public final LinearLayout commentLayoutView;
        public final ImageView commentsIcon;
        public final LinearLayout titleShimmer;
        public final View metaShimmer;
        public final LinearLayout metaContainer;
        public final ImageView metaFavicon;
        public final ImageView smallPreviewImage;
        public final ImageView largePreviewImage;
        public final TextView indexTextView;
        @Nullable
        public final MaterialCardView storyCard;

        private int touchX, touchY;
        private Disposable smallPreviewImageRequest;
        private Story smallPreviewImageRequestStory;
        private Disposable largePreviewImageRequest;
        private Story largePreviewImageRequestStory;
        private ValueAnimator storyCardTintAnimator;
        private ValueAnimator clickedStateAnimator;
        private Integer currentStoryCardBackgroundColor;

        public Story story;

        public StoryViewHolder(StoryListItemBinding binding) {
            this(
                    binding.getRoot(),
                    binding.storyTitle,
                    binding.storyMeta,
                    binding.storyMetaContainer,
                    binding.storyComments,
                    binding.storyLinkLayout,
                    binding.storyCommentLayout,
                    binding.storyCommentsIcon,
                    binding.storyTitleShimmer,
                    binding.storyTitleShimmerMeta,
                    binding.storyMetaFavicon,
                    binding.storyPreviewImageSmall,
                    binding.storyPreviewImageLarge,
                    binding.storyIndex,
                    null);
        }

        public StoryViewHolder(StoryListItemLeftBinding binding) {
            this(
                    binding.getRoot(),
                    binding.storyTitle,
                    binding.storyMeta,
                    binding.storyMetaContainer,
                    binding.storyComments,
                    binding.storyLinkLayout,
                    binding.storyCommentLayout,
                    binding.storyCommentsIcon,
                    binding.storyTitleShimmer,
                    binding.storyTitleShimmerMeta,
                    binding.storyMetaFavicon,
                    binding.storyPreviewImageSmall,
                    binding.storyPreviewImageLarge,
                    binding.storyIndex,
                    null);
        }

        public StoryViewHolder(StoryListItemCardBinding binding) {
            this(
                    binding.getRoot(),
                    binding.storyContainer.storyTitle,
                    binding.storyContainer.storyMeta,
                    binding.storyContainer.storyMetaContainer,
                    binding.storyContainer.storyComments,
                    binding.storyContainer.storyLinkLayout,
                    binding.storyContainer.storyCommentLayout,
                    binding.storyContainer.storyCommentsIcon,
                    binding.storyContainer.storyTitleShimmer,
                    binding.storyContainer.storyTitleShimmerMeta,
                    binding.storyContainer.storyMetaFavicon,
                    binding.storyContainer.storyPreviewImageSmall,
                    binding.storyContainer.storyPreviewImageLarge,
                    binding.storyContainer.storyIndex,
                    binding.storyCard);
        }

        public StoryViewHolder(StoryListItemCardLeftBinding binding) {
            this(
                    binding.getRoot(),
                    binding.storyContainer.storyTitle,
                    binding.storyContainer.storyMeta,
                    binding.storyContainer.storyMetaContainer,
                    binding.storyContainer.storyComments,
                    binding.storyContainer.storyLinkLayout,
                    binding.storyContainer.storyCommentLayout,
                    binding.storyContainer.storyCommentsIcon,
                    binding.storyContainer.storyTitleShimmer,
                    binding.storyContainer.storyTitleShimmerMeta,
                    binding.storyContainer.storyMetaFavicon,
                    binding.storyContainer.storyPreviewImageSmall,
                    binding.storyContainer.storyPreviewImageLarge,
                    binding.storyContainer.storyIndex,
                    binding.storyCard);
        }

        @SuppressLint("ClickableViewAccessibility")
        private StoryViewHolder(View view,
                                TextView title,
                                TextView meta,
                                LinearLayout metaLayout,
                                TextView comments,
                                LinearLayout linkLayout,
                                LinearLayout commentLayout,
                                ImageView commentIcon,
                                LinearLayout shimmerTitle,
                                View shimmerMeta,
                                ImageView favicon,
                                ImageView smallPreview,
                                ImageView largePreview,
                                TextView index,
                                @Nullable MaterialCardView card) {
            super(view);
            mView = view;
            titleView = title;
            metaView = meta;
            metaContainer = metaLayout;
            commentsView = comments;
            linkLayoutView = linkLayout;
            commentLayoutView = commentLayout;
            commentsIcon = commentIcon;
            titleShimmer = shimmerTitle;
            metaShimmer = shimmerMeta;
            metaFavicon = favicon;
            smallPreviewImage = smallPreview;
            largePreviewImage = largePreview;
            indexTextView = index;
            storyCard = card;
            ViewCompat.setAccessibilityHeading(titleView, true);

            linkLayoutView.setOnClickListener(v -> linkClickListener.onItemClick(getAbsoluteAdapterPosition()));
            commentLayoutView.setOnClickListener(v -> commentClickListener.onItemClick(getAbsoluteAdapterPosition()));
            if (largePreviewImage != null) {
                largePreviewImage.setOnClickListener(v -> linkClickListener.onItemClick(getAbsoluteAdapterPosition()));
            }

            if (longClickListener != null) {
                linkLayoutView.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        touchX = (int) event.getX();
                        touchY = (int) event.getY();
                        return false;
                    }
                });

                linkLayoutView.setOnLongClickListener(v -> longClickListener.onLongClick(v, getAbsoluteAdapterPosition(), touchX, touchY));
                if (largePreviewImage != null) {
                    largePreviewImage.setOnTouchListener(new View.OnTouchListener() {
                        @Override
                        public boolean onTouch(View v, MotionEvent event) {
                            touchX = (int) event.getX();
                            touchY = (int) event.getY();
                            return false;
                        }
                    });
                    largePreviewImage.setOnLongClickListener(v -> longClickListener.onLongClick(v, getAbsoluteAdapterPosition(), touchX, touchY));
                }
            }
        }

        void setPreviewImageRequest(ImageView previewImage, Story story, Disposable disposable) {
            if (previewImage == smallPreviewImage) {
                smallPreviewImageRequest = disposable;
                smallPreviewImageRequestStory = story;
            } else if (previewImage == largePreviewImage) {
                largePreviewImageRequest = disposable;
                largePreviewImageRequestStory = story;
            }
        }

        void cancelPreviewImageRequest(ImageView previewImage) {
            if (previewImage != null) {
                previewImage.setTag(null);
            }
            if (previewImage == smallPreviewImage) {
                smallPreviewImageRequest = disposePreviewImageRequest(smallPreviewImageRequest, smallPreviewImageRequestStory);
                smallPreviewImageRequestStory = null;
            } else if (previewImage == largePreviewImage) {
                largePreviewImageRequest = disposePreviewImageRequest(largePreviewImageRequest, largePreviewImageRequestStory);
                largePreviewImageRequestStory = null;
            }
        }

        void cancelPreviewImageRequests() {
            cancelPreviewImageRequest(smallPreviewImage);
            cancelPreviewImageRequest(largePreviewImage);
        }

        void cancelStoryCardTintAnimator() {
            if (storyCardTintAnimator != null) {
                storyCardTintAnimator.cancel();
                storyCardTintAnimator = null;
            }
        }

        void cancelClickedStateAnimator() {
            if (clickedStateAnimator != null) {
                clickedStateAnimator.cancel();
                clickedStateAnimator = null;
            }
        }

        private Disposable disposePreviewImageRequest(Disposable disposable, Story story) {
            if (disposable != null && !disposable.isDisposed()) {
                disposable.dispose();
                if (story != null) {
                    story.previewImageLoading = false;
                }
            }
            return null;
        }
    }

    public class CommentViewHolder extends RecyclerView.ViewHolder {

        public final TextView headerText;
        public final LinearLayout headerLoading;
        public final TextView headerLoadingPrefix;
        public final TextView headerTime;
        public final HtmlTextView bodyText;
        public final Button storyButton;
        public final Button repliesButton;
        public final View scrim;
        private int headerBoundStoryId = -1;
        private boolean headerShowingLoading = false;


        public CommentViewHolder(SubmissionsCommentBinding binding) {
            this(binding.getRoot(), binding, false);
        }

        public CommentViewHolder(SubmissionsCommentCardBinding binding) {
            this(binding.getRoot(), binding.submissionsCommentContainer, true);
        }

        private CommentViewHolder(View view, SubmissionsCommentBinding binding, boolean cardStyle) {
            super(view);
            headerText = binding.submissionsCommentHeader;
            headerLoading = binding.submissionsCommentHeaderLoading;
            headerLoadingPrefix = binding.submissionsCommentHeaderLoadingPrefix;
            headerTime = binding.submissionsCommentTime;
            bodyText = binding.submissionsCommentBody;
            storyButton = binding.submissionsCommentButtonStory;
            repliesButton = binding.submissionsCommentButtonReplies;
            scrim = binding.submissionsCommentScrim;

            Context ctx = view.getContext();

            GradientDrawable gradientDrawable = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{
                            Color.TRANSPARENT,
                            cardStyle
                                    ? Utils.getColorViaAttr(ctx, com.google.android.material.R.attr.colorSurfaceContainerHigh)
                                    : ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx))
                    });

            scrim.setBackground(gradientDrawable);

            bodyText.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    Utils.openLinkMaybeHN(widget.getContext(), href);
                    return true;
                }
            });

            storyButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int pos = getAbsoluteAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        commentStoryClickListener.onItemClick(getAbsoluteAdapterPosition());
                    }
                }
            });

            repliesButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    int pos = getAbsoluteAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) {
                        commentRepliesClickListener.onItemClick(getAbsoluteAdapterPosition());
                    }
                }
            });
        }

        void cancelHeaderAnimation() {
            headerText.animate().cancel();
            headerLoading.animate().cancel();
            headerLoading.animate().setListener(null);
        }

        void resetHeaderState() {
            cancelHeaderAnimation();
            headerBoundStoryId = -1;
            headerShowingLoading = false;
            headerText.setAlpha(1.0f);
            headerLoading.setAlpha(1.0f);
            headerLoading.setVisibility(View.GONE);
            headerLoading.setContentDescription(null);
        }
    }

    public void setOnLinkClickListener(ClickListener clickListener) {
        linkClickListener = clickListener;
    }

    public void setOnCommentClickListener(ClickListener clickListener) {
        commentClickListener = clickListener;
    }

    public void setOnCommentStoryClickListener(ClickListener clickListener) {
        commentStoryClickListener = clickListener;
    }

    public void setOnCommentRepliesClickListener(ClickListener clickListener) {
        commentRepliesClickListener = clickListener;
    }

    public void setOnLongClickListener(LongClickCoordinateListener clickListener) {
        longClickListener = clickListener;
    }

    public void setOnLoadMoreClickListener(View.OnClickListener listener) {
        loadMoreClickListener = listener;
    }

    public void setLoadMoreLoading(boolean loading) {
        if (loadMoreLoading == loading) {
            return;
        }

        boolean hadLoadMoreButton = hasLoadMoreButton();
        int loadMorePosition = getVisibleItemCount();
        loadMoreLoading = loading;
        boolean hasLoadMoreButton = hasLoadMoreButton();
        if (hadLoadMoreButton && hasLoadMoreButton) {
            notifyItemChanged(loadMorePosition, PAYLOAD_LOAD_MORE_STATE);
        } else if (hadLoadMoreButton) {
            notifyItemRemoved(loadMorePosition);
        } else if (hasLoadMoreButton) {
            notifyItemInserted(loadMorePosition);
        }
    }

    public interface ClickListener {
        void onItemClick(int position);
    }

    public interface LongClickCoordinateListener {
        boolean onLongClick(View v, int position, int x, int y);
    }

    public static class LoadMoreViewHolder extends RecyclerView.ViewHolder {
        public Button loadMoreButton;
        public View loadMoreProgress;
        @Nullable
        private Boolean showingLoading;
        @Nullable
        private AnimatorSet stateAnimator;

        public LoadMoreViewHolder(LoadMoreButtonBinding binding) {
            super(binding.getRoot());
            loadMoreButton = binding.loadMoreButton;
            loadMoreProgress = binding.loadMoreProgress;
        }

        private void applyLoadingState(boolean loading) {
            if (stateAnimator != null) {
                stateAnimator.cancel();
                stateAnimator = null;
            }

            showingLoading = loading;
            loadMoreButton.setEnabled(!loading);
            setTransitionViewState(loadMoreButton, !loading, 1f, 1f);
            setTransitionViewState(loadMoreProgress, loading, 1f, 1f);
        }

        private void animateLoadingState(boolean loading) {
            if (stateAnimator != null) {
                stateAnimator.cancel();
            }

            showingLoading = loading;
            loadMoreButton.setEnabled(false);
            View outgoing = loading ? loadMoreButton : loadMoreProgress;
            View incoming = loading ? loadMoreProgress : loadMoreButton;

            setTransitionViewState(outgoing, true, 1f, 1f);
            setTransitionViewState(incoming, true, 0f, 0.8f);

            AnimatorSet fadeOut = new AnimatorSet();
            fadeOut.playTogether(
                    ObjectAnimator.ofFloat(outgoing, View.ALPHA, 1f, 0f),
                    ObjectAnimator.ofFloat(outgoing, View.SCALE_X, 1f, 0.9f),
                    ObjectAnimator.ofFloat(outgoing, View.SCALE_Y, 1f, 0.9f)
            );
            fadeOut.setDuration(LOAD_MORE_FADE_OUT_DURATION_MS);
            fadeOut.setInterpolator(LOAD_MORE_TRANSITION_INTERPOLATOR);

            AnimatorSet fadeIn = new AnimatorSet();
            fadeIn.playTogether(
                    ObjectAnimator.ofFloat(incoming, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(incoming, View.SCALE_X, 0.8f, 1f),
                    ObjectAnimator.ofFloat(incoming, View.SCALE_Y, 0.8f, 1f)
            );
            fadeIn.setDuration(LOAD_MORE_FADE_IN_DURATION_MS);
            fadeIn.setInterpolator(LOAD_MORE_TRANSITION_INTERPOLATOR);

            stateAnimator = new AnimatorSet();
            stateAnimator.playSequentially(fadeOut, fadeIn);
            stateAnimator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!cancelled) {
                        stateAnimator = null;
                        applyLoadingState(loading);
                    }
                }
            });
            stateAnimator.start();
        }

        private static void setTransitionViewState(View view, boolean visible, float alpha, float scale) {
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            view.setAlpha(alpha);
            view.setScaleX(scale);
            view.setScaleY(scale);
        }
    }

    public void loadNextPage() {
        int oldVisibleCount = visibleStoryCount;
        visibleStoryCount = Math.min(visibleStoryCount + PAGINATION_PAGE_SIZE, stories.size());

        // Notify about the new items that are now visible
        int itemsAdded = visibleStoryCount - oldVisibleCount;
        if (itemsAdded > 0) {
            notifyItemRangeInserted(oldVisibleCount, itemsAdded);
        }

        // Update or remove the button
        if (visibleStoryCount >= stories.size()) {
            if (showLoadMoreButton) {
                notifyItemChanged(visibleStoryCount);
            } else {
                // All stories are visible, remove the button
                notifyItemRemoved(visibleStoryCount);
            }
        } else {
            // More stories remain, update the button text
            notifyItemChanged(visibleStoryCount);
        }
    }

    private int getStoryViewType() {
        if (shouldUseStoryCardShell()) {
            return leftAlign ? TYPE_STORY_CARD_LEFT : TYPE_STORY_CARD;
        }
        return leftAlign ? TYPE_STORY_LEFT : TYPE_STORY;
    }

    private int getCommentViewType() {
        return cardStyle ? TYPE_COMMENT_CARD : TYPE_COMMENT;
    }

    private static boolean isStoryViewType(int viewType) {
        return viewType == TYPE_STORY
                || viewType == TYPE_STORY_LEFT
                || viewType == TYPE_STORY_CARD
                || viewType == TYPE_STORY_CARD_LEFT;
    }

}
