package com.simon.harmonichackernews.adapters;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
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
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

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
import com.simon.harmonichackernews.databinding.CommentsHeaderBinding;
import com.simon.harmonichackernews.databinding.CommentsHeaderLinkPreviewBinding;
import com.simon.harmonichackernews.databinding.CommentsItemBinding;
import com.simon.harmonichackernews.databinding.CommentsItemCardBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewArxivBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewGithubBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewGitlabBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewNitterBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewStackExchangeBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewWikipediaBinding;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.network.LinkSummaryLoader;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.StoryPreviewImageLoader;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.CollectedReferenceLinks;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.AccessibilityTextUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.PreviewImageLayoutUtils;
import com.simon.harmonichackernews.utils.PreviewImageFailureAnimator;
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryPreviewImageMemoryCache;
import com.simon.harmonichackernews.utils.TextSizeImageSpan;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.core.MarkwonTheme;
import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;
import org.sufficientlysecure.htmltextview.OnLongClickATagListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import coil.Coil;
import coil.request.ImageRequest;
import coil.target.ImageViewTarget;
import coil.target.Target;
import coil.util.CoilUtils;

public class CommentsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final Object HEADER_SUMMARY_UPDATE_PAYLOAD = new Object();
    private static final Object COMMENT_USER_TAG_UPDATE_PAYLOAD = new Object();

    private final List<Comment> comments;
    private HeaderClickListener headerClickListener;
    private CommentClickListener commentClickListener;
    private CommentClickListener commentLongClickListener;
    private ReferenceLinkLongClickListener referenceLinkLongClickListener;
    private HeaderPreviewLongClickListener headerPreviewLongClickListener;
    private boolean headerPreviewImageSuppressed;
    private HeaderActionClickListener headerActionClickListener;
    private HeaderBackgroundColorListener headerBackgroundColorListener;
    private RetryListener retryListener;
    private final Map<Integer, Comment> commentsById = new HashMap<>();
    private final Map<Integer, Boolean> commentVisibilityById = new HashMap<>();
    private final Map<Integer, String> hackerNewsReferenceTitlesByItemId = new HashMap<>();
    private final Set<String> requestedHackerNewsReferenceUrls = new HashSet<>();
    private final CommentSubtreeIndex commentSubtreeIndex = new CommentSubtreeIndex();
    private int commentLookupSize = -1;
    private Map<String, String> userTagsByUser = new HashMap<>();
    private String userTagsJson;
    @Nullable
    private FontUtils.Typography typography;

    public LinearLayout bottomSheet;
    public FragmentManager fragmentManager;
    public Story story;
    public boolean loadingFailed = false;
    public boolean loadingFailedServerError = false;
    public boolean commentsLoaded = false;
    public boolean commentsRefreshInProgress = false;
    public boolean collapseParent;
    public boolean showThumbnail;
    public boolean showHeaderPreviewImage;
    public boolean tintHeader;
    public String paletteTintMode;
    public String commentDepthIndicatorMode;
    public boolean showNavigationBar;
    public boolean showInvert;
    public String faviconProvider;
    public boolean integratedWebview;
    public boolean showTopLevelDepthIndicator;
    public boolean swapLongPressTap;
    public boolean cardStyle;
    public boolean cardBorder;
    public boolean showDividers;
    public boolean highlightCommentMeta;
    public boolean collectReferenceLinks;
    public boolean hasAccountDetails;
    public boolean canProvideSummary;
    private boolean readerModeAvailable = false;
    private boolean readerModeEnabled = false;
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
    private boolean storySummaryLoading = false;
    private boolean storySummaryReceivedProgress = false;
    @Nullable
    private ValueAnimator headerSummaryHeightAnimator;
    private boolean headerSummaryCompletionTransitionRunning = false;
    private boolean headerSummaryCompletionPending = false;
    private boolean headerSummaryCompletionScheduled = false;
    @Nullable
    private LayoutTransition headerSummaryLayoutTransition;
    @Nullable
    private ValueAnimator headerSummaryActionReflowAnimator;
    @Nullable
    private String displayedHeaderSummary;
    @Nullable
    private String pendingHeaderSummary;
    @Nullable
    private String headerSummaryAnimationSummary;
    @Nullable
    private CharSequence headerSummaryAnimationText;
    private float headerSlideOffset = 1f;
    private boolean initialCommentsRevealPending = false;
    private boolean storyHeaderLoading;
    private boolean storyHeaderRevealPending;
    @Nullable
    private Integer currentHeaderContentBackgroundColor;
    @Nullable
    private HeaderViewHolder boundHeaderViewHolder;
    @Nullable
    private StoryPreviewImageLoader.PreviewImageRequest headerPreviewImageUrlRequest;
    private long headerBindingGeneration;
    private int commentViewStyleGeneration;
    @Nullable
    private ValueAnimator storyHeaderRevealHeightAnimator;
    @Nullable
    private HeaderViewHolder storyHeaderRevealViewHolder;
    private final List<View> storyHeaderRevealViews = new ArrayList<>();
    private int headerContentInsetLeft;
    private int headerContentInsetRight;
    // Payloads can be dropped while an item is off-screen. Track every adapter update that can
    // affect position zero so a pooled header only takes the fast path when its state is current.
    private final RecyclerView.AdapterDataObserver headerBindingObserver =
            new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    headerBindingGeneration++;
                }

                @Override
                public void onItemRangeChanged(int positionStart, int itemCount) {
                    if (positionStart == 0 && itemCount > 0) {
                        headerBindingGeneration++;
                    }
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    if (positionStart == 0 && itemCount > 0) {
                        headerBindingGeneration++;
                    }
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    if (positionStart == 0 && itemCount > 0) {
                        headerBindingGeneration++;
                    }
                }

                @Override
                public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                    if (itemCount > 0 && (fromPosition == 0 || toPosition == 0)) {
                        headerBindingGeneration++;
                    }
                }
            };

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_COMMENT = 1;
    public static final int TYPE_COLLAPSED = 2;
    public static final int TYPE_COMMENT_CARD = 3;
    private static final float COMMENT_HIGHLIGHT_ALPHA_DARK = 0.14f;
    private static final float COMMENT_HIGHLIGHT_ALPHA_LIGHT = 0.08f;
    private static final int REFRESH_PROMPT_HIDE_DURATION_MS = 200;
    private static final int HEADER_STATUS_ROW_DURATION_MS = 220;
    private static final int HEADER_STATUS_ROW_FADE_OUT_DURATION_MS = 100;
    private static final int HEADER_STATUS_ROW_FADE_IN_DURATION_MS = 160;
    private static final int STORY_HEADER_REVEAL_DURATION_MS = 280;
    private static final int STORY_HEADER_CONTENT_FADE_IN_DURATION_MS = 180;
    private static final float HEADER_STATUS_ROW_HIDDEN_SCALE = 0.9f;
    private static final int HEADER_STATUS_ROW_HIDDEN_TRANSLATION_Y_DP = 12;
    private static final int INITIAL_LOADING_TOP_MARGIN_DP = 44;
    private static final int REFRESH_LOADING_TOP_MARGIN_DP = 16;
    private static final int HEADER_ACTION_ICON_SWAP_OUT_DURATION_MS = 90;
    private static final int HEADER_ACTION_ICON_SWAP_IN_DURATION_MS = 150;
    private static final float HEADER_ACTION_ICON_SWAP_MIN_SCALE = 0.72f;
    private static final int HEADER_READER_BUTTON_VISIBILITY_DURATION_MS = 160;
    private static final int HEADER_OP_FILTER_VISIBILITY_DURATION_MS = 220;
    private static final float HEADER_OP_FILTER_HIDDEN_SCALE = 0.96f;
    private static final int HEADER_OP_FILTER_HIDDEN_TRANSLATION_Y_DP = 8;
    private static final int HEADER_SUMMARY_HEIGHT_DURATION_MS = 220;
    private static final int HEADER_SUMMARY_COMPLETION_DURATION_MS = 180;
    private static final int HEADER_FAVORITE_LOADING_SIZE_DP = 28;
    private static final int REFERENCE_LINKS_CONTAINER_TOP_MARGIN_DP = 5;
    private static final int INTERLEAVED_REFERENCE_LINK_TOP_MARGIN_DP = 4;
    private static final int INTERLEAVED_REFERENCE_LINK_BOTTOM_MARGIN_DP = 2;
    private static final int INTERLEAVED_COMMENT_TEXT_TOP_MARGIN_DP = 5;
    private static final int HEADER_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP = 176;
    private static final int HEADER_PREVIEW_IMAGE_TOP_PADDING_REDUCTION_DP = 4;
    private static final int HEADER_FAVICON_TINT_SIZE_DP = 64;
    private static final int HEADER_BOTTOM_FADE_STOP_COUNT = 5;
    private static final int HEADER_BOTTOM_FADE_OVERLAP_DP = 10;

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
    public final static int FLAG_ACTION_CLICK_READER = -7;
    public final static int FLAG_ACTION_CLICK_COMMENTS_REFRESH = -8;

    public CommentsRecyclerViewAdapter(boolean useIntegratedWebview,
                                       LinearLayout sheet,
                                       FragmentManager fm,
                                       List<Comment> items,
                                       Story masterItem,
                                       String usernameParam,
                                       CommentDisplaySettings displaySettings,
                                       CommentsRecyclerViewAdapter.RequestSummaryCallback requestSummaryCallback) {
        integratedWebview = useIntegratedWebview;
        bottomSheet = sheet;
        fragmentManager = fm;
        comments = items;
        story = masterItem;
        username = usernameParam;
        isTablet = displaySettings.isTablet;
        displaySettings.applyToAdapter(this);
        summaryCallback = requestSummaryCallback;
        registerAdapterDataObserver(headerBindingObserver);
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (isCommentViewType(viewType)) {
            if (viewType == TYPE_COMMENT_CARD) {
                return new ItemViewHolder(CommentsItemCardBinding.inflate(inflater, parent, false));
            }
            return new ItemViewHolder(CommentsItemBinding.inflate(inflater, parent, false));
        } else if (viewType == TYPE_COLLAPSED) {
            return new RecyclerView.ViewHolder(new View(parent.getContext())) {
            };
        } else {
            return new HeaderViewHolder(CommentsHeaderBinding.inflate(inflater, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder,
                                 int position,
                                 @NonNull List<Object> payloads) {
        if (holder instanceof ItemViewHolder
                && payloads.size() == 1
                && payloads.get(0) == COMMENT_USER_TAG_UPDATE_PAYLOAD) {
            ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            Comment comment = comments.get(position);
            bindCommentAuthor(
                    itemViewHolder,
                    comment,
                    holder.itemView.getContext(),
                    TextUtils.equals(story.by, comment.by));
            return;
        }
        if (position == 0
                && holder instanceof HeaderViewHolder
                && payloads.size() == 1
                && payloads.get(0) == HEADER_SUMMARY_UPDATE_PAYLOAD) {
            HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
            bindHeaderSummary(headerViewHolder, holder.itemView.getContext());
            markHeaderBindingCurrent(headerViewHolder);
            return;
        }
        onBindViewHolder(holder, position);
    }

    @SuppressLint({"RecyclerView", "SetTextI18n"})
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Context ctx = holder.itemView.getContext();
        if (holder instanceof HeaderViewHolder) {
            final HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;
            boundHeaderViewHolder = headerViewHolder;
            setHeaderSlideOffset(getCurrentHeaderSlideOffset());
            if (isHeaderBindingCurrent(headerViewHolder)) {
                bindHeaderReattachmentState(headerViewHolder, ctx);
                schedulePendingStoryHeaderReveal(headerViewHolder);
                return;
            }

            if (story.isLink && story.url != null) {
                try {
                    headerViewHolder.urlView.setText(
                            "(" + story.getDisplayDomain(true) + ")");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            headerViewHolder.headerView.setClickable(story.isLink);
            headerViewHolder.linkImage.setVisibility(
                    !storyHeaderLoading && story.isLink && !story.isComment
                            ? View.VISIBLE : GONE);
            headerViewHolder.previewImage.setOnClickListener(v -> {
                if (headerClickListener != null) {
                    headerClickListener.onItemClick(story);
                }
            });
            headerViewHolder.previewImage.setOnLongClickListener(v -> {
                if (headerPreviewLongClickListener == null
                        || v.getVisibility() != VISIBLE
                        || headerViewHolder.previewImage.getDrawable() == null
                        || TextUtils.isEmpty(story.previewImageUrl)) {
                    return false;
                }
                headerPreviewLongClickListener.onLongClick(
                        story.previewImageUrl, headerViewHolder.previewImage);
                return true;
            });
            bindHeaderPreviewImage(headerViewHolder);
            bindHeaderTint(headerViewHolder);
            bindReaderModeButton(headerViewHolder);
            bindStoryText(headerViewHolder);

            if (!storyHeaderLoading) {
                LinkPreviewHeaderBinder.bind(ctx, headerViewHolder, story);
            }

            if (!storyHeaderLoading && story.pollOptionArrayList != null) {
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

            bindHeaderTitle(headerViewHolder, ctx);
            bindHeaderMeta(headerViewHolder, ctx);

            FontUtils.Typography resolvedTypography = getTypography(ctx);
            resolvedTypography.applyCommentsHeaderMeta(
                    headerViewHolder.urlView,
                    headerViewHolder.metaVotes,
                    headerViewHolder.metaComments,
                    headerViewHolder.metaTime,
                    headerViewHolder.metaBy);

            resolvedTypography.applyCommentsHeaderTitle(headerViewHolder.titleView);
            resolvedTypography.applyCommentText(headerViewHolder.textView);

            bindHeaderLoadingState(headerViewHolder, ctx);

            bindHeaderSpacer(headerViewHolder);

            headerViewHolder.setRefreshButtonVisible(showUpdate);

            int actionContainerPadding = Math.round(headerViewHolder.actionsContainer.getResources().getDimension(R.dimen.comments_header_action_padding));
            headerViewHolder.actionsContainer.setPadding(actionContainerPadding, 0, actionContainerPadding, 0);
            applyHeaderContentSideInsets(headerViewHolder);

            headerViewHolder.favicon.setVisibility(
                    !storyHeaderLoading && showThumbnail ? View.VISIBLE : GONE);
            headerViewHolder.linkInfoContainer.setVisibility(
                    !storyHeaderLoading && !story.isComment && story.isLink
                            ? View.VISIBLE : View.GONE);

            if (!storyHeaderLoading && showThumbnail && !TextUtils.isEmpty(story.url)) {
                FaviconLoader.loadFavicon(
                        story, headerViewHolder.favicon, ctx, faviconProvider);
            }

            bindHeaderSummary(headerViewHolder, ctx);

            bindHeaderActions(headerViewHolder, ctx);
            markHeaderBindingCurrent(headerViewHolder);
            schedulePendingStoryHeaderReveal(headerViewHolder);

        } else if (holder instanceof ItemViewHolder) {
            final ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            Comment comment = comments.get(position);
            applyCommentViewStyle(itemViewHolder);
            itemViewHolder.comment = comment;
            applyCommentHighlight(itemViewHolder, comment.id == highlightedCommentId);

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
            // Pull the first comment into the tint fade's near-background tail to tighten the
            // transition without changing untinted comment spacing.
            int headerFadeOverlap = shouldTintHeader() && position == 1
                    ? Utils.pxFromDpInt(ctx.getResources(), HEADER_BOTTOM_FADE_OVERLAP_DP)
                    : 0;

            // 16 is base padding, then add depth-based indentation for child comments.
            int leftMargin = Math.max(0, horizontalStartMargin - cardShadowPadding);
            int adjustedTopMargin =
                    Math.max(-headerFadeOverlap, topMargin - cardShadowPadding - headerFadeOverlap);
            int rightMargin =
                    Math.max(0, Utils.pxFromDpInt(ctx.getResources(), 16) - cardShadowPadding);
            int adjustedBottomMargin = Math.max(0, bottomMargin - cardShadowPadding);

            ViewGroup.LayoutParams currentParams = itemViewHolder.itemView.getLayoutParams();
            ViewGroup.MarginLayoutParams params;
            boolean layoutParamsChanged;
            if (currentParams instanceof ViewGroup.MarginLayoutParams) {
                params = (ViewGroup.MarginLayoutParams) currentParams;
                layoutParamsChanged =
                        params.width != ViewGroup.LayoutParams.MATCH_PARENT
                                || params.height != ViewGroup.LayoutParams.WRAP_CONTENT
                                || params.leftMargin != leftMargin
                                || params.topMargin != adjustedTopMargin
                                || params.rightMargin != rightMargin
                                || params.bottomMargin != adjustedBottomMargin;
            } else {
                params = new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                layoutParamsChanged = true;
            }

            if (layoutParamsChanged) {
                params.width = ViewGroup.LayoutParams.MATCH_PARENT;
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                params.setMargins(
                        leftMargin,
                        adjustedTopMargin,
                        rightMargin,
                        adjustedBottomMargin);
                itemViewHolder.itemView.setLayoutParams(params);
            }

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

            String formattedTime = comment.getTimeFormatted();
            itemViewHolder.commentByTime.setText(formattedTime);

            boolean byOp = TextUtils.equals(story.by, comment.by);
            boolean byUser = false;
            if (!TextUtils.isEmpty(username)) {
                byUser = comment.by.equals(username);
            }

            bindCommentAuthor(itemViewHolder, comment, ctx, byOp);
            itemViewHolder.commentByTime.setContentDescription("Posted " + formattedTime);

            if (byUser) {
                itemViewHolder.commentBy.setTextColor(itemViewHolder.selfCommentColor);
            } else if (byOp) {
                itemViewHolder.commentBy.setTextColor(itemViewHolder.opCommentColor);
            } else {
                itemViewHolder.commentBy.setTextColor(itemViewHolder.defaultCommentMetaColor);
            }

            boolean commentTextCollapsed = !comment.expanded && collapseParent;
            itemViewHolder.commentBody.setVisibility((itemViewHolder.commentBodyHasText && !commentTextCollapsed) ? View.VISIBLE : GONE);
            itemViewHolder.referenceLinksContainer.setVisibility((itemViewHolder.referenceLinksVisible && !commentTextCollapsed) ? View.VISIBLE : GONE);
            itemViewHolder.commentHiddenText.setVisibility((!comment.expanded && collapseParent) ? View.VISIBLE : GONE);

            int subCommentCount = getIndexOfLastChild(position) - position;
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

    private void bindCommentAuthor(ItemViewHolder itemViewHolder,
                                   Comment comment,
                                   Context ctx,
                                   boolean byOp) {
        String tag = getCachedUserTag(ctx, comment.by);
        String displayName = comment.by;
        if (!TextUtils.isEmpty(tag)) {
            displayName += " (" + tag + ")";
        }
        itemViewHolder.commentBy.setText(getCommentByWithOpBadge(
                ctx,
                displayName,
                byOp,
                itemViewHolder.opCommentColor));
        itemViewHolder.commentBy.setContentDescription(
                "Comment by " + comment.by + (byOp ? ", original poster" : ""));
    }

    public void notifyCommentUserTagChanged(int position) {
        notifyItemChanged(position, COMMENT_USER_TAG_UPDATE_PAYLOAD);
    }

    private void applyCommentMetaHighlight(ItemViewHolder itemViewHolder) {
        int horizontalPadding = highlightCommentMeta
                ? Utils.pxFromDpInt(itemViewHolder.commentMetaContainer.getResources(), 7)
                : 0;
        int verticalPadding = highlightCommentMeta
                ? Utils.pxFromDpInt(itemViewHolder.commentMetaContainer.getResources(), 2)
                : 0;
        itemViewHolder.commentMetaContainer.setBackgroundResource(
                highlightCommentMeta ? R.drawable.comment_meta_highlight_background : 0);
        itemViewHolder.commentMetaContainer.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding);
    }

    private void applyCommentViewStyle(@NonNull ItemViewHolder itemViewHolder) {
        if (itemViewHolder.commentViewStyleGeneration == commentViewStyleGeneration) {
            return;
        }

        applyCommentCardChrome(itemViewHolder);
        applyCommentMetaHighlight(itemViewHolder);

        FontUtils.Typography resolvedTypography =
                getTypography(itemViewHolder.itemView.getContext());
        resolvedTypography.applyCommentText(itemViewHolder.commentBody);
        resolvedTypography.applyBold(itemViewHolder.commentBy);
        resolvedTypography.applyRegular(itemViewHolder.commentByTime);
        resolvedTypography.applyRegular(itemViewHolder.commentHiddenText);

        itemViewHolder.opCommentColor =
                MaterialColors.getColor(itemViewHolder.commentBy, R.attr.opCommentColor);
        itemViewHolder.selfCommentColor =
                MaterialColors.getColor(itemViewHolder.commentBy, R.attr.selfCommentColor);
        itemViewHolder.defaultCommentMetaColor = MaterialColors.getColor(
                itemViewHolder.commentBy,
                highlightCommentMeta ? R.attr.storyColorNormal : R.attr.storyColorDisabled);
        itemViewHolder.commentByTime.setTextColor(itemViewHolder.defaultCommentMetaColor);

        View commentBackgroundView = itemViewHolder.itemView;
        if (itemViewHolder.commentCard instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) itemViewHolder.commentCard;
            commentBackgroundView = card;
            itemViewHolder.defaultCommentBackgroundColor = MaterialColors.getColor(
                    card,
                    com.google.android.material.R.attr.colorSurfaceContainerHigh,
                    Color.TRANSPARENT);
            itemViewHolder.itemView.setBackgroundColor(Color.TRANSPARENT);
        } else {
            itemViewHolder.defaultCommentBackgroundColor = MaterialColors.getColor(
                    itemViewHolder.itemView,
                    android.R.attr.colorBackground,
                    Color.TRANSPARENT);
        }
        itemViewHolder.highlightedCommentBackgroundColor = getCommentHighlightColor(
                commentBackgroundView,
                itemViewHolder.defaultCommentBackgroundColor);
        itemViewHolder.commentHighlightInitialized = false;
        itemViewHolder.commentViewStyleGeneration = commentViewStyleGeneration;
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

    public void setHeaderContentSideInsets(int left, int right) {
        int safeLeft = Math.max(0, left);
        int safeRight = Math.max(0, right);
        if (headerContentInsetLeft == safeLeft
                && headerContentInsetRight == safeRight) {
            return;
        }

        headerContentInsetLeft = safeLeft;
        headerContentInsetRight = safeRight;
        if (boundHeaderViewHolder != null) {
            applyHeaderContentSideInsets(boundHeaderViewHolder);
        }
    }

    private float getHeaderAlphaForSlideOffset(float slideOffset) {
        return Math.min(1f, slideOffset * slideOffset * 20f);
    }

    private float getEffectiveHeaderTintProgress() {
        return headerSlideOffset * getHeaderAlphaForSlideOffset(headerSlideOffset);
    }

    public void setBoundHeaderAlpha(float alpha) {
        if (boundHeaderViewHolder != null
                && ViewCompat.isAttachedToWindow(boundHeaderViewHolder.headerView)) {
            boundHeaderViewHolder.headerView.setAlpha(alpha);
        }
    }

    public void setBoundSheetButtonsContentAlpha(float alpha) {
        if (boundHeaderViewHolder != null
                && ViewCompat.isAttachedToWindow(boundHeaderViewHolder.sheetButtonsContainer)) {
            boundHeaderViewHolder.setSheetButtonsContentAlpha(alpha);
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

    public void setStoryHeaderLoading(boolean loading) {
        storyHeaderLoading = loading;
        if (!loading) {
            storyHeaderRevealPending = false;
        }
    }

    public boolean revealLoadedStoryHeader() {
        if (!storyHeaderLoading || story == null || !story.loaded) {
            return false;
        }

        if (boundHeaderViewHolder == null
                || !ViewCompat.isAttachedToWindow(boundHeaderViewHolder.itemView)) {
            storyHeaderRevealPending = true;
            notifyItemChanged(0);
            return true;
        }

        storyHeaderLoading = false;
        animateLoadedStoryHeaderReveal(
                boundHeaderViewHolder,
                boundHeaderViewHolder.itemView.getContext());
        return true;
    }

    private void schedulePendingStoryHeaderReveal(HeaderViewHolder headerViewHolder) {
        if (!storyHeaderRevealPending) {
            return;
        }

        headerViewHolder.itemView.post(() -> {
            if (!storyHeaderRevealPending
                    || boundHeaderViewHolder != headerViewHolder
                    || !ViewCompat.isAttachedToWindow(headerViewHolder.itemView)
                    || story == null
                    || !story.loaded) {
                return;
            }

            storyHeaderRevealPending = false;
            storyHeaderLoading = false;
            animateLoadedStoryHeaderReveal(
                    headerViewHolder,
                    headerViewHolder.itemView.getContext());
        });
    }

    private void animateLoadedStoryHeaderReveal(
            HeaderViewHolder headerViewHolder,
            Context ctx) {
        cancelStoryHeaderRevealAnimation();

        View headerItem = headerViewHolder.itemView;
        int startHeight = headerItem.getHeight();
        int width = headerItem.getWidth();

        bindHeaderStoryViews(headerViewHolder, ctx);
        bindHeaderPreviewImage(headerViewHolder);
        markHeaderBindingCurrent(headerViewHolder);

        if (startHeight <= 0 || width <= 0) {
            return;
        }

        storyHeaderRevealViews.clear();
        for (int index = 0; index < headerViewHolder.headerView.getChildCount(); index++) {
            View child = headerViewHolder.headerView.getChildAt(index);
            if (child.getVisibility() != VISIBLE) {
                continue;
            }
            child.animate().cancel();
            child.setAlpha(0f);
            storyHeaderRevealViews.add(child);
        }

        ViewGroup.LayoutParams layoutParams = headerItem.getLayoutParams();
        int originalLayoutHeight = layoutParams.height;
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        int widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        headerItem.measure(widthSpec, heightSpec);
        int targetHeight = headerItem.getMeasuredHeight();
        layoutParams.height = startHeight;
        headerItem.setLayoutParams(layoutParams);

        storyHeaderRevealViewHolder = headerViewHolder;
        ValueAnimator animator = ValueAnimator.ofInt(startHeight, targetHeight);
        storyHeaderRevealHeightAnimator = animator;
        animator.setDuration(STORY_HEADER_REVEAL_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            if (storyHeaderRevealHeightAnimator != animator) {
                return;
            }
            ViewGroup.LayoutParams animatedLayoutParams = headerItem.getLayoutParams();
            animatedLayoutParams.height = (int) animation.getAnimatedValue();
            headerItem.setLayoutParams(animatedLayoutParams);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (storyHeaderRevealHeightAnimator != animator) {
                    return;
                }
                storyHeaderRevealHeightAnimator = null;
                finishStoryHeaderReveal(headerViewHolder, originalLayoutHeight);
            }
        });

        for (View child : storyHeaderRevealViews) {
            child.animate()
                    .alpha(1f)
                    .setStartDelay(30L)
                    .setDuration(STORY_HEADER_CONTENT_FADE_IN_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
        animator.start();
    }

    private void cancelStoryHeaderRevealAnimation() {
        ValueAnimator animator = storyHeaderRevealHeightAnimator;
        storyHeaderRevealHeightAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
        if (storyHeaderRevealViewHolder != null) {
            finishStoryHeaderReveal(
                    storyHeaderRevealViewHolder,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void finishStoryHeaderReveal(
            HeaderViewHolder headerViewHolder,
            int finalLayoutHeight) {
        for (View child : storyHeaderRevealViews) {
            child.animate().cancel();
            child.setAlpha(1f);
        }
        storyHeaderRevealViews.clear();

        ViewGroup.LayoutParams layoutParams = headerViewHolder.itemView.getLayoutParams();
        layoutParams.height = finalLayoutHeight;
        headerViewHolder.itemView.setLayoutParams(layoutParams);
        storyHeaderRevealViewHolder = null;
    }

    public void refreshCanProvideSummary(Context ctx) {
        canProvideSummary = story != null
                && story.isLink
                && Utils.canProvideSummary(ctx);
    }

    public boolean updateBoundHeaderLoadingState() {
        if (boundHeaderViewHolder == null
                || !ViewCompat.isAttachedToWindow(boundHeaderViewHolder.itemView)) {
            return false;
        }

        bindHeaderLoadingState(
                boundHeaderViewHolder,
                boundHeaderViewHolder.itemView.getContext());
        return true;
    }

    public void setReaderModeEnabled(boolean enabled) {
        if (readerModeEnabled == enabled) {
            return;
        }

        readerModeEnabled = enabled;
        if (boundHeaderViewHolder != null
                && ViewCompat.isAttachedToWindow(boundHeaderViewHolder.sheetReaderIcon)) {
            bindReaderModeButtonState(boundHeaderViewHolder);
        }
    }

    public void setReaderModeAvailable(boolean available) {
        if (readerModeAvailable == available) {
            return;
        }

        readerModeAvailable = available;
        if (boundHeaderViewHolder != null
                && ViewCompat.isAttachedToWindow(boundHeaderViewHolder.sheetReaderContainer)) {
            setReaderModeButtonVisible(boundHeaderViewHolder, available, true);
        }
    }

    private void bindReaderModeButton(HeaderViewHolder headerViewHolder) {
        setReaderModeButtonVisible(headerViewHolder, readerModeAvailable, false);
        bindReaderModeButtonState(headerViewHolder);
    }

    private void bindReaderModeButtonState(HeaderViewHolder headerViewHolder) {
        int normalColor = MaterialColors.getColor(headerViewHolder.sheetReaderIcon, R.attr.drawableColor);
        if (readerModeEnabled) {
            int activeColor = MaterialColors.getColor(
                    headerViewHolder.sheetReaderIcon,
                    com.google.android.material.R.attr.colorSecondary,
                    normalColor);
            ViewCompat.setBackgroundTintList(headerViewHolder.sheetReaderIcon, ColorStateList.valueOf(activeColor));
            headerViewHolder.sheetReaderButton.setContentDescription("Reader mode on");
            TooltipCompat.setTooltipText(headerViewHolder.sheetReaderButton, "Reader mode on");
        } else {
            ViewCompat.setBackgroundTintList(headerViewHolder.sheetReaderIcon, ColorStateList.valueOf(normalColor));
            headerViewHolder.sheetReaderButton.setContentDescription("Toggle reader mode");
            TooltipCompat.setTooltipText(headerViewHolder.sheetReaderButton, "Reader mode");
        }
    }

    private void setReaderModeButtonVisible(HeaderViewHolder headerViewHolder, boolean visible, boolean animate) {
        View readerContainer = headerViewHolder.sheetReaderContainer;
        readerContainer.animate().setListener(null);
        readerContainer.animate().cancel();

        if (!animate || !ViewCompat.isAttachedToWindow(readerContainer)) {
            readerContainer.setVisibility(visible ? VISIBLE : GONE);
            readerContainer.setAlpha(1f);
            readerContainer.setScaleX(1f);
            readerContainer.setScaleY(1f);
            headerViewHolder.sheetReaderButton.setEnabled(visible);
            return;
        }

        headerViewHolder.sheetReaderButton.setEnabled(visible);
        if (visible) {
            if (readerContainer.getVisibility() != VISIBLE) {
                AutoTransition transition = new AutoTransition();
                transition.setDuration(HEADER_READER_BUTTON_VISIBILITY_DURATION_MS);
                TransitionManager.beginDelayedTransition(headerViewHolder.sheetButtonsContainer, transition);
                readerContainer.setAlpha(0f);
                readerContainer.setScaleX(HEADER_ACTION_ICON_SWAP_MIN_SCALE);
                readerContainer.setScaleY(HEADER_ACTION_ICON_SWAP_MIN_SCALE);
                readerContainer.setVisibility(VISIBLE);
            }
            readerContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(HEADER_READER_BUTTON_VISIBILITY_DURATION_MS)
                    .start();
        } else {
            readerContainer.animate()
                    .alpha(0f)
                    .scaleX(HEADER_ACTION_ICON_SWAP_MIN_SCALE)
                    .scaleY(HEADER_ACTION_ICON_SWAP_MIN_SCALE)
                    .setDuration(HEADER_READER_BUTTON_VISIBILITY_DURATION_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            readerContainer.animate().setListener(null);
                            if (readerModeAvailable) {
                                return;
                            }

                            AutoTransition transition = new AutoTransition();
                            transition.setDuration(HEADER_READER_BUTTON_VISIBILITY_DURATION_MS);
                            TransitionManager.beginDelayedTransition(headerViewHolder.sheetButtonsContainer, transition);
                            readerContainer.setVisibility(GONE);
                            readerContainer.setAlpha(1f);
                            readerContainer.setScaleX(1f);
                            readerContainer.setScaleY(1f);
                        }
                    })
                    .start();
        }
    }

    private void bindHeaderStoryViews(HeaderViewHolder headerViewHolder, Context ctx) {
        if (story.isLink && story.url != null) {
            try {
                headerViewHolder.urlView.setText(
                        "(" + story.getDisplayDomain(true) + ")");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        headerViewHolder.headerView.setClickable(story.isLink);
        headerViewHolder.linkImage.setVisibility(
                !storyHeaderLoading && story.isLink && !story.isComment
                        ? View.VISIBLE : GONE);
        bindStoryText(headerViewHolder);
        if (!storyHeaderLoading) {
            LinkPreviewHeaderBinder.bind(ctx, headerViewHolder, story);
        }
        bindHeaderTitle(headerViewHolder, ctx);
        bindHeaderMeta(headerViewHolder, ctx);
        bindHeaderLoadingState(headerViewHolder, ctx);
        bindReaderModeButton(headerViewHolder);

        headerViewHolder.favicon.setVisibility(
                !storyHeaderLoading && showThumbnail ? View.VISIBLE : GONE);
        headerViewHolder.linkInfoContainer.setVisibility(
                !storyHeaderLoading && !story.isComment && story.isLink
                        ? View.VISIBLE : View.GONE);
        if (!storyHeaderLoading && showThumbnail && !TextUtils.isEmpty(story.url)) {
            FaviconLoader.loadFavicon(
                    story, headerViewHolder.favicon, ctx, faviconProvider);
        }
        bindHeaderSummary(headerViewHolder, ctx);
        headerViewHolder.emptyViewText.setText(story.isComment ? "No replies" : "No comments");
        bindHeaderAccountActionVisibility(headerViewHolder);
        bindHeaderTint(headerViewHolder);
    }

    private boolean isHeaderBindingCurrent(HeaderViewHolder headerViewHolder) {
        return headerViewHolder.boundStory == story
                && headerViewHolder.headerBindingGeneration == headerBindingGeneration;
    }

    private void markHeaderBindingCurrent(HeaderViewHolder headerViewHolder) {
        headerViewHolder.boundStory = story;
        headerViewHolder.headerBindingGeneration = headerBindingGeneration;
    }

    private void bindHeaderReattachmentState(
            HeaderViewHolder headerViewHolder,
            Context ctx) {
        bindHeaderSpacer(headerViewHolder);
        bindReaderModeButton(headerViewHolder);
        applyHeaderPreviewImageSuppression(headerViewHolder);
        bindHeaderActions(headerViewHolder, ctx);
    }

    private void bindHeaderSpacer(HeaderViewHolder headerViewHolder) {
        ViewGroup.LayoutParams layoutParams = headerViewHolder.spacer.getLayoutParams();
        if (layoutParams != null && layoutParams.height == spacerHeight) {
            return;
        }
        headerViewHolder.spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                spacerHeight));
    }

    private void bindHeaderActions(HeaderViewHolder headerViewHolder, Context ctx) {
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
            showHeaderFavoriteLoading(
                    headerViewHolder.favoriteButton,
                    storyFavoriteLoadingTarget,
                    false);
        } else {
            showHeaderFavoriteButton(headerViewHolder.favoriteButton, isFavorited, false);
        }

        headerViewHolder.emptyViewText.setText(story.isComment ? "No replies" : "No comments");
        setHeaderOpFilterVisibleImmediately(headerViewHolder, commentsByOpFilterActive);
        headerViewHolder.bookmarkButtonParent.setVisibility(
                bookmarksEnabled && !hasAccountDetails ? VISIBLE : GONE);
        bindHeaderAccountActionVisibility(headerViewHolder);
    }

    private void bindHeaderSummary(HeaderViewHolder headerViewHolder, Context ctx) {
        bindHeaderSummaryContent(headerViewHolder, ctx);
        bindHeaderSummaryAction(headerViewHolder, ctx);
    }

    private void bindHeaderSummaryContent(HeaderViewHolder headerViewHolder,
                                          Context ctx) {
        LinearLayout summaryContainer = headerViewHolder.summaryContainer;
        cancelHeaderSummaryCompletionAnimation(headerViewHolder);
        restoreHeaderSummaryLayoutTransition(summaryContainer);
        cancelHeaderSummaryHeightAnimation();
        pendingHeaderSummary = null;
        headerSummaryAnimationSummary = null;
        headerSummaryAnimationText = null;
        headerSummaryCompletionPending = false;
        headerSummaryCompletionScheduled = false;

        boolean hasSummary = !TextUtils.isEmpty(story.summary);
        if (currentHeaderContentBackgroundColor != null) {
            summaryContainer.setBackgroundColor(currentHeaderContentBackgroundColor);
        }
        summaryContainer.setVisibility(hasSummary ? VISIBLE : GONE);
        headerViewHolder.summaryContentContainer.setVisibility(hasSummary ? VISIBLE : GONE);
        headerViewHolder.summaryContentContainer.setAlpha(1f);
        headerViewHolder.summary.setMaxLines(Integer.MAX_VALUE);
        headerViewHolder.summary.setEllipsize(null);
        if (hasSummary) {
            renderHeaderSummaryMarkdown(headerViewHolder.summary, ctx, story.summary);
        } else {
            headerViewHolder.summary.setText(null);
        }
        bindHeaderSummaryDebugInfo(headerViewHolder, ctx);
        displayedHeaderSummary = story.summary;
        resetHeaderSummaryHeight(headerViewHolder);
    }

    private void bindHeaderSummaryAction(HeaderViewHolder headerViewHolder, Context ctx) {
        boolean canSummarize = story.isLink
                && canProvideSummary
                && !story.summaryGeneratedSuccessfully;
        if (!headerSummaryCompletionTransitionRunning) {
            resetHeaderSummaryActionTransform(headerViewHolder.summarizeButtonParent);
        }
        headerViewHolder.summarizeButtonParent.setVisibility(canSummarize ? VISIBLE : GONE);

        if (storySummaryLoading) {
            showHeaderSummaryLoading(headerViewHolder.summarizeButton, false);
        } else {
            showHeaderSummaryButton(headerViewHolder.summarizeButton, false);
        }

        headerViewHolder.summarizeButton.setOnClickListener(v -> {
            if (storySummaryLoading) {
                return;
            }

            storySummaryLoading = true;
            storySummaryReceivedProgress = false;
            story.summaryDebugInfo = null;
            headerViewHolder.summaryDebugInfo.setVisibility(GONE);
            pendingHeaderSummary = null;
            headerSummaryAnimationSummary = null;
            headerSummaryAnimationText = null;
            headerSummaryCompletionPending = false;
            headerSummaryCompletionScheduled = false;
            showHeaderSummaryLoading(headerViewHolder.summarizeButton, true);
            summaryCallback.onRequest(
                    () -> {
                        storySummaryReceivedProgress = true;
                        enqueueBoundHeaderSummary(false);
                    },
                    () -> {
                        storySummaryLoading = false;
                        if (storySummaryReceivedProgress) {
                            enqueueBoundHeaderSummary(true);
                        } else if (story.summaryGeneratedSuccessfully
                                && !TextUtils.isEmpty(story.summary)) {
                            enqueueBoundHeaderSummary(true);
                        } else {
                            notifyItemChanged(0, HEADER_SUMMARY_UPDATE_PAYLOAD);
                        }
                        storySummaryReceivedProgress = false;
                    });
        });
    }

    private void enqueueBoundHeaderSummary(boolean completed) {
        HeaderViewHolder headerViewHolder = boundHeaderViewHolder;
        if (headerViewHolder == null
                || !ViewCompat.isAttachedToWindow(headerViewHolder.itemView)) {
            notifyItemChanged(0, HEADER_SUMMARY_UPDATE_PAYLOAD);
            return;
        }

        bindHeaderSummaryDebugInfo(
                headerViewHolder, headerViewHolder.itemView.getContext());

        pendingHeaderSummary = story.summary;
        if (completed) {
            headerSummaryCompletionPending = true;
        }
        processPendingHeaderSummary(headerViewHolder);
    }

    private void processPendingHeaderSummary(HeaderViewHolder headerViewHolder) {
        if (headerViewHolder != boundHeaderViewHolder
                || !ViewCompat.isAttachedToWindow(headerViewHolder.itemView)
                || headerSummaryHeightAnimator != null
                || headerSummaryCompletionTransitionRunning
                || headerSummaryCompletionScheduled) {
            return;
        }

        String nextSummary = pendingHeaderSummary;
        if (nextSummary != null) {
            pendingHeaderSummary = null;
            if (!TextUtils.equals(displayedHeaderSummary, nextSummary)) {
                applyStreamedHeaderSummary(headerViewHolder, nextSummary);
                return;
            }
        }

        if (headerSummaryCompletionPending) {
            scheduleHeaderSummaryCompletion(headerViewHolder);
        }
    }

    private void applyStreamedHeaderSummary(HeaderViewHolder headerViewHolder,
                                            String nextSummary) {
        Context context = headerViewHolder.itemView.getContext();
        LinearLayout summaryContainer = headerViewHolder.summaryContainer;
        int width = summaryContainer.getWidth();
        if (width <= 0 || TextUtils.isEmpty(nextSummary)) {
            commitStreamedHeaderSummary(headerViewHolder, nextSummary, null);
            return;
        }

        disableHeaderSummaryLayoutTransition(summaryContainer);
        int startHeight = summaryContainer.getVisibility() == VISIBLE
                ? summaryContainer.getHeight()
                : 0;
        int startHeaderItemHeight = headerViewHolder.itemView.getHeight();
        CharSequence currentText = headerViewHolder.summary.getText();

        if (currentHeaderContentBackgroundColor != null) {
            summaryContainer.setBackgroundColor(currentHeaderContentBackgroundColor);
        }
        summaryContainer.setVisibility(VISIBLE);
        headerViewHolder.summaryContentContainer.setVisibility(VISIBLE);
        headerViewHolder.summaryContentContainer.setAlpha(1f);
        renderHeaderSummaryMarkdown(headerViewHolder.summary, context, nextSummary);

        summaryContainer.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int targetHeight = summaryContainer.getMeasuredHeight();
        CharSequence renderedText = new SpannableStringBuilder(
                headerViewHolder.summary.getText());
        headerViewHolder.summary.setText(currentText);

        if (targetHeight <= startHeight || startHeaderItemHeight <= 0) {
            commitStreamedHeaderSummary(headerViewHolder, nextSummary, renderedText);
            return;
        }

        headerSummaryAnimationSummary = nextSummary;
        headerSummaryAnimationText = renderedText;
        headerViewHolder.summaryContentContainer.setAlpha(startHeight == 0 ? 0f : 1f);
        setHeaderSummaryHeight(summaryContainer, startHeight);
        setHeaderSummaryHeight(headerViewHolder.itemView, startHeaderItemHeight);
        ValueAnimator animator = ValueAnimator.ofInt(startHeight, targetHeight);
        headerSummaryHeightAnimator = animator;
        animator.setDuration(HEADER_SUMMARY_HEIGHT_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (headerSummaryHeightAnimator == valueAnimator) {
                int animatedHeight = (Integer) valueAnimator.getAnimatedValue();
                int animatedHeaderItemHeight = startHeaderItemHeight
                        + animatedHeight
                        - startHeight;
                setHeaderSummaryHeight(summaryContainer, animatedHeight);
                setHeaderSummaryHeight(headerViewHolder.itemView, animatedHeaderItemHeight);
                ViewCompat.postInvalidateOnAnimation(headerViewHolder.itemView);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (headerSummaryHeightAnimator != animation) {
                    return;
                }
                headerSummaryHeightAnimator = null;
                String animatedSummary = headerSummaryAnimationSummary;
                CharSequence animatedText = headerSummaryAnimationText;
                headerSummaryAnimationSummary = null;
                headerSummaryAnimationText = null;
                setHeaderSummaryHeight(summaryContainer, ViewGroup.LayoutParams.WRAP_CONTENT);
                setHeaderSummaryHeight(
                        headerViewHolder.itemView,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (animatedSummary != null) {
                    headerViewHolder.summary.setText(animatedText);
                    displayedHeaderSummary = animatedSummary;
                }
                headerViewHolder.summaryContentContainer.setAlpha(1f);
                restoreHeaderSummaryLayoutTransition(summaryContainer);
                postProcessPendingHeaderSummary(headerViewHolder);
            }
        });
        animator.start();
    }

    private void commitStreamedHeaderSummary(HeaderViewHolder headerViewHolder,
                                             @Nullable String summary,
                                             @Nullable CharSequence renderedText) {
        boolean hasSummary = !TextUtils.isEmpty(summary);
        headerViewHolder.summaryContainer.setVisibility(hasSummary ? VISIBLE : GONE);
        headerViewHolder.summaryContentContainer.setVisibility(hasSummary ? VISIBLE : GONE);
        headerViewHolder.summaryContentContainer.setAlpha(1f);
        if (hasSummary) {
            if (renderedText != null) {
                headerViewHolder.summary.setText(renderedText);
            } else {
                renderHeaderSummaryMarkdown(
                        headerViewHolder.summary,
                        headerViewHolder.itemView.getContext(),
                        summary);
            }
        } else {
            headerViewHolder.summary.setText(null);
        }
        displayedHeaderSummary = summary;
        setHeaderSummaryHeight(
                headerViewHolder.summaryContainer,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        setHeaderSummaryHeight(
                headerViewHolder.itemView,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        restoreHeaderSummaryLayoutTransition(headerViewHolder.summaryContainer);
        postProcessPendingHeaderSummary(headerViewHolder);
    }

    private void renderHeaderSummaryMarkdown(TextView summaryView,
                                             Context context,
                                             String summary) {
        int bulletWidth = Math.max(1, Math.round(summaryView.getTextSize() * 0.28f));
        Markwon markwon = Markwon.builder(context)
                .usePlugin(new AbstractMarkwonPlugin() {
                    @Override
                    public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                        builder.bulletWidth(bulletWidth);
                    }
                })
                .build();
        markwon.setMarkdown(summaryView, summary);
    }

    private void bindHeaderSummaryDebugInfo(HeaderViewHolder headerViewHolder,
                                            Context context) {
        boolean showDebugInfo = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("pref_debug_show_llm_summary_info", false);
        boolean visible = showDebugInfo && !TextUtils.isEmpty(story.summaryDebugInfo);
        headerViewHolder.summaryDebugInfo.setText(visible ? story.summaryDebugInfo : null);
        headerViewHolder.summaryDebugInfo.setVisibility(visible ? VISIBLE : GONE);
    }

    private void postProcessPendingHeaderSummary(HeaderViewHolder headerViewHolder) {
        headerViewHolder.itemView.postOnAnimation(() ->
                processPendingHeaderSummary(headerViewHolder));
    }

    private void scheduleHeaderSummaryCompletion(HeaderViewHolder headerViewHolder) {
        if (headerSummaryCompletionScheduled) {
            return;
        }
        headerSummaryCompletionScheduled = true;
        headerViewHolder.actionsContainer.postOnAnimation(() -> {
            headerSummaryCompletionScheduled = false;
            if (headerViewHolder != boundHeaderViewHolder
                    || !ViewCompat.isAttachedToWindow(headerViewHolder.itemView)) {
                headerSummaryCompletionPending = false;
                return;
            }
            if (headerSummaryHeightAnimator != null || pendingHeaderSummary != null) {
                processPendingHeaderSummary(headerViewHolder);
                return;
            }
            if (!headerSummaryCompletionPending) {
                return;
            }
            headerSummaryCompletionPending = false;
            animateHeaderSummaryActionRemoval(headerViewHolder);
        });
    }

    private void animateHeaderSummaryActionRemoval(HeaderViewHolder headerViewHolder) {
        if (!story.summaryGeneratedSuccessfully) {
            bindHeaderSummaryAction(headerViewHolder, headerViewHolder.itemView.getContext());
            return;
        }

        View summarizeParent = headerViewHolder.summarizeButtonParent;
        if (summarizeParent.getVisibility() != VISIBLE) {
            bindHeaderSummaryAction(headerViewHolder, headerViewHolder.itemView.getContext());
            return;
        }

        Map<View, Float> startCenters = new HashMap<>();
        for (int i = 0; i < headerViewHolder.actionsContainer.getChildCount(); i++) {
            View child = headerViewHolder.actionsContainer.getChildAt(i);
            if (child != summarizeParent && child.getVisibility() == VISIBLE) {
                startCenters.put(child, child.getX() + child.getWidth() / 2f);
            }
        }

        headerSummaryCompletionTransitionRunning = true;
        summarizeParent.animate().cancel();
        summarizeParent.animate()
                .alpha(0f)
                .scaleX(HEADER_ACTION_ICON_SWAP_MIN_SCALE)
                .scaleY(HEADER_ACTION_ICON_SWAP_MIN_SCALE)
                .setDuration(HEADER_ACTION_ICON_SWAP_OUT_DURATION_MS)
                .withEndAction(() -> {
                    if (headerViewHolder != boundHeaderViewHolder
                            || !ViewCompat.isAttachedToWindow(headerViewHolder.itemView)) {
                        resetHeaderSummaryActionTransform(summarizeParent);
                        headerSummaryCompletionTransitionRunning = false;
                        return;
                    }
                    bindHeaderSummaryAction(
                            headerViewHolder,
                            headerViewHolder.itemView.getContext());
                    resetHeaderSummaryActionTransform(summarizeParent);
                    animateHeaderSummaryActionReflow(headerViewHolder, startCenters);
                })
                .start();
    }

    private void animateHeaderSummaryActionReflow(HeaderViewHolder headerViewHolder,
                                                  Map<View, Float> startCenters) {
        LinearLayout actionsContainer = headerViewHolder.actionsContainer;
        actionsContainer.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        ViewTreeObserver observer = actionsContainer.getViewTreeObserver();
                        if (observer.isAlive()) {
                            observer.removeOnPreDrawListener(this);
                        }
                        if (headerViewHolder != boundHeaderViewHolder
                                || !ViewCompat.isAttachedToWindow(headerViewHolder.itemView)) {
                            headerSummaryCompletionTransitionRunning = false;
                            return true;
                        }
                        if (!headerSummaryCompletionTransitionRunning) {
                            return true;
                        }

                        Map<View, Float> startTranslations = new HashMap<>();
                        for (Map.Entry<View, Float> entry : startCenters.entrySet()) {
                            View child = entry.getKey();
                            if (child.getVisibility() != VISIBLE) {
                                continue;
                            }
                            float endCenter = child.getX() + child.getWidth() / 2f;
                            float translation = entry.getValue() - endCenter;
                            child.setTranslationX(translation);
                            startTranslations.put(child, translation);
                        }
                        startHeaderSummaryActionReflowAnimator(
                                headerViewHolder,
                                startTranslations);
                        return true;
                    }
                });
        actionsContainer.requestLayout();
    }

    private void startHeaderSummaryActionReflowAnimator(
            HeaderViewHolder headerViewHolder,
            Map<View, Float> startTranslations) {
        if (startTranslations.isEmpty()) {
            headerSummaryCompletionTransitionRunning = false;
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        headerSummaryActionReflowAnimator = animator;
        animator.setDuration(HEADER_SUMMARY_COMPLETION_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(valueAnimator -> {
            if (headerSummaryActionReflowAnimator != valueAnimator) {
                return;
            }
            float progress = (Float) valueAnimator.getAnimatedValue();
            for (Map.Entry<View, Float> entry : startTranslations.entrySet()) {
                entry.getKey().setTranslationX(entry.getValue() * (1f - progress));
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (headerSummaryActionReflowAnimator != animation) {
                    return;
                }
                headerSummaryActionReflowAnimator = null;
                for (View child : startTranslations.keySet()) {
                    child.setTranslationX(0f);
                }
                headerSummaryCompletionTransitionRunning = false;
            }
        });
        animator.start();
    }

    private void cancelHeaderSummaryCompletionAnimation(HeaderViewHolder headerViewHolder) {
        headerSummaryCompletionTransitionRunning = false;
        resetHeaderSummaryActionTransform(headerViewHolder.summarizeButtonParent);

        ValueAnimator animator = headerSummaryActionReflowAnimator;
        headerSummaryActionReflowAnimator = null;
        if (animator != null) {
            animator.cancel();
        }
        for (int i = 0; i < headerViewHolder.actionsContainer.getChildCount(); i++) {
            headerViewHolder.actionsContainer.getChildAt(i).setTranslationX(0f);
        }
    }

    private void resetHeaderSummaryActionTransform(View summarizeParent) {
        summarizeParent.animate().cancel();
        summarizeParent.setAlpha(1f);
        summarizeParent.setScaleX(1f);
        summarizeParent.setScaleY(1f);
    }

    private void resetHeaderSummaryHeight(HeaderViewHolder headerViewHolder) {
        cancelHeaderSummaryHeightAnimation();
        setHeaderSummaryHeight(
                headerViewHolder.summaryContainer,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        setHeaderSummaryHeight(
                headerViewHolder.itemView,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        restoreHeaderSummaryLayoutTransition(headerViewHolder.summaryContainer);
    }

    private void cancelHeaderSummaryHeightAnimation() {
        ValueAnimator animator = headerSummaryHeightAnimator;
        headerSummaryHeightAnimator = null;
        headerSummaryAnimationSummary = null;
        headerSummaryAnimationText = null;
        if (animator != null) {
            animator.cancel();
        }
    }

    private void setHeaderSummaryHeight(View container, int height) {
        ViewGroup.LayoutParams layoutParams = container.getLayoutParams();
        if (layoutParams.height == height) {
            return;
        }
        layoutParams.height = height;
        container.setLayoutParams(layoutParams);
    }

    private void disableHeaderSummaryLayoutTransition(LinearLayout summaryContainer) {
        LayoutTransition layoutTransition = summaryContainer.getLayoutTransition();
        if (layoutTransition == null) {
            return;
        }
        headerSummaryLayoutTransition = layoutTransition;
        summaryContainer.setLayoutTransition(null);
    }

    private void restoreHeaderSummaryLayoutTransition(LinearLayout summaryContainer) {
        if (headerSummaryLayoutTransition == null) {
            return;
        }
        summaryContainer.setLayoutTransition(headerSummaryLayoutTransition);
        headerSummaryLayoutTransition = null;
    }


    private void configureSummaryTitleIcon(TextView title) {
        Drawable icon = ContextCompat.getDrawable(title.getContext(), R.drawable.ic_auto_awesome);
        if (icon == null) {
            return;
        }

        int iconSize = Utils.pxFromDpInt(title.getResources(), 14);
        icon = icon.mutate();
        icon.setBounds(0, 0, iconSize, iconSize);
        icon.setTint(title.getCurrentTextColor());
        title.setCompoundDrawablePadding(Utils.pxFromDpInt(title.getResources(), 4));
        title.setCompoundDrawables(icon, null, null, null);
    }

    private void bindHeaderAccountActionVisibility(HeaderViewHolder headerViewHolder) {
        boolean canReply = hasAccountDetails && !Utils.timeInSecondsMoreThanTwoWeeksAgo(story.time);
        headerViewHolder.commentButtonParent.setVisibility(canReply ? VISIBLE : GONE);
        headerViewHolder.voteButtonParent.setVisibility(hasAccountDetails ? VISIBLE : GONE);
        headerViewHolder.favoriteButtonParent.setVisibility(hasAccountDetails ? VISIBLE : GONE);
        headerViewHolder.refreshButtonParent.setVisibility(hasAccountDetails ? GONE : VISIBLE);
        headerViewHolder.commentButton.setContentDescription(story.isComment ? "Reply to comment" : "Reply to post");
    }

    private void bindHeaderTitle(HeaderViewHolder headerViewHolder, Context ctx) {
        boolean hasTitle = !TextUtils.isEmpty(story.title);
        if (!loadingFailed
                && (storyHeaderLoading || (!story.loaded && !hasTitle))) {
            headerViewHolder.titleView.setVisibility(GONE);
            headerViewHolder.titleShimmer.setAlpha(1f);
            headerViewHolder.titleShimmer.setVisibility(View.VISIBLE);
            return;
        }

        headerViewHolder.titleShimmer.setVisibility(GONE);
        headerViewHolder.titleView.setVisibility(story.loaded || hasTitle ? View.VISIBLE : GONE);
        if (!TextUtils.isEmpty(story.pdfTitle)) {
            headerViewHolder.titleView.setText(TextSizeImageSpan.createWithTrailingBadge(ctx, story.pdfTitle, R.drawable.ic_action_pdf_large));
        } else if (!TextUtils.isEmpty(story.videoTitle)) {
            headerViewHolder.titleView.setText(TextSizeImageSpan.createWithTrailingBadge(ctx, story.videoTitle, R.drawable.ic_action_video_large));
        } else {
            headerViewHolder.titleView.setText(story.title);
        }
    }

    private CharSequence getCommentByWithOpBadge(Context ctx, String displayName, boolean byOp, int badgeColor) {
        if (!byOp) {
            return displayName;
        }

        SpannableStringBuilder sb = new SpannableStringBuilder(displayName + "OP");
        int badgeStart = sb.length() - 2;
        sb.setSpan(new OpBadgeSpan(ctx, badgeColor), badgeStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    private static class OpBadgeSpan extends ReplacementSpan {
        private final int color;
        private final int backgroundColor;
        private final float leadingMargin;
        private final float trailingMargin;
        private final float horizontalPadding;
        private final float cornerRadius;

        OpBadgeSpan(Context ctx, int color) {
            this.color = color;
            this.backgroundColor = ColorUtils.setAlphaComponent(color, 35);
            this.leadingMargin = Utils.pxFromDp(ctx.getResources(), 3);
            this.trailingMargin = Utils.pxFromDp(ctx.getResources(), 3);
            this.horizontalPadding = Utils.pxFromDp(ctx.getResources(), 3);
            this.cornerRadius = Utils.pxFromDp(ctx.getResources(), 3);
        }

        @Override
        public int getSize(@NonNull Paint paint,
                           CharSequence text,
                           int start,
                           int end,
                           @Nullable Paint.FontMetricsInt fm) {
            return Math.round(leadingMargin + paint.measureText(text, start, end) + 2 * horizontalPadding + trailingMargin);
        }

        @Override
        public void draw(@NonNull Canvas canvas,
                         CharSequence text,
                         int start,
                         int end,
                         float x,
                         int top,
                         int y,
                         int bottom,
                         @NonNull Paint paint) {
            int oldColor = paint.getColor();
            Paint.Style oldStyle = paint.getStyle();
            boolean oldFakeBold = paint.isFakeBoldText();

            Paint.FontMetrics fontMetrics = paint.getFontMetrics();
            float textWidth = paint.measureText(text, start, end);
            float badgeStart = x + leadingMargin;
            float badgeWidth = textWidth + 2 * horizontalPadding;
            float lineCenter = (top + bottom) / 2f;
            float badgeHeight = fontMetrics.descent - fontMetrics.ascent;
            float rectTop = lineCenter - badgeHeight / 2f;
            float rectBottom = lineCenter + badgeHeight / 2f;
            float textBaseline = lineCenter - (fontMetrics.ascent + fontMetrics.descent) / 2f;
            RectF rect = new RectF(badgeStart, rectTop, badgeStart + badgeWidth, rectBottom);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(backgroundColor);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

            paint.setColor(color);
            paint.setFakeBoldText(true);
            canvas.drawText(text, start, end, badgeStart + horizontalPadding, textBaseline, paint);

            paint.setFakeBoldText(oldFakeBold);
            paint.setStyle(oldStyle);
            paint.setColor(oldColor);
        }
    }

    private void bindHeaderMeta(HeaderViewHolder headerViewHolder, Context ctx) {
        boolean showLoadedStory = story.loaded && !storyHeaderLoading;
        if (showLoadedStory) {
            headerViewHolder.metaVotes.setText(String.valueOf(story.score));
            headerViewHolder.metaComments.setText(String.valueOf(story.descendants));
            String formattedTime = story.getTimeFormatted();
            headerViewHolder.metaTime.setText(formattedTime);
            String tag = getCachedUserTag(ctx, story.by);
            headerViewHolder.metaBy.setText(TextUtils.isEmpty(tag) ? story.by : story.by + " (" + tag + ")");
            headerViewHolder.metaVotes.setContentDescription(AccessibilityTextUtils.pointCountDescription(story.score));
            headerViewHolder.metaComments.setContentDescription(AccessibilityTextUtils.commentCountDescription(story.descendants));
            headerViewHolder.metaTime.setContentDescription("Posted " + formattedTime);
            headerViewHolder.metaBy.setContentDescription("Submitted by " + story.by);
            headerViewHolder.userButton.setContentDescription("Open submitter " + story.by);
        }

        headerViewHolder.metaContainer.setVisibility(showLoadedStory ? View.VISIBLE : GONE);
        headerViewHolder.urlView.setVisibility(
                showLoadedStory && story.isLink ? View.VISIBLE : GONE);
        headerViewHolder.metaVotes.setVisibility(story.isComment ? GONE : View.VISIBLE);
        headerViewHolder.metaVotesIcon.setVisibility(story.isComment ? GONE : View.VISIBLE);
    }

    private void bindHeaderLoadingState(HeaderViewHolder headerViewHolder, Context ctx) {
        boolean showLoadingIndicator = !initialCommentsRevealPending
                && !loadingFailed
                && (!commentsLoaded || commentsRefreshInProgress);
        boolean showEmptyState = !initialCommentsRevealPending
                && !loadingFailed
                && commentsLoaded
                && story.descendants <= 0
                && comments.size() <= 1;

        if (showLoadingIndicator) {
            setLoadingIndicatorTopMargin(headerViewHolder, commentsLoaded);
        }
        headerViewHolder.emptyViewText.setText(story.isComment ? "No replies" : "No comments");
        if (loadingFailed) {
            if (!Utils.isNetworkAvailable(ctx)) {
                headerViewHolder.loadingFailedText.setText("No internet connection");
            } else {
                headerViewHolder.loadingFailedText.setText("Loading failed");
            }
        }

        headerViewHolder.serverErrorText.setVisibility(loadingFailedServerError ? VISIBLE : GONE);
        headerViewHolder.openInBrowserButton.setVisibility(loadingFailedServerError ? VISIBLE : GONE);
        setHeaderStatusRows(
                headerViewHolder,
                showLoadingIndicator,
                loadingFailed,
                showEmptyState);
    }

    private void setLoadingIndicatorTopMargin(
            HeaderViewHolder headerViewHolder,
            boolean compact) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams)
                headerViewHolder.loadingIndicator.getLayoutParams();
        int targetTopMargin = Utils.pxFromDpInt(
                headerViewHolder.loadingIndicator.getResources(),
                compact ? REFRESH_LOADING_TOP_MARGIN_DP : INITIAL_LOADING_TOP_MARGIN_DP);
        if (layoutParams.topMargin == targetTopMargin) {
            return;
        }
        layoutParams.topMargin = targetTopMargin;
        headerViewHolder.loadingIndicator.setLayoutParams(layoutParams);
    }

    private void setHeaderStatusRows(
            HeaderViewHolder headerViewHolder,
            boolean showLoadingIndicator,
            boolean showFailure,
            boolean showEmptyState) {
        if (!headerViewHolder.statusRowsInitialized) {
            setHeaderStatusRowImmediately(
                    headerViewHolder,
                    headerViewHolder.loadingContainer,
                    showLoadingIndicator);
            setHeaderStatusRowImmediately(
                    headerViewHolder,
                    headerViewHolder.loadingFailedContainer,
                    showFailure);
            setHeaderStatusRowImmediately(
                    headerViewHolder,
                    headerViewHolder.emptyContainer,
                    showEmptyState);
            headerViewHolder.statusRowsInitialized = true;
            return;
        }

        Boolean loadingWasVisible = headerViewHolder.statusRowVisibilityTargets.get(
                headerViewHolder.loadingContainer);
        Boolean emptyWasVisible = headerViewHolder.statusRowVisibilityTargets.get(
                headerViewHolder.emptyContainer);
        if (Boolean.TRUE.equals(loadingWasVisible) && !showLoadingIndicator) {
            setHeaderStatusRowVisible(
                    headerViewHolder,
                    headerViewHolder.loadingFailedContainer,
                    showFailure);
            if (!Boolean.TRUE.equals(emptyWasVisible) && showEmptyState) {
                fadeLoadingIndicatorToEmptyState(headerViewHolder);
            } else {
                setHeaderStatusRowVisible(
                        headerViewHolder,
                        headerViewHolder.emptyContainer,
                        showEmptyState);
                if (commentsLoaded && comments.size() > 1) {
                    // Existing comments were pushed down as this refresh row expanded. Collapse
                    // the same row through its height so they move directly up to their final
                    // positions instead of jumping when the row abruptly becomes GONE.
                    setHeaderStatusRowVisible(
                            headerViewHolder,
                            headerViewHolder.loadingContainer,
                            false);
                } else {
                    fadeLoadingIndicatorOut(headerViewHolder);
                }
            }
            return;
        }

        setHeaderStatusRowVisible(
                headerViewHolder,
                headerViewHolder.loadingContainer,
                showLoadingIndicator);
        setHeaderStatusRowVisible(
                headerViewHolder,
                headerViewHolder.loadingFailedContainer,
                showFailure);
        setHeaderStatusRowVisible(
                headerViewHolder,
                headerViewHolder.emptyContainer,
                showEmptyState);
    }

    private void fadeLoadingIndicatorOut(HeaderViewHolder headerViewHolder) {
        fadeLoadingIndicatorOut(headerViewHolder, null);
    }

    private void fadeLoadingIndicatorOut(
            HeaderViewHolder headerViewHolder,
            @Nullable Runnable afterHeaderRelayout) {
        FrameLayout loadingContainer = headerViewHolder.loadingContainer;
        View loadingContent = loadingContainer.getChildAt(0);

        cancelHeaderStatusRowHeightAnimation(headerViewHolder, loadingContainer);
        loadingContent.animate().cancel();
        headerViewHolder.statusRowVisibilityTargets.put(loadingContainer, false);

        resetHeaderStatusRowContentTransform(loadingContent);
        setHeaderStatusRowContainerHeight(loadingContainer, ViewGroup.LayoutParams.WRAP_CONTENT);
        loadingContent.animate()
                .alpha(0f)
                .setDuration(HEADER_STATUS_ROW_FADE_OUT_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    Boolean showLoading = headerViewHolder.statusRowVisibilityTargets.get(
                            loadingContainer);
                    if (Boolean.TRUE.equals(showLoading)) {
                        return;
                    }
                    loadingContainer.setVisibility(GONE);
                    setHeaderStatusRowContainerHeight(
                            loadingContainer,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    loadingContent.setAlpha(1f);
                    if (afterHeaderRelayout != null) {
                        runAfterHeaderRelayout(headerViewHolder, afterHeaderRelayout);
                    }
                })
                .start();
    }

    /**
     * Fades and removes the initial loading row, then waits for the shorter header to be laid out
     * before allowing the first comment insert notifications to run.
     */
    public boolean fadeInitialLoadingIndicatorOutThen(@NonNull Runnable afterHeaderRelayout) {
        HeaderViewHolder headerViewHolder = boundHeaderViewHolder;
        if (headerViewHolder == null
                || !ViewCompat.isAttachedToWindow(headerViewHolder.itemView)
                || !headerViewHolder.statusRowsInitialized
                || headerViewHolder.loadingContainer.getVisibility() != VISIBLE
                || !Boolean.TRUE.equals(headerViewHolder.statusRowVisibilityTargets.get(
                headerViewHolder.loadingContainer))) {
            return false;
        }

        initialCommentsRevealPending = true;
        fadeLoadingIndicatorOut(headerViewHolder, () -> {
            try {
                afterHeaderRelayout.run();
            } finally {
                // Keep suppressing the loading row until comment application and its completion
                // updates have both run, so an intermediate header refresh cannot briefly animate
                // the row back in and push the newly inserted comments down.
                initialCommentsRevealPending = false;
            }
        });
        return true;
    }

    private void runAfterHeaderRelayout(
            HeaderViewHolder headerViewHolder,
            @NonNull Runnable afterHeaderRelayout) {
        View headerItem = headerViewHolder.itemView;
        ViewTreeObserver viewTreeObserver = headerItem.getViewTreeObserver();
        if (!viewTreeObserver.isAlive()) {
            headerItem.post(afterHeaderRelayout);
            return;
        }

        viewTreeObserver.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                ViewTreeObserver currentObserver = headerItem.getViewTreeObserver();
                if (currentObserver.isAlive()) {
                    currentObserver.removeOnPreDrawListener(this);
                }
                // RecyclerView may still be completing this layout pass. Queue the adapter
                // insertions after it, with the collapsed header geometry already committed.
                headerItem.post(afterHeaderRelayout);
                return true;
            }
        });
        headerItem.requestLayout();
    }

    private void fadeLoadingIndicatorToEmptyState(HeaderViewHolder headerViewHolder) {
        FrameLayout loadingContainer = headerViewHolder.loadingContainer;
        FrameLayout emptyContainer = headerViewHolder.emptyContainer;
        View loadingContent = loadingContainer.getChildAt(0);
        View emptyContent = emptyContainer.getChildAt(0);

        cancelHeaderStatusRowHeightAnimation(headerViewHolder, loadingContainer);
        cancelHeaderStatusRowHeightAnimation(headerViewHolder, emptyContainer);
        loadingContent.animate().cancel();
        emptyContent.animate().cancel();

        headerViewHolder.statusRowVisibilityTargets.put(loadingContainer, false);
        headerViewHolder.statusRowVisibilityTargets.put(emptyContainer, true);

        resetHeaderStatusRowContentTransform(loadingContent);
        resetHeaderStatusRowContentTransform(emptyContent);
        setHeaderStatusRowContainerHeight(loadingContainer, ViewGroup.LayoutParams.WRAP_CONTENT);
        setHeaderStatusRowContainerHeight(emptyContainer, ViewGroup.LayoutParams.WRAP_CONTENT);
        emptyContainer.setVisibility(GONE);

        loadingContent.animate()
                .alpha(0f)
                .setDuration(HEADER_STATUS_ROW_FADE_OUT_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    loadingContainer.setVisibility(GONE);
                    loadingContent.setAlpha(1f);

                    Boolean showEmpty = headerViewHolder.statusRowVisibilityTargets.get(emptyContainer);
                    if (!Boolean.TRUE.equals(showEmpty)) {
                        return;
                    }

                    emptyContent.setAlpha(0f);
                    emptyContent.setVisibility(VISIBLE);
                    emptyContainer.setVisibility(VISIBLE);
                    emptyContent.animate()
                            .alpha(1f)
                            .setDuration(HEADER_STATUS_ROW_FADE_IN_DURATION_MS)
                            .setInterpolator(new DecelerateInterpolator())
                            .start();
                })
                .start();
    }

    private void cancelHeaderStatusRowHeightAnimation(
            HeaderViewHolder headerViewHolder,
            FrameLayout container) {
        ValueAnimator animator = headerViewHolder.statusRowHeightAnimators.remove(container);
        if (animator != null) {
            animator.cancel();
        }
    }

    private void resetHeaderStatusRowContentTransform(View content) {
        content.setAlpha(1f);
        content.setScaleX(1f);
        content.setScaleY(1f);
        content.setTranslationY(0f);
    }

    private void setHeaderStatusRowContainerHeight(FrameLayout container, int height) {
        ViewGroup.LayoutParams layoutParams = container.getLayoutParams();
        layoutParams.height = height;
        container.setLayoutParams(layoutParams);
    }

    private void setHeaderStatusRowImmediately(
            HeaderViewHolder headerViewHolder,
            FrameLayout container,
            boolean visible) {
        cancelHeaderStatusRowHeightAnimation(headerViewHolder, container);
        headerViewHolder.statusRowVisibilityTargets.put(container, visible);

        View content = container.getChildAt(0);
        content.animate().cancel();
        resetHeaderStatusRowContentTransform(content);

        ViewGroup.LayoutParams layoutParams = container.getLayoutParams();
        if (visible) {
            content.setVisibility(VISIBLE);
            container.setVisibility(VISIBLE);
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            container.setVisibility(GONE);
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        container.setLayoutParams(layoutParams);
    }

    private void setHeaderStatusRowVisible(
            HeaderViewHolder headerViewHolder,
            FrameLayout container,
            boolean visible) {
        Boolean previousTarget = headerViewHolder.statusRowVisibilityTargets.get(container);
        if (previousTarget != null && previousTarget == visible) {
            return;
        }
        headerViewHolder.statusRowVisibilityTargets.put(container, visible);

        ValueAnimator previousAnimator = headerViewHolder.statusRowHeightAnimators.remove(container);
        if (previousAnimator != null) {
            previousAnimator.cancel();
        }

        View content = container.getChildAt(0);
        content.animate().cancel();
        int hiddenTranslationY = Utils.pxFromDpInt(
                container.getResources(),
                HEADER_STATUS_ROW_HIDDEN_TRANSLATION_Y_DP);

        if (visible) {
            int startHeight = container.getHeight();
            if (container.getVisibility() != VISIBLE) {
                ViewGroup.LayoutParams layoutParams = container.getLayoutParams();
                layoutParams.height = 0;
                container.setLayoutParams(layoutParams);
                container.setVisibility(VISIBLE);
                // getHeight() still reports the row's last laid-out height until the next layout
                // pass. Use the height we just assigned so the first reveal animates from zero,
                // just like subsequent reveals after a completed collapse.
                startHeight = 0;
                content.setAlpha(0f);
                content.setScaleX(HEADER_STATUS_ROW_HIDDEN_SCALE);
                content.setScaleY(HEADER_STATUS_ROW_HIDDEN_SCALE);
                content.setTranslationY(hiddenTranslationY);
            }
            content.setVisibility(VISIBLE);
            content.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(HEADER_STATUS_ROW_DURATION_MS)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            animateHeaderStatusRowHeight(
                    headerViewHolder,
                    container,
                    startHeight,
                    measureHeaderStatusRowHeight(container),
                    true);
            return;
        }

        if (container.getVisibility() != VISIBLE) {
            return;
        }

        content.animate()
                .alpha(0f)
                .scaleX(HEADER_STATUS_ROW_HIDDEN_SCALE)
                .scaleY(HEADER_STATUS_ROW_HIDDEN_SCALE)
                .translationY(-hiddenTranslationY)
                .setDuration(HEADER_STATUS_ROW_DURATION_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        animateHeaderStatusRowHeight(
                headerViewHolder,
                container,
                container.getHeight(),
                0,
                false);
    }

    private void animateHeaderStatusRowHeight(
            HeaderViewHolder headerViewHolder,
            FrameLayout container,
            int startHeight,
            int endHeight,
            boolean visibleAtEnd) {
        if (startHeight == endHeight) {
            finishHeaderStatusRowAnimation(headerViewHolder, container, visibleAtEnd);
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(Math.max(0, startHeight), endHeight);
        headerViewHolder.statusRowHeightAnimators.put(container, animator);
        animator.setDuration(HEADER_STATUS_ROW_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            ViewGroup.LayoutParams layoutParams = container.getLayoutParams();
            layoutParams.height = (int) animation.getAnimatedValue();
            container.setLayoutParams(layoutParams);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (headerViewHolder.statusRowHeightAnimators.get(container) != animator) {
                    return;
                }
                headerViewHolder.statusRowHeightAnimators.remove(container);
                Boolean target = headerViewHolder.statusRowVisibilityTargets.get(container);
                if (target == null || target != visibleAtEnd) {
                    return;
                }
                finishHeaderStatusRowAnimation(headerViewHolder, container, visibleAtEnd);
            }
        });
        animator.start();
    }

    private void finishHeaderStatusRowAnimation(
            HeaderViewHolder headerViewHolder,
            FrameLayout container,
            boolean visible) {
        View content = container.getChildAt(0);
        ViewGroup.LayoutParams layoutParams = container.getLayoutParams();
        if (visible) {
            content.setVisibility(VISIBLE);
            content.setAlpha(1f);
            content.setScaleX(1f);
            content.setScaleY(1f);
            content.setTranslationY(0f);
            container.setVisibility(VISIBLE);
        } else {
            container.setVisibility(GONE);
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            content.setAlpha(1f);
            content.setScaleX(1f);
            content.setScaleY(1f);
            content.setTranslationY(0f);
        }
        container.setLayoutParams(layoutParams);
    }

    private void setHeaderOpFilterVisibleImmediately(
            HeaderViewHolder headerViewHolder,
            boolean visible) {
        ValueAnimator animator = headerViewHolder.opFilterVisibilityAnimator;
        headerViewHolder.opFilterVisibilityAnimator = null;
        if (animator != null) {
            animator.cancel();
        }

        LinearLayout container = headerViewHolder.opFilterContainer;
        ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) container.getLayoutParams();
        if (!visible) {
            // The collapse animation has already reduced the margin to zero. Remove the view
            // before restoring its XML margin so the parent never sees an unanimated gap.
            container.setVisibility(GONE);
        }
        layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        layoutParams.bottomMargin = headerViewHolder.opFilterBaseBottomMargin;
        container.setLayoutParams(layoutParams);
        if (visible) {
            container.setVisibility(VISIBLE);
        }
        container.setAlpha(1f);
        container.setScaleX(1f);
        container.setScaleY(1f);
        container.setTranslationY(0f);
        headerViewHolder.opFilterResetButton.setEnabled(visible);
    }

    private void animateHeaderOpFilterVisible(
            HeaderViewHolder headerViewHolder,
            boolean visible) {
        LinearLayout container = headerViewHolder.opFilterContainer;
        ValueAnimator previousAnimator = headerViewHolder.opFilterVisibilityAnimator;
        headerViewHolder.opFilterVisibilityAnimator = null;
        if (previousAnimator != null) {
            previousAnimator.cancel();
        }

        int startHeight = container.getVisibility() == VISIBLE
                ? container.getHeight()
                : 0;
        float startAlpha = container.getVisibility() == VISIBLE
                ? container.getAlpha()
                : 0f;
        float startScale = container.getVisibility() == VISIBLE
                ? container.getScaleX()
                : HEADER_OP_FILTER_HIDDEN_SCALE;
        int hiddenTranslationY = Utils.pxFromDpInt(
                container.getResources(),
                HEADER_OP_FILTER_HIDDEN_TRANSLATION_Y_DP);
        float startTranslationY = container.getVisibility() == VISIBLE
                ? container.getTranslationY()
                : hiddenTranslationY;
        int endHeight = visible ? measureHeaderOpFilterHeight(headerViewHolder) : 0;
        ViewGroup.MarginLayoutParams initialLayoutParams =
                (ViewGroup.MarginLayoutParams) container.getLayoutParams();
        int startBottomMargin = container.getVisibility() == VISIBLE
                ? initialLayoutParams.bottomMargin
                : 0;
        int endBottomMargin = visible
                ? headerViewHolder.opFilterBaseBottomMargin
                : 0;

        if (visible) {
            initialLayoutParams.height = startHeight;
            initialLayoutParams.bottomMargin = startBottomMargin;
            container.setLayoutParams(initialLayoutParams);
            container.setVisibility(VISIBLE);
        }
        headerViewHolder.opFilterResetButton.setEnabled(visible);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        headerViewHolder.opFilterVisibilityAnimator = animator;
        animator.setDuration(HEADER_OP_FILTER_VISIBILITY_DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            ViewGroup.MarginLayoutParams layoutParams =
                    (ViewGroup.MarginLayoutParams) container.getLayoutParams();
            layoutParams.height = Math.round(startHeight + (endHeight - startHeight) * progress);
            layoutParams.bottomMargin = Math.round(
                    startBottomMargin
                            + (endBottomMargin - startBottomMargin) * progress);
            container.setLayoutParams(layoutParams);
            container.setAlpha(startAlpha + ((visible ? 1f : 0f) - startAlpha) * progress);
            float targetScale = visible ? 1f : HEADER_OP_FILTER_HIDDEN_SCALE;
            float scale = startScale + (targetScale - startScale) * progress;
            container.setScaleX(scale);
            container.setScaleY(scale);
            float targetTranslationY = visible ? 0f : -hiddenTranslationY;
            container.setTranslationY(
                    startTranslationY + (targetTranslationY - startTranslationY) * progress);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (headerViewHolder.opFilterVisibilityAnimator != animator) {
                    return;
                }
                headerViewHolder.opFilterVisibilityAnimator = null;
                setHeaderOpFilterVisibleImmediately(headerViewHolder, visible);
            }
        });
        animator.start();
    }

    private int measureHeaderOpFilterHeight(HeaderViewHolder headerViewHolder) {
        LinearLayout container = headerViewHolder.opFilterContainer;
        ViewGroup.MarginLayoutParams layoutParams =
                (ViewGroup.MarginLayoutParams) container.getLayoutParams();
        int availableWidth = headerViewHolder.itemView.getWidth();
        if (availableWidth <= 0) {
            availableWidth = container.getResources().getDisplayMetrics().widthPixels;
        }
        int containerWidth = Math.max(
                0,
                availableWidth - layoutParams.leftMargin - layoutParams.rightMargin);
        container.measure(
                View.MeasureSpec.makeMeasureSpec(containerWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return container.getMeasuredHeight();
    }

    private int measureHeaderStatusRowHeight(FrameLayout container) {
        View content = container.getChildAt(0);
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) content.getLayoutParams();
        int availableWidth = container.getWidth();
        if (availableWidth <= 0) {
            availableWidth = container.getResources().getDisplayMetrics().widthPixels;
        }
        int contentWidth = Math.max(
                0,
                availableWidth
                        - container.getPaddingLeft()
                        - container.getPaddingRight()
                        - layoutParams.leftMargin
                        - layoutParams.rightMargin);
        content.measure(
                View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return container.getPaddingTop()
                + layoutParams.topMargin
                + content.getMeasuredHeight()
                + layoutParams.bottomMargin
                + container.getPaddingBottom();
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
        return showHeaderPreviewImage
                && !storyHeaderLoading
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
                cacheHeaderPreviewState(appContext, story);
                notifyHeaderChanged();
                return;
            }

            setPreviewImageUrl(story, imageUrl);
            story.previewImageLoadFailed = false;
            cacheHeaderPreviewState(appContext, story);
            notifyHeaderChanged();
        });
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        getTypography(recyclerView.getContext());
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        storyHeaderRevealPending = false;
        cancelStoryHeaderRevealAnimation();
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

        PreviewImageFailureAnimator.cancel(previewImage);
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
                            animateHeaderPreviewImageFailure(headerViewHolder);
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
        int defaultHeight = Utils.pxFromDpInt(
                previewImage.getResources(),
                HEADER_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP);
        return viewHeight > 0 ? Math.max(viewHeight, defaultHeight) : defaultHeight;
    }

    private void updateHeaderPreviewImageLayout(HeaderViewHolder headerViewHolder, Drawable drawable) {
        if (headerViewHolder.previewImage == null || drawable == null) {
            return;
        }

        PreviewImageLayoutUtils.applyWideImageHeight(
                headerViewHolder.previewImage,
                drawable,
                HEADER_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP);
    }

    private void setHeaderPreviewImageVisibility(HeaderViewHolder headerViewHolder, int visibility) {
        if (headerViewHolder.previewImage == null) {
            return;
        }

        applyHeaderPreviewImagePadding(headerViewHolder, visibility != GONE);
        int resolvedVisibility = headerPreviewImageSuppressed && visibility == VISIBLE
                ? View.INVISIBLE : visibility;
        if (headerViewHolder.previewImage.getVisibility() != resolvedVisibility) {
            headerViewHolder.previewImage.setVisibility(resolvedVisibility);
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
        int defaultHeight = Utils.pxFromDpInt(
                previewImage.getResources(),
                HEADER_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP);
        ViewGroup.LayoutParams layoutParams = previewImage.getLayoutParams();
        boolean hasDefaultHeight = layoutParams == null || layoutParams.height == defaultHeight;
        if (previewImage.getVisibility() == GONE
                && previewImage.getTag() == null
                && previewImage.getDrawable() == null
                && Float.compare(previewImage.getAlpha(), 1f) == 0
                && hasDefaultHeight
                && headerViewHolder.headerView.getPaddingTop()
                == headerViewHolder.headerBasePaddingTop) {
            return;
        }

        PreviewImageFailureAnimator.cancel(previewImage);
        applyHeaderPreviewImagePadding(headerViewHolder, false);
        CoilUtils.dispose(previewImage);
        previewImage.animate().cancel();
        previewImage.clearAnimation();
        previewImage.setTag(null);
        previewImage.setAlpha(1f);
        previewImage.setImageDrawable(null);
        previewImage.setVisibility(GONE);
        PreviewImageLayoutUtils.resetHeight(
                previewImage,
                HEADER_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP);
    }

    private void animateHeaderPreviewImageFailure(HeaderViewHolder headerViewHolder) {
        ImageView previewImage = headerViewHolder.previewImage;
        if (previewImage == null || previewImage.getVisibility() == GONE) {
            resetHeaderPreviewImage(headerViewHolder);
            return;
        }

        int startPaddingTop = headerViewHolder.headerView.getPaddingTop();
        int targetPaddingTop = headerViewHolder.headerBasePaddingTop;
        PreviewImageFailureAnimator.collapse(
                previewImage,
                PreviewImageFailureAnimator.Axis.VERTICAL,
                progress -> {
                    int topPadding = Math.round(startPaddingTop
                            + (targetPaddingTop - startPaddingTop) * progress);
                    headerViewHolder.headerView.setPadding(
                            headerViewHolder.headerView.getPaddingLeft(),
                            topPadding,
                            headerViewHolder.headerView.getPaddingRight(),
                            headerViewHolder.headerView.getPaddingBottom());
                },
                () -> resetHeaderPreviewImage(headerViewHolder));
    }

    private static boolean isCurrentHeaderPreviewTarget(ImageView previewImage, String imageUrl) {
        return imageUrl.equals(previewImage.getTag());
    }

    private void bindHeaderTint(HeaderViewHolder headerViewHolder) {
        int baseColor = getPreviewTintBaseColor(headerViewHolder.itemView);
        if (shouldTintHeader()) {
            hydrateCachedHeaderPreviewTintColor(
                    headerViewHolder.itemView.getContext(),
                    story,
                    baseColor);
        }
        applyHeaderBackground(headerViewHolder);
        if (shouldUseHeaderFaviconTint(story)
                && !shouldUseHeaderPreviewTint(story, baseColor)) {
            loadHeaderFaviconTintColor(headerViewHolder.itemView.getContext(), story, headerViewHolder);
        }
    }

    private void applyHeaderBackground(HeaderViewHolder headerViewHolder) {
        if (headerViewHolder == null) {
            return;
        }

        int normalColor = getNormalHeaderBackgroundColor(headerViewHolder.itemView);
        int previewTintBaseColor = getPreviewTintBaseColor(headerViewHolder.itemView);
        int targetColor = getHeaderTintColor(
                story,
                normalColor,
                previewTintBaseColor,
                getDefaultHeaderTintColor(headerViewHolder.itemView));
        int color = ColorUtils.blendARGB(normalColor, targetColor, headerSlideOffset);
        currentHeaderContentBackgroundColor = color;
        int visibleColor = ColorUtils.blendARGB(normalColor, targetColor, getEffectiveHeaderTintProgress());
        boolean hasTint = shouldTintHeader();
        applyHeaderContentSideInsets(headerViewHolder);
        headerViewHolder.itemView.setBackgroundColor(normalColor);
        headerViewHolder.spacer.setBackgroundColor(visibleColor);
        headerViewHolder.sheetHandleContainer.setBackgroundColor(visibleColor);
        headerViewHolder.sheetButtonsContainer.setBackgroundColor(visibleColor);
        headerViewHolder.headerView.setBackgroundColor(color);
        headerViewHolder.summaryContainer.setBackgroundColor(color);
        headerViewHolder.actionsContainer.setBackgroundColor(color);
        if (headerBackgroundColorListener != null) {
            headerBackgroundColorListener.onHeaderBackgroundColorChanged(visibleColor);
        }
        applyHeaderBottomTransition(headerViewHolder, normalColor, visibleColor, hasTint);
    }

    private int getHeaderTintColor(
            Story story,
            int normalColor,
            int previewTintBaseColor,
            int defaultTintColor) {
        if (shouldUseHeaderPreviewTint(story, previewTintBaseColor)) {
            return story.previewImageTintColor;
        }
        if (shouldUseHeaderFaviconTint(story)
                && story.faviconTintColorLoaded
                && isFaviconTintColorCurrent(story, previewTintBaseColor)) {
            return story.faviconTintColor;
        }
        return shouldTintHeader()
                ? defaultTintColor
                : normalColor;
    }

    private int getDefaultHeaderTintColor(View view) {
        return MaterialColors.getColor(
                view,
                R.attr.storyCardBackgroundColor,
                getPreviewTintBaseColor(view));
    }

    private void applyHeaderBottomTransition(
            HeaderViewHolder headerViewHolder,
            int normalColor,
            int headerColor,
            boolean showTintFade) {
        headerViewHolder.divider.setVisibility(showTintFade ? GONE : VISIBLE);
        headerViewHolder.tintFade.setVisibility(showTintFade ? VISIBLE : GONE);
        if (!showTintFade) {
            headerViewHolder.tintFade.setBackground(null);
            return;
        }

        int[] colors = new int[HEADER_BOTTOM_FADE_STOP_COUNT];
        for (int i = 0; i < colors.length; i++) {
            float position = i / (float) (colors.length - 1);
            float progress = position * position * (3f - 2f * position);
            colors[i] = ColorUtils.blendARGB(headerColor, normalColor, progress);
        }
        GradientDrawable fade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                colors);
        headerViewHolder.tintFade.setBackground(fade);
    }

    private void applyHeaderContentSideInsets(HeaderViewHolder headerViewHolder) {
        headerViewHolder.setHeaderContentSideInsets(
                headerContentInsetLeft,
                headerContentInsetRight);
    }

    private int getNormalHeaderBackgroundColor(View view) {
        return ContextCompat.getColor(view.getContext(), ThemeUtils.getBackgroundColorResource(view.getContext()));
    }

    private int getPreviewTintBaseColor(View view) {
        return PreviewImageTintUtils.getTintBaseColor(view.getContext());
    }

    private int getPreviewTintBaseColor(Context context) {
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
        if (!shouldTintHeader() || context == null || story == null || drawable == null) {
            return;
        }

        boolean updated = PreviewImageTintUtils.updateStoryPreviewImageTintColor(
                story,
                imageUrl,
                drawable,
                getPreviewTintBaseColor(context),
                paletteTintMode);
        if (updated) {
            cacheHeaderPreviewState(context, story);
        }
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

        int baseColor = getPreviewTintBaseColor(context);
        if (story.faviconTintColorLoaded && !isFaviconTintColorCurrent(story, baseColor)) {
            story.faviconTintColorLoaded = false;
            story.faviconTintColorLoading = false;
            story.faviconTintColorLoadFailed = false;
        }

        boolean loadedBeforeHydration = story.faviconTintColorLoaded;
        if (!loadedBeforeHydration) {
            hydrateCachedHeaderFaviconTintColor(context, story, faviconUrl, baseColor);
        }

        if (story.faviconTintColorLoaded) {
            if (!loadedBeforeHydration) {
                if (headerViewHolder == boundHeaderViewHolder) {
                    applyHeaderBackground(headerViewHolder);
                } else {
                    notifyHeaderChanged();
                }
            }
            return;
        }

        if (story.faviconTintColorLoading || story.faviconTintColorLoadFailed) {
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
                        if (story.faviconTintColorLoaded) {
                            cacheHeaderPreviewState(context, story);
                        }
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
            int baseColor = getPreviewTintBaseColor(context);
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

    private void hydrateCachedHeaderFaviconTintColor(
            Context context,
            Story story,
            String faviconUrl,
            int baseColor) {
        Integer tintColor = StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                context,
                story.id,
                faviconUrl,
                baseColor);
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

    private void cacheHeaderPreviewState(@Nullable Context context, Story story) {
        if (context == null || story == null) {
            return;
        }

        Context appContext = context.getApplicationContext();
        Utils.cacheStoryPreviewState(appContext, story);
        if (story.previewImageTintColorLoaded && !TextUtils.isEmpty(story.previewImageTintSourceUrl)) {
            StoryPreviewImageLoader.saveCachedPreviewImageTintColor(
                    appContext,
                    story.id,
                    story.previewImageTintSourceUrl,
                    story.previewImageTintBaseColor,
                    story.previewImageTintColor);
        }
        if (story.faviconTintColorLoaded && !TextUtils.isEmpty(story.faviconTintSourceUrl)) {
            StoryPreviewImageLoader.saveCachedPreviewImageTintColor(
                    appContext,
                    story.id,
                    story.faviconTintSourceUrl,
                    story.faviconTintBaseColor,
                    story.faviconTintColor);
        }
    }

    private boolean shouldUseHeaderPreviewTint(Story story, int baseColor) {
        PreviewImageTintUtils.syncStoryPreviewImageTintColorFromCache(story, baseColor, paletteTintMode);
        return shouldTintHeader()
                && story != null
                && showHeaderPreviewImage
                && !story.previewImageLoadFailed
                && PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(story, baseColor, paletteTintMode);
    }

    private boolean shouldUseHeaderFaviconTint(Story story) {
        return shouldTintHeader()
                && story != null
                && showThumbnail
                && story.loaded
                && !story.loadingFailed
                && !story.isComment
                && !TextUtils.isEmpty(story.url);
    }

    private boolean isFaviconTintColorCurrent(Story story, int baseColor) {
        return story != null
                && story.faviconTintColorLoaded
                && TextUtils.equals(story.faviconTintSourceUrl, getFaviconTintSourceUrl(story))
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

        int baseColor = getPreviewTintBaseColor(context);
        return PreviewImageTintUtils.isStoryPreviewImageTintColorCurrent(story, baseColor, paletteTintMode)
                || PreviewImageTintUtils.syncStoryPreviewImageTintColorFromCache(story, baseColor, paletteTintMode)
                || hydrateCachedHeaderPreviewTintColor(context, story, baseColor);
    }

    private boolean hydrateCachedHeaderPreviewTintColor(Context context, Story story, int baseColor) {
        if (story == null || TextUtils.isEmpty(story.previewImageUrl)) {
            return false;
        }

        Integer tintColor = StoryPreviewImageLoader.loadCachedPreviewImageTintColor(
                context,
                story.id,
                story.previewImageUrl,
                baseColor);
        return tintColor != null
                && PreviewImageTintUtils.applyCachedStoryPreviewImageTintColor(
                        story,
                        story.previewImageUrl,
                        baseColor,
                        paletteTintMode,
                        tintColor);
    }

    private boolean shouldTintHeader() {
        return tintHeader;
    }

    private void notifyHeaderChanged() {
        notifyItemChanged(0);
    }

    private void bindStoryText(HeaderViewHolder headerViewHolder) {
        if (storyHeaderLoading) {
            headerViewHolder.textView.setVisibility(GONE);
            bindReferenceLinks(headerViewHolder.referenceLinksContainer, null);
            return;
        }
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
            setReferenceLinksContainerTopMargin(
                    itemViewHolder.referenceLinksContainer,
                    REFERENCE_LINKS_CONTAINER_TOP_MARGIN_DP);
            itemViewHolder.referenceLinksVisible = bindReferenceLinks(itemViewHolder.referenceLinksContainer, null);
            return;
        }

        String expandedCommentText = comment.getExpandedAnchorText();
        CollectedReferenceLinks.Result referenceLinks = null;
        if (collectReferenceLinks) {
            referenceLinks = getCommentReferenceLinks(comment);
        }

        boolean hasCollectedLinks = referenceLinks != null && referenceLinks.hasLinks();
        if (hasCollectedLinks && referenceLinks.hasInterleavedLinks()) {
            bindInterleavedCommentContent(itemViewHolder, referenceLinks);
            bindInterleavedHiddenCommentPreview(itemViewHolder, comment);
            return;
        }

        setReferenceLinksContainerTopMargin(
                itemViewHolder.referenceLinksContainer,
                REFERENCE_LINKS_CONTAINER_TOP_MARGIN_DP);
        String bodyHtml = hasCollectedLinks ? referenceLinks.getBodyHtml() : expandedCommentText;
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
        } else {
            itemViewHolder.commentBody.setText("");
        }

        if (collapseParent) {
            itemViewHolder.commentHiddenText.setText(" • " + Html.fromHtml(comment.text.substring(0, Math.min(120, comment.text.length()))));
        }

        itemViewHolder.referenceLinksVisible = bindReferenceLinks(itemViewHolder.referenceLinksContainer, referenceLinks);
    }

    private void bindInterleavedCommentContent(
            ItemViewHolder itemViewHolder,
            CollectedReferenceLinks.Result referenceLinks) {
        itemViewHolder.referenceLinksContainer.removeAllViews();
        setReferenceLinksContainerTopMargin(itemViewHolder.referenceLinksContainer, 0);

        List<CollectedReferenceLinks.ContentBlock> blocks = referenceLinks.getContentBlocks();
        int firstContainerBlock = 0;
        if (!blocks.isEmpty() && !blocks.get(0).isLink()) {
            String bodyHtml = blocks.get(0).getBodyHtml();
            itemViewHolder.commentBodyHasText = !TextUtils.isEmpty(bodyHtml);
            if (itemViewHolder.commentBodyHasText) {
                itemViewHolder.commentBody.setHtml(bodyHtml);
            } else {
                itemViewHolder.commentBody.setText("");
            }
            firstContainerBlock = 1;
        } else {
            itemViewHolder.commentBodyHasText = false;
            itemViewHolder.commentBody.setText("");
        }

        for (int i = firstContainerBlock; i < blocks.size(); i++) {
            CollectedReferenceLinks.ContentBlock block = blocks.get(i);
            if (block.isLink()) {
                View row = createReferenceLinkRow(itemViewHolder.referenceLinksContainer, block.getLink());
                setInterleavedReferenceLinkMargins(row);
                itemViewHolder.referenceLinksContainer.addView(row);
            } else if (!TextUtils.isEmpty(block.getBodyHtml())) {
                itemViewHolder.referenceLinksContainer.addView(
                        createInterleavedCommentBodyView(itemViewHolder, block.getBodyHtml()));
            }
        }

        itemViewHolder.referenceLinksVisible = itemViewHolder.referenceLinksContainer.getChildCount() > 0;
        itemViewHolder.referenceLinksContainer.setVisibility(itemViewHolder.referenceLinksVisible ? VISIBLE : GONE);
    }

    private HtmlTextView createInterleavedCommentBodyView(ItemViewHolder itemViewHolder, String bodyHtml) {
        Context context = itemViewHolder.referenceLinksContainer.getContext();
        HtmlTextView body = new HtmlTextView(context);
        body.setTextColor(MaterialColors.getColor(itemViewHolder.referenceLinksContainer, R.attr.storyColorNormal));
        configureCommentBodyInteractions(itemViewHolder, body);
        body.setHtml(bodyHtml);
        getTypography(context).applyCommentText(body);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Utils.pxFromDpInt(
                context.getResources(),
                INTERLEAVED_COMMENT_TEXT_TOP_MARGIN_DP);
        body.setLayoutParams(params);
        return body;
    }

    private void configureCommentBodyInteractions(ItemViewHolder itemViewHolder, HtmlTextView body) {
        body.setOnLongClickListener(v -> {
            if (swapLongPressTap) {
                commentClickListener.onItemClick(
                        itemViewHolder.comment,
                        itemViewHolder.getAbsoluteAdapterPosition(),
                        itemViewHolder.getCommentActionSourceView());
            } else {
                commentLongClickListener.onItemClick(
                        itemViewHolder.comment,
                        itemViewHolder.getAbsoluteAdapterPosition(),
                        itemViewHolder.getCommentActionSourceView());
            }
            return true;
        });
        body.setOnClickListener(v -> {
            if (swapLongPressTap) {
                commentLongClickListener.onItemClick(
                        itemViewHolder.comment,
                        itemViewHolder.getAbsoluteAdapterPosition(),
                        itemViewHolder.getCommentActionSourceView());
            } else {
                commentClickListener.onItemClick(
                        itemViewHolder.comment,
                        itemViewHolder.getAbsoluteAdapterPosition(),
                        itemViewHolder.getCommentActionSourceView());
            }
        });
        body.setOnClickATagListener(new OnClickATagListener() {
            @Override
            public boolean onClick(View widget, String spannedText, @Nullable String href) {
                if (disableCommentATagClick) return true;

                Utils.openLinkMaybeHN(widget.getContext(), href);
                return true;
            }
        });
        body.setOnLongClickATagListener(new OnLongClickATagListener() {
            @Override
            public boolean onLongClick(
                    View widget,
                    String spannedText,
                    @Nullable String href,
                    @NonNull RectF sourceBounds) {
                if (disableCommentATagClick || referenceLinkLongClickListener == null
                        || TextUtils.isEmpty(href)) {
                    return disableCommentATagClick;
                }
                referenceLinkLongClickListener.onLongClick(
                        href, spannedText, widget, sourceBounds, null);
                return true;
            }
        });
    }

    private void bindInterleavedHiddenCommentPreview(ItemViewHolder itemViewHolder, Comment comment) {
        if (collapseParent) {
            itemViewHolder.commentHiddenText.setText(" \u2022 " + Html.fromHtml(comment.text.substring(0, Math.min(120, comment.text.length()))));
        }
    }

    private void setReferenceLinksContainerTopMargin(LinearLayout container, int marginDp) {
        setTopMargin(container, Utils.pxFromDpInt(container.getResources(), marginDp));
    }

    private void setInterleavedReferenceLinkMargins(View view) {
        int margin = Utils.pxFromDpInt(
                view.getResources(),
                INTERLEAVED_REFERENCE_LINK_TOP_MARGIN_DP);
        int bottomMargin = Utils.pxFromDpInt(
                view.getResources(),
                INTERLEAVED_REFERENCE_LINK_BOTTOM_MARGIN_DP);
        setVerticalMargins(view, margin, bottomMargin);
    }

    private void setTopMargin(View view, int topMargin) {
        setVerticalMargins(view, topMargin, null);
    }

    private void setVerticalMargins(View view, int topMargin, @Nullable Integer bottomMargin) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
            boolean changed = marginLayoutParams.topMargin != topMargin;
            if (changed) {
                marginLayoutParams.topMargin = topMargin;
            }
            if (bottomMargin != null && marginLayoutParams.bottomMargin != bottomMargin) {
                marginLayoutParams.bottomMargin = bottomMargin;
                changed = true;
            }
            if (changed) {
                view.setLayoutParams(marginLayoutParams);
            }
        }
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
            comment.collectedReferenceLinks =
                    CollectedReferenceLinks.parse(comment.getExpandedAnchorText());
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
        prefetchHackerNewsReferenceIfNeeded(container.getContext(), link);
        View row = ReferenceLinkRowUtils.createReferenceLinkRow(
                container,
                link,
                font,
                Math.max(12f, preferredTextSize - 2f),
                getReferenceLinkContentDescription(link),
                faviconProvider,
                v -> Utils.openLinkMaybeHN(v.getContext(), link.getUrl()));
        row.setOnLongClickListener(v -> {
            if (referenceLinkLongClickListener == null) {
                return false;
            }
            referenceLinkLongClickListener.onLongClick(
                    link.getUrl(),
                    ReferenceLinkRowUtils.getReferenceLinkLabel(link),
                    v,
                    null,
                    link);
            return true;
        });
        return row;
    }

    private String getReferenceLinkContentDescription(CollectedReferenceLinks.ReferenceLink link) {
        String label = ReferenceLinkRowUtils.getReferenceLinkLabel(link);
        if (link.hasNumber()) {
            return "Open reference link " + link.getNumber() + ": " + label;
        }
        return "Open link: " + label;
    }

    private void prefetchHackerNewsReferenceIfNeeded(
            Context context,
            CollectedReferenceLinks.ReferenceLink link) {
        int itemId = getHackerNewsReferenceItemId(link.getUrl());
        if (itemId <= 0) {
            return;
        }

        String cachedTitle = hackerNewsReferenceTitlesByItemId.get(itemId);
        if (!TextUtils.isEmpty(cachedTitle)) {
            link.setResolvedTitle(cachedTitle);
        }

        LinkSummaryLoader.Result cachedSummary =
                StoryPreviewImageLoader.getCachedLinkSummary(context, link.getUrl());
        if (LinkSummaryLoader.isHackerNewsItemResult(cachedSummary)) {
            if (!TextUtils.isEmpty(cachedSummary.title)) {
                hackerNewsReferenceTitlesByItemId.put(itemId, cachedSummary.title);
                link.setResolvedTitle(cachedSummary.title);
            }
            return;
        }

        if (!requestedHackerNewsReferenceUrls.add(link.getUrl())) {
            return;
        }

        LinkSummaryLoader.load(
                context,
                link.getUrl(),
                ReferenceLinkRowUtils.getReferenceLinkLabel(link),
                new LinkSummaryLoader.Callback() {
                    @Override
                    public void onSuccess(@NonNull LinkSummaryLoader.Result result) {
                        if (TextUtils.isEmpty(result.title)) {
                            return;
                        }

                        hackerNewsReferenceTitlesByItemId.put(itemId, result.title);
                        applyHackerNewsReferenceTitle(itemId, result.title);
                        notifyHackerNewsReferenceTitleChanged(itemId);
                    }

                    @Override
                    public void onFailure(@NonNull String message) {
                    }
                });
    }

    private int getHackerNewsReferenceItemId(String url) {
        if (TextUtils.isEmpty(url)) {
            return -1;
        }

        Uri uri = Uri.parse(url);
        if (!Utils.isHackerNewsItemUri(uri)) {
            return -1;
        }

        String fragment = uri.getFragment();
        String itemId = !TextUtils.isEmpty(fragment) && TextUtils.isDigitsOnly(fragment)
                ? fragment
                : uri.getQueryParameter("id");
        if (TextUtils.isEmpty(itemId)) {
            return -1;
        }

        try {
            return Integer.parseInt(itemId);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void applyHackerNewsReferenceTitle(int itemId, String title) {
        if (story != null && story.collectedReferenceLinks != null) {
            applyHackerNewsReferenceTitle(story.collectedReferenceLinks, itemId, title);
        }

        for (Comment comment : comments) {
            if (comment.collectedReferenceLinks != null) {
                applyHackerNewsReferenceTitle(comment.collectedReferenceLinks, itemId, title);
            }
        }
    }

    private void applyHackerNewsReferenceTitle(
            CollectedReferenceLinks.Result referenceLinks,
            int itemId,
            String title) {
        for (CollectedReferenceLinks.ReferenceLink link : referenceLinks.getLinks()) {
            if (getHackerNewsReferenceItemId(link.getUrl()) == itemId) {
                link.setResolvedTitle(title);
            }
        }
    }

    private void notifyHackerNewsReferenceTitleChanged(int itemId) {
        if (story != null && story.collectedReferenceLinks != null
                && hasHackerNewsReferenceLink(story.collectedReferenceLinks, itemId)) {
            notifyItemChanged(0);
        }

        for (int i = 1; i < comments.size(); i++) {
            Comment comment = comments.get(i);
            if (comment.collectedReferenceLinks != null
                    && hasHackerNewsReferenceLink(comment.collectedReferenceLinks, itemId)
                    && isCommentViewType(getItemViewType(i))) {
                notifyItemChanged(i);
            }
        }
    }

    private boolean hasHackerNewsReferenceLink(CollectedReferenceLinks.Result referenceLinks, int itemId) {
        for (CollectedReferenceLinks.ReferenceLink link : referenceLinks.getLinks()) {
            if (getHackerNewsReferenceItemId(link.getUrl()) == itemId) {
                return true;
            }
        }
        return false;
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
        if (itemViewHolder.commentHighlightInitialized
                && itemViewHolder.commentHighlighted == highlighted) {
            return;
        }

        if (itemViewHolder.commentCard instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) itemViewHolder.commentCard;
            card.setCardBackgroundColor(highlighted
                    ? itemViewHolder.highlightedCommentBackgroundColor
                    : itemViewHolder.defaultCommentBackgroundColor);
        } else {
            itemViewHolder.itemView.setBackgroundColor(highlighted
                    ? itemViewHolder.highlightedCommentBackgroundColor
                    : Color.TRANSPARENT);
        }
        itemViewHolder.commentHighlighted = highlighted;
        itemViewHolder.commentHighlightInitialized = true;
    }

    private void applyCommentCardChrome(@NonNull ItemViewHolder itemViewHolder) {
        if (!(itemViewHolder.commentCard instanceof MaterialCardView)) {
            return;
        }

        MaterialCardView card = (MaterialCardView) itemViewHolder.commentCard;
        int strokeWidth = cardBorder ? Utils.pxFromDpInt(card.getResources(), 1) : 0;
        int strokeColor = cardBorder
                ? MaterialColors.getColor(card, R.attr.commentDividerColor, Color.TRANSPARENT)
                : Color.TRANSPARENT;
        float elevation = cardBorder ? Utils.pxFromDpInt(card.getResources(), 1) : 0f;
        card.setStrokeWidth(strokeWidth);
        card.setStrokeColor(strokeColor);
        card.setCardElevation(elevation);
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
                bookmarked ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark,
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
                bookmarked ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark,
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

    public boolean isStoryFavoriteLoading() {
        return storyFavoriteLoading;
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

    public boolean isStoryVoteLoading() {
        return storyVoteLoading;
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
                upvoted ? R.drawable.ic_thumb_up_filled : R.drawable.ic_thumb_up,
                upvoted ? "Remove vote" : "Vote",
                animate);
    }

    private void showHeaderFavoriteLoading(ImageButton button, boolean favorite, boolean animate) {
        String label = favorite ? "Adding favorite" : "Removing favorite";
        showHeaderActionLoading(button, label, animate);
    }

    private void showHeaderSummaryLoading(ImageButton button, boolean animate) {
        showHeaderActionLoading(button, "Summarizing", animate);
    }

    private void showHeaderSummaryButton(ImageButton button, boolean animate) {
        showHeaderActionButton(button, R.drawable.ic_auto_awesome, "Summarize", animate);
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
                favorited ? R.drawable.ic_star_filled : R.drawable.ic_star,
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
        public final LinearLayout commentMetaContainer;
        public final TextView commentHiddenCount;
        public final TextView commentHiddenText;
        public final View commentIndentIndicator;
        public final View commentCard;
        public final LinearLayout referenceLinksContainer;
        public boolean commentBodyHasText = true;
        public boolean referenceLinksVisible = false;
        public Comment comment;
        private int commentViewStyleGeneration = -1;
        private int opCommentColor;
        private int selfCommentColor;
        private int defaultCommentMetaColor;
        private int defaultCommentBackgroundColor;
        private int highlightedCommentBackgroundColor;
        private boolean commentHighlightInitialized;
        private boolean commentHighlighted;

        public ItemViewHolder(CommentsItemBinding binding) {
            this(
                    binding.getRoot(),
                    binding.commentBody,
                    binding.commentBy,
                    binding.commentByTime,
                    binding.commentMetaContainer,
                    binding.commentHiddenCount,
                    binding.commentHiddenShort,
                    binding.commentIndentIndicator,
                    null,
                    binding.commentReferenceLinksContainer);
        }

        public ItemViewHolder(CommentsItemCardBinding binding) {
            this(
                    binding.getRoot(),
                    binding.commentBody,
                    binding.commentBy,
                    binding.commentByTime,
                    binding.commentMetaContainer,
                    binding.commentHiddenCount,
                    binding.commentHiddenShort,
                    binding.commentIndentIndicator,
                    binding.commentCard,
                    binding.commentReferenceLinksContainer);
        }

        private ItemViewHolder(View view,
                               HtmlTextView body,
                               TextView by,
                               TextView byTime,
                               LinearLayout metaContainer,
                               TextView hiddenCount,
                               TextView hiddenText,
                               View indentIndicator,
                               @Nullable View card,
                               LinearLayout linksContainer) {
            super(view);
            commentBody = body;
            commentBy = by;
            commentByTime = byTime;
            commentMetaContainer = metaContainer;
            commentHiddenCount = hiddenCount;
            commentHiddenText = hiddenText;
            commentIndentIndicator = indentIndicator;
            commentCard = card;
            referenceLinksContainer = linksContainer;

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
            commentBody.setOnLongClickATagListener(new OnLongClickATagListener() {
                @Override
                public boolean onLongClick(
                        View widget,
                        String spannedText,
                        @Nullable String href,
                        @NonNull RectF sourceBounds) {
                    if (disableCommentATagClick || referenceLinkLongClickListener == null
                            || TextUtils.isEmpty(href)) {
                        return disableCommentATagClick;
                    }
                    referenceLinkLongClickListener.onLongClick(
                            href, spannedText, widget, sourceBounds, null);
                    return true;
                }
            });
        }

        public View getCommentActionSourceView() {
            return commentCard != null ? commentCard : itemView;
        }

        private void tapped(Comment comment, int pos, View v) {
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            if (swapLongPressTap) {
                commentLongClickListener.onItemClick(comment, pos, v);
            } else {
                commentClickListener.onItemClick(comment, pos, v);
            }
        }

        private void longPressed(Comment comment, int pos, View v) {
            if (pos == RecyclerView.NO_POSITION) {
                return;
            }
            if (swapLongPressTap) {
                commentClickListener.onItemClick(comment, pos, v);
            } else {
                commentLongClickListener.onItemClick(comment, pos, v);
            }
        }
    }

    public class HeaderViewHolder extends RecyclerView.ViewHolder {
        public final CommentsHeaderBinding headerBinding;
        public LinkPreviewArxivBinding arxivBinding;
        public LinkPreviewGithubBinding githubBinding;
        public LinkPreviewGitlabBinding gitLabBinding;
        public LinkPreviewNitterBinding nitterBinding;
        public LinkPreviewStackExchangeBinding stackExchangeBinding;
        public LinkPreviewWikipediaBinding wikiBinding;
        public final View mView;
        public final TextView titleView;
        public final LinearLayout titleShimmer;
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
        public LinearLayout infoContainer;
        public TextView arxivAbstract;
        public LinearLayout githubContainer;
        public LinearLayout gitLabContainer;
        public LinearLayout arxivContainer;
        public LinearLayout stackExchangeContainer;
        public LinearLayout wikiContainer;
        public LinearLayout nitterContainer;

        public TextView infoHeader;
        public LinearLayout linkPreviewLoadingContainer;
        public LinearLayout linkPreviewContentContainer;
        public ValueAnimator linkPreviewHeightAnimator;
        public int linkPreviewAnimationGeneration;
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
        public final ImageButton headerRefreshButton;
        public final ImageButton summarizeButton;
        public final RelativeLayout summarizeButtonParent;
        public final LinearLayout summaryContainer;
        public final LinearLayout summaryContentContainer;
        public final TextView summary;
        public final TextView summaryTitle;
        public final TextView summaryDebugInfo;
        public final ImageButton moreButton;
        public final RelativeLayout userButtonParent;
        public final RelativeLayout moreButtonParent;
        public final RelativeLayout refreshButtonParent;
        public final RelativeLayout commentButtonParent;
        public final RelativeLayout voteButtonParent;
        public final RelativeLayout favoriteButtonParent;
        public final RelativeLayout bookmarkButtonParent;
        public final View divider;
        public final View tintFade;
        public final View spacer;
        public TextView githubAbout;
        public HtmlTextView githubWebsite;
        public TextView githubLicense;
        public TextView githubLanguage;
        public TextView githubStars;
        public TextView githubWatching;
        public TextView githubForks;
        public LinearLayout githubWebsiteContainer;
        public LinearLayout githubLicenseContainer;
        public LinearLayout githubLanguageContainer;

        public TextView gitLabDescription;
        public HtmlTextView gitLabWebsite;
        public TextView gitLabVisibility;
        public TextView gitLabLanguage;
        public TextView gitLabStars;
        public TextView gitLabForks;
        public LinearLayout gitLabWebsiteContainer;
        public LinearLayout gitLabVisibilityContainer;
        public LinearLayout gitLabLanguageContainer;

        public TextView stackExchangeTitle;
        public TextView stackExchangeBy;
        public TextView stackExchangeScore;
        public TextView stackExchangeAnswers;
        public TextView stackExchangeViews;
        public TextView stackExchangeAnswerState;
        public TextView stackExchangeAuthor;
        public TextView stackExchangeTags;
        public LinearLayout stackExchangeTagsContainer;

        public TextView arxivBy;
        public TextView arxivDate;
        public TextView arxivSubjects;
        public ImageView arxivByIcon;
        public Button arxivDownloadButton;

        public HtmlTextView wikiSummary;

        public HtmlTextView nitterText;
        public Button nitterButton;
        public TextView nitterDate;
        public TextView nitterReplyCount;
        public TextView nitterReposts;
        public TextView nitterLikes;
        public ImageView nitterLikesImageView;
        public ImageView nitterRetweetImageView;
        public ImageView nitterReplyImageView;

        public FrameLayout nitterMediaContainer;
        public ImageView nitterImage;
        public TextView nitterVideoLabel;

        public final ImageView favicon;
        public final ImageView previewImage;
        public final RelativeLayout sheetRefreshButton;
        public final RelativeLayout sheetExpandButton;
        public final RelativeLayout sheetBrowserButton;
        public final RelativeLayout sheetReaderContainer;
        public final RelativeLayout sheetReaderButton;
        public final ImageView sheetReaderIcon;
        public final RelativeLayout sheetInvertButton;
        public final View sheetHandleContainer;
        public final LinearLayout sheetButtonsContainer;
        public final LinearLayout actionsContainer;
        public final LinearLayout linkInfoContainer;
        public final FrameLayout loadingContainer;
        public final FrameLayout loadingFailedContainer;
        public final FrameLayout emptyContainer;
        public final Button retryButton;
        public final Button openInBrowserButton;
        public final LinearLayout opFilterContainer;
        public final MaterialButton opFilterResetButton;
        public final LinearLayout pollLayout;
        public final LinearLayout headerView;
        public final int headerBasePaddingTop;
        private final Map<View, Integer> baseLeftPaddings = new HashMap<>();
        private final Map<View, Integer> baseRightPaddings = new HashMap<>();
        private final Map<View, ValueAnimator> statusRowHeightAnimators = new HashMap<>();
        private final Map<View, Boolean> statusRowVisibilityTargets = new HashMap<>();
        final Map<Float, Markwon> linkPreviewLatexRenderers = new HashMap<>();
        private ValueAnimator refreshPromptHeightAnimator;
        @Nullable
        private ValueAnimator opFilterVisibilityAnimator;
        private final int opFilterBaseBottomMargin;
        private boolean statusRowsInitialized;
        private boolean sheetSlideOffsetApplied;
        private float lastAppliedSheetSlideOffset;
        @Nullable
        private Story boundStory;
        private long headerBindingGeneration = -1;

        public HeaderViewHolder(CommentsHeaderBinding binding) {
            super(binding.getRoot());
            headerBinding = binding;
            View view = binding.getRoot();
            mView = view;
            titleView = binding.commentsHeaderTitle;
            titleShimmer = binding.commentsHeaderTitleShimmer;
            linkImage = binding.commentsHeaderLinkImage;
            metaContainer = binding.commentsHeaderMetaContainer;
            metaVotes = binding.commentsHeaderMetaVotes;
            metaComments = binding.commentsHeaderMetaComments;
            metaTime = binding.commentsHeaderMetaTime;
            metaBy = binding.commentsHeaderMetaBy;
            metaVotesIcon = binding.commentsHeaderMetaVotesIcon;
            urlView = binding.commentsHeaderUrl;
            textView = binding.commentsHeaderText;
            referenceLinksContainer = binding.commentsHeaderReferenceLinksContainer;
            emptyView = binding.commentsHeaderEmpty;
            emptyViewText = binding.commentsHeaderEmptyText;
            headerView = binding.commentsHeader;
            headerBasePaddingTop = headerView.getPaddingTop();
            loadingContainer = binding.commentsHeaderLoadingContainer;
            loadingIndicator = binding.commentsHeaderLoading;
            loadingFailedContainer = binding.commentsHeaderLoadingFailedContainer;
            loadingFailed = binding.commentsHeaderLoadingFailed;
            loadingFailedText = binding.commentsHeaderLoadingFailedText;
            serverErrorText = binding.commentsHeaderServerError;
            refreshPrompt = binding.commentsHeaderRefreshPrompt;
            lastRefreshedText = binding.commentsHeaderLastRefreshed;
            refreshButton = binding.commentsHeaderRefresh;
            emptyContainer = binding.commentsHeaderEmptyContainer;
            favicon = binding.commentsHeaderFavicon;
            previewImage = binding.commentsHeaderStoryPreviewImage;
            linkInfoContainer = binding.commentsHeaderLinkInfoContainer;
            userButton = binding.commentsHeaderButtonUser;
            commentButton = binding.commentsHeaderButtonComment;
            voteButton = binding.commentsHeaderButtonVote;
            favoriteButton = binding.commentsHeaderButtonFavorite;
            bookmarkButton = binding.commentsHeaderButtonBookmark;
            shareButton = binding.commentsHeaderButtonShare;
            headerRefreshButton = binding.commentsHeaderButtonRefresh;
            summarizeButtonParent = binding.commentsHeaderButtonSummarizeParent;
            summarizeButton = binding.commentsHeaderButtonSummarize;
            summaryContainer = binding.commentsHeaderSummaryContainer;
            summaryContentContainer = binding.commentsHeaderSummaryContentContainer;
            summary = binding.commentsHeaderSummary;
            summaryTitle = binding.commentsHeaderSummaryTitle;
            summaryDebugInfo = binding.commentsHeaderSummaryDebugInfo;
            configureSummaryTitleIcon(summaryTitle);
            moreButton = binding.commentsHeaderButtonMore;
            userButtonParent = binding.commentsHeaderButtonUserParent;
            moreButtonParent = binding.commentsHeaderButtonMoreParent;
            refreshButtonParent = binding.commentsHeaderButtonRefreshParent;
            commentButtonParent = binding.commentsHeaderButtonCommentParent;
            voteButtonParent = (RelativeLayout) voteButton.getParent();
            favoriteButtonParent = binding.commentsHeaderButtonFavoriteParent;
            bookmarkButtonParent = binding.commentsHeaderButtonBookmarkParent;
            divider = binding.commentsHeaderDivider;
            tintFade = binding.commentsHeaderTintFade;
            retryButton = binding.commentsHeaderRetry;
            openInBrowserButton = binding.commentsHeaderOpenInBrowser;
            opFilterContainer = binding.commentsHeaderOpFilter;
            opFilterResetButton = binding.commentsHeaderOpFilterReset;
            opFilterBaseBottomMargin =
                    ((ViewGroup.MarginLayoutParams) opFilterContainer.getLayoutParams())
                            .bottomMargin;
            pollLayout = binding.commentsHeaderPollLayout;
            sheetRefreshButton = binding.commentsSheetLayoutRefresh;
            sheetExpandButton = binding.commentsSheetLayoutExpand;
            sheetBrowserButton = binding.commentsSheetLayoutBrowser;
            sheetReaderContainer = binding.commentsSheetContainerReader;
            sheetReaderButton = binding.commentsSheetLayoutReader;
            sheetReaderIcon = binding.commentsSheetReaderIcon;
            sheetInvertButton = binding.commentsSheetLayoutInvert;
            sheetHandleContainer = binding.commentsSheetHandleContainer;
            sheetButtonsContainer = binding.commentSheetButtonsContainer;
            actionsContainer = binding.commentsHeaderActionsContainer;
            spacer = binding.commentsHeaderSpacer;

            final int SHEET_ITEM_HEIGHT = Utils.pxFromDpInt(view.getResources(), 56);

            retryButton.setOnClickListener((v) -> retryListener.onRetry());
            openInBrowserButton.setOnClickListener((v) -> retryListener.onOpenInBrowser());
            opFilterResetButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_RESET_OP_FILTER, view));

            refreshButton.setOnClickListener((v) -> {
                showUpdate = false;
                setRefreshButtonVisible(false);
                retryListener.onRetry();
            });

            userButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_USER, null));
            commentButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_COMMENT, null));
            voteButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_VOTE, v));
            favoriteButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_FAVORITE, v));
            shareButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_SHARE, v));
            headerRefreshButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_COMMENTS_REFRESH, v));
            moreButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_MORE, v));
            sheetRefreshButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_REFRESH, view));
            sheetExpandButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_EXPAND, view));
            sheetBrowserButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_BROWSER, view));
            sheetReaderButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_READER, view));
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
            TooltipCompat.setTooltipText(headerRefreshButton, "Refresh");
            TooltipCompat.setTooltipText(moreButton, "More");
            ViewCompat.setAccessibilityHeading(titleView, true);
            ViewCompat.setAccessibilityHeading(summaryTitle, true);
            ViewCompat.setAccessibilityHeading(emptyViewText, true);
            ViewCompat.setAccessibilityHeading(loadingFailedText, true);

            sheetRefreshButton.setContentDescription("Refresh comments");
            sheetExpandButton.setContentDescription("Open comments");
            sheetBrowserButton.setContentDescription("Open story in browser");
            sheetInvertButton.setContentDescription("Invert colors");

            if (!showInvert) {
                binding.commentsSheetContainerInvert.setVisibility(GONE);
            }

            headerView.setOnClickListener(view1 -> headerClickListener.onItemClick(story));

            textView.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    Utils.openLinkMaybeHN(mView.getContext(), href);
                    return true;
                }
            });
            textView.setOnLongClickATagListener(new OnLongClickATagListener() {
                @Override
                public boolean onLongClick(
                        View widget,
                        String spannedText,
                        @Nullable String href,
                        @NonNull RectF sourceBounds) {
                    if (referenceLinkLongClickListener == null || TextUtils.isEmpty(href)) {
                        return false;
                    }
                    referenceLinkLongClickListener.onLongClick(
                            href, spannedText, widget, sourceBounds, null);
                    return true;
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
                    if (sheetSlideOffsetApplied
                            && Float.compare(lastAppliedSheetSlideOffset, slideOffset) == 0) {
                        return;
                    }
                    sheetSlideOffsetApplied = true;
                    lastAppliedSheetSlideOffset = slideOffset;

                    // 0 when small, 1 when opened
                    setSheetButtonsContentAlpha((1 - slideOffset) * (1 - slideOffset) * (1 - slideOffset));
                    sheetButtonsContainer.getLayoutParams().height = Math.round((1 - slideOffset) * (SHEET_ITEM_HEIGHT + navbarHeight));
                    sheetButtonsContainer.requestLayout();

                    float headerAlpha = getHeaderAlphaForSlideOffset(slideOffset);
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
                binding.commentsSheetHandle.setVisibility(GONE);
            }
        }

        boolean hasLinkPreviewViews() {
            return infoContainer != null;
        }

        void ensureLinkPreviewViews() {
            if (hasLinkPreviewViews()) {
                return;
            }

            CommentsHeaderLinkPreviewBinding linkPreviewBinding =
                    CommentsHeaderLinkPreviewBinding.bind(
                            headerBinding.commentsHeaderLinkPreviewStub.inflate());
            arxivBinding = linkPreviewBinding.commentsHeaderArxivContainer;
            githubBinding = linkPreviewBinding.commentsHeaderGithubContainer;
            gitLabBinding = linkPreviewBinding.commentsHeaderGitlabContainer;
            nitterBinding = linkPreviewBinding.commentsHeaderNitterContainer;
            stackExchangeBinding = linkPreviewBinding.commentsHeaderStackExchangeContainer;
            wikiBinding = linkPreviewBinding.commentsHeaderWikipediaContainer;

            infoContainer = linkPreviewBinding.commentsHeaderInfoContainer;
            infoHeader = linkPreviewBinding.commentsHeaderInfoHeader;
            linkPreviewLoadingContainer = linkPreviewBinding.commentsHeaderLinkPreviewLoading;
            linkPreviewContentContainer = linkPreviewBinding.commentsHeaderLinkPreviewContent;
            arxivAbstract = arxivBinding.commentsHeaderArxivAbstract;
            githubContainer = githubBinding.commentsHeaderGithubContainer;
            gitLabContainer = gitLabBinding.commentsHeaderGitlabContainer;
            arxivContainer = arxivBinding.commentsHeaderArxivContainer;
            stackExchangeContainer = stackExchangeBinding.commentsHeaderStackExchangeContainer;
            wikiContainer = wikiBinding.commentsHeaderWikipediaContainer;
            nitterContainer = nitterBinding.commentsHeaderNitterContainer;
            wikiSummary = wikiBinding.commentsHeaderWikipediaSummary;

            githubAbout = githubBinding.commentsHeaderGithubAbout;
            githubWebsite = githubBinding.commentsHeaderGithubWebsite;
            githubLicense = githubBinding.commentsHeaderGithubLicense;
            githubLanguage = githubBinding.commentsHeaderGithubLanguage;
            githubStars = githubBinding.commentsHeaderGithubStars;
            githubWatching = githubBinding.commentsHeaderGithubWatching;
            githubForks = githubBinding.commentsHeaderGithubForks;
            githubWebsiteContainer = githubBinding.commentsHeaderGithubWebsiteContainer;
            githubLicenseContainer = githubBinding.commentsHeaderGithubLicenseContainer;
            githubLanguageContainer = githubBinding.commentsHeaderGithubLanguageContainer;

            gitLabDescription = gitLabBinding.commentsHeaderGitlabDescription;
            gitLabWebsite = gitLabBinding.commentsHeaderGitlabWebsite;
            gitLabVisibility = gitLabBinding.commentsHeaderGitlabVisibility;
            gitLabLanguage = gitLabBinding.commentsHeaderGitlabLanguage;
            gitLabStars = gitLabBinding.commentsHeaderGitlabStars;
            gitLabForks = gitLabBinding.commentsHeaderGitlabForks;
            gitLabWebsiteContainer = gitLabBinding.commentsHeaderGitlabWebsiteContainer;
            gitLabVisibilityContainer = gitLabBinding.commentsHeaderGitlabVisibilityContainer;
            gitLabLanguageContainer = gitLabBinding.commentsHeaderGitlabLanguageContainer;

            stackExchangeTitle = stackExchangeBinding.commentsHeaderStackExchangeTitle;
            stackExchangeBy = stackExchangeBinding.commentsHeaderStackExchangeBy;
            stackExchangeScore = stackExchangeBinding.commentsHeaderStackExchangeScore;
            stackExchangeAnswers = stackExchangeBinding.commentsHeaderStackExchangeAnswers;
            stackExchangeViews = stackExchangeBinding.commentsHeaderStackExchangeViews;
            stackExchangeAnswerState = stackExchangeBinding.commentsHeaderStackExchangeAnswerState;
            stackExchangeAuthor = stackExchangeBinding.commentsHeaderStackExchangeAuthor;
            stackExchangeTags = stackExchangeBinding.commentsHeaderStackExchangeTags;
            stackExchangeTagsContainer = stackExchangeBinding.commentsHeaderStackExchangeTagsContainer;

            arxivBy = arxivBinding.commentsHeaderArxivBy;
            arxivDate = arxivBinding.commentsHeaderArxivDate;
            arxivSubjects = arxivBinding.commentsHeaderArxivSubjects;
            arxivByIcon = arxivBinding.commentsHeaderArxivByIcon;
            arxivDownloadButton = arxivBinding.commentsHeaderArxivDownload;

            nitterText = nitterBinding.commentsHeaderNitterText;
            nitterDate = nitterBinding.commentsHeaderNitterDate;
            nitterButton = nitterBinding.commentsHeaderNitterButtonOpen;
            nitterReplyCount = nitterBinding.commentsHeaderNitterReplyCount;
            nitterReposts = nitterBinding.commentsHeaderNitterReposts;
            nitterLikes = nitterBinding.commentsHeaderNitterLikes;
            nitterLikesImageView = nitterBinding.commentsHeaderNitterLikesImage;
            nitterRetweetImageView = nitterBinding.commentsHeaderNitterRepostsImage;
            nitterReplyImageView = nitterBinding.commentsHeaderNitterReplyImage;
            nitterMediaContainer = nitterBinding.commentsHeaderNitterMediaContainer;
            nitterImage = nitterBinding.commentsHeaderNitterImage;
            nitterVideoLabel = nitterBinding.commentsHeaderNitterVideoLabel;

            ViewCompat.setAccessibilityHeading(infoHeader, true);
            arxivDownloadButton.setOnClickListener(v ->
                    Utils.downloadPDF(v.getContext(), story.arxivInfo.getPDFURL()));
            githubWebsite.setOnClickATagListener((widget, spannedText, href) -> {
                Utils.launchCustomTab(mView.getContext(), story.repoInfo.website);
                return false;
            });
            gitLabWebsite.setOnClickATagListener((widget, spannedText, href) -> {
                Utils.launchCustomTab(mView.getContext(), story.gitLabInfo.website);
                return false;
            });
            nitterText.setOnClickATagListener((widget, spannedText, href) -> {
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
            });
        }

        private void setHeaderContentSideInsets(int leftInset, int rightInset) {
            int safeLeftInset = Math.max(0, leftInset);
            int safeRightInset = Math.max(0, rightInset);
            // The header always owns the full RecyclerView width so its background is stable from
            // the first frame. Insets belong only to the content inside that background.
            setHorizontalPaddingForContentInsets(
                    sheetHandleContainer, safeLeftInset, safeRightInset);
            setHorizontalPaddingForContentInsets(
                    sheetButtonsContainer, safeLeftInset, safeRightInset);
            setHorizontalPaddingForContentInsets(headerView, safeLeftInset, safeRightInset);
            setHorizontalPaddingForContentInsets(
                    summaryContainer, safeLeftInset, safeRightInset);
            setHorizontalPaddingForContentInsets(
                    actionsContainer, safeLeftInset, safeRightInset);
            setHorizontalPaddingForContentInsets(
                    opFilterContainer, safeLeftInset, safeRightInset);
            setHorizontalPaddingForContentInsets(
                    loadingContainer, safeLeftInset, safeRightInset);
            setHorizontalPaddingForContentInsets(
                    refreshPrompt, safeLeftInset, safeRightInset);
            setHorizontalPaddingForContentInsets(
                    loadingFailedContainer, safeLeftInset, safeRightInset);
            setHorizontalPaddingForContentInsets(
                    emptyContainer, safeLeftInset, safeRightInset);
        }

        private void setHorizontalPaddingForContentInsets(
                View view,
                int leftInset,
                int rightInset) {
            if (view == null) {
                return;
            }

            int targetLeftPadding = getBaseLeftPadding(view) + leftInset;
            int targetRightPadding = getBaseRightPadding(view) + rightInset;
            if (view.getPaddingLeft() != targetLeftPadding
                    || view.getPaddingRight() != targetRightPadding) {
                view.setPadding(
                        targetLeftPadding,
                        view.getPaddingTop(),
                        targetRightPadding,
                        view.getPaddingBottom());
            }
        }

        private int getBaseLeftPadding(View view) {
            Integer baseLeftPadding = baseLeftPaddings.get(view);
            if (baseLeftPadding == null) {
                baseLeftPadding = view.getPaddingLeft();
                baseLeftPaddings.put(view, baseLeftPadding);
            }
            return baseLeftPadding;
        }

        private int getBaseRightPadding(View view) {
            Integer baseRightPadding = baseRightPaddings.get(view);
            if (baseRightPadding == null) {
                baseRightPadding = view.getPaddingRight();
                baseRightPaddings.put(view, baseRightPadding);
            }
            return baseRightPadding;
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

    public void setOnReferenceLinkLongClickListener(ReferenceLinkLongClickListener clickListener) {
        referenceLinkLongClickListener = clickListener;
    }

    public void setOnHeaderPreviewLongClickListener(HeaderPreviewLongClickListener clickListener) {
        headerPreviewLongClickListener = clickListener;
    }

    @Nullable
    public ImageView getBoundHeaderPreviewImage() {
        if (boundHeaderViewHolder == null
                || !ViewCompat.isAttachedToWindow(boundHeaderViewHolder.previewImage)
                || boundHeaderViewHolder.previewImage.getVisibility() == GONE) {
            return null;
        }
        return boundHeaderViewHolder.previewImage;
    }

    @Nullable
    public Integer getCurrentHeaderContentBackgroundColor() {
        return currentHeaderContentBackgroundColor;
    }

    public boolean isBoundHeaderView(@Nullable View view) {
        if (view == null || boundHeaderViewHolder == null) {
            return false;
        }
        View current = view;
        while (current != null) {
            if (current == boundHeaderViewHolder.itemView) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    public void setHeaderPreviewImageSuppressed(boolean suppressed) {
        headerPreviewImageSuppressed = suppressed;
        if (boundHeaderViewHolder == null
                || !ViewCompat.isAttachedToWindow(boundHeaderViewHolder.previewImage)) {
            return;
        }
        applyHeaderPreviewImageSuppression(boundHeaderViewHolder);
    }

    private void applyHeaderPreviewImageSuppression(HeaderViewHolder headerViewHolder) {
        ImageView previewImage = headerViewHolder.previewImage;
        if (headerPreviewImageSuppressed && previewImage.getVisibility() == VISIBLE) {
            previewImage.setVisibility(View.INVISIBLE);
        } else if (!headerPreviewImageSuppressed
                && previewImage.getVisibility() == View.INVISIBLE
                && previewImage.getDrawable() != null) {
            previewImage.setVisibility(VISIBLE);
        }
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
            if (boundHeaderViewHolder != null
                    && ViewCompat.isAttachedToWindow(boundHeaderViewHolder.itemView)) {
                animateHeaderOpFilterVisible(boundHeaderViewHolder, active);
            }
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

    public interface ReferenceLinkLongClickListener {
        void onLongClick(
                String url,
                String title,
                View view,
                @Nullable RectF sourceBounds,
                @Nullable CollectedReferenceLinks.ReferenceLink referenceLink);
    }

    public interface HeaderPreviewLongClickListener {
        void onLongClick(String imageUrl, ImageView view);
    }

    public int getIndexOfLastChild(int pos) {
        return commentSubtreeIndex.getLastChildIndex(comments, pos);
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
        commentSubtreeIndex.invalidate();
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

    private static String hiddenReplyCountDescription(int count) {
        if (count == 1) {
            return "1 hidden reply";
        }
        return count + " hidden replies";
    }

    private FontUtils.Typography getTypography(Context context) {
        if (typography == null) {
            typography = FontUtils.resolveTypography(
                    context,
                    font,
                    SettingsUtils.DEFAULT_STORY_TEXT_SIZE,
                    preferredTextSize);
        }
        return typography;
    }

    void invalidateTypography() {
        typography = null;
    }

    void invalidateCommentViewStyle() {
        commentViewStyleGeneration++;
    }

    public interface RequestSummaryCallback {
        void onRequest(Runnable onUpdate, Runnable onDone);
    }
}
