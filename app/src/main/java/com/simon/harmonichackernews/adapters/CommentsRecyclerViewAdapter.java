package com.simon.harmonichackernews.adapters;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.AutoTransition;
import androidx.transition.TransitionManager;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
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
import com.simon.harmonichackernews.databinding.CommentsItemBinding;
import com.simon.harmonichackernews.databinding.CommentsItemCardBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewArxivBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewGithubBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewGitlabBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewNitterBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewStackExchangeBinding;
import com.simon.harmonichackernews.databinding.LinkPreviewWikipediaBinding;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.StoryPreviewImageLoader;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.CollectedReferenceLinks;
import com.simon.harmonichackernews.utils.CommentDepthIndicatorUtils;
import com.simon.harmonichackernews.utils.AccessibilityTextUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.PreviewImageTintUtils;
import com.simon.harmonichackernews.utils.PreviewImageLayoutUtils;
import com.simon.harmonichackernews.utils.ReferenceLinkRowUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.StoryPreviewImageMemoryCache;
import com.simon.harmonichackernews.utils.TextSizeImageSpan;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import io.noties.markwon.Markwon;
import org.json.JSONException;
import org.json.JSONObject;
import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

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
    private final Set<Integer> requestedHackerNewsReferenceTitleItemIds = new HashSet<>();
    private int commentLookupSize = -1;
    private Map<String, String> userTagsByUser = new HashMap<>();
    private String userTagsJson;

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
    public boolean collectReferenceLinks;
    public boolean hasAccountDetails;
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
    private float headerSlideOffset = 1f;
    @Nullable
    private Integer currentHeaderContentBackgroundColor;
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
    private static final int HEADER_STATUS_ROW_DURATION_MS = 220;
    private static final float HEADER_STATUS_ROW_HIDDEN_SCALE = 0.9f;
    private static final int HEADER_STATUS_ROW_HIDDEN_TRANSLATION_Y_DP = 12;
    private static final int INITIAL_LOADING_TOP_MARGIN_DP = 44;
    private static final int REFRESH_LOADING_TOP_MARGIN_DP = 16;
    private static final int HEADER_ACTION_ICON_SWAP_OUT_DURATION_MS = 90;
    private static final int HEADER_ACTION_ICON_SWAP_IN_DURATION_MS = 150;
    private static final float HEADER_ACTION_ICON_SWAP_MIN_SCALE = 0.72f;
    private static final int HEADER_READER_BUTTON_VISIBILITY_DURATION_MS = 160;
    private static final int HEADER_FAVORITE_LOADING_SIZE_DP = 28;
    private static final int REFERENCE_LINKS_CONTAINER_TOP_MARGIN_DP = 5;
    private static final int INTERLEAVED_REFERENCE_LINK_TOP_MARGIN_DP = 4;
    private static final int INTERLEAVED_REFERENCE_LINK_BOTTOM_MARGIN_DP = 2;
    private static final int INTERLEAVED_COMMENT_TEXT_TOP_MARGIN_DP = 5;
    private static final int HEADER_PREVIEW_IMAGE_DEFAULT_HEIGHT_DP = 176;
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

            bindHeaderTitle(headerViewHolder, ctx);

            if (story.loaded) {
                headerViewHolder.metaVotes.setText(String.valueOf(story.score));
                headerViewHolder.metaComments.setText(String.valueOf(story.descendants));
                headerViewHolder.metaTime.setText(story.getTimeFormatted());
                String tag = getCachedUserTag(ctx, story.by);
                headerViewHolder.metaBy.setText(TextUtils.isEmpty(tag) ? story.by : story.by + " (" + tag + ")");
                headerViewHolder.metaVotes.setContentDescription(AccessibilityTextUtils.pointCountDescription(story.score));
                headerViewHolder.metaComments.setContentDescription(AccessibilityTextUtils.commentCountDescription(story.descendants));
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

            bindHeaderLoadingState(headerViewHolder, ctx);

            headerViewHolder.spacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, spacerHeight));

            headerViewHolder.setRefreshButtonVisible(showUpdate);

            int actionContainerPadding = Math.round(headerViewHolder.actionsContainer.getResources().getDimension(R.dimen.comments_header_action_padding));
            headerViewHolder.actionsContainer.setPadding(actionContainerPadding, 0, actionContainerPadding, 0);
            applyHeaderEndBleed(headerViewHolder);

            headerViewHolder.favicon.setVisibility(showThumbnail ? View.VISIBLE : GONE);
            headerViewHolder.linkInfoContainer.setVisibility(!story.isComment && story.isLink ? View.VISIBLE : View.GONE);

            if (showThumbnail && !TextUtils.isEmpty(story.url)) {
                FaviconLoader.loadFavicon(story.url, headerViewHolder.favicon, ctx, faviconProvider);
            }

            bindHeaderSummary(headerViewHolder, ctx);

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
            headerViewHolder.bookmarkButtonParent.setVisibility(bookmarksEnabled && !hasAccountDetails ? VISIBLE : GONE);
            bindHeaderAccountActionVisibility(headerViewHolder);

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

            boolean byOp = TextUtils.equals(story.by, comment.by);
            boolean byUser = false;
            if (!TextUtils.isEmpty(username)) {
                byUser = comment.by.equals(username);
            }

            String cTag = getCachedUserTag(ctx, comment.by);
            String displayName = comment.by;
            if (!TextUtils.isEmpty(cTag)) {
                displayName += " (" + cTag + ")";
            }
            int opCommentColor = MaterialColors.getColor(itemViewHolder.commentBy, R.attr.opCommentColor);
            itemViewHolder.commentBy.setText(getCommentByWithOpBadge(ctx, displayName, byOp, opCommentColor));
            itemViewHolder.commentBy.setContentDescription(
                    "Comment by " + comment.by + (byOp ? ", original poster" : ""));
            itemViewHolder.commentByTime.setContentDescription("Posted " + comment.getTimeFormatted());

            if (byUser) {
                itemViewHolder.commentBy.setTextColor(MaterialColors.getColor(itemViewHolder.commentBy, R.attr.selfCommentColor));
            } else if (byOp) {
                itemViewHolder.commentBy.setTextColor(opCommentColor);
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
        bindReaderModeButton(headerViewHolder);

        headerViewHolder.favicon.setVisibility(showThumbnail ? View.VISIBLE : GONE);
        headerViewHolder.linkInfoContainer.setVisibility(!story.isComment && story.isLink ? View.VISIBLE : View.GONE);
        if (showThumbnail && !TextUtils.isEmpty(story.url)) {
            FaviconLoader.loadFavicon(story.url, headerViewHolder.favicon, ctx, faviconProvider);
        }
        bindHeaderSummary(headerViewHolder, ctx);
        headerViewHolder.emptyViewText.setText(story.isComment ? "No replies" : "No comments");
        bindHeaderAccountActionVisibility(headerViewHolder);
        bindHeaderTint(headerViewHolder);
    }

    private void bindHeaderSummary(HeaderViewHolder headerViewHolder, Context ctx) {
        boolean canSummarize = story.isLink
                && Utils.canProvideSummary(ctx)
                && !story.summaryGeneratedSuccessfully;
        headerViewHolder.summarizeButtonParent.setVisibility(canSummarize ? VISIBLE : GONE);

        boolean hasSummary = !TextUtils.isEmpty(story.summary);
        headerViewHolder.summaryContainer.setVisibility(hasSummary ? VISIBLE : GONE);
        headerViewHolder.summaryContentContainer.setVisibility(hasSummary ? VISIBLE : GONE);
        headerViewHolder.summary.setMaxLines(Integer.MAX_VALUE);
        headerViewHolder.summary.setEllipsize(null);
        if (hasSummary) {
            Markwon.create(ctx).setMarkdown(headerViewHolder.summary, story.summary);
        } else {
            headerViewHolder.summary.setText(null);
        }

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
            showHeaderSummaryLoading(headerViewHolder.summarizeButton, true);
            summaryCallback.onRequest(() -> {
                storySummaryLoading = false;
                notifyItemChanged(0);
                if (story.summaryGeneratedSuccessfully) {
                    headerViewHolder.summarizeButtonParent.setVisibility(GONE);
                    return;
                }

                ImageButton button = resolveStorySummaryButton(v);
                if (button != null) {
                    showHeaderSummaryButton(button, true);
                }
            });
        });
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
        if (!story.loaded && !loadingFailed && !hasTitle) {
            headerViewHolder.titleView.setVisibility(GONE);
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
        if (story.loaded) {
            headerViewHolder.metaVotes.setText(String.valueOf(story.score));
            headerViewHolder.metaComments.setText(String.valueOf(story.descendants));
            headerViewHolder.metaTime.setText(story.getTimeFormatted());
            String tag = getCachedUserTag(ctx, story.by);
            headerViewHolder.metaBy.setText(TextUtils.isEmpty(tag) ? story.by : story.by + " (" + tag + ")");
            headerViewHolder.metaVotes.setContentDescription(AccessibilityTextUtils.pointCountDescription(story.score));
            headerViewHolder.metaComments.setContentDescription(AccessibilityTextUtils.commentCountDescription(story.descendants));
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
        boolean showLoadingIndicator = !loadingFailed
                && (!commentsLoaded || commentsRefreshInProgress);
        boolean showEmptyState = !loadingFailed
                && commentsLoaded
                && story.descendants <= 0
                && comments.size() <= 1;

        setLoadingIndicatorTopMargin(headerViewHolder, commentsLoaded);
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

    private void setHeaderStatusRowImmediately(
            HeaderViewHolder headerViewHolder,
            FrameLayout container,
            boolean visible) {
        ValueAnimator animator = headerViewHolder.statusRowHeightAnimators.remove(container);
        if (animator != null) {
            animator.cancel();
        }
        headerViewHolder.statusRowVisibilityTargets.put(container, visible);

        View content = container.getChildAt(0);
        content.animate().cancel();
        content.setAlpha(1f);
        content.setScaleX(1f);
        content.setScaleY(1f);
        content.setTranslationY(0f);

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
            if (container.getVisibility() != VISIBLE) {
                ViewGroup.LayoutParams layoutParams = container.getLayoutParams();
                layoutParams.height = 0;
                container.setLayoutParams(layoutParams);
                container.setVisibility(VISIBLE);
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
                    container.getHeight(),
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
        applyHeaderEndBleed(headerViewHolder);
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

        GradientDrawable fade = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{headerColor, normalColor});
        headerViewHolder.tintFade.setBackground(fade);
    }

    private void applyHeaderEndBleed(HeaderViewHolder headerViewHolder) {
        headerViewHolder.setHeaderEndBleed(getRecyclerViewEndPadding(headerViewHolder.itemView));
    }

    private int getRecyclerViewEndPadding(View itemView) {
        ViewParent parent = itemView.getParent();
        if (parent instanceof RecyclerView) {
            return ((RecyclerView) parent).getPaddingRight();
        }
        return 0;
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
                    story.previewImageTintMode,
                    story.previewImageTintColor);
        }
        if (story.faviconTintColorLoaded && !TextUtils.isEmpty(story.faviconTintSourceUrl)) {
            StoryPreviewImageLoader.saveCachedPreviewImageTintColor(
                    appContext,
                    story.id,
                    story.faviconTintSourceUrl,
                    story.faviconTintBaseColor,
                    story.faviconTintMode,
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
                baseColor,
                paletteTintMode);
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

        String expandedCommentText = Utils.expandShortenedAnchorText(comment.text);
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
            FontUtils.setCommentTextTypeface(itemViewHolder.commentBody, preferredTextSize);
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
                FontUtils.setCommentTextTypeface(itemViewHolder.commentBody, preferredTextSize);
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
        FontUtils.setCommentTextTypeface(body, preferredTextSize);

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
        loadHackerNewsReferenceTitleIfNeeded(container.getContext(), link);
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
            referenceLinkLongClickListener.onLongClick(link, v);
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

    private void loadHackerNewsReferenceTitleIfNeeded(
            Context context,
            CollectedReferenceLinks.ReferenceLink link) {
        int itemId = getHackerNewsReferencePostCandidateId(link.getUrl());
        if (itemId <= 0) {
            return;
        }

        String cachedTitle = hackerNewsReferenceTitlesByItemId.get(itemId);
        if (!TextUtils.isEmpty(cachedTitle)) {
            link.setResolvedTitle(cachedTitle);
            return;
        }

        if (!requestedHackerNewsReferenceTitleItemIds.add(itemId)) {
            return;
        }

        String url = "https://hacker-news.firebaseio.com/v0/item/" + itemId + ".json";
        StringRequest request = new StringRequest(Request.Method.GET, url, response -> {
            String title = parseHackerNewsItemTitle(response);
            if (TextUtils.isEmpty(title)) {
                return;
            }

            hackerNewsReferenceTitlesByItemId.put(itemId, title);
            applyHackerNewsReferenceTitle(itemId, title);
            notifyHackerNewsReferenceTitleChanged(itemId);
        }, error -> {
        });
        NetworkComponent.getRequestQueueInstance(context).add(request);
    }

    private int getHackerNewsReferencePostCandidateId(String url) {
        if (TextUtils.isEmpty(url)) {
            return -1;
        }

        Uri uri = Uri.parse(url);
        if (!Utils.isHackerNewsItemUri(uri) || !TextUtils.isEmpty(uri.getFragment())) {
            return -1;
        }

        String itemId = uri.getQueryParameter("id");
        if (TextUtils.isEmpty(itemId)) {
            return -1;
        }

        try {
            return Integer.parseInt(itemId);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String parseHackerNewsItemTitle(String response) {
        if (TextUtils.isEmpty(response)) {
            return null;
        }

        try {
            JSONObject item = new JSONObject(response);
            if ("comment".equals(item.optString("type"))) {
                return null;
            }

            String title = item.optString("title", "").replace('\n', ' ').replaceAll("\\s+", " ").trim();
            return TextUtils.isEmpty(title) ? null : title;
        } catch (JSONException e) {
            return null;
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
            if (getHackerNewsReferencePostCandidateId(link.getUrl()) == itemId) {
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
            if (getHackerNewsReferencePostCandidateId(link.getUrl()) == itemId) {
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

    @Nullable
    private ImageButton resolveStorySummaryButton(@Nullable View actionView) {
        if (actionView instanceof ImageButton
                && actionView.getId() == R.id.comments_header_button_summarize
                && ViewCompat.isAttachedToWindow(actionView)) {
            return (ImageButton) actionView;
        }
        if (boundHeaderViewHolder != null
                && ViewCompat.isAttachedToWindow(boundHeaderViewHolder.summarizeButton)) {
            return boundHeaderViewHolder.summarizeButton;
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
        public final TextView commentHiddenCount;
        public final TextView commentHiddenText;
        public final View commentIndentIndicator;
        public final View commentCard;
        public final LinearLayout referenceLinksContainer;
        public boolean commentBodyHasText = true;
        public boolean referenceLinksVisible = false;
        public Comment comment;

        public ItemViewHolder(CommentsItemBinding binding) {
            this(
                    binding.getRoot(),
                    binding.commentBody,
                    binding.commentBy,
                    binding.commentByTime,
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
                               TextView hiddenCount,
                               TextView hiddenText,
                               View indentIndicator,
                               @Nullable View card,
                               LinearLayout linksContainer) {
            super(view);
            commentBody = body;
            commentBy = by;
            commentByTime = byTime;
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
        }

        public View getCommentActionSourceView() {
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
        public final CommentsHeaderBinding headerBinding;
        public final LinkPreviewArxivBinding arxivBinding;
        public final LinkPreviewGithubBinding githubBinding;
        public final LinkPreviewGitlabBinding gitLabBinding;
        public final LinkPreviewNitterBinding nitterBinding;
        public final LinkPreviewStackExchangeBinding stackExchangeBinding;
        public final LinkPreviewWikipediaBinding wikiBinding;
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
        public final ImageButton headerRefreshButton;
        public final ImageButton summarizeButton;
        public final RelativeLayout summarizeButtonParent;
        public final LinearLayout summaryContainer;
        public final LinearLayout summaryContentContainer;
        public final TextView summary;
        public final TextView summaryTitle;
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
        private final Map<View, Integer> baseRightPaddings = new HashMap<>();
        private final Map<View, ValueAnimator> statusRowHeightAnimators = new HashMap<>();
        private final Map<View, Boolean> statusRowVisibilityTargets = new HashMap<>();
        private ValueAnimator refreshPromptHeightAnimator;
        private boolean statusRowsInitialized;

        public HeaderViewHolder(CommentsHeaderBinding binding) {
            super(binding.getRoot());
            headerBinding = binding;
            arxivBinding = binding.commentsHeaderArxivContainer;
            githubBinding = binding.commentsHeaderGithubContainer;
            gitLabBinding = binding.commentsHeaderGitlabContainer;
            nitterBinding = binding.commentsHeaderNitterContainer;
            stackExchangeBinding = binding.commentsHeaderStackExchangeContainer;
            wikiBinding = binding.commentsHeaderWikipediaContainer;
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
            arxivAbstract = arxivBinding.commentsHeaderArxivAbstract;
            infoContainer = binding.commentsHeaderInfoContainer;
            infoHeader = binding.commentsHeaderInfoHeader;
            linkPreviewLoadingContainer = binding.commentsHeaderLinkPreviewLoading;
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
            githubContainer = githubBinding.commentsHeaderGithubContainer;
            gitLabContainer = gitLabBinding.commentsHeaderGitlabContainer;
            arxivContainer = arxivBinding.commentsHeaderArxivContainer;
            stackExchangeContainer = stackExchangeBinding.commentsHeaderStackExchangeContainer;
            wikiContainer = wikiBinding.commentsHeaderWikipediaContainer;
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

            final int SHEET_ITEM_HEIGHT = Utils.pxFromDpInt(view.getResources(), 56);

            nitterContainer = nitterBinding.commentsHeaderNitterContainer;
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
            ViewCompat.setAccessibilityHeading(infoHeader, true);
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

        private void setHeaderEndBleed(int endBleed) {
            int safeEndBleed = Math.max(0, endBleed);
            setItemViewWidthForEndBleed(safeEndBleed);
            setEndPaddingForBleed(sheetHandleContainer, safeEndBleed);
            setEndPaddingForBleed(sheetButtonsContainer, safeEndBleed);
            setEndPaddingForBleed(headerView, safeEndBleed);
            setEndPaddingForBleed(summaryContainer, safeEndBleed);
            setEndPaddingForBleed(actionsContainer, safeEndBleed);
        }

        private void setItemViewWidthForEndBleed(int endBleed) {
            int targetWidth = ViewGroup.LayoutParams.MATCH_PARENT;
            if (endBleed > 0) {
                ViewParent parent = itemView.getParent();
                if (parent instanceof RecyclerView) {
                    RecyclerView recyclerView = (RecyclerView) parent;
                    int parentWidth = recyclerView.getWidth();
                    int leftPadding = recyclerView.getPaddingLeft();
                    if (parentWidth > leftPadding) {
                        targetWidth = parentWidth - leftPadding;
                    }
                }
            }

            ViewGroup.LayoutParams layoutParams = itemView.getLayoutParams();
            if (layoutParams != null && layoutParams.width != targetWidth) {
                layoutParams.width = targetWidth;
                itemView.setLayoutParams(layoutParams);
            }
        }

        private void setEndPaddingForBleed(View view, int endBleed) {
            if (view == null) {
                return;
            }

            int targetRightPadding = getBaseRightPadding(view) + endBleed;
            if (view.getPaddingRight() != targetRightPadding) {
                view.setPadding(
                        view.getPaddingLeft(),
                        view.getPaddingTop(),
                        targetRightPadding,
                        view.getPaddingBottom());
            }
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

    public void setHeaderPreviewImageSuppressed(boolean suppressed) {
        headerPreviewImageSuppressed = suppressed;
        if (boundHeaderViewHolder == null
                || !ViewCompat.isAttachedToWindow(boundHeaderViewHolder.previewImage)) {
            return;
        }
        ImageView previewImage = boundHeaderViewHolder.previewImage;
        if (suppressed && previewImage.getVisibility() == VISIBLE) {
            previewImage.setVisibility(View.INVISIBLE);
        } else if (!suppressed && previewImage.getVisibility() == View.INVISIBLE
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

    public interface ReferenceLinkLongClickListener {
        void onLongClick(CollectedReferenceLinks.ReferenceLink link, View view);
    }

    public interface HeaderPreviewLongClickListener {
        void onLongClick(String imageUrl, ImageView view);
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
