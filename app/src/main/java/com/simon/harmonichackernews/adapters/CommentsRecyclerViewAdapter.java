package com.simon.harmonichackernews.adapters;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.PollOption;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.StoryPreviewImageLoader;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.CollectedReferenceLinks;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryPreviewImageMemoryCache;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import coil.Coil;
import coil.request.ImageRequest;
import coil.target.ImageViewTarget;
import coil.target.Target;
import coil.util.CoilUtils;

public class CommentsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Comment> comments;
    private HeaderClickListener headerClickListener;
    private CommentClickListener commentClickListener;
    private CommentClickListener commentLongClickListener;
    private HeaderActionClickListener headerActionClickListener;
    private HeaderBackgroundColorListener headerBackgroundColorListener;
    private RetryListener retryListener;
    private final Map<Integer, Comment> commentsById = new HashMap<>();
    private final Map<Integer, Boolean> commentVisibilityById = new HashMap<>();
    private int commentLookupSize = -1;
    private Map<String, String> userTagsByUser = new HashMap<>();
    private String userTagsJson;

    public LinearLayout bottomSheet;
    public FragmentManager fragmentManager;
    public Story story;
    public boolean loadingFailed = false;
    public boolean loadingFailedServerError = false;
    public boolean commentsLoaded = false;
    public boolean collapseParent;
    public boolean showThumbnail;
    public String previewImageMode;
    public boolean tintHeaderUsingPreview;
    public String paletteTintMode;
    public String commentDepthIndicatorMode;
    public boolean showNavigationBar;
    public boolean showInvert;
    public String faviconProvider;
    public boolean integratedWebview;
    public boolean showTopLevelDepthIndicator;
    public boolean swapLongPressTap;
    public boolean cardStyle;
    public boolean collectReferenceLinks;
    private boolean commentsByOpFilterActive = false;
    public String username;
    public float preferredTextSize;
    private final boolean isTablet;
    public String theme;
    public String font;
    public boolean showUpdate = false;
    public long lastRefreshed = 0;
    public int spacerHeight = 0;
    private int navbarHeight = 0;
    private int highlightedCommentId = -1;
    public boolean disableCommentATagClick = false;
    private RequestSummaryCallback summaryCallback;
    private boolean storyFavoriteLoading = false;
    private boolean storyFavoriteLoadingTarget = false;
    private boolean storyVoteLoading = false;
    private boolean storyVoteLoadingTarget = false;
    private float headerSlideOffset = 1f;
    @Nullable
    private HeaderViewHolder boundHeaderViewHolder;
    @Nullable
    private StoryPreviewImageLoader.PreviewImageRequest headerPreviewImageUrlRequest;

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_COMMENT = 1;
    public static final int TYPE_COLLAPSED = 2;
    public static final int TYPE_COMMENT_CARD = 3;
    private static final float COMMENT_HIGHLIGHT_ALPHA_DARK = 0.14f;
    private static final float COMMENT_HIGHLIGHT_ALPHA_LIGHT = 0.08f;
    private static final int REFRESH_PROMPT_HIDE_DURATION_MS = 200;
    private static final int HEADER_ACTION_ICON_SWAP_OUT_DURATION_MS = 90;
    private static final int HEADER_ACTION_ICON_SWAP_IN_DURATION_MS = 150;
    private static final float HEADER_ACTION_ICON_SWAP_MIN_SCALE = 0.72f;
    private static final int HEADER_FAVORITE_LOADING_SIZE_DP = 28;
    private static final int REFERENCE_LINK_MIN_HEIGHT_DP = 38;
    private static final int REFERENCE_LINK_CORNER_RADIUS_DP = 6;
    private static final int REFERENCE_LINK_ICON_SIZE_DP = 17;
    private static final int HEADER_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP = 176;
    private static final int HEADER_PREVIEW_IMAGE_MIN_HEIGHT_DP = 164;
    private static final int HEADER_PREVIEW_IMAGE_MAX_HEIGHT_DP = 208;
    private static final int HEADER_PREVIEW_IMAGE_TOP_PADDING_REDUCTION_DP = 4;
    private static final int HEADER_FAVICON_TINT_SIZE_DP = 64;

    public final static int FLAG_ACTION_CLICK_USER = 0;
    public final static int FLAG_ACTION_CLICK_COMMENT = 1;
    public final static int FLAG_ACTION_CLICK_VOTE = 2;
    public final static int FLAG_ACTION_CLICK_FAVORITE = 3;
    public final static int FLAG_ACTION_CLICK_SHARE = 4;
    public final static int FLAG_ACTION_CLICK_MORE = 5;
    public final static int FLAG_ACTION_CLICK_REFRESH = -2;
    public final static int FLAG_ACTION_CLICK_EXPAND = -3;
    public final static int FLAG_ACTION_CLICK_BROWSER = -4;
    public final static int FLAG_ACTION_CLICK_INVERT = -5;
    public final static int FLAG_ACTION_CLICK_RESET_OP_FILTER = -6;

    public CommentsRecyclerViewAdapter(boolean useIntegratedWebview,
                                       LinearLayout sheet,
                                       FragmentManager fm,
                                       List<Comment> items,
                                       Story masterItem,
                                       boolean shouldCollapseParent,
                                       boolean shouldShowThumbnail,
                                       String preferredPreviewImageMode,
                                       boolean shouldTintHeaderUsingPreview,
                                       String preferredPaletteTintMode,
                                       String usernameParam,
                                       float prefTextSize,
                                       String prefCommentDepthIndicatorMode,
                                       boolean shouldShowNavigationBar,
                                       String prefFont,
                                       boolean shouldShowInvert,
                                       boolean shouldShowTopLevelDepthIndicator,
                                       String prefTheme,
                                        boolean tablet,
                                        String favProvider,
                                        boolean shouldSwapLongPressTap,
                                        boolean shouldUseCardStyle,
                                        boolean shouldCollectReferenceLinks,
                                        CommentsRecyclerViewAdapter.RequestSummaryCallback requestSummaryCallback) {
        integratedWebview = useIntegratedWebview;
        bottomSheet = sheet;
        fragmentManager = fm;
        comments = items;
        story = masterItem;
        collapseParent = shouldCollapseParent;
        showThumbnail = shouldShowThumbnail;
        previewImageMode = preferredPreviewImageMode;
        tintHeaderUsingPreview = shouldTintHeaderUsingPreview;
        paletteTintMode = SettingsUtils.getPaletteTintConfigKey(preferredPaletteTintMode);
        commentDepthIndicatorMode = CommentDepthIndicatorUtils.sanitizeMode(prefCommentDepthIndicatorMode);
        showNavigationBar = shouldShowNavigationBar;
        username = usernameParam;
        preferredTextSize = prefTextSize;
        font = prefFont;
        showInvert = shouldShowInvert;
        showTopLevelDepthIndicator = shouldShowTopLevelDepthIndicator;
        theme = prefTheme;
        isTablet = tablet;
        faviconProvider = favProvider;
        swapLongPressTap = shouldSwapLongPressTap;
        cardStyle = shouldUseCardStyle;
        collectReferenceLinks = shouldCollectReferenceLinks;
        summaryCallback = requestSummaryCallback;
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (isCommentViewType(viewType)) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(viewType == TYPE_COMMENT_CARD ? R.layout.comments_item_card : R.layout.comments_item, parent, false);
            return new ItemViewHolder(view);
        } else if (viewType == TYPE_COLLAPSED) {
            return new RecyclerView.ViewHolder(new View(parent.getContext())) {
            };
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.comments_header, parent, false);

            return new HeaderViewHolder(view);
        }
    }

    @SuppressLint({"RecyclerView", "SetTextI18n"})
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Context ctx = holder.itemView.getContext();
        if (holder instanceof HeaderViewHolder) {
            final HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
            boundHeaderViewHolder = headerViewHolder;
            setHeaderSlideOffset(getCurrentHeaderSlideOffset());

            if (story.isLink && story.url != null) {
                try {
                    headerViewHolder.urlView.setText("(" + Utils.getDomainName(story.url) + ")");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            headerViewHolder.headerView.setClickable(story.isLink);
            headerViewHolder.linkImage.setVisibility(story.isLink && !story.isComment ? View.VISIBLE : GONE);
            bindHeaderPreviewImage(headerViewHolder);
            bindHeaderTint(headerViewHolder);
            bindStoryText(headerViewHolder);

            LinkPreviewHeaderBinder.bind(ctx, headerViewHolder, story, integratedWebview, bottomSheet);

            if (story.pollOptionArrayList != null) {
                headerViewHolder.pollLayout.setVisibility(View.VISIBLE);
                headerViewHolder.pollLayout.removeAllViews();
                for (int i = 0; i < story.pollOptionArrayList.size(); i++) {
                    PollOption pollOption = story.pollOptionArrayList.get(i);
                    if (pollOption.loaded) {
                        MaterialButton materialButton = new MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                        materialButton.setText(pollOption.text + " (" + pollOption.points + (pollOption.points == 1 ? " point" : " points") + ")");

                        materialButton.setTextColor(Utils.getColorViaAttr(ctx, R.attr.storyColorNormal));

                        materialButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                UserActions.votePollOption(ctx, pollOption.id, fragmentManager);
                            }
                        });
                        headerViewHolder.pollLayout.addView(materialButton);
                    } else {
                        LoadingIndicator loadingIndicator = new LoadingIndicator(ctx);
                        int indicatorSize = Utils.pxFromDpInt(ctx.getResources(), 42);
                        loadingIndicator.setIndicatorSize(indicatorSize);
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, indicatorSize);
                        headerViewHolder.pollLayout.addView(loadingIndicator, params);
                    }
                }
            } else {
                headerViewHolder.pollLayout.setVisibility(GONE);
            }

            if (!TextUtils.isEmpty(story.pdfTitle)) {
                SpannableStringBuilder sb = new SpannableStringBuilder(story.pdfTitle + " ");
                ImageSpan imageSpan = new ImageSpan(ctx, R.drawable.ic_action_pdf_large);
                sb.setSpan(imageSpan, story.pdfTitle.length(), story.pdfTitle.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                headerViewHolder.titleView.setText(sb);
            } else {
                headerViewHolder.titleView.setText(story.title);
            }

            if (story.loaded) {
                headerViewHolder.metaVotes.setText(String.valueOf(story.score));
                headerViewHolder.metaComments.setText(String.valueOf(story.descendants));
                headerViewHolder.metaTime.setText(story.getTimeFormatted());
                String tag = getCachedUserTag(ctx, story.by);
                headerViewHolder.metaBy.setText(TextUtils.isEmpty(tag) ? story.by : story.by + " (" + tag + ")");
                headerViewHolder.metaVotes.setContentDescription(pointCountDescription(story.score));
                headerViewHolder.metaComments.setContentDescription(commentCountDescription(story.descendants));
                headerViewHolder.metaTime.setContentDescription("Posted " + story.getTimeFormatted());
                headerViewHolder.metaBy.setContentDescription("Submitted by " + story.by);
                headerViewHolder.userButton.setContentDescription("Open submitter " + story.by);
            }

            headerViewHolder.metaContainer.setVisibility(story.loaded ? View.VISIBLE : GONE);
            headerViewHolder.urlView.setVisibility(story.isLink ? View.VISIBLE : GONE);
            headerViewHolder.metaVotes.setVisibility(story.isComment ? GONE : View.VISIBLE);
            headerViewHolder.metaVotesIcon.setVisibility(story.isComment ? GONE : View.VISIBLE);

            FontUtils.setCommentsHeaderMetaTypefaces(
                    headerViewHolder.urlView,
                    headerViewHolder.metaVotes,
                    headerViewHolder.metaComments,
                    headerViewHolder.metaTime,
                    headerViewHolder.metaBy);

            FontUtils.setCommentsHeaderTitleTypeface(headerViewHolder.titleView);
            FontUtils.setCommentTextTypeface(headerViewHolder.textView, preferredTextSize);

            if (loadingFailed) {
                headerViewHolder.loadingIndicator.setVisibility(GONE);
                headerViewHolder.emptyView.setVisibility(GONE);
            } else {
                if (commentsLoaded) {
                    headerViewHolder.loadingIndicator.setVisibility(GONE);
                    headerViewHolder.emptyView.setVisibility(story.descendants > 0 || comments.size() > 1 ? GONE : View.VISIBLE);
                } else {
                    headerViewHolder.loadingIndicator.setVisibility(View.VISIBLE);
                    headerViewHolder.emptyView.setVisibility(GONE);
                }
            }

            headerViewHolder.spacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, spacerHeight));

            headerViewHolder.setRefreshButtonVisible(showUpdate);

            int actionContainerPadding = Math.round(headerViewHolder.actionsContainer.getResources().getDimension(R.dimen.comments_header_action_padding));
            headerViewHolder.actionsContainer.setPadding(actionContainerPadding, 0, actionContainerPadding, 0);

            headerViewHolder.favicon.setVisibility(showThumbnail ? View.VISIBLE : GONE);
            headerViewHolder.linkInfoContainer.setVisibility(!story.isComment && story.isLink ? View.VISIBLE : View.GONE);

            if (showThumbnail && !TextUtils.isEmpty(story.url)) {
                FaviconLoader.loadFavicon(story.url, headerViewHolder.favicon, ctx, faviconProvider);
            }

            headerViewHolder.summarizeButtonParent.setVisibility(story.isLink && Utils.canProvideSummary(ctx) ? View.VISIBLE : View.GONE);

            headerViewHolder.summaryContainer.setVisibility(TextUtils.isEmpty(story.summary) ? View.GONE : VISIBLE);

            headerViewHolder.summaryLoadingContainer.setVisibility(TextUtils.isEmpty(story.summary) ? View.VISIBLE : View.GONE);
            headerViewHolder.summaryContentContainer.setVisibility(TextUtils.isEmpty(story.summary) ? View.GONE : View.VISIBLE);
            headerViewHolder.summary.setText(story.summary);
            headerViewHolder.summary.setText("• Introduces algebraic effects: resumable “exceptions” that serve as a single primitive for custom control flow.\n" +
                    "• Shows how to build generators, coroutines, async/await, exception handling, and schedulers purely as libraries.\n" +
                    "• Demonstrates modeling services (databases, logging, randomness, allocation) as effects you can swap or mock via handlers.\n" +
                    "• Highlights cleaner APIs through implicit state/context threading and enforced purity/security (can IO, can Print) enabling deterministic replay/debugging.");

            headerViewHolder.summarizeButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    headerViewHolder.summaryContainer.setVisibility(View.VISIBLE);

                    headerViewHolder.summaryLoadingContainer.setVisibility(TextUtils.isEmpty(story.summary) ? View.VISIBLE : View.GONE);
                    headerViewHolder.summaryContentContainer.setVisibility(TextUtils.isEmpty(story.summary) ? View.GONE : View.VISIBLE);

                    summaryCallback.onRequest(new Runnable() {
                        @Override
                        public void run() {
                            notifyItemChanged(0);

                            headerViewHolder.summaryLoadingContainer.setVisibility(TextUtils.isEmpty(story.summary) ? View.VISIBLE : View.GONE);
                            headerViewHolder.summaryContentContainer.setVisibility(TextUtils.isEmpty(story.summary) ? View.GONE : View.VISIBLE);
                            headerViewHolder.summary.setText(story.summary);
                        }
                    });
                }
            });

            boolean isUpvoted = Utils.isUpvoted(ctx, story.id, story.isComment);
            if (storyVoteLoading) {
                showHeaderVoteLoading(headerViewHolder.voteButton, storyVoteLoadingTarget, false);
            } else {
                showHeaderVoteButton(headerViewHolder.voteButton, isUpvoted, false);
            }

            boolean bookmarksEnabled = SettingsUtils.shouldUseBookmarks(ctx);
            if (bookmarksEnabled) {
                boolean isBookmarked = Utils.isBookmarked(ctx, story.id);
                bindStoryBookmarkButton(headerViewHolder.bookmarkButton, isBookmarked);
            } else {
                headerViewHolder.bookmarkButton.setOnClickListener(null);
            }

            boolean isFavorited = Utils.isFavorited(ctx, story.id);
            if (storyFavoriteLoading) {
                showHeaderFavoriteLoading(headerViewHolder.favoriteButton, storyFavoriteLoadingTarget, false);
            } else {
                showHeaderFavoriteButton(headerViewHolder.favoriteButton, isFavorited, false);
            }

            headerViewHolder.emptyViewText.setText(story.isComment ? "No replies" : "No comments");
            headerViewHolder.opFilterContainer.setVisibility(commentsByOpFilterActive ? VISIBLE : GONE);
            headerViewHolder.bookmarkButtonParent.setVisibility(bookmarksEnabled ? VISIBLE : GONE);
            headerViewHolder.commentButtonParent.setVisibility(Utils.timeInSecondsMoreThanTwoWeeksAgo(story.time) ? GONE : View.VISIBLE);
            headerViewHolder.commentButton.setContentDescription(story.isComment ? "Reply to comment" : "Reply to post");

            headerViewHolder.loadingFailed.setVisibility(loadingFailed ? VISIBLE : GONE);
            if (loadingFailed) {
                if (!Utils.isNetworkAvailable(ctx)) {
                    headerViewHolder.loadingFailedText.setText("No internet connection");
                } else {
                    headerViewHolder.loadingFailedText.setText("Loading failed");
                }
            }

            headerViewHolder.serverErrorText.setVisibility(loadingFailedServerError ? VISIBLE : GONE);
            headerViewHolder.openInBrowserButton.setVisibility(loadingFailedServerError ? VISIBLE : GONE);

        } else if (holder instanceof ItemViewHolder) {
            final ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            Comment comment = comments.get(position);
            itemViewHolder.comment = comment;
            applyCommentHighlight(itemViewHolder, comment.id == highlightedCommentId);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            int width = ctx.getResources().getDisplayMetrics().widthPixels;
            if (isTablet) {
                width /= 2;
            }

            int horizontalStartMargin = Math.min(
                    Utils.pxFromDpInt(ctx.getResources(), 16 + 12 * comment.depth),
                    Math.round(((float) width) * 0.6f));
            int topMargin = Utils.pxFromDpInt(
                    ctx.getResources(),
                    cardStyle ? (comment.depth > 0 && !collapseParent ? 6 : 4) : (comment.depth > 0 && !collapseParent ? 10 : 6));
            int bottomMargin = Utils.pxFromDpInt(ctx.getResources(), cardStyle ? 4 : 6);
            int cardShadowPadding = cardStyle ? ctx.getResources().getDimensionPixelSize(R.dimen.comment_card_shadow_padding) : 0;

            // 16 is base padding, then add depth-based indentation for child comments.
            params.setMargins(
                    Math.max(0, horizontalStartMargin - cardShadowPadding),
                    Math.max(0, topMargin - cardShadowPadding),
                    Math.max(0, Utils.pxFromDpInt(ctx.getResources(), 16) - cardShadowPadding),
                    Math.max(0, bottomMargin - cardShadowPadding));
            itemViewHolder.itemView.setLayoutParams(params);

            if (!CommentDepthIndicatorUtils.shouldShowIndicators(commentDepthIndicatorMode)) {
                itemViewHolder.commentIndentIndicator.setVisibility(cardStyle ? View.INVISIBLE : GONE);
            } else if (comment.depth == 0 && !showTopLevelDepthIndicator) {
                itemViewHolder.commentIndentIndicator.setVisibility(cardStyle ? View.INVISIBLE : GONE);
            } else {
                itemViewHolder.commentIndentIndicator.setVisibility(View.VISIBLE);
                int index = (comment.depth + (showTopLevelDepthIndicator ? 0 : -1)) % 7;

                itemViewHolder.commentIndentIndicator.setBackgroundResource(
                        CommentDepthIndicatorUtils.getColorResource(ctx, commentDepthIndicatorMode, theme, index));
            }

            bindCommentText(itemViewHolder, comment);

            itemViewHolder.commentByTime.setText(comment.getTimeFormatted());

            boolean byOp = story.by.equals(comment.by);
            boolean byUser = false;
            if (!TextUtils.isEmpty(username)) {
                byUser = comment.by.equals(username);
            }

            String cTag = getCachedUserTag(ctx, comment.by);
            String displayName = comment.by;
            if (!TextUtils.isEmpty(cTag)) {
                displayName += " (" + cTag + ")";
            }
            if (byOp) {
                displayName += " (OP)";
            }
            itemViewHolder.commentBy.setText(displayName);
            itemViewHolder.commentBy.setContentDescription("Comment by " + comment.by);
            itemViewHolder.commentByTime.setContentDescription("Posted " + comment.getTimeFormatted());

            if (byUser) {
                itemViewHolder.commentBy.setTextColor(MaterialColors.getColor(itemViewHolder.commentBy, R.attr.selfCommentColor));
            } else if (byOp) {
                itemViewHolder.commentBy.setTextColor(MaterialColors.getColor(itemViewHolder.commentBy, R.attr.opCommentColor));
            } else {
                itemViewHolder.commentBy.setTextColor(MaterialColors.getColor(itemViewHolder.commentBy, R.attr.storyColorDisabled));
            }

            itemViewHolder.commentBy.setTypeface(FontUtils.activeBold);
            itemViewHolder.commentByTime.setTypeface(FontUtils.activeRegular);
            if (collapseParent) {
                itemViewHolder.commentHiddenText.setTypeface(FontUtils.activeRegular);
            }

            boolean commentTextCollapsed = !comment.expanded && collapseParent;
            itemViewHolder.commentBody.setVisibility((itemViewHolder.commentBodyHasText && !commentTextCollapsed) ? View.VISIBLE : GONE);
            itemViewHolder.referenceLinksContainer.setVisibility((itemViewHolder.referenceLinksVisible && !commentTextCollapsed) ? View.VISIBLE : GONE);
            itemViewHolder.commentHiddenText.setVisibility((!comment.expanded && collapseParent) ? View.VISIBLE : GONE);

            int subCommentCount = getIndexOfLastChild(comment.depth, position) - position;
            itemViewHolder.commentHiddenCount.animate().cancel();
            itemViewHolder.commentHiddenCount.setAlpha(1f);
            if (subCommentCount > 0) {
                itemViewHolder.commentHiddenCount.setText("+" + subCommentCount);
                itemViewHolder.commentHiddenCount.setVisibility(comment.expanded ? View.INVISIBLE : View.VISIBLE);
                itemViewHolder.commentHiddenCount.setContentDescription(
                        comment.expanded ? null : hiddenReplyCountDescription(subCommentCount));
            } else {
                itemViewHolder.commentHiddenCount.setVisibility(GONE);
                itemViewHolder.commentHiddenCount.setContentDescription(null);
            }
        }
    }

    public void setHeaderSlideOffset(float slideOffset) {
        float sanitizedSlideOffset = sanitizeHeaderSlideOffset(slideOffset);
        if (!integratedWebview) {
            sanitizedSlideOffset = 1f;
        }
        headerSlideOffset = sanitizedSlideOffset;
        if (boundHeaderViewHolder != null) {
            applyHeaderBackground(boundHeaderViewHolder);
        }
    }

    private float getCurrentHeaderSlideOffset() {
        if (bottomSheet == null) {
            return 1f;
        }

        BottomSheetBehavior<LinearLayout> behavior = BottomSheetBehavior.from(bottomSheet);
        int state = behavior.getState();
        if (state == BottomSheetBehavior.STATE_COLLAPSED) {
            return 0f;
        }
        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            return 1f;
        }
        return behavior.calculateSlideOffset();
    }

    private float sanitizeHeaderSlideOffset(float slideOffset) {
        if (Float.isNaN(slideOffset)) {
            return 1f;
        }
        return Math.max(0f, Math.min(1f, slideOffset));
    }

    public boolean updateBoundHeaderStoryViews() {
        if (boundHeaderViewHolder == null
                || !ViewCompat.isAttachedToWindow(boundHeaderViewHolder.itemView)) {
            return false;
        }

        bindHeaderStoryViews(boundHeaderViewHolder, boundHeaderViewHolder.itemView.getContext());
        return true;
    }

    private void bindHeaderStoryViews(HeaderViewHolder headerViewHolder, Context ctx) {
        if (story.isLink && story.url != null) {
            try {
                headerViewHolder.urlView.setText("(" + Utils.getDomainName(story.url) + ")");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        headerViewHolder.headerView.setClickable(story.isLink);
        headerViewHolder.linkImage.setVisibility(story.isLink && !story.isComment ? View.VISIBLE : GONE);
        bindStoryText(headerViewHolder);
        LinkPreviewHeaderBinder.bind(ctx, headerViewHolder, story, integratedWebview, bottomSheet);
        bindHeaderTitle(headerViewHolder, ctx);
        bindHeaderMeta(headerViewHolder, ctx);
        bindHeaderLoadingState(headerViewHolder, ctx);

        headerViewHolder.favicon.setVisibility(showThumbnail ? View.VISIBLE : GONE);
        headerViewHolder.linkInfoContainer.setVisibility(!story.isComment && story.isLink ? View.VISIBLE : View.GONE);
        if (showThumbnail && !TextUtils.isEmpty(story.url)) {
            FaviconLoader.loadFavicon(story.url, headerViewHolder.favicon, ctx, faviconProvider);
        }
        headerViewHolder.summarizeButtonParent.setVisibility(story.isLink && Utils.canProvideSummary(ctx) ? View.VISIBLE : View.GONE);
        headerViewHolder.emptyViewText.setText(story.isComment ? "No replies" : "No comments");
        headerViewHolder.commentButtonParent.setVisibility(Utils.timeInSecondsMoreThanTwoWeeksAgo(story.time) ? GONE : View.VISIBLE);
        headerViewHolder.commentButton.setContentDescription(story.isComment ? "Reply to comment" : "Reply to post");
        bindHeaderTint(headerViewHolder);
    }

    private void bindHeaderTitle(HeaderViewHolder headerViewHolder, Context ctx) {
        if (!TextUtils.isEmpty(story.pdfTitle)) {
            SpannableStringBuilder sb = new SpannableStringBuilder(story.pdfTitle + " ");
            ImageSpan imageSpan = new ImageSpan(ctx, R.drawable.ic_action_pdf_large);
            sb.setSpan(imageSpan, story.pdfTitle.length(), story.pdfTitle.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            headerViewHolder.titleView.setText(sb);
        } else {
            headerViewHolder.titleView.setText(story.title);
        }
    }

    private void bindHeaderMeta(HeaderViewHolder headerViewHolder, Context ctx) {
        if (story.loaded) {
            headerViewHolder.metaVotes.setText(String.valueOf(story.score));
            headerViewHolder.metaComments.setText(String.valueOf(story.descendants));
            headerViewHolder.metaTime.setText(story.getTimeFormatted());
            String tag = getCachedUserTag(ctx, story.by);
            headerViewHolder.metaBy.setText(TextUtils.isEmpty(tag) ? story.by : story.by + " (" + tag + ")");
            headerViewHolder.metaVotes.setContentDescription(pointCountDescription(story.score));
            headerViewHolder.metaComments.setContentDescription(commentCountDescription(story.descendants));
            headerViewHolder.metaTime.setContentDescription("Posted " + story.getTimeFormatted());
            headerViewHolder.metaBy.setContentDescription("Submitted by " + story.by);
            headerViewHolder.userButton.setContentDescription("Open submitter " + story.by);
        }

        headerViewHolder.metaContainer.setVisibility(story.loaded ? View.VISIBLE : GONE);
        headerViewHolder.urlView.setVisibility(story.isLink ? View.VISIBLE : GONE);
        headerViewHolder.metaVotes.setVisibility(story.isComment ? GONE : View.VISIBLE);
        headerViewHolder.metaVotesIcon.setVisibility(story.isComment ? GONE : View.VISIBLE);
    }

    private void bindHeaderLoadingState(HeaderViewHolder headerViewHolder, Context ctx) {
        if (loadingFailed) {
            headerViewHolder.loadingIndicator.setVisibility(GONE);
            headerViewHolder.emptyView.setVisibility(GONE);
        } else if (commentsLoaded) {
            headerViewHolder.loadingIndicator.setVisibility(GONE);
            headerViewHolder.emptyView.setVisibility(story.descendants > 0 || comments.size() > 1 ? GONE : View.VISIBLE);
        } else {
            headerViewHolder.loadingIndicator.setVisibility(View.VISIBLE);
            headerViewHolder.emptyView.setVisibility(GONE);
        }

        headerViewHolder.loadingFailed.setVisibility(loadingFailed ? VISIBLE : GONE);
        if (loadingFailed) {
            if (!Utils.isNetworkAvailable(ctx)) {
                headerViewHolder.loadingFailedText.setText("No internet connection");
            } else {
                headerViewHolder.loadingFailedText.setText("Loading failed");
            }
        }

        headerViewHolder.serverErrorText.setVisibility(loadingFailedServerError ? VISIBLE : GONE);
        headerViewHolder.openInBrowserButton.setVisibility(loadingFailedServerError ? VISIBLE : GONE);
    }

    private void bindHeaderPreviewImage(final HeaderViewHolder headerViewHolder) {
        if (!shouldLoadHeaderPreviewImage(story)) {
            resetHeaderPreviewImage(headerViewHolder);
            return;
        }

        if (!TextUtils.isEmpty(story.previewImageUrl)) {
            loadHeaderPreviewImage(headerViewHolder, story);
            return;
        }

        if (story.previewImageUrlLoaded) {
            resetHeaderPreviewImage(headerViewHolder);
            return;
        }

        resetHeaderPreviewImage(headerViewHolder);
        loadHeaderPreviewImageUrl(headerViewHolder.itemView.getContext(), story);
    }

    private boolean shouldLoadHeaderPreviewImage(Story story) {
        return !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)
                && story != null
                && story.loaded
                && !story.loadingFailed
                && !story.isComment
                && !TextUtils.isEmpty(story.url)
                && !story.previewImageLoadFailed;
    }

    private void loadHeaderPreviewImageUrl(@Nullable Context context, Story story) {
        if (story.previewImageUrlLoaded || story.previewImageUrlLoading) {
            return;
        }

        story.previewImageUrlLoading = true;
        Context appContext = context == null ? null : context.getApplicationContext();
        headerPreviewImageUrlRequest = StoryPreviewImageLoader.loadPreviewImageUrl(appContext, story.id, story.url, imageUrl -> {
            headerPreviewImageUrlRequest = null;
            story.previewImageUrlLoading = false;
            story.previewImageUrlLoaded = true;
            if (TextUtils.isEmpty(imageUrl)) {
                story.previewImageLoadFailed = true;
                PreviewImageTintUtils.clearStoryPreviewImageTintColor(story);
                notifyHeaderChanged();
                return;
            }

            setPreviewImageUrl(story, imageUrl);
            story.previewImageLoadFailed = false;
            notifyHeaderChanged();
        });
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        cancelHeaderPreviewImageUrlRequest();
        super.onDetachedFromRecyclerView(recyclerView);
    }

    private void cancelHeaderPreviewImageUrlRequest() {
        if (headerPreviewImageUrlRequest != null) {
            headerPreviewImageUrlRequest.cancel();
            headerPreviewImageUrlRequest = null;
        }
        if (story != null) {
            story.previewImageUrlLoading = false;
        }
    }

    private void loadHeaderPreviewImage(final HeaderViewHolder headerViewHolder, final Story story) {
        final ImageView previewImage = headerViewHolder.previewImage;
        final String imageUrl = story.previewImageUrl;
        if (previewImage == null || TextUtils.isEmpty(imageUrl)) {
            return;
        }

        if (imageUrl.equals(previewImage.getTag()) && previewImage.getDrawable() != null) {
            updatePreviewImageTintColor(previewImage.getContext(), story, imageUrl, previewImage.getDrawable());
            if (!shouldTintHeader() || hasCurrentPreviewTint(previewImage.getContext(), story)) {
                setHeaderPreviewImageVisibility(headerViewHolder, VISIBLE);
                return;
            }
        }

        CoilUtils.dispose(previewImage);
        previewImage.animate().cancel();
        previewImage.clearAnimation();
        previewImage.setTag(imageUrl);
        boolean hasMemoryPreviewImage = bindCachedHeaderPreviewImage(headerViewHolder, story, imageUrl);

        ImageRequest request = new ImageRequest.Builder(previewImage.getContext())
                .data(imageUrl)
                .setHeader("User-Agent", NetworkComponent.USER_AGENT)
                .size(getHeaderPreviewImageWidth(previewImage), getHeaderPreviewImageHeight(previewImage))
                .allowHardware(!shouldTintHeader())
                .target(new ImageViewTarget(previewImage) {
                    @Override
                    public void onStart(Drawable placeholder) {
                        story.previewImageLoading = true;
                        if (!hasMemoryPreviewImage && isCurrentHeaderPreviewTarget(previewImage, imageUrl)) {
                            super.onStart((Drawable) null);
                            setHeaderPreviewImageVisibility(headerViewHolder, View.INVISIBLE);
                        }
                    }

                    @Override
                    public void onError(Drawable error) {
                        story.previewImageLoading = false;
                        if (isCurrentHeaderPreviewTarget(previewImage, imageUrl)) {
                            if (hasMemoryPreviewImage || hasCurrentPreviewTint(previewImage.getContext(), story)) {
                                story.previewImageLoadFailed = false;
                                bindHeaderTint(headerViewHolder);
                                return;
                            }
                            story.previewImageLoadFailed = true;
                            PreviewImageTintUtils.clearStoryPreviewImageTintColor(story);
                            super.onError(null);
                            resetHeaderPreviewImage(headerViewHolder);
                            bindHeaderTint(headerViewHolder);
                        }
                    }

                    @Override
                    public void onSuccess(Drawable result) {
                        story.previewImageLoading = false;
                        story.previewImageLoaded = true;
                        story.previewImageLoadFailed = false;
                        StoryPreviewImageMemoryCache.put(story.id, imageUrl, result);
                        updatePreviewImageTintColor(previewImage.getContext(), story, imageUrl, result);
                        if (isCurrentHeaderPreviewTarget(previewImage, imageUrl)) {
                            updateHeaderPreviewImageLayout(headerViewHolder, result);
                            super.onSuccess(result);
                            setHeaderPreviewImageVisibility(headerViewHolder, VISIBLE);
                            bindHeaderTint(headerViewHolder);
                        }
                    }
                })
                .build();

        Coil.imageLoader(previewImage.getContext()).enqueue(request);
    }

    private boolean bindCachedHeaderPreviewImage(
            HeaderViewHolder headerViewHolder,
            Story story,
            String imageUrl) {
        Drawable cachedPreviewImage = StoryPreviewImageMemoryCache.get(story.id, imageUrl);
        if (cachedPreviewImage == null) {
            return false;
        }

        headerViewHolder.previewImage.setImageDrawable(cachedPreviewImage);
        updateHeaderPreviewImageLayout(headerViewHolder, cachedPreviewImage);
        setHeaderPreviewImageVisibility(headerViewHolder, VISIBLE);
        story.previewImageLoaded = true;
        updatePreviewImageTintColor(headerViewHolder.previewImage.getContext(), story, imageUrl, cachedPreviewImage);
        bindHeaderTint(headerViewHolder);
        return true;
    }

    private int getHeaderPreviewImageWidth(ImageView previewImage) {
        int viewWidth = previewImage.getWidth();
        return viewWidth > 0 ? viewWidth : previewImage.getResources().getDisplayMetrics().widthPixels;
    }

    private int getHeaderPreviewImageHeight(ImageView previewImage) {
        int viewHeight = previewImage.getHeight();
        int maxHeight = Utils.pxFromDpInt(previewImage.getResources(), HEADER_PREVIEW_IMAGE_MAX_HEIGHT_DP);
        return viewHeight > 0 ? Math.max(viewHeight, maxHeight) : maxHeight;
    }

    private void updateHeaderPreviewImageLayout(HeaderViewHolder headerViewHolder, Drawable drawable) {
        if (headerViewHolder.previewImage == null || drawable == null) {
            return;
        }

        int width = headerViewHolder.previewImage.getWidth();
        if (width <= 0) {
            width = getHeaderPreviewImageWidth(headerViewHolder.previewImage);
        }

        int targetHeight = Utils.pxFromDpInt(
                headerViewHolder.previewImage.getResources(),
                HEADER_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP);
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (width > 0 && intrinsicWidth > 0 && intrinsicHeight > 0) {
            targetHeight = Math.round((float) width * intrinsicHeight / intrinsicWidth);
        }

        int minHeight = Utils.pxFromDpInt(
                headerViewHolder.previewImage.getResources(),
                HEADER_PREVIEW_IMAGE_MIN_HEIGHT_DP);
        int maxHeight = Utils.pxFromDpInt(
                headerViewHolder.previewImage.getResources(),
                HEADER_PREVIEW_IMAGE_MAX_HEIGHT_DP);
        targetHeight = Math.max(minHeight, Math.min(maxHeight, targetHeight));

        ViewGroup.LayoutParams layoutParams = headerViewHolder.previewImage.getLayoutParams();
        if (layoutParams != null && layoutParams.height != targetHeight) {
            layoutParams.height = targetHeight;
            headerViewHolder.previewImage.setLayoutParams(layoutParams);
        }
    }

    private void setHeaderPreviewImageVisibility(HeaderViewHolder headerViewHolder, int visibility) {
        if (headerViewHolder.previewImage == null) {
            return;
        }

        applyHeaderPreviewImagePadding(headerViewHolder, visibility != GONE);
        if (headerViewHolder.previewImage.getVisibility() != visibility) {
            headerViewHolder.previewImage.setVisibility(visibility);
            headerViewHolder.itemView.requestLayout();
        }
    }

    private void applyHeaderPreviewImagePadding(HeaderViewHolder headerViewHolder, boolean imageVisible) {
        int topPadding = headerViewHolder.headerBasePaddingTop;
        if (imageVisible) {
            topPadding = Math.max(
                    0,
                    topPadding - Utils.pxFromDpInt(
                            headerViewHolder.headerView.getResources(),
                            HEADER_PREVIEW_IMAGE_TOP_PADDING_REDUCTION_DP));
        }

        if (headerViewHolder.headerView.getPaddingTop() != topPadding) {
            headerViewHolder.headerView.setPadding(
                    headerViewHolder.headerView.getPaddingLeft(),
                    topPadding,
                    headerViewHolder.headerView.getPaddingRight(),
                    headerViewHolder.headerView.getPaddingBottom());
        }
    }

    private void resetHeaderPreviewImage(HeaderViewHolder headerViewHolder) {
        ImageView previewImage = headerViewHolder.previewImage;
        if (previewImage == null) {
            return;
        }

        applyHeaderPreviewImagePadding(headerViewHolder, false);
        CoilUtils.dispose(previewImage);
        previewImage.animate().cancel();
        previewImage.clearAnimation();
        previewImage.setTag(null);
        previewImage.setAlpha(1f);
        previewImage.setImageDrawable(null);
        previewImage.setVisibility(GONE);
    }

    private static boolean isCurrentHeaderPreviewTarget(ImageView previewImage, String imageUrl) {
        return imageUrl.equals(previewImage.getTag());
    }

    private void bindHeaderTint(HeaderViewHolder headerViewHolder) {
        applyHeaderBackground(headerViewHolder);
        if (shouldUseHeaderFaviconTint(story)) {
            loadHeaderFaviconTintColor(headerViewHolder.itemView.getContext(), story, headerViewHolder);
        }
    }

    private void applyHeaderBackground(HeaderViewHolder headerViewHolder) {
        if (headerViewHolder == null) {
            return;
        }

        int normalColor = getNormalHeaderBackgroundColor(headerViewHolder.itemView);
        int previewTintBaseColor = getPreviewTintBaseColor(headerViewHolder.itemView);
        int targetColor = getHeaderTintColor(story, normalColor, previewTintBaseColor);
        int color = ColorUtils.blendARGB(normalColor, targetColor, headerSlideOffset);
        headerViewHolder.itemView.setBackgroundColor(normalColor);
        headerViewHolder.spacer.setBackgroundColor(color);
        headerViewHolder.sheetHandleContainer.setBackgroundColor(color);
        headerViewHolder.sheetButtonsContainer.setBackgroundColor(color);
        headerViewHolder.headerView.setBackgroundColor(color);
        headerViewHolder.summaryContainer.setBackgroundColor(color);
        headerViewHolder.actionsContainer.setBackgroundColor(color);
        if (headerBackgroundColorListener != null) {
            headerBackgroundColorListener.onHeaderBackgroundColorChanged(color);
        }
        applyHeaderBottomTransition(headerViewHolder, normalColor, color, previewTintBaseColor);
    }

    private int getHeaderTintColor(Story story, int normalColor, int previewTintBaseColor) {
        if (shouldUseHeaderPreviewTint(story, previewTintBaseColor)) {
            return story.previewImageTintColor;
        }
        if (shouldUseHeaderFaviconTint(story)
                && story.faviconTintColorLoaded
                && isFaviconTintColorCurrent(story)) {
            return story.faviconTintColor;
        }
        return normalColor;
    }

    private void applyHeaderBottomTransition(
            HeaderViewHolder headerViewHolder,
            int normalColor,
            int headerColor,
            int previewTintBaseColor) {
        boolean showTintFade = hasHeaderTint(story, previewTintBaseColor);
        headerViewHolder.divider.setVisibility(showTintFade ? GONE : VISIBLE);
        headerViewHolder.tintFade.setVisibility(showTintFade ? VISIBLE : GONE);
        if (!showTintFade) {
            headerViewHolder.tintFade.setBackground(null);
            return;
        }

        GradientDrawable fade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{headerColor, normalColor});
        headerViewHolder.tintFade.setBackground(fade);
    }

    private int getNormalHeaderBackgroundColor(View view) {
        return ContextCompat.getColor(view.getContext(), ThemeUtils.getBackgroundColorResource(view.getContext()));
    }

    private int getPreviewTintBaseColor(View view) {
        return MaterialColors.getColor(
                view,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
    }

    private int getPreviewTintBaseColor(Context context) {
        return MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                Color.TRANSPARENT);
    }

    private void updatePreviewImageTintColor(Context context, Story story, Drawable drawable) {
        updatePreviewImageTintColor(
                context,
                story,
                story == null ? null : story.previewImageUrl,
                drawable);
    }

    private void updatePreviewImageTintColor(Context context, Story story, String imageUrl, Drawable drawable) {
        if (!shouldTintHeader() || context == null || story == null || drawable == null) {
            return;
        }

        PreviewImageTintUtils.updateStoryPreviewImageTintColor(
                story,
                imageUrl,
                drawable,
                getPreviewTintBaseColor(context),
                paletteTintMode);
    }

    private void loadHeaderFaviconTintColor(Context context, Story story, @Nullable HeaderViewHolder headerViewHolder) {
        if (context == null || !shouldUseHeaderFaviconTint(story)) {
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

        if (story.faviconTintColorLoaded && !isFaviconTintColorCurrent(story)) {
            story.faviconTintColorLoaded = false;
            story.faviconTintColorLoading = false;
            story.faviconTintColorLoadFailed = false;
        }

        if (story.faviconTintColorLoaded || story.faviconTintColorLoading || story.faviconTintColorLoadFailed) {
            return;
        }

        story.faviconTintColorLoading = true;
        int faviconSize = Utils.pxFromDpInt(context.getResources(), HEADER_FAVICON_TINT_SIZE_DP);
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
                    }

                    @Override
                    public void onSuccess(Drawable result) {
                        if (!TextUtils.equals(story.faviconTintSourceUrl, faviconUrl)) {
                            return;
                        }

                        story.faviconTintColorLoading = false;
                        updateFaviconTintColor(context, story, result);
                        if (headerViewHolder == boundHeaderViewHolder) {
                            applyHeaderBackground(headerViewHolder);
                        } else {
                            notifyHeaderChanged();
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
            story.faviconTintColor = PreviewImageTintUtils.calculateCardTint(context, drawable, paletteTintMode);
            story.faviconTintColorLoaded = true;
            story.faviconTintMode = SettingsUtils.getPaletteTintConfigKey(paletteTintMode);
            story.faviconTintColorLoadFailed = false;
        } catch (RuntimeException e) {
            story.faviconTintColorLoaded = false;
            story.faviconTintColorLoadFailed = true;
        }
    }

    private boolean shouldUseHeaderPreviewTint(Story story, int baseColor) {
        PreviewImageTintUtils.syncStoryPreviewImageTintColorFromCache(story, baseColor, paletteTintMode);
        return shouldTintHeader()
                && story != null
                && !SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)
                && !story.previewImageLoadFailed
                && PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(story, baseColor, paletteTintMode);
    }

    private boolean hasHeaderTint(Story story, int baseColor) {
        return shouldUseHeaderPreviewTint(story, baseColor)
                || (shouldUseHeaderFaviconTint(story)
                && story.faviconTintColorLoaded
                && isFaviconTintColorCurrent(story));
    }

    private boolean shouldUseHeaderFaviconTint(Story story) {
        return shouldTintHeader()
                && story != null
                && story.loaded
                && !story.loadingFailed
                && !story.isComment
                && !TextUtils.isEmpty(story.url)
                && (SettingsUtils.STORY_PREVIEW_IMAGE_OFF.equals(previewImageMode)
                || hasNoPreviewImageUrl(story));
    }

    private boolean hasNoPreviewImageUrl(Story story) {
        return story != null
                && story.previewImageUrlLoaded
                && TextUtils.isEmpty(story.previewImageUrl);
    }

    private boolean isFaviconTintColorCurrent(Story story) {
        return story != null
                && story.faviconTintColorLoaded
                && TextUtils.equals(story.faviconTintSourceUrl, getFaviconTintSourceUrl(story))
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

        int baseColor = getPreviewTintBaseColor(context);
        return PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(story, baseColor, paletteTintMode)
                || PreviewImageTintUtils.syncStoryPreviewImageTintColorFromCache(story, baseColor, paletteTintMode);
    }

    private boolean shouldTintHeader() {
        return tintHeaderUsingPreview;
    }

    private void notifyHeaderChanged() {
        notifyItemChanged(0);
    }

    private void bindStoryText(HeaderViewHolder headerViewHolder) {
        if (TextUtils.isEmpty(story.text)) {
            headerViewHolder.textView.setVisibility(GONE);
            bindReferenceLinks(headerViewHolder.referenceLinksContainer, null);
            return;
        }

        CollectedReferenceLinks.Result referenceLinks = null;
        if (collectReferenceLinks) {
            referenceLinks = getStoryReferenceLinks();
        }

        boolean hasCollectedLinks = referenceLinks != null && referenceLinks.hasLinks();
        String bodyHtml = hasCollectedLinks ? referenceLinks.getBodyHtml() : story.text;
        headerViewHolder.textView.setVisibility(TextUtils.isEmpty(bodyHtml) ? GONE : View.VISIBLE);

        if (!TextUtils.isEmpty(bodyHtml)) {
            if (hasCollectedLinks) {
                if (story.collectedReferenceLinksSpannedText != null) {
                    headerViewHolder.textView.setHtml(story.collectedReferenceLinksSpannedText);
                } else {
                    headerViewHolder.textView.setHtml(bodyHtml);
                    story.collectedReferenceLinksSpannedText = (Spanned) headerViewHolder.textView.getText();
                }
            } else if (story.spannedText != null) {
                headerViewHolder.textView.setHtml(story.spannedText);
            } else {
                headerViewHolder.textView.setHtml(story.text);
                story.spannedText = (Spanned) headerViewHolder.textView.getText();
            }
        }

        bindReferenceLinks(headerViewHolder.referenceLinksContainer, referenceLinks);
    }

    private void bindCommentText(ItemViewHolder itemViewHolder, Comment comment) {
        if (TextUtils.isEmpty(comment.text)) {
            itemViewHolder.commentBodyHasText = false;
            itemViewHolder.commentBody.setText("");
            itemViewHolder.referenceLinksVisible = bindReferenceLinks(itemViewHolder.referenceLinksContainer, null);
            return;
        }

        CollectedReferenceLinks.Result referenceLinks = null;
        if (collectReferenceLinks) {
            referenceLinks = getCommentReferenceLinks(comment);
        }

        boolean hasCollectedLinks = referenceLinks != null && referenceLinks.hasLinks();
        String bodyHtml = hasCollectedLinks ? referenceLinks.getBodyHtml() : Utils.expandShortenedAnchorText(comment.text);
        itemViewHolder.commentBodyHasText = !TextUtils.isEmpty(bodyHtml);

        if (itemViewHolder.commentBodyHasText) {
            if (hasCollectedLinks) {
                if (comment.collectedReferenceLinksSpannedText != null) {
                    itemViewHolder.commentBody.setHtml(comment.collectedReferenceLinksSpannedText);
                } else {
                    itemViewHolder.commentBody.setHtml(bodyHtml);
                    comment.collectedReferenceLinksSpannedText = (Spanned) itemViewHolder.commentBody.getText();
                }
            } else if (comment.spannedText != null) {
                itemViewHolder.commentBody.setHtml(comment.spannedText);
            } else {
                itemViewHolder.commentBody.setHtml(bodyHtml);
                comment.spannedText = (Spanned) itemViewHolder.commentBody.getText();
            }
            FontUtils.setCommentTextTypeface(itemViewHolder.commentBody, preferredTextSize);
        } else {
            itemViewHolder.commentBody.setText("");
        }

        if (collapseParent) {
            itemViewHolder.commentHiddenText.setText(" • " + Html.fromHtml(comment.text.substring(0, Math.min(120, comment.text.length()))));
        }

        itemViewHolder.referenceLinksVisible = bindReferenceLinks(itemViewHolder.referenceLinksContainer, referenceLinks);
    }

    private CollectedReferenceLinks.Result getStoryReferenceLinks() {
        if (!TextUtils.equals(story.collectedReferenceLinksSource, story.text)
                || story.collectedReferenceLinks == null) {
            story.collectedReferenceLinksSource = story.text;
            story.collectedReferenceLinks = CollectedReferenceLinks.parse(story.text);
            story.collectedReferenceLinksSpannedText = null;
        }
        return story.collectedReferenceLinks;
    }

    private CollectedReferenceLinks.Result getCommentReferenceLinks(Comment comment) {
        if (!TextUtils.equals(comment.collectedReferenceLinksSource, comment.text)
                || comment.collectedReferenceLinks == null) {
            comment.collectedReferenceLinksSource = comment.text;
            comment.collectedReferenceLinks = CollectedReferenceLinks.parse(Utils.expandShortenedAnchorText(comment.text));
            comment.collectedReferenceLinksSpannedText = null;
        }
        return comment.collectedReferenceLinks;
    }

    private boolean bindReferenceLinks(LinearLayout container, @Nullable CollectedReferenceLinks.Result referenceLinks) {
        if (!collectReferenceLinks || referenceLinks == null || !referenceLinks.hasLinks()) {
            container.removeAllViews();
            container.setVisibility(GONE);
            return false;
        }

        container.removeAllViews();
        container.setVisibility(View.VISIBLE);
        for (CollectedReferenceLinks.ReferenceLink link : referenceLinks.getLinks()) {
            container.addView(createReferenceLinkRow(container, link));
        }
        return true;
    }

    private View createReferenceLinkRow(LinearLayout container, CollectedReferenceLinks.ReferenceLink link) {
        Context ctx = container.getContext();

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setMinimumHeight(Utils.pxFromDpInt(ctx.getResources(), REFERENCE_LINK_MIN_HEIGHT_DP));
        row.setPadding(
                Utils.pxFromDpInt(ctx.getResources(), 8),
                Utils.pxFromDpInt(ctx.getResources(), 5),
                Utils.pxFromDpInt(ctx.getResources(), 8),
                Utils.pxFromDpInt(ctx.getResources(), 5));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setCornerRadius(Utils.pxFromDpInt(ctx.getResources(), REFERENCE_LINK_CORNER_RADIUS_DP));
        background.setStroke(
                Utils.pxFromDpInt(ctx.getResources(), 1),
                MaterialColors.getColor(container, R.attr.commentDividerColor));
        row.setBackground(background);

        TypedValue selectableBackground = new TypedValue();
        if (ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectableBackground, true)) {
            row.setForeground(ContextCompat.getDrawable(ctx, selectableBackground.resourceId));
        }

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = Utils.pxFromDpInt(ctx.getResources(), 4);
        row.setLayoutParams(rowParams);

        ImageView favicon = new ImageView(ctx);
        int iconSize = Utils.pxFromDpInt(ctx.getResources(), REFERENCE_LINK_ICON_SIZE_DP);
        LinearLayout.LayoutParams faviconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        faviconParams.rightMargin = Utils.pxFromDpInt(ctx.getResources(), 8);
        favicon.setLayoutParams(faviconParams);
        favicon.setImageResource(R.drawable.ic_action_web);
        favicon.setContentDescription(null);
        favicon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(favicon);

        if (link.hasNumber()) {
            TextView number = new TextView(ctx);
            number.setText("[" + link.getNumber() + "]");
            number.setTextColor(MaterialColors.getColor(container, R.attr.storyColorDisabled));
            number.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            FontUtils.setTypefaceForFont(number, font, true, 13);
            LinearLayout.LayoutParams numberParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            numberParams.rightMargin = Utils.pxFromDpInt(ctx.getResources(), 8);
            number.setLayoutParams(numberParams);
            row.addView(number);
        }

        TextView label = new TextView(ctx);
        label.setText(getReferenceLinkLabel(link));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextColor(MaterialColors.getColor(container, R.attr.storyColorNormal));
        FontUtils.setTypefaceForFont(label, font, false, Math.max(12f, preferredTextSize - 2f));
        label.setLayoutParams(new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f));
        row.addView(label);

        row.setContentDescription(getReferenceLinkContentDescription(link));
        row.setOnClickListener(v -> Utils.openLinkMaybeHN(v.getContext(), link.getUrl()));
        FaviconLoader.loadFavicon(link.getUrl(), favicon, ctx, faviconProvider);

        return row;
    }

    private String getReferenceLinkContentDescription(CollectedReferenceLinks.ReferenceLink link) {
        if (link.hasNumber()) {
            return "Open reference link " + link.getNumber() + ": " + getReferenceLinkLabel(link);
        }
        return "Open link: " + getReferenceLinkLabel(link);
    }

    private String getReferenceLinkLabel(CollectedReferenceLinks.ReferenceLink link) {
        String label = link.getLabel();
        if (TextUtils.isEmpty(label)) {
            return link.getUrl();
        }
        return label.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    public void setHighlightedCommentId(int commentId) {
        if (highlightedCommentId == commentId) {
            return;
        }

        int previousCommentId = highlightedCommentId;
        highlightedCommentId = commentId;

        notifyCommentChangedById(previousCommentId);
        notifyCommentChangedById(highlightedCommentId);
    }

    public void clearHighlightedCommentId(int commentId) {
        if (highlightedCommentId != commentId) {
            return;
        }

        highlightedCommentId = -1;
        notifyCommentChangedById(commentId);
    }

    private void notifyCommentChangedById(int commentId) {
        if (commentId == -1) {
            return;
        }

        for (int i = 1; i < comments.size(); i++) {
            if (comments.get(i).id == commentId) {
                if (isCommentViewType(getItemViewType(i))) {
                    notifyItemChanged(i);
                }
                return;
            }
        }
    }

    private void applyCommentHighlight(@NonNull ItemViewHolder itemViewHolder, boolean highlighted) {
        if (itemViewHolder.commentCard instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) itemViewHolder.commentCard;
            int baseColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.TRANSPARENT);
            int cardBackgroundColor = highlighted ? getCommentHighlightColor(card, baseColor) : baseColor;
            card.setCardBackgroundColor(cardBackgroundColor);
            itemViewHolder.itemView.setBackgroundColor(Color.TRANSPARENT);
        } else {
            int baseColor = MaterialColors.getColor(
                    itemViewHolder.itemView,
                    android.R.attr.colorBackground,
                    Color.TRANSPARENT);
            int highlightColor = getCommentHighlightColor(itemViewHolder.itemView, baseColor);
            itemViewHolder.itemView.setBackgroundColor(highlighted ? highlightColor : Color.TRANSPARENT);
        }
    }

    private int getCommentHighlightColor(@NonNull View view, int baseColor) {
        int overlayColor = MaterialColors.getColor(
                view,
                com.google.android.material.R.attr.colorOnSurface,
                Color.WHITE);
        float alpha = ColorUtils.calculateLuminance(baseColor) < 0.5
                ? COMMENT_HIGHLIGHT_ALPHA_DARK
                : COMMENT_HIGHLIGHT_ALPHA_LIGHT;
        return ColorUtils.blendARGB(baseColor, overlayColor, alpha);
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_HEADER;
        } else {
            return shouldShow(comments.get(position)) ? getCommentViewType() : TYPE_COLLAPSED;
        }
    }

    public int getCommentViewType() {
        return cardStyle ? TYPE_COMMENT_CARD : TYPE_COMMENT;
    }

    public static boolean isCommentViewType(int viewType) {
        return viewType == TYPE_COMMENT || viewType == TYPE_COMMENT_CARD;
    }

    private void bindStoryBookmarkButton(ImageButton button, boolean bookmarked) {
        setHeaderActionButtonIcon(
                button,
                bookmarked ? R.drawable.ic_action_bookmark_filled : R.drawable.ic_action_bookmark_border,
                bookmarked ? "Remove bookmark" : "Bookmark");
        resetHeaderActionButtonVisual(button);
        button.setEnabled(true);
        button.setClickable(true);
        button.setOnClickListener(v -> toggleStoryBookmark(button));
    }

    private void toggleStoryBookmark(ImageButton button) {
        if (story == null) {
            return;
        }

        Context ctx = button.getContext();
        boolean bookmarked = !Utils.isBookmarked(ctx, story.id);
        if (bookmarked) {
            Utils.addBookmark(ctx, story.id);
        } else {
            Utils.removeBookmark(ctx, story.id);
        }

        animateHeaderActionIconChange(
                button,
                bookmarked ? R.drawable.ic_action_bookmark_filled : R.drawable.ic_action_bookmark_border,
                bookmarked ? "Remove bookmark" : "Bookmark");
    }

    public void showStoryFavoriteLoading(@Nullable View actionView, boolean favorite) {
        storyFavoriteLoading = true;
        storyFavoriteLoadingTarget = favorite;
        ImageButton button = resolveStoryFavoriteButton(actionView);
        if (button != null) {
            showHeaderFavoriteLoading(button, favorite, true);
        }
    }

    public void showStoryFavoriteResult(@Nullable View actionView, boolean favorited) {
        storyFavoriteLoading = false;
        storyFavoriteLoadingTarget = favorited;
        ImageButton button = resolveStoryFavoriteButton(actionView);
        if (button != null) {
            showHeaderFavoriteButton(button, favorited, true);
        }
    }

    public void showStoryVoteLoading(@Nullable View actionView, boolean upvoted) {
        storyVoteLoading = true;
        storyVoteLoadingTarget = upvoted;
        ImageButton button = resolveStoryVoteButton(actionView);
        if (button != null) {
            showHeaderVoteLoading(button, upvoted, true);
        }
    }

    public void showStoryVoteResult(@Nullable View actionView, boolean upvoted) {
        storyVoteLoading = false;
        storyVoteLoadingTarget = upvoted;
        ImageButton button = resolveStoryVoteButton(actionView);
        if (button != null) {
            showHeaderVoteButton(button, upvoted, true);
        }
    }

    @Nullable
    private ImageButton resolveStoryFavoriteButton(@Nullable View actionView) {
        if (actionView instanceof ImageButton
                && actionView.getId() == R.id.comments_header_button_favorite
                && ViewCompat.isAttachedToWindow(actionView)) {
            return (ImageButton) actionView;
        }
        if (boundHeaderViewHolder != null
                && ViewCompat.isAttachedToWindow(boundHeaderViewHolder.favoriteButton)) {
            return boundHeaderViewHolder.favoriteButton;
        }
        return null;
    }

    @Nullable
    private ImageButton resolveStoryVoteButton(@Nullable View actionView) {
        if (actionView instanceof ImageButton
                && actionView.getId() == R.id.comments_header_button_vote
                && ViewCompat.isAttachedToWindow(actionView)) {
            return (ImageButton) actionView;
        }
        if (boundHeaderViewHolder != null
                && ViewCompat.isAttachedToWindow(boundHeaderViewHolder.voteButton)) {
            return boundHeaderViewHolder.voteButton;
        }
        return null;
    }

    private void showHeaderVoteLoading(ImageButton button, boolean upvoted, boolean animate) {
        String label = upvoted ? "Upvoting" : "Removing vote";
        showHeaderActionLoading(button, label, animate);
    }

    private void showHeaderVoteButton(ImageButton button, boolean upvoted, boolean animate) {
        showHeaderActionButton(
                button,
                upvoted ? R.drawable.ic_action_thumbs_up : R.drawable.ic_action_thumbs_up_outline,
                upvoted ? "Remove vote" : "Vote",
                animate);
    }

    private void showHeaderFavoriteLoading(ImageButton button, boolean favorite, boolean animate) {
        String label = favorite ? "Adding favorite" : "Removing favorite";
        showHeaderActionLoading(button, label, animate);
    }

    private void showHeaderActionLoading(ImageButton button, String label, boolean animate) {
        button.setEnabled(false);
        button.setClickable(false);
        button.setContentDescription(label);
        TooltipCompat.setTooltipText(button, label);

        RelativeLayout parent = getHeaderActionParent(button);
        if (parent == null) {
            return;
        }

        Runnable showLoading = () -> {
            button.setVisibility(View.INVISIBLE);
            resetHeaderActionButtonVisual(button);
            addHeaderFavoriteLoadingIndicator(parent, label, animate);
        };
        if (animate && button.getVisibility() == VISIBLE) {
            animateHeaderActionViewOut(button, showLoading);
        } else {
            showLoading.run();
        }
    }

    private void showHeaderFavoriteButton(ImageButton button, boolean favorited, boolean animate) {
        showHeaderActionButton(
                button,
                favorited ? R.drawable.ic_action_star_filled : R.drawable.ic_action_star,
                favorited ? "Remove favorite" : "Favorite",
                animate);
    }

    private void showHeaderActionButton(ImageButton button, int iconRes, String label, boolean animate) {
        RelativeLayout parent = getHeaderActionParent(button);
        View loadingIndicator = parent == null ? null : getHeaderActionLoadingIndicator(parent);

        Runnable showButton = () -> {
            if (parent != null) {
                removeHeaderActionLoadingIndicators(parent);
            }
            setHeaderActionButtonIcon(button, iconRes, label);
            button.setVisibility(VISIBLE);
            button.setEnabled(true);
            button.setClickable(true);
            if (animate) {
                animateHeaderActionViewIn(button, null);
            } else {
                resetHeaderActionButtonVisual(button);
            }
        };

        if (animate && loadingIndicator != null) {
            animateHeaderActionViewOut(loadingIndicator, showButton);
        } else {
            showButton.run();
        }
    }

    private void addHeaderFavoriteLoadingIndicator(RelativeLayout parent, String label, boolean animate) {
        removeHeaderActionLoadingIndicators(parent);
        LoadingIndicator loadingIndicator = new LoadingIndicator(parent.getContext());
        int indicatorSize = Utils.pxFromDpInt(parent.getResources(), HEADER_FAVORITE_LOADING_SIZE_DP);
        loadingIndicator.setIndicatorSize(indicatorSize);
        loadingIndicator.setContentDescription(label);
        loadingIndicator.setClickable(false);
        loadingIndicator.setFocusable(false);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(indicatorSize, indicatorSize);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        parent.addView(loadingIndicator, params);

        if (animate) {
            animateHeaderActionViewIn(loadingIndicator, null);
        }
    }

    @Nullable
    private RelativeLayout getHeaderActionParent(ImageButton button) {
        if (button.getParent() instanceof RelativeLayout) {
            return (RelativeLayout) button.getParent();
        }
        return null;
    }

    @Nullable
    private View getHeaderActionLoadingIndicator(RelativeLayout parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof LoadingIndicator) {
                return child;
            }
        }
        return null;
    }

    private void removeHeaderActionLoadingIndicators(RelativeLayout parent) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            if (parent.getChildAt(i) instanceof LoadingIndicator) {
                parent.removeViewAt(i);
            }
        }
    }

    private void animateHeaderActionIconChange(ImageButton button, int iconRes, String label) {
        button.setEnabled(false);
        animateHeaderActionViewOut(button, () -> {
            setHeaderActionButtonIcon(button, iconRes, label);
            animateHeaderActionViewIn(button, () -> button.setEnabled(true));
        });
    }

    private void setHeaderActionButtonIcon(ImageButton button, int iconRes, String label) {
        button.setImageResource(iconRes);
        button.setContentDescription(label);
        TooltipCompat.setTooltipText(button, label);
    }

    private void resetHeaderActionButtonVisual(View view) {
        view.animate().setListener(null);
        view.animate().cancel();
        view.setAlpha(1f);
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    private void animateHeaderActionViewOut(View view, Runnable afterOut) {
        view.animate().setListener(null);
        view.animate().cancel();
        if (!ViewCompat.isAttachedToWindow(view) || view.getVisibility() != VISIBLE) {
            view.setAlpha(0f);
            view.setScaleX(HEADER_ACTION_ICON_SWAP_MIN_SCALE);
            view.setScaleY(HEADER_ACTION_ICON_SWAP_MIN_SCALE);
            afterOut.run();
            return;
        }

        view.animate()
                .alpha(0f)
                .scaleX(HEADER_ACTION_ICON_SWAP_MIN_SCALE)
                .scaleY(HEADER_ACTION_ICON_SWAP_MIN_SCALE)
                .setDuration(HEADER_ACTION_ICON_SWAP_OUT_DURATION_MS)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.animate().setListener(null);
                        afterOut.run();
                    }
                })
                .start();
    }

    private void animateHeaderActionViewIn(View view, @Nullable Runnable afterIn) {
        view.animate().setListener(null);
        view.animate().cancel();
        view.setAlpha(0f);
        view.setScaleX(HEADER_ACTION_ICON_SWAP_MIN_SCALE);
        view.setScaleY(HEADER_ACTION_ICON_SWAP_MIN_SCALE);
        if (!ViewCompat.isAttachedToWindow(view)) {
            resetHeaderActionButtonVisual(view);
            if (afterIn != null) {
                afterIn.run();
            }
            return;
        }

        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(HEADER_ACTION_ICON_SWAP_IN_DURATION_MS)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.animate().setListener(null);
                        if (afterIn != null) {
                            afterIn.run();
                        }
                    }
                })
                .start();
    }

    public class ItemViewHolder extends RecyclerView.ViewHolder {
        public final HtmlTextView commentBody;
        public final TextView commentBy;
        public final TextView commentByTime;
        public final TextView commentHiddenCount;
        public final TextView commentHiddenText;
        public final View commentIndentIndicator;
        public final View commentCard;
        public final LinearLayout referenceLinksContainer;
        public boolean commentBodyHasText = true;
        public boolean referenceLinksVisible = false;
        public Comment comment;

        public ItemViewHolder(View view) {
            super(view);
            commentBody = view.findViewById(R.id.comment_body);
            commentBy = view.findViewById(R.id.comment_by);
            commentByTime = view.findViewById(R.id.comment_by_time);
            commentHiddenCount = view.findViewById(R.id.comment_hidden_count);
            commentHiddenText = view.findViewById(R.id.comment_hidden_short);
            commentIndentIndicator = view.findViewById(R.id.comment_indent_indicator);
            commentCard = view.findViewById(R.id.comment_card);
            referenceLinksContainer = view.findViewById(R.id.comment_reference_links_container);

            itemView.setOnLongClickListener(v -> {
                longPressed(comment, getAbsoluteAdapterPosition(), getCommentActionSourceView());
                return true;
            });

            commentBody.setOnLongClickListener(v -> {
                longPressed(comment, getAbsoluteAdapterPosition(), getCommentActionSourceView());
                return true;
            });

            itemView.setOnClickListener(v ->
                    tapped(comment, getAbsoluteAdapterPosition(), getCommentActionSourceView()));

            commentBody.setOnClickListener(v ->
                    tapped(comment, getAbsoluteAdapterPosition(), getCommentActionSourceView()));

            commentBody.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    if (disableCommentATagClick) return true;

                    Utils.openLinkMaybeHN(widget.getContext(), href);
                    return true;
                }
            });
        }

        private View getCommentActionSourceView() {
            return commentCard != null ? commentCard : itemView;
        }

        private void tapped(Comment comment, int pos, View v) {
            if (swapLongPressTap) {
                commentLongClickListener.onItemClick(comment, pos, v);
            } else {
                commentClickListener.onItemClick(comment, pos, v);
            }
        }

        private void longPressed(Comment comment, int pos, View v) {
            if (swapLongPressTap) {
                commentClickListener.onItemClick(comment, pos, v);
            } else {
                commentLongClickListener.onItemClick(comment, pos, v);
            }
        }
    }

    public class HeaderViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final TextView titleView;
        public final ImageView linkImage;
        public final LinearLayout metaContainer;
        public final TextView metaVotes;
        public final TextView metaComments;
        public final TextView metaTime;
        public final TextView metaBy;
        public final ImageView metaVotesIcon;
        public final TextView urlView;
        public final HtmlTextView textView;
        public final LinearLayout referenceLinksContainer;
        public final LinearLayout infoContainer;
        public final TextView arxivAbstract;
        public final LinearLayout githubContainer;
        public final LinearLayout gitLabContainer;
        public final LinearLayout arxivContainer;
        public final LinearLayout stackExchangeContainer;
        public final LinearLayout wikiContainer;
        public final LinearLayout nitterContainer;

        public final TextView infoHeader;
        public final LinearLayout linkPreviewLoadingContainer;
        public final LinearLayout emptyView;
        public final TextView emptyViewText;
        public final LoadingIndicator loadingIndicator;
        public final LinearLayout loadingFailed;
        public final TextView loadingFailedText;
        public final TextView serverErrorText;
        public final LinearLayout refreshPrompt;
        public final TextView lastRefreshedText;
        public final ExtendedFloatingActionButton refreshButton;
        public final ImageButton userButton;
        public final ImageButton commentButton;
        public final ImageButton voteButton;
        public final ImageButton favoriteButton;
        public final ImageButton bookmarkButton;
        public final ImageButton shareButton;
        public final ImageButton summarizeButton;
        public final RelativeLayout summarizeButtonParent;
        public final LinearLayout summaryContainer;
        public final LinearLayout summaryContentContainer;
        public final LinearLayout summaryLoadingContainer;
        public final TextView summary;
        public final TextView summaryTitle;
        public final ImageButton moreButton;
        public final RelativeLayout userButtonParent;
        public final RelativeLayout moreButtonParent;
        public final RelativeLayout commentButtonParent;
        public final RelativeLayout favoriteButtonParent;
        public final RelativeLayout bookmarkButtonParent;
        public final View divider;
        public final View tintFade;
        public final View spacer;
        public final TextView githubAbout;
        public final HtmlTextView githubWebsite;
        public final TextView githubLicense;
        public final TextView githubLanguage;
        public final TextView githubStars;
        public final TextView githubWatching;
        public final TextView githubForks;
        public final LinearLayout githubWebsiteContainer;
        public final LinearLayout githubLicenseContainer;
        public final LinearLayout githubLanguageContainer;

        public final TextView gitLabDescription;
        public final HtmlTextView gitLabWebsite;
        public final TextView gitLabVisibility;
        public final TextView gitLabLanguage;
        public final TextView gitLabStars;
        public final TextView gitLabForks;
        public final LinearLayout gitLabWebsiteContainer;
        public final LinearLayout gitLabVisibilityContainer;
        public final LinearLayout gitLabLanguageContainer;

        public final TextView stackExchangeTitle;
        public final TextView stackExchangeBy;
        public final TextView stackExchangeScore;
        public final TextView stackExchangeAnswers;
        public final TextView stackExchangeViews;
        public final TextView stackExchangeAnswerState;
        public final TextView stackExchangeAuthor;
        public final TextView stackExchangeTags;
        public final LinearLayout stackExchangeTagsContainer;

        public final TextView arxivBy;
        public final TextView arxivDate;
        public final TextView arxivSubjects;
        public final ImageView arxivByIcon;
        public final Button arxivDownloadButton;

        public final HtmlTextView wikiSummary;

        public final HtmlTextView nitterText;
        public final Button nitterButton;
        public final TextView nitterDate;
        public final TextView nitterReplyCount;
        public final TextView nitterReposts;
        public final TextView nitterLikes;
        public final ImageView nitterLikesImageView;
        public final ImageView nitterRetweetImageView;
        public final ImageView nitterReplyImageView;

        public final FrameLayout nitterMediaContainer;
        public final ImageView nitterImage;
        public final TextView nitterVideoLabel;

        public final ImageView favicon;
        public final ImageView previewImage;
        public final RelativeLayout sheetRefreshButton;
        public final RelativeLayout sheetExpandButton;
        public final RelativeLayout sheetBrowserButton;
        public final RelativeLayout sheetInvertButton;
        public final View sheetHandleContainer;
        public final LinearLayout sheetButtonsContainer;
        public final LinearLayout actionsContainer;
        public final LinearLayout linkInfoContainer;
        public final Button retryButton;
        public final Button openInBrowserButton;
        public final LinearLayout opFilterContainer;
        public final MaterialButton opFilterResetButton;
        public final LinearLayout pollLayout;
        public final LinearLayout headerView;
        public final int headerBasePaddingTop;
        private ValueAnimator refreshPromptHeightAnimator;

        public HeaderViewHolder(View view) {
            super(view);
            mView = view;
            titleView = view.findViewById(R.id.comments_header_title);
            linkImage = view.findViewById(R.id.comments_header_link_image);
            metaContainer = view.findViewById(R.id.comments_header_meta_container);
            metaVotes = view.findViewById(R.id.comments_header_meta_votes);
            metaComments = view.findViewById(R.id.comments_header_meta_comments);
            metaTime = view.findViewById(R.id.comments_header_meta_time);
            metaBy = view.findViewById(R.id.comments_header_meta_by);
            metaVotesIcon = view.findViewById(R.id.comments_header_meta_votes_icon);
            urlView = view.findViewById(R.id.comments_header_url);
            textView = view.findViewById(R.id.comments_header_text);
            referenceLinksContainer = view.findViewById(R.id.comments_header_reference_links_container);
            arxivAbstract = view.findViewById(R.id.comments_header_arxiv_abstract);
            infoContainer = view.findViewById(R.id.comments_header_info_container);
            infoHeader = view.findViewById(R.id.comments_header_info_header);
            linkPreviewLoadingContainer = view.findViewById(R.id.comments_header_link_preview_loading);
            emptyView = view.findViewById(R.id.comments_header_empty);
            emptyViewText = view.findViewById(R.id.comments_header_empty_text);
            headerView = view.findViewById(R.id.comments_header);
            headerBasePaddingTop = headerView.getPaddingTop();
            loadingIndicator = view.findViewById(R.id.comments_header_loading);
            loadingFailed = view.findViewById(R.id.comments_header_loading_failed);
            loadingFailedText = view.findViewById(R.id.comments_header_loading_failed_text);
            serverErrorText = view.findViewById(R.id.comments_header_server_error);
            refreshPrompt = view.findViewById(R.id.comments_header_refresh_prompt);
            lastRefreshedText = view.findViewById(R.id.comments_header_last_refreshed);
            refreshButton = view.findViewById(R.id.comments_header_refresh);
            favicon = view.findViewById(R.id.comments_header_favicon);
            previewImage = view.findViewById(R.id.comments_header_story_preview_image);
            linkInfoContainer = view.findViewById(R.id.comments_header_link_info_container);
            userButton = view.findViewById(R.id.comments_header_button_user);
            commentButton = view.findViewById(R.id.comments_header_button_comment);
            voteButton = view.findViewById(R.id.comments_header_button_vote);
            favoriteButton = view.findViewById(R.id.comments_header_button_favorite);
            bookmarkButton = view.findViewById(R.id.comments_header_button_bookmark);
            shareButton = view.findViewById(R.id.comments_header_button_share);
            summarizeButtonParent = view.findViewById(R.id.comments_header_button_summarize_parent);
            summarizeButton = view.findViewById(R.id.comments_header_button_summarize);
            summaryContainer = view.findViewById(R.id.comments_header_summary_container);
            summaryContentContainer = view.findViewById(R.id.comments_header_summary_content_container);
            summaryLoadingContainer = view.findViewById(R.id.comments_header_summary_loading);
            summary = view.findViewById(R.id.comments_header_summary);
            summaryTitle = view.findViewById(R.id.comments_header_summary_title);
            moreButton = view.findViewById(R.id.comments_header_button_more);
            userButtonParent = view.findViewById(R.id.comments_header_button_user_parent);
            moreButtonParent = view.findViewById(R.id.comments_header_button_more_parent);
            commentButtonParent = view.findViewById(R.id.comments_header_button_comment_parent);
            favoriteButtonParent = view.findViewById(R.id.comments_header_button_favorite_parent);
            bookmarkButtonParent = view.findViewById(R.id.comments_header_button_bookmark_parent);
            divider = view.findViewById(R.id.comments_header_divider);
            tintFade = view.findViewById(R.id.comments_header_tint_fade);
            retryButton = view.findViewById(R.id.comments_header_retry);
            openInBrowserButton = view.findViewById(R.id.comments_header_open_in_browser);
            opFilterContainer = view.findViewById(R.id.comments_header_op_filter);
            opFilterResetButton = view.findViewById(R.id.comments_header_op_filter_reset);
            pollLayout = view.findViewById(R.id.comments_header_poll_layout);
            sheetRefreshButton = view.findViewById(R.id.comments_sheet_layout_refresh);
            sheetExpandButton = view.findViewById(R.id.comments_sheet_layout_expand);
            sheetBrowserButton = view.findViewById(R.id.comments_sheet_layout_browser);
            sheetInvertButton = view.findViewById(R.id.comments_sheet_layout_invert);
            sheetHandleContainer = view.findViewById(R.id.comments_sheet_handle_container);
            sheetButtonsContainer = view.findViewById(R.id.comment_sheet_buttons_container);
            actionsContainer = view.findViewById(R.id.comments_header_actions_container);
            spacer = view.findViewById(R.id.comments_header_spacer);
            githubContainer = view.findViewById(R.id.comments_header_github_container);
            gitLabContainer = view.findViewById(R.id.comments_header_gitlab_container);
            arxivContainer = view.findViewById(R.id.comments_header_arxiv_container);
            stackExchangeContainer = view.findViewById(R.id.comments_header_stack_exchange_container);
            wikiContainer = view.findViewById(R.id.comments_header_wikipedia_container);
            wikiSummary = view.findViewById(R.id.comments_header_wikipedia_summary);
            githubAbout = view.findViewById(R.id.comments_header_github_about);
            githubWebsite = view.findViewById(R.id.comments_header_github_website);
            githubLicense = view.findViewById(R.id.comments_header_github_license);
            githubLanguage = view.findViewById(R.id.comments_header_github_language);
            githubStars = view.findViewById(R.id.comments_header_github_stars);
            githubWatching = view.findViewById(R.id.comments_header_github_watching);
            githubForks = view.findViewById(R.id.comments_header_github_forks);
            githubWebsiteContainer = view.findViewById(R.id.comments_header_github_website_container);
            githubLicenseContainer = view.findViewById(R.id.comments_header_github_license_container);
            githubLanguageContainer = view.findViewById(R.id.comments_header_github_language_container);
            gitLabDescription = view.findViewById(R.id.comments_header_gitlab_description);
            gitLabWebsite = view.findViewById(R.id.comments_header_gitlab_website);
            gitLabVisibility = view.findViewById(R.id.comments_header_gitlab_visibility);
            gitLabLanguage = view.findViewById(R.id.comments_header_gitlab_language);
            gitLabStars = view.findViewById(R.id.comments_header_gitlab_stars);
            gitLabForks = view.findViewById(R.id.comments_header_gitlab_forks);
            gitLabWebsiteContainer = view.findViewById(R.id.comments_header_gitlab_website_container);
            gitLabVisibilityContainer = view.findViewById(R.id.comments_header_gitlab_visibility_container);
            gitLabLanguageContainer = view.findViewById(R.id.comments_header_gitlab_language_container);
            stackExchangeTitle = view.findViewById(R.id.comments_header_stack_exchange_title);
            stackExchangeBy = view.findViewById(R.id.comments_header_stack_exchange_by);
            stackExchangeScore = view.findViewById(R.id.comments_header_stack_exchange_score);
            stackExchangeAnswers = view.findViewById(R.id.comments_header_stack_exchange_answers);
            stackExchangeViews = view.findViewById(R.id.comments_header_stack_exchange_views);
            stackExchangeAnswerState = view.findViewById(R.id.comments_header_stack_exchange_answer_state);
            stackExchangeAuthor = view.findViewById(R.id.comments_header_stack_exchange_author);
            stackExchangeTags = view.findViewById(R.id.comments_header_stack_exchange_tags);
            stackExchangeTagsContainer = view.findViewById(R.id.comments_header_stack_exchange_tags_container);
            arxivBy = view.findViewById(R.id.comments_header_arxiv_by);
            arxivDate = view.findViewById(R.id.comments_header_arxiv_date);
            arxivSubjects = view.findViewById(R.id.comments_header_arxiv_subjects);
            arxivByIcon = view.findViewById(R.id.comments_header_arxiv_by_icon);
            arxivDownloadButton = view.findViewById(R.id.comments_header_arxiv_download);

            final int SHEET_ITEM_HEIGHT = Utils.pxFromDpInt(view.getResources(), 56);

            nitterContainer = view.findViewById(R.id.comments_header_nitter_container);
            nitterText = view.findViewById(R.id.comments_header_nitter_text);
            nitterDate = view.findViewById(R.id.comments_header_nitter_date);
            nitterButton = view.findViewById(R.id.comments_header_nitter_button_open);
            nitterReplyCount = view.findViewById(R.id.comments_header_nitter_reply_count);
            nitterReposts = view.findViewById(R.id.comments_header_nitter_reposts);
            nitterLikes = view.findViewById(R.id.comments_header_nitter_likes);
            nitterLikesImageView = view.findViewById(R.id.comments_header_nitter_likes_image);
            nitterRetweetImageView = view.findViewById(R.id.comments_header_nitter_reposts_image);
            nitterReplyImageView = view.findViewById(R.id.comments_header_nitter_reply_image);
            nitterMediaContainer = view.findViewById(R.id.comments_header_nitter_media_container);
            nitterImage = view.findViewById(R.id.comments_header_nitter_image);
            nitterVideoLabel = view.findViewById(R.id.comments_header_nitter_video_label);

            retryButton.setOnClickListener((v) -> retryListener.onRetry());
            openInBrowserButton.setOnClickListener((v) -> retryListener.onOpenInBrowser());
            opFilterResetButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_RESET_OP_FILTER, view));

            refreshButton.setOnClickListener((v) -> {
                showUpdate = false;
                setRefreshButtonVisible(false);
                retryListener.onRetry();
            });

            arxivDownloadButton.setOnClickListener((v) -> {
                Utils.downloadPDF(v.getContext(), story.arxivInfo.getPDFURL());
            });

            userButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_USER, null));
            commentButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_COMMENT, null));
            voteButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_VOTE, v));
            favoriteButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_FAVORITE, v));
            shareButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_SHARE, v));
            moreButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_MORE, v));
            sheetRefreshButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_REFRESH, view));
            sheetExpandButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_EXPAND, view));
            sheetBrowserButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_BROWSER, view));
            sheetInvertButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_INVERT, view));

            TooltipCompat.setTooltipText(sheetRefreshButton, "Refresh");
            TooltipCompat.setTooltipText(sheetExpandButton, "Open comments");
            TooltipCompat.setTooltipText(sheetBrowserButton, "Open in browser");
            TooltipCompat.setTooltipText(sheetInvertButton, "Invert colors");

            TooltipCompat.setTooltipText(userButton, "User");
            TooltipCompat.setTooltipText(commentButton, "Comment");
            TooltipCompat.setTooltipText(voteButton, "Vote");
            TooltipCompat.setTooltipText(favoriteButton, "Favorite");
            TooltipCompat.setTooltipText(bookmarkButton, "Bookmark");
            TooltipCompat.setTooltipText(summarizeButton, "Summarize");
            TooltipCompat.setTooltipText(shareButton, "Share");
            TooltipCompat.setTooltipText(moreButton, "More");
            ViewCompat.setAccessibilityHeading(titleView, true);
            ViewCompat.setAccessibilityHeading(infoHeader, true);
            ViewCompat.setAccessibilityHeading(summaryTitle, true);
            ViewCompat.setAccessibilityHeading(emptyViewText, true);
            ViewCompat.setAccessibilityHeading(loadingFailedText, true);

            sheetRefreshButton.setContentDescription("Refresh comments");
            sheetExpandButton.setContentDescription("Open comments");
            sheetBrowserButton.setContentDescription("Open story in browser");
            sheetInvertButton.setContentDescription("Invert colors");

            if (!showInvert) {
                view.findViewById(R.id.comments_sheet_container_invert).setVisibility(GONE);
            }

            headerView.setOnClickListener(view1 -> headerClickListener.onItemClick(story));

            textView.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    Utils.openLinkMaybeHN(mView.getContext(), href);
                    return true;
                }
            });

            githubWebsite.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    Utils.launchCustomTab(view.getContext(), story.repoInfo.website);
                    return false;
                }
            });

            gitLabWebsite.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    Utils.launchCustomTab(view.getContext(), story.gitLabInfo.website);
                    return false;
                }
            });

            nitterText.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    if (TextUtils.isEmpty(href)) {
                        return false;
                    }
                    if (spannedText.startsWith("#") && href.startsWith("/search?q=")) {
                        Utils.launchCustomTab(widget.getContext(), "https://www.x.com/" + href);
                    } else if (spannedText.startsWith("@") && href.startsWith("/")) {
                        Utils.launchCustomTab(widget.getContext(), "https://www.x.com/" + href);
                    } else {
                        Utils.launchCustomTab(widget.getContext(), href);
                    }
                    return false;
                }
            });

            BottomSheetBehavior.from(bottomSheet).addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                    if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                        CommentsRecyclerViewAdapter.this.setHeaderSlideOffset(0f);
                    } else if (newState == BottomSheetBehavior.STATE_EXPANDED) {
                        CommentsRecyclerViewAdapter.this.setHeaderSlideOffset(1f);
                    }
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    // 0 when small, 1 when opened
                    setSheetButtonsContentAlpha((1 - slideOffset) * (1 - slideOffset) * (1 - slideOffset));
                    sheetButtonsContainer.getLayoutParams().height = Math.round((1 - slideOffset) * (SHEET_ITEM_HEIGHT + navbarHeight));
                    sheetButtonsContainer.requestLayout();

                    float headerAlpha = Math.min(1, slideOffset * slideOffset * 20);
                    actionsContainer.setAlpha(headerAlpha);
                    headerView.setAlpha(headerAlpha);
                    CommentsRecyclerViewAdapter.this.setHeaderSlideOffset(slideOffset);
                }
            });

            if (integratedWebview) {
                if (BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    setSheetButtonsContentAlpha(0f);
                    sheetButtonsContainer.getLayoutParams().height = 0;
                    sheetButtonsContainer.requestLayout();
                } else {
                    // Make sure we set correct height when starting on the WebView
                    setSheetButtonsContentAlpha(1f);
                    sheetButtonsContainer.getLayoutParams().height = SHEET_ITEM_HEIGHT + navbarHeight;
                    sheetButtonsContainer.requestLayout();
                }
            } else {
                sheetButtonsContainer.setVisibility(GONE);
                view.findViewById(R.id.comments_sheet_handle).setVisibility(GONE);
            }
        }

        private void setSheetButtonsContentAlpha(float alpha) {
            sheetButtonsContainer.setAlpha(1f);
            for (int i = 0; i < sheetButtonsContainer.getChildCount(); i++) {
                sheetButtonsContainer.getChildAt(i).setAlpha(alpha);
            }
        }

        private void setRefreshButtonVisible(boolean visible) {
            if (refreshPromptHeightAnimator != null) {
                refreshPromptHeightAnimator.cancel();
                refreshPromptHeightAnimator = null;
            }

            if (visible && lastRefreshed > 0) {
                lastRefreshedText.setVisibility(VISIBLE);
                lastRefreshedText.setText("Last refreshed: "
                        + android.text.format.DateFormat.getTimeFormat(lastRefreshedText.getContext())
                        .format(new java.util.Date(lastRefreshed)));
            } else if (visible) {
                lastRefreshedText.setVisibility(GONE);
            }

            if (visible) {
                refreshPrompt.animate().cancel();
                ViewGroup.LayoutParams layoutParams = refreshPrompt.getLayoutParams();
                if (layoutParams != null && layoutParams.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                    layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    refreshPrompt.setLayoutParams(layoutParams);
                }
                refreshPrompt.setAlpha(1f);
                refreshPrompt.setScaleX(1f);
                refreshPrompt.setScaleY(1f);
                if (refreshPrompt.getVisibility() != VISIBLE) {
                    refreshPrompt.setVisibility(VISIBLE);
                    refreshButton.show();
                }
            } else if (refreshPrompt.getVisibility() == VISIBLE) {
                int startHeight = refreshPrompt.getHeight();
                ViewGroup.LayoutParams layoutParams = refreshPrompt.getLayoutParams();
                if (layoutParams != null && startHeight > 0) {
                    layoutParams.height = startHeight;
                    refreshPrompt.setLayoutParams(layoutParams);

                    refreshPromptHeightAnimator = ValueAnimator.ofInt(startHeight, 0);
                    refreshPromptHeightAnimator.setDuration(REFRESH_PROMPT_HIDE_DURATION_MS);
                    refreshPromptHeightAnimator.addUpdateListener(animation -> {
                        ViewGroup.LayoutParams animatedParams = refreshPrompt.getLayoutParams();
                        if (animatedParams != null) {
                            animatedParams.height = (int) animation.getAnimatedValue();
                            refreshPrompt.setLayoutParams(animatedParams);
                        }
                    });
                    refreshPromptHeightAnimator.start();
                }

                refreshPrompt.animate()
                        .alpha(0f)
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .setDuration(REFRESH_PROMPT_HIDE_DURATION_MS)
                        .withEndAction(() -> {
                            if (refreshPromptHeightAnimator != null) {
                                refreshPromptHeightAnimator.cancel();
                                refreshPromptHeightAnimator = null;
                            }
                            refreshPrompt.setVisibility(GONE);
                            lastRefreshedText.setVisibility(GONE);
                            refreshPrompt.setAlpha(1f);
                            refreshPrompt.setScaleX(1f);
                            refreshPrompt.setScaleY(1f);
                            ViewGroup.LayoutParams endParams = refreshPrompt.getLayoutParams();
                            if (endParams != null) {
                                endParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                                refreshPrompt.setLayoutParams(endParams);
                            }
                        })
                        .start();
            }
        }

    }

    public void setOnHeaderClickListener(CommentsRecyclerViewAdapter.HeaderClickListener clickListener) {
        headerClickListener = clickListener;
    }

    public void setOnCommentClickListener(CommentClickListener clickListener) {
        commentClickListener = clickListener;
    }

    public void setOnCommentLongClickListener(CommentClickListener clickListener) {
        commentLongClickListener = clickListener;
    }

    public void setOnHeaderActionClickListener(HeaderActionClickListener clickListener) {
        headerActionClickListener = clickListener;
    }

    public void setHeaderBackgroundColorListener(HeaderBackgroundColorListener listener) {
        headerBackgroundColorListener = listener;
    }

    public void setRetryListener(RetryListener listener) {
        retryListener = listener;
    }

    public void setNavbarHeight(int navbarHeight) {
        if (this.navbarHeight != navbarHeight) {
            this.navbarHeight = navbarHeight;
            notifyItemChanged(0);
        }
    }

    public void setCommentsByOpFilterActive(boolean active) {
        if (commentsByOpFilterActive != active) {
            commentsByOpFilterActive = active;
            notifyItemChanged(0);
        }
    }

    public interface RetryListener {
        void onRetry();
        void onOpenInBrowser();
    }

    public interface HeaderActionClickListener {
        void onActionClicked(int flag, View view);
    }

    public interface HeaderBackgroundColorListener {
        void onHeaderBackgroundColorChanged(int color);
    }

    public interface HeaderClickListener {
        void onItemClick(Story story);
    }

    public interface CommentClickListener {
        void onItemClick(Comment comment, int pos, View view);
    }

    public int getIndexOfLastChild(int commentDepth, int pos) {
        int lastChildIndex = pos;
        for (int i = pos + 1; i < comments.size(); i++) {
            if (comments.get(i).depth > commentDepth) {
                lastChildIndex = i;
            } else {
                return lastChildIndex;
            }
        }
        return lastChildIndex;
    }

    private boolean shouldShow(Comment comment) {
        Boolean cachedVisibility = commentVisibilityById.get(comment.id);
        if (cachedVisibility != null) {
            return cachedVisibility;
        }

        ensureCommentLookup(comment);

        Comment current = comment;
        for (int i = 0; i < comments.size() && current.parent != -1; i++) {
            Comment parent = commentsById.get(current.parent);
            if (parent == null) {
                commentVisibilityById.put(comment.id, true);
                return true;
            }
            if (!parent.expanded) {
                commentVisibilityById.put(comment.id, false);
                return false;
            }
            current = parent;
        }
        commentVisibilityById.put(comment.id, true);
        return true;
    }

    private void ensureCommentLookup(Comment currentComment) {
        if (commentLookupSize == comments.size()
                && commentsById.get(currentComment.id) == currentComment) {
            return;
        }

        commentsById.clear();
        commentVisibilityById.clear();
        for (Comment comment : comments) {
            commentsById.put(comment.id, comment);
        }
        commentLookupSize = comments.size();
    }

    public void invalidateCommentLookup() {
        commentsById.clear();
        commentVisibilityById.clear();
        commentLookupSize = -1;
    }

    public void invalidateCommentVisibility() {
        commentVisibilityById.clear();
    }

    public void loadUserTags(Context ctx) {
        userTagsJson = SettingsUtils.readStringFromSharedPreferences(ctx, Utils.KEY_SHARED_PREFERENCES_USER_TAGS, "");
        userTagsByUser = Utils.getUserTags(ctx);
    }

    public boolean reloadUserTagsIfChanged(Context ctx) {
        String latestUserTagsJson = SettingsUtils.readStringFromSharedPreferences(ctx, Utils.KEY_SHARED_PREFERENCES_USER_TAGS, "");
        if (TextUtils.equals(userTagsJson, latestUserTagsJson)) {
            return false;
        }
        userTagsJson = latestUserTagsJson;
        userTagsByUser = Utils.getUserTags(ctx);
        return true;
    }

    private String getCachedUserTag(Context ctx, String username) {
        if (TextUtils.isEmpty(username)) {
            return "";
        }
        if (userTagsJson == null) {
            loadUserTags(ctx);
        }
        String tag = userTagsByUser.get(username.toLowerCase().trim());
        return tag == null ? "" : tag;
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

    private static String hiddenReplyCountDescription(int count) {
        if (count == 1) {
            return "1 hidden reply";
        }
        return count + " hidden replies";
    }

    public interface RequestSummaryCallback {
        //maybe take a callback as argument here
        void onRequest(Runnable onDone);
    }
}
