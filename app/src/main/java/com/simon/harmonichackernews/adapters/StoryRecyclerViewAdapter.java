package com.simon.harmonichackernews.adapters;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.network.StoryPreviewImageLoader;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.List;

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
    private static final long CARD_TINT_ANIMATION_DURATION_MS = 180;

    public boolean showPoints;
    public boolean showCommentsCount;
    public boolean compactView;
    public boolean thumbnails;
    public String previewImageMode;
    public float storyTextSize;
    public boolean showIndex;
    public boolean compactHeader;
    public boolean leftAlign;
    public boolean cardStyle;
    public boolean tintCardUsingPreview;
    public String faviconProvider;
    public int hotness;
    public int type;
    public String font;
    public boolean allowCommentRows;
    public boolean disableClickedEffects;
    public boolean grayOutClicked;

    public boolean paginationMode = false;
    public boolean showLoadMoreButton = false;
    public static final int PAGINATION_PAGE_SIZE = 30;
    public int visibleStoryCount = 30;

    public StoryRecyclerViewAdapter(List<Story> items,
                                    boolean shouldShowPoints,
                                    boolean shouldShowCommentsCount,
                                    boolean shouldUseCompactView,
                                    boolean shouldShowThumbnails,
                                    String preferredPreviewImageMode,
                                    float preferredStoryTextSize,
                                    boolean shouldShowIndex,
                                    boolean shouldUseCompactHeader,
                                    boolean shouldLeftAlign,
                                    boolean shouldUseCardStyle,
                                    boolean shouldTintCardUsingPreview,
                                    boolean shouldGrayOutClicked,
                                    int preferredHotness,
                                    String faviconProv,
                                    String prefFont,
                                    String submissionsUserName,
                                    int wantedType) {
        stories = items;
        showPoints = shouldShowPoints;
        showCommentsCount = shouldShowCommentsCount;
        compactView = shouldUseCompactView;
        thumbnails = shouldShowThumbnails;
        previewImageMode = preferredPreviewImageMode;
        storyTextSize = SettingsUtils.clampStoryTextSize(preferredStoryTextSize);
        showIndex = shouldShowIndex;
        compactHeader = shouldUseCompactHeader;
        leftAlign = shouldLeftAlign;
        cardStyle = shouldUseCardStyle;
        tintCardUsingPreview = shouldTintCardUsingPreview;
        grayOutClicked = shouldGrayOutClicked;
        hotness = preferredHotness;
        faviconProvider = faviconProv;
        font = prefFont;
        type = wantedType;

        atSubmissions = !TextUtils.isEmpty(submissionsUserName);
        submitter = submissionsUserName;
        setHasStableIds(true);
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        if (isStoryViewType(viewType)) {
            return new StoryViewHolder(LayoutInflater.from(parent.getContext()).inflate(getStoryLayout(viewType), parent, false));
        } else if (viewType == TYPE_LOAD_MORE_BUTTON) {
            return new LoadMoreViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.load_more_button, parent, false));
        } else {
            int layout = viewType == TYPE_COMMENT_CARD ? R.layout.submissions_comment_card : R.layout.submissions_comment;
            return new CommentViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NotNull final RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof LoadMoreViewHolder) {
            LoadMoreViewHolder loadMoreHolder = (LoadMoreViewHolder) holder;

            loadMoreHolder.loadMoreButton.setOnClickListener(v -> {
                if (loadMoreClickListener != null) {
                    loadMoreClickListener.onClick(v);
                }
            });
            return;
        }

        if (holder instanceof StoryViewHolder) {
            final StoryViewHolder storyViewHolder = (StoryViewHolder) holder;
            final Context ctx = storyViewHolder.itemView.getContext();

            storyViewHolder.story = stories.get(position);
            boolean useClickedEffects = storyViewHolder.story.clicked && grayOutClicked && !disableClickedEffects;
            resetPreviewImages(storyViewHolder);
            applyStoryCardBackground(storyViewHolder, storyViewHolder.story, false);
            setPreviewImageAlpha(storyViewHolder, useClickedEffects);

            if (showIndex) {
                int displayIndex = position + 1;
                storyViewHolder.indexTextView.setText(displayIndex + ".");
                storyViewHolder.indexTextView.setContentDescription("Story " + displayIndex);

                if (useClickedEffects) {
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

            if (storyViewHolder.story.loaded || storyViewHolder.story.loadingFailed) {
                if (!TextUtils.isEmpty(storyViewHolder.story.pdfTitle)) {
                    SpannableStringBuilder sb = new SpannableStringBuilder(storyViewHolder.story.pdfTitle + " ");

                    ImageSpan imageSpan = new ImageSpan(ctx, useClickedEffects ? R.drawable.ic_action_pdf_clicked : R.drawable.ic_action_pdf);
                    sb.setSpan(imageSpan, sb.length() - 1, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    storyViewHolder.titleView.setText(sb);
                } else {
                    storyViewHolder.titleView.setText(storyViewHolder.story.title);
                }

                final String commentCountText;
                if (showCommentsCount) {
                    commentCountText = Integer.toString(storyViewHolder.story.descendants);
                } else if (storyViewHolder.story.descendants > 0) {
                    commentCountText = "•";
                } else {
                    commentCountText = "";
                }
                storyViewHolder.commentsView.setText(commentCountText);
                storyViewHolder.commentsView.setContentDescription(commentCountDescription(storyViewHolder.story.descendants));
                storyViewHolder.commentLayoutView.setContentDescription(commentCountDescription(storyViewHolder.story.descendants));

                String host = "";
                try {
                    if (storyViewHolder.story.url != null) {
                        host = Utils.getDomainName(storyViewHolder.story.url);
                    }
                } catch (Exception e) {
                    host = "Unknown";
                }

                if (showPoints && !storyViewHolder.story.isComment) {
                    String ptsString = storyViewHolder.story.score == 1 ? " point" : " points";
                    storyViewHolder.metaView.setText(storyViewHolder.story.score + ptsString + " • " + host + " • " + storyViewHolder.story.getTimeFormatted());
                    storyViewHolder.metaView.setContentDescription(
                            pointCountDescription(storyViewHolder.story.score) + ", "
                                    + host + ", "
                                    + storyViewHolder.story.getTimeFormatted());
                } else {
                    storyViewHolder.metaView.setText(host + " • " + storyViewHolder.story.getTimeFormatted());
                    storyViewHolder.metaView.setContentDescription(host + ", " + storyViewHolder.story.getTimeFormatted());
                }

                if (thumbnails) {
                    FaviconLoader.loadFavicon(storyViewHolder.story.url, storyViewHolder.metaFavicon, ctx, faviconProvider);
                }

                bindPreviewImage(storyViewHolder, storyViewHolder.story);

                storyViewHolder.commentsIcon.setImageResource(hotness > 0 && storyViewHolder.story.score + storyViewHolder.story.descendants > hotness ? R.drawable.ic_action_whatshot : R.drawable.ic_action_comment);

                applyStoryTextSizes(storyViewHolder);
                FontUtils.setTypeface(storyViewHolder.commentsView, true, 14, 13.5f, 13, 13, 14, 14, 14);

                if (useClickedEffects) {
                    storyViewHolder.titleView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.storyColorDisabled));
                    storyViewHolder.commentsIcon.setAlpha(0.6f);
                    storyViewHolder.metaFavicon.setAlpha(0.6f);
                    storyViewHolder.commentsView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.textColorDisabled));
                    storyViewHolder.metaView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.textColorDisabled));
                } else {
                    storyViewHolder.titleView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.storyColorNormal));
                    storyViewHolder.commentsIcon.setAlpha(1.0f);
                    storyViewHolder.metaFavicon.setAlpha(1.0f);
                    storyViewHolder.commentsView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.textColorDefault));
                    storyViewHolder.metaView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.textColorDefault));
                }

                storyViewHolder.titleShimmer.setVisibility(View.GONE);
                storyViewHolder.metaShimmer.setVisibility(View.GONE);
                storyViewHolder.titleView.setVisibility(View.VISIBLE);
                storyViewHolder.metaContainer.setVisibility(compactView ? View.GONE : View.VISIBLE);
                storyViewHolder.commentsView.setVisibility(compactView ? View.GONE : View.VISIBLE);
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
                reserveLargePreviewImageSpaceForLoadingStory(storyViewHolder, storyViewHolder.story);
                storyViewHolder.commentsIcon.setImageResource(R.drawable.ic_action_comment);
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
        } else if (holder instanceof CommentViewHolder) {
            final CommentViewHolder commentViewHolder = (CommentViewHolder) holder;

            Story story = stories.get(position);

            String masterTitle = TextUtils.isEmpty(story.commentMasterTitle) ? "Hacker News thread" : story.commentMasterTitle;
            commentViewHolder.headerText.setText("On \"" + masterTitle + "\" " + Utils.getTimeAgo(story.time));
            commentViewHolder.storyButton.setEnabled(story.commentMasterId > 0 || story.parentId > 0);
            if (story.spannedText != null) {
                commentViewHolder.bodyText.setHtml(story.spannedText);
            } else {
                commentViewHolder.bodyText.setHtml(story.text == null ? "" : story.text);
                if (story.loaded) {
                    story.spannedText = (Spanned) commentViewHolder.bodyText.getText();
                }
            }

            commentViewHolder.bodyText.post(new Runnable() {
                @Override
                public void run() {
                    commentViewHolder.scrim.setVisibility(ViewUtils.isTextTruncated(commentViewHolder.bodyText) ? View.VISIBLE : View.GONE);
                }
            });

        }
    }

    @Override
    public void onViewRecycled(@NotNull RecyclerView.ViewHolder holder) {
        if (holder instanceof StoryViewHolder) {
            StoryViewHolder storyViewHolder = (StoryViewHolder) holder;
            storyViewHolder.story = null;
            resetPreviewImages(storyViewHolder);
            resetStoryCardBackground(storyViewHolder);
        }
        super.onViewRecycled(holder);
    }

    private void bindPreviewImage(final StoryViewHolder storyViewHolder, final Story story) {
        if (!shouldLoadPreviewImage(story)) {
            return;
        }

        if (!TextUtils.isEmpty(story.previewImageUrl)) {
            loadPreviewImage(storyViewHolder, story);
            return;
        }

        if (story.previewImageUrlLoaded) {
            return;
        }

        reservePreviewImageSpace(storyViewHolder);
        loadPreviewImageUrl(storyViewHolder.itemView.getContext(), story);
    }

    public void prefetchPreviewImage(Context context, Story story) {
        if (!shouldLoadPreviewImage(story)) {
            return;
        }

        if (!TextUtils.isEmpty(story.previewImageUrl)) {
            prefetchPreviewImageDrawable(context, story);
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
                && !story.previewImageLoadFailed;
    }

    private void loadPreviewImageUrl(@Nullable Context context, Story story) {
        if (story.previewImageUrlLoaded || story.previewImageUrlLoading) {
            return;
        }

        story.previewImageUrlLoading = true;
        Context appContext = context == null ? null : context.getApplicationContext();
        StoryPreviewImageLoader.loadPreviewImageUrl(appContext, story.id, story.url, imageUrl -> {
            story.previewImageUrlLoading = false;
            story.previewImageUrlLoaded = true;
            if (TextUtils.isEmpty(imageUrl)) {
                story.previewImageLoadFailed = true;
                int index = stories.indexOf(story);
                if (index >= 0 && !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)) {
                    notifyItemChanged(index);
                }
                return;
            }

            story.previewImageUrl = imageUrl;
            story.previewImageLoadFailed = false;
            prefetchPreviewImageDrawable(appContext, story);
            int index = stories.indexOf(story);
            if (index >= 0 && !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)) {
                notifyItemChanged(index);
            }
        });
    }

    private void prefetchPreviewImageDrawable(@Nullable Context context, Story story) {
        if (context == null
                || TextUtils.isEmpty(story.previewImageUrl)
                || story.previewImageLoaded
                || story.previewImageLoading) {
            return;
        }

        story.previewImageLoading = true;
        int previewWidth = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? context.getResources().getDisplayMetrics().widthPixels
                : Utils.pxFromDpInt(context.getResources(), 72);
        int previewHeight = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? Utils.pxFromDpInt(context.getResources(), 176)
                : Utils.pxFromDpInt(context.getResources(), 54);
        ImageRequest request = new ImageRequest.Builder(context)
                .data(story.previewImageUrl)
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
                        updatePreviewImageTintColor(context, story, result);
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

        final boolean fadeInWhenLoaded = !story.previewImageLoaded;
        storyViewHolder.cancelPreviewImageRequest(previewImage);
        previewImage.setTag(imageUrl);

        int previewWidth = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? previewImage.getResources().getDisplayMetrics().widthPixels
                : Utils.pxFromDpInt(previewImage.getResources(), 72);
        int previewHeight = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? Utils.pxFromDpInt(previewImage.getResources(), 176)
                : Utils.pxFromDpInt(previewImage.getResources(), 54);
        ImageRequest request = new ImageRequest.Builder(previewImage.getContext())
                .data(imageUrl)
                .size(previewWidth, previewHeight)
                .allowHardware(!shouldTintStoryCards())
                .target(new ImageViewTarget(previewImage) {
                    @Override
                    public void onStart(Drawable placeholder) {
                        story.previewImageLoading = true;
                        if (isCurrentPreviewTarget(previewImage, imageUrl)) {
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
                            story.previewImageLoadFailed = true;
                            story.previewImageTintColorLoaded = false;
                            previewImage.animate().cancel();
                            super.onError(null);
                            setPreviewImageVisibility(storyViewHolder, previewImage, View.GONE);
                            applyStoryCardBackground(storyViewHolder, story, false);
                        }
                    }

                    @Override
                    public void onSuccess(Drawable result) {
                        story.previewImageLoading = false;
                        story.previewImageLoaded = true;
                        updatePreviewImageTintColor(previewImage.getContext(), story, result);
                        if (isCurrentPreviewTarget(previewImage, imageUrl)) {
                            super.onSuccess(result);
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

    private void reservePreviewImageSpace(StoryViewHolder storyViewHolder) {
        ImageView previewImage = SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                ? storyViewHolder.largePreviewImage
                : storyViewHolder.smallPreviewImage;
        if (previewImage != null) {
            setPreviewImageVisibility(storyViewHolder, previewImage, View.INVISIBLE);
        }
    }

    private void reserveLargePreviewImageSpaceForLoadingStory(StoryViewHolder storyViewHolder, Story story) {
        if (SettingsUtils.STORY_PREVIEW_IMAGE_LARGE.equals(previewImageMode)
                && story != null
                && !story.loadingFailed
                && !story.previewImageLoadFailed
                && storyViewHolder.largePreviewImage != null) {
            setPreviewImageVisibility(storyViewHolder, storyViewHolder.largePreviewImage, View.INVISIBLE);
        }
    }

    private static void resetPreviewImages(StoryViewHolder storyViewHolder) {
        storyViewHolder.cancelPreviewImageRequests();
        boolean changed = resetPreviewImage(storyViewHolder.smallPreviewImage)
                | resetPreviewImage(storyViewHolder.largePreviewImage);
        if (changed) {
            storyViewHolder.itemView.requestLayout();
        }
    }

    private static boolean resetPreviewImage(ImageView previewImage) {
        if (previewImage == null) {
            return false;
        }

        boolean changed = previewImage.getVisibility() != View.GONE
                || previewImage.getDrawable() != null
                || previewImage.getTag() != null
                || previewImage.getAlpha() != 1.0f;
        CoilUtils.dispose(previewImage);
        previewImage.animate().cancel();
        previewImage.clearAnimation();
        previewImage.setTag(null);
        previewImage.setAlpha(1.0f);
        previewImage.setImageDrawable(null);
        previewImage.setVisibility(View.GONE);
        return changed;
    }

    private void applyStoryCardBackground(StoryViewHolder storyViewHolder, Story story, boolean animate) {
        if (storyViewHolder.storyCard == null) {
            return;
        }

        int targetColor = getDefaultStoryCardBackgroundColor(storyViewHolder.storyCard);
        if (tintCardUsingPreview
                && cardStyle
                && story != null
                && story.previewImageTintColorLoaded) {
            targetColor = story.previewImageTintColor;
        }

        setStoryCardBackgroundColor(storyViewHolder, targetColor, animate);
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
        return MaterialColors.getColor(
                view,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
    }

    private void updatePreviewImageTintColor(Context context, Story story, Drawable drawable) {
        if (!shouldTintStoryCards() || context == null || story == null || drawable == null) {
            return;
        }

        try {
            story.previewImageTintColor = PreviewImageTintUtils.calculateCardTint(context, drawable);
            story.previewImageTintColorLoaded = true;
        } catch (RuntimeException e) {
            story.previewImageTintColorLoaded = false;
        }
    }

    private boolean shouldTintStoryCards() {
        return tintCardUsingPreview && cardStyle;
    }

    private static boolean isVisibleOnScreen(View view) {
        return view != null
                && view.isShown()
                && ViewCompat.isAttachedToWindow(view)
                && view.getGlobalVisibleRect(new Rect());
    }

    private static void setPreviewImageAlpha(StoryViewHolder storyViewHolder, boolean useClickedEffects) {
        float alpha = useClickedEffects ? CLICKED_PREVIEW_IMAGE_ALPHA : 1.0f;
        setPreviewImageAlpha(storyViewHolder.smallPreviewImage, alpha);
        setPreviewImageAlpha(storyViewHolder.largePreviewImage, alpha);
    }

    private static void setPreviewImageAlpha(ImageView previewImage, float alpha) {
        if (previewImage != null) {
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
        float clampedStoryTextSize = SettingsUtils.clampStoryTextSize(storyTextSize);
        float titleDelta = clampedStoryTextSize - SettingsUtils.DEFAULT_STORY_TEXT_SIZE;
        float metaScale = clampedStoryTextSize / SettingsUtils.DEFAULT_STORY_TEXT_SIZE;

        FontUtils.setTypeface(
                storyViewHolder.titleView,
                true,
                17.5f + titleDelta,
                17 + titleDelta,
                18 + titleDelta,
                16 + titleDelta,
                17 + titleDelta,
                17 + titleDelta,
                18 + titleDelta);
        FontUtils.setTypeface(
                storyViewHolder.metaView,
                false,
                13 * metaScale,
                12.5f * metaScale,
                13 * metaScale,
                12 * metaScale,
                12 * metaScale,
                13 * metaScale,
                13 * metaScale);
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
        return showLoadMoreButton || (paginationMode && visibleStoryCount < stories.size());
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
        public final MaterialCardView storyCard;

        private int touchX, touchY;
        private Disposable smallPreviewImageRequest;
        private Story smallPreviewImageRequestStory;
        private Disposable largePreviewImageRequest;
        private Story largePreviewImageRequestStory;
        private ValueAnimator storyCardTintAnimator;
        private Integer currentStoryCardBackgroundColor;

        public Story story;

        @SuppressLint("ClickableViewAccessibility")
        public StoryViewHolder(View view) {
            super(view);
            mView = view;
            titleView = view.findViewById(R.id.story_title);
            metaView = view.findViewById(R.id.story_meta);
            metaContainer = view.findViewById(R.id.story_meta_container);
            commentsView = view.findViewById(R.id.story_comments);
            linkLayoutView = view.findViewById(R.id.story_link_layout);
            commentLayoutView = view.findViewById(R.id.story_comment_layout);
            commentsIcon = view.findViewById(R.id.story_comments_icon);
            titleShimmer = view.findViewById(R.id.story_title_shimmer);
            metaShimmer = view.findViewById(R.id.story_title_shimmer_meta);
            metaFavicon = view.findViewById(R.id.story_meta_favicon);
            smallPreviewImage = view.findViewById(R.id.story_preview_image_small);
            largePreviewImage = view.findViewById(R.id.story_preview_image_large);
            indexTextView = view.findViewById(R.id.story_index);
            storyCard = view.findViewById(R.id.story_card);
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
        public final HtmlTextView bodyText;
        public final Button storyButton;
        public final Button repliesButton;
        public final View scrim;


        public CommentViewHolder(View view) {
            super(view);
            headerText = view.findViewById(R.id.submissions_comment_header);
            bodyText = view.findViewById(R.id.submissions_comment_body);
            storyButton = view.findViewById(R.id.submissions_comment_button_story);
            repliesButton = view.findViewById(R.id.submissions_comment_button_replies);
            scrim = view.findViewById(R.id.submissions_comment_scrim);

            Context ctx = view.getContext();

            GradientDrawable gradientDrawable = new GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{
                            Color.TRANSPARENT,
                            view.findViewById(R.id.submissions_comment_card) != null
                                    ? Utils.getColorViaAttr(ctx, com.google.android.material.R.attr.colorSurfaceContainerHigh)
                                    : ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx))
                    });

            scrim.setBackground(gradientDrawable);

            bodyText.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    Utils.launchCustomTab(widget.getContext(), href);
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

    public interface ClickListener {
        void onItemClick(int position);
    }

    public interface LongClickCoordinateListener {
        boolean onLongClick(View v, int position, int x, int y);
    }

    public static class LoadMoreViewHolder extends RecyclerView.ViewHolder {
        public Button loadMoreButton;

        public LoadMoreViewHolder(View view) {
            super(view);
            loadMoreButton = view.findViewById(R.id.load_more_button);
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

    private static String commentCountDescription(int count) {
        if (count == 1) {
            return "1 comment";
        }
        return count + " comments";
    }

    private static String pointCountDescription(int count) {
        if (count == 1) {
            return "1 point";
        }
        return count + " points";
    }

    private int getStoryViewType() {
        if (cardStyle) {
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

    private static int getStoryLayout(int viewType) {
        if (viewType == TYPE_STORY_CARD) {
            return R.layout.story_list_item_card;
        }
        if (viewType == TYPE_STORY_CARD_LEFT) {
            return R.layout.story_list_item_card_left;
        }
        if (viewType == TYPE_STORY_LEFT) {
            return R.layout.story_list_item_left;
        }
        return R.layout.story_list_item;
    }

}
