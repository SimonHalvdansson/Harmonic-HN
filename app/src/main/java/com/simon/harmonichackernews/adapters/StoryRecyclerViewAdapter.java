package com.simon.harmonichackernews.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
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

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.network.StoryPreviewImageLoader;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.List;

import coil.Coil;
import coil.request.ImageRequest;
import coil.target.Target;

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

    public boolean showPoints;
    public boolean showCommentsCount;
    public boolean compactView;
    public boolean thumbnails;
    public String previewImageMode;
    public boolean showIndex;
    public boolean compactHeader;
    public boolean leftAlign;
    public boolean cardStyle;
    public String faviconProvider;
    public int hotness;
    public int type;
    public boolean allowCommentRows;
    public boolean disableClickedEffects;
    public boolean grayOutClicked;

    public boolean paginationMode = false;
    public static final int PAGINATION_PAGE_SIZE = 30;
    public int visibleStoryCount = 30;

    public StoryRecyclerViewAdapter(List<Story> items,
                                    boolean shouldShowPoints,
                                    boolean shouldShowCommentsCount,
                                    boolean shouldUseCompactView,
                                    boolean shouldShowThumbnails,
                                    String preferredPreviewImageMode,
                                    boolean shouldShowIndex,
                                    boolean shouldUseCompactHeader,
                                    boolean shouldLeftAlign,
                                    boolean shouldUseCardStyle,
                                    boolean shouldGrayOutClicked,
                                    int preferredHotness,
                                    String faviconProv,
                                    String submissionsUserName,
                                    int wantedType) {
        stories = items;
        showPoints = shouldShowPoints;
        showCommentsCount = shouldShowCommentsCount;
        compactView = shouldUseCompactView;
        thumbnails = shouldShowThumbnails;
        previewImageMode = preferredPreviewImageMode;
        showIndex = shouldShowIndex;
        compactHeader = shouldUseCompactHeader;
        leftAlign = shouldLeftAlign;
        cardStyle = shouldUseCardStyle;
        grayOutClicked = shouldGrayOutClicked;
        hotness = preferredHotness;
        faviconProvider = faviconProv;
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
                    storyViewHolder.indexTextView.setPadding(0, Utils.pxFromDpInt(ctx.getResources(), 0.5f), 0, 0);
                } else {
                    storyViewHolder.indexTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    storyViewHolder.indexTextView.setPadding(0, Utils.pxFromDpInt(ctx.getResources(), 3.8f), 0, 0);
                }
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

                FontUtils.setTypeface(storyViewHolder.titleView, true, 17.5f, 18, 16, 17, 17, 18);
                FontUtils.setTypeface(storyViewHolder.metaView, false, 13, 13, 12, 12, 13, 13);
                FontUtils.setTypeface(storyViewHolder.commentsView, true, 14, 13, 13, 14, 14, 14);

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

                storyViewHolder.linkLayoutView.setClickable(true);
                storyViewHolder.commentLayoutView.setClickable(!storyViewHolder.story.loadingFailed);
            } else {
                storyViewHolder.commentsIcon.setImageResource(R.drawable.ic_action_comment);
                storyViewHolder.titleShimmer.setVisibility(View.VISIBLE);
                storyViewHolder.metaShimmer.setVisibility(compactView ? View.GONE : View.VISIBLE);
                storyViewHolder.titleView.setVisibility(View.GONE);
                storyViewHolder.metaContainer.setVisibility(View.GONE);
                storyViewHolder.commentsView.setText(null);
                storyViewHolder.commentsView.setContentDescription(null);
                storyViewHolder.metaView.setContentDescription(null);
                storyViewHolder.commentLayoutView.setContentDescription(null);
                storyViewHolder.linkLayoutView.setClickable(false);
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
                story.spannedText = (Spanned) commentViewHolder.bodyText.getText();
            }

            commentViewHolder.bodyText.post(new Runnable() {
                @Override
                public void run() {
                    commentViewHolder.scrim.setVisibility(ViewUtils.isTextTruncated(commentViewHolder.bodyText) ? View.VISIBLE : View.GONE);
                }
            });

        }
    }

    private void bindPreviewImage(final StoryViewHolder storyViewHolder, final Story story) {
        if (!shouldLoadPreviewImage(story)) {
            return;
        }

        if (!TextUtils.isEmpty(story.previewImageUrl)) {
            loadPreviewImage(storyViewHolder, story);
            return;
        }

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
                .target(new Target() {
                    @Override
                    public void onStart(Drawable placeholder) {
                        story.previewImageLoading = true;
                        if (isCurrentPreviewTarget(previewImage, imageUrl)) {
                            previewImage.setImageDrawable(null);
                            previewImage.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onError(Drawable error) {
                        story.previewImageLoading = false;
                        if (isCurrentPreviewTarget(previewImage, imageUrl)) {
                            story.previewImageLoadFailed = true;
                            previewImage.setImageDrawable(null);
                            previewImage.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onSuccess(Drawable result) {
                        story.previewImageLoading = false;
                        story.previewImageLoaded = true;
                        if (isCurrentPreviewTarget(previewImage, imageUrl)) {
                            previewImage.setImageDrawable(result);
                            previewImage.setVisibility(View.VISIBLE);
                        }
                    }
                })
                .build();

        Coil.imageLoader(previewImage.getContext()).enqueue(request);
    }

    private static void resetPreviewImages(StoryViewHolder storyViewHolder) {
        resetPreviewImage(storyViewHolder.smallPreviewImage);
        resetPreviewImage(storyViewHolder.largePreviewImage);
    }

    private static void resetPreviewImage(ImageView previewImage) {
        if (previewImage == null) {
            return;
        }

        previewImage.setTag(null);
        previewImage.setAlpha(1.0f);
        previewImage.setImageDrawable(null);
        previewImage.setVisibility(View.GONE);
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

    private static boolean isCurrentPreviewTarget(ImageView previewImage, String imageUrl) {
        return imageUrl.equals(previewImage.getTag());
    }

    @Override
    public int getItemViewType(int position) {
        if (atSubmissions) {
            return stories.get(position).isComment ? getCommentViewType() : getStoryViewType();
        }

        if (paginationMode && position == visibleStoryCount && visibleStoryCount < stories.size()) {
            return TYPE_LOAD_MORE_BUTTON;
        }

        if (allowCommentRows && stories.get(position).isComment) {
            return getCommentViewType();
        }

        return getStoryViewType();
    }

    @Override
    public int getItemCount() {
        if (atSubmissions) {
            return stories.size();
        }

        // Non-submissions: stories list contains only actual stories
        if (paginationMode && visibleStoryCount < stories.size()) {
            // visible stories + Load More button
            return visibleStoryCount + 1;
        }
        return stories.size();
    }

    @Override
    public long getItemId(int position) {
        if (!atSubmissions && paginationMode && position == visibleStoryCount && visibleStoryCount < stories.size()) {
            return Long.MAX_VALUE;
        }

        return stories.get(position).id;
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

        private int touchX, touchY;

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
            // All stories are visible, remove the button
            notifyItemRemoved(visibleStoryCount);
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
