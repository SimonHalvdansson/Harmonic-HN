package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.text.Html;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.BackEventCompat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.FragmentManager;
import androidx.transition.Transition;
import androidx.transition.TransitionListenerAdapter;
import androidx.transition.TransitionManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.transition.MaterialContainerTransform;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.databinding.CommentActionOverlayBinding;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ShareUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import okhttp3.Response;

final class CommentActionOverlayController {
    interface Host {
        @Nullable
        Context getCommentActionContext();

        @NonNull
        Context requireCommentActionContext();

        @NonNull
        FragmentManager getCommentActionActivityFragmentManager();

        @NonNull
        FragmentManager getCommentActionParentFragmentManager();

        @Nullable
        Story getCommentActionStory();

        @Nullable
        String getCommentActionReplyPostTitle();

        boolean isCommentActionHostAdded();

        boolean shouldUseCommentActionCardStyle(Context ctx);

        @Nullable
        ViewGroup getCommentActionOverlayHost();

        @Nullable
        View findCommentActionSourceView(int commentId);

        @Nullable
        Comment findCommentActionComment(int commentId);

        void stopCommentActionListScroll();

        void setCommentActionLinksDisabled(boolean disabled);

        void syncCommentActionBackState();

        void onCommentActionOverlayRemoved();

        void updateCommentActionUserTags(String changedUser);
    }

    static final int NO_COMMENT_ID = -1;

    private static final int ACTION_VIEW_USER = 0;
    private static final int ACTION_SHARE = 1;
    private static final int ACTION_COPY = 2;
    private static final int ACTION_BOOKMARK = 4;
    private static final int ACTION_FAVORITE = 5;
    private static final int ACTION_UPVOTE = 6;
    private static final int ACTION_UNVOTE = 7;
    private static final int ACTION_DOWNVOTE = 8;
    private static final int ACTION_REPLY = 9;
    private static final int NO_VOTE_LOADING_ACTION = -1;
    private static final int TEXT_MAX_HEIGHT_DP = 300;
    private static final int TRANSFORM_DURATION_MS = 280;
    private static final float TRANSFORM_START_PROGRESS = 0f;
    private static final float TRANSFORM_END_PROGRESS = 1f;
    private static final int STANDARD_SOURCE_CORNER_RADIUS_DP = 0;
    private static final int CARD_SOURCE_CORNER_RADIUS_DP = 8;
    private static final int CARD_CORNER_RADIUS_DP = 28;
    private static final int PREDICTIVE_BACK_TRANSLATION_X_DP = 56;
    private static final int PREDICTIVE_BACK_TRANSLATION_Y_DP = 18;
    private static final float PREDICTIVE_BACK_MIN_SCALE = 0.9f;
    private static final float PREDICTIVE_BACK_MIN_SCRIM_ALPHA = 0.45f;
    private static final int ICON_SWAP_OUT_DURATION_MS = 90;
    private static final int ICON_SWAP_IN_DURATION_MS = 150;
    private static final float ICON_SWAP_MIN_SCALE = 0.72f;
    private static final int FAVORITE_LOADING_SIZE_DP = 28;

    private final Host host;
    private final Set<Integer> favoriteLoadingIds = new HashSet<>();
    private final Map<Integer, Integer> voteLoadingActions = new HashMap<>();
    private final Set<Integer> downvotedIds = new HashSet<>();

    private FrameLayout overlay;
    private CommentActionOverlayBinding binding;
    private MaterialCardView card;
    private View sourceView;
    private int visibleCommentId = NO_COMMENT_ID;
    private int pendingCommentId = NO_COMMENT_ID;
    private boolean dismissing = false;
    private boolean predictiveBackActive = false;

    CommentActionOverlayController(Host host) {
        this.host = host;
    }

    boolean isShowing() {
        return overlay != null;
    }

    boolean isPredictiveBackActive() {
        return predictiveBackActive;
    }

    void setPendingCommentId(int commentId) {
        pendingCommentId = commentId;
    }

    int getRestorableCommentId() {
        return overlay != null ? visibleCommentId : pendingCommentId;
    }

    void refreshForConfiguration() {
        if (overlay == null || card == null || binding == null) {
            return;
        }

        overlay.post(() -> {
            if (overlay == null || card == null || binding == null) {
                return;
            }

            configureCardWidth(card);
            NestedScrollView cardScroll = binding.commentActionCardScroll;
            if (cardScroll != null) {
                ViewGroup.LayoutParams params = cardScroll.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                cardScroll.setLayoutParams(params);
            }

            NestedScrollView textScroll = binding.commentActionTextScroll;
            HtmlTextView commentText = binding.commentActionText;
            if (textScroll != null && commentText != null) {
                ViewGroup.LayoutParams params = textScroll.getLayoutParams();
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                textScroll.setLayoutParams(params);
                resizeTextBox(textScroll, commentText);
            } else {
                resizeDialogScroll();
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    void show(Comment comment, @Nullable View sourceView, boolean animate) {
        Context ctx = host.getCommentActionContext();
        ViewGroup overlayHost = host.getCommentActionOverlayHost();
        if (ctx == null || overlayHost == null || comment == null) {
            return;
        }

        removeNow();

        visibleCommentId = comment.id;
        this.sourceView = sourceView;
        dismissing = false;

        host.setCommentActionLinksDisabled(true);

        binding = CommentActionOverlayBinding.inflate(LayoutInflater.from(ctx), overlayHost, false);
        overlay = binding.getRoot();
        overlayHost.addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        cancelCurrentCommentListTouch(overlayHost);

        View scrim = binding.commentActionScrim;
        View content = binding.commentActionContent;
        card = binding.commentActionCard;
        card.setCardBackgroundColor(getCardBackgroundColor(ctx));
        card.setStrokeWidth(0);
        card.setStrokeColor(Color.TRANSPARENT);

        configureOverlayInsets(content);
        configureCardWidth(card);
        bind(comment);

        scrim.setOnClickListener(v -> dismiss(true));
        content.setOnClickListener(v -> dismiss(true));
        card.setOnTouchListener((v, event) -> true);

        host.syncCommentActionBackState();

        overlay.post(() -> {
            if (overlay == null || card == null) {
                return;
            }

            if (animate && isUsableTransition(overlayHost, sourceView, card)) {
                MaterialContainerTransform transform = createTransform(
                        overlayHost,
                        sourceView,
                        card,
                        MaterialContainerTransform.TRANSITION_DIRECTION_ENTER);
                transform.addTarget(card);
                TransitionManager.beginDelayedTransition(overlayHost, transform);
                scrim.animate().alpha(1f).setDuration(TRANSFORM_DURATION_MS).start();
                setSourceVisible(sourceView, false);
                card.setVisibility(View.VISIBLE);
            } else {
                scrim.setAlpha(1f);
                setSourceVisible(sourceView, false);
                card.setVisibility(View.VISIBLE);
            }
        });
    }

    private void cancelCurrentCommentListTouch(ViewGroup overlayHost) {
        long eventTime = SystemClock.uptimeMillis();
        MotionEvent cancelEvent = MotionEvent.obtain(
                eventTime,
                eventTime,
                MotionEvent.ACTION_CANCEL,
                0f,
                0f,
                0);
        overlayHost.dispatchTouchEvent(cancelEvent);
        cancelEvent.recycle();
        host.stopCommentActionListScroll();
    }

    private void bind(Comment comment) {
        Context ctx = host.requireCommentActionContext();

        boolean bookmarksEnabled = SettingsUtils.shouldUseBookmarks(ctx);
        boolean oldBookmarked = bookmarksEnabled && Utils.isBookmarked(ctx, comment.id);
        boolean oldFavorited = Utils.isFavorited(ctx, comment.id);

        MaterialButton userButton = binding.commentActionUser;
        String userLabel = TextUtils.isEmpty(comment.by) ? "Unknown user" : comment.by;
        Story story = host.getCommentActionStory();
        if (story != null && TextUtils.equals(story.by, comment.by)) {
            userLabel += " (OP)";
        }
        userButton.setText(userLabel);
        userButton.setAllCaps(false);
        userButton.setContentDescription("View user " + (TextUtils.isEmpty(comment.by) ? "profile" : comment.by));
        TooltipCompat.setTooltipText(userButton, "View user");
        userButton.setOnClickListener(v ->
                performAction(ACTION_VIEW_USER, comment, oldBookmarked, oldFavorited));

        HtmlTextView commentText = binding.commentActionText;
        String text = Utils.expandShortenedAnchorText(comment.text == null ? "" : comment.text);
        commentText.setHtml(text);
        commentText.setTextIsSelectable(true);
        commentText.setOnClickATagListener((widget, spannedText, href) -> {
            Utils.openLinkMaybeHN(widget.getContext(), href);
            return true;
        });
        FontUtils.setCommentTextTypeface(commentText, SettingsUtils.getPreferredCommentTextSize(ctx));

        NestedScrollView textScroll = binding.commentActionTextScroll;
        resizeTextBox(textScroll, commentText);

        LinearLayout actionsContainer = binding.commentActionActions;
        bindButtons(actionsContainer, ctx, comment, bookmarksEnabled, oldBookmarked, oldFavorited);
    }

    private void bindButtons(LinearLayout actionsContainer,
                             Context ctx,
                             Comment comment,
                             boolean bookmarksEnabled,
                             boolean oldBookmarked,
                             boolean oldFavorited) {
        actionsContainer.removeAllViews();

        boolean hasAccount = AccountUtils.hasAccountDetails(ctx);

        ArrayList<ActionItem> iconActions = new ArrayList<>();
        if (hasAccount) {
            boolean upvoted = Utils.isUpvoted(ctx, comment.id, true);
            boolean downvoted = !upvoted && downvotedIds.contains(comment.id);
            int voteLoadingAction = getVoteLoadingAction(comment.id);
            iconActions.add(createVoteItem(ACTION_UPVOTE, upvoted, downvoted, voteLoadingAction));
            iconActions.add(createVoteItem(ACTION_DOWNVOTE, upvoted, downvoted, voteLoadingAction));
            iconActions.add(createVoteItem(ACTION_UNVOTE, upvoted, downvoted, voteLoadingAction));
        }

        if (bookmarksEnabled) {
            iconActions.add(new ActionItem(
                    ACTION_BOOKMARK,
                    oldBookmarked ? "Remove bookmark" : "Bookmark",
                    oldBookmarked ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark));
        }
        if (hasAccount) {
            boolean favoriteLoading = favoriteLoadingIds.contains(comment.id);
            iconActions.add(new ActionItem(
                    ACTION_FAVORITE,
                    favoriteLoading ? (oldFavorited ? "Removing favorite" : "Adding favorite") : (oldFavorited ? "Remove favorite" : "Favorite"),
                    oldFavorited ? R.drawable.ic_star_filled : R.drawable.ic_star,
                    favoriteLoading));
        }

        iconActions.add(new ActionItem(ACTION_COPY, "Copy text", R.drawable.ic_content_copy));
        iconActions.add(new ActionItem(ACTION_SHARE, "Share link", R.drawable.ic_share));
        addIconRow(actionsContainer, iconActions, comment, oldBookmarked, oldFavorited);

        if (hasAccount && !Utils.timeInSecondsMoreThanTwoWeeksAgo(comment.time)) {
            addReplyButton(actionsContainer, comment, oldBookmarked, oldFavorited);
        }
    }

    private ActionItem createVoteItem(int action,
                                      boolean upvoted,
                                      boolean downvoted,
                                      int loadingAction) {
        boolean loading = loadingAction == action;
        return new ActionItem(
                action,
                getVoteLabel(action, upvoted, downvoted, loading),
                getVoteIconRes(action, upvoted, downvoted),
                loading,
                !isVoteAction(loadingAction));
    }

    private String getVoteLabel(int action,
                                boolean upvoted,
                                boolean downvoted,
                                boolean loading) {
        switch (action) {
            case ACTION_UPVOTE:
                return loading ? "Upvoting" : (upvoted ? "Upvoted" : "Vote up");

            case ACTION_DOWNVOTE:
                return loading ? "Downvoting" : (downvoted ? "Downvoted" : "Vote down");

            case ACTION_UNVOTE:
                return loading ? "Removing vote" : "Unvote";

            default:
                return "";
        }
    }

    private int getVoteIconRes(int action, boolean upvoted, boolean downvoted) {
        switch (action) {
            case ACTION_UPVOTE:
                return upvoted ? R.drawable.ic_thumb_up_filled : R.drawable.ic_thumb_up;

            case ACTION_DOWNVOTE:
                return downvoted ? R.drawable.ic_thumb_down_filled : R.drawable.ic_thumb_down;

            case ACTION_UNVOTE:
                return R.drawable.ic_thumbs_up_down_unvote;

            default:
                return 0;
        }
    }

    private int getVoteLoadingAction(int commentId) {
        Integer loadingAction = voteLoadingActions.get(commentId);
        return loadingAction == null ? NO_VOTE_LOADING_ACTION : loadingAction;
    }

    private boolean isVoteAction(int action) {
        return action == ACTION_UPVOTE
                || action == ACTION_DOWNVOTE
                || action == ACTION_UNVOTE;
    }

    private void addIconRow(LinearLayout actionsContainer,
                            List<ActionItem> actionItems,
                            Comment comment,
                            boolean oldBookmarked,
                            boolean oldFavorited) {
        if (actionItems.isEmpty()) {
            return;
        }

        LinearLayout row = new LinearLayout(actionsContainer.getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER);
        row.setBaselineAligned(false);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (actionsContainer.getChildCount() > 0) {
            rowParams.topMargin = Utils.pxFromDpInt(host.requireCommentActionContext().getResources(), 4);
        }
        actionsContainer.addView(row, rowParams);

        for (ActionItem actionItem : actionItems) {
            FrameLayout buttonSlot = new FrameLayout(row.getContext());
            buttonSlot.setTag(actionItem.action);
            buttonSlot.setClipChildren(false);
            buttonSlot.setClipToPadding(false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    0,
                    Utils.pxFromDpInt(host.requireCommentActionContext().getResources(), 48),
                    1f);
            params.leftMargin = Utils.pxFromDpInt(host.requireCommentActionContext().getResources(), 1);
            params.rightMargin = Utils.pxFromDpInt(host.requireCommentActionContext().getResources(), 1);
            row.addView(buttonSlot, params);
            if (actionItem.loading) {
                showLoadingIndicator(buttonSlot, actionItem.label, false);
            } else {
                setIconButton(buttonSlot, actionItem, comment, oldBookmarked, oldFavorited, false);
            }
        }
    }

    private void setIconButton(FrameLayout buttonSlot,
                               ActionItem actionItem,
                               Comment comment,
                               boolean oldBookmarked,
                               boolean oldFavorited,
                               boolean animate) {
        buttonSlot.removeAllViews();
        ImageButton button = createIconButton(buttonSlot.getContext(), actionItem);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        buttonSlot.addView(button, params);
        button.setOnClickListener(v -> performAction(actionItem.action, comment, oldBookmarked, oldFavorited, button));
        button.setEnabled(actionItem.enabled);
        if (animate) {
            animateViewIn(button, null);
        }
    }

    private ImageButton createIconButton(Context ctx, ActionItem actionItem) {
        ImageButton button = new ImageButton(ctx);
        button.setImageResource(actionItem.iconRes);
        button.setTag(actionItem.iconRes);
        button.setImageTintList(ColorStateList.valueOf(MaterialColors.getColor(button, R.attr.storyColorNormal)));
        button.setBackgroundResource(resolveSelectableItemBackgroundBorderless(ctx));
        button.setContentDescription(actionItem.label);
        button.setPadding(
                Utils.pxFromDpInt(ctx.getResources(), 8),
                Utils.pxFromDpInt(ctx.getResources(), 10),
                Utils.pxFromDpInt(ctx.getResources(), 8),
                Utils.pxFromDpInt(ctx.getResources(), 10));
        button.setScaleType(ImageView.ScaleType.CENTER);
        TooltipCompat.setTooltipText(button, actionItem.label);
        return button;
    }

    private void showLoadingIndicator(FrameLayout buttonSlot, String label, boolean animate) {
        buttonSlot.removeAllViews();
        LoadingIndicator loadingIndicator = new LoadingIndicator(buttonSlot.getContext());
        int indicatorSize = Utils.pxFromDpInt(buttonSlot.getResources(), FAVORITE_LOADING_SIZE_DP);
        loadingIndicator.setIndicatorSize(indicatorSize);
        loadingIndicator.setContentDescription(label);
        loadingIndicator.setClickable(false);
        loadingIndicator.setFocusable(false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                indicatorSize,
                indicatorSize,
                android.view.Gravity.CENTER);
        buttonSlot.addView(loadingIndicator, params);
        if (animate) {
            animateViewIn(loadingIndicator, null);
        }
    }

    private void addReplyButton(LinearLayout actionsContainer,
                                Comment comment,
                                boolean oldBookmarked,
                                boolean oldFavorited) {
        Context buttonContext = new ContextThemeWrapper(
                actionsContainer.getContext(),
                com.google.android.material.R.style.Widget_Material3Expressive_Button_ElevatedButton);
        MaterialButton button = new MaterialButton(buttonContext);
        button.setText("Reply");
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setIconResource(R.drawable.ic_reply);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(Utils.pxFromDpInt(button.getResources(), 8));
        int replyBackgroundColor = MaterialColors.getColor(
                button,
                R.attr.overlayButtonColor);
        button.setTextColor(Color.WHITE);
        button.setIconTint(ColorStateList.valueOf(Color.WHITE));
        button.setBackgroundTintList(ColorStateList.valueOf(replyBackgroundColor));
        button.setContentDescription("Reply");
        button.setOnClickListener(v -> performAction(ACTION_REPLY, comment, oldBookmarked, oldFavorited));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Utils.pxFromDpInt(button.getResources(), 56));
        params.topMargin = Utils.pxFromDpInt(button.getResources(), 10);
        actionsContainer.addView(button, params);
    }

    private int resolveSelectableItemBackgroundBorderless(Context ctx) {
        TypedValue typedValue = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true);
        return typedValue.resourceId;
    }

    private void performAction(int action, Comment comment, boolean oldBookmarked, boolean oldFavorited) {
        performAction(action, comment, oldBookmarked, oldFavorited, null);
    }

    private void performAction(int action, Comment comment, boolean oldBookmarked, boolean oldFavorited, @Nullable View actionView) {
        if (!host.isCommentActionHostAdded()) {
            return;
        }

        Context ctx = host.requireCommentActionContext();
        switch (action) {
            case ACTION_VIEW_USER:
                UserDialogFragment.showUserDialog(host.getCommentActionActivityFragmentManager(), comment.by, accepted -> {
                    if (accepted) {
                        host.updateCommentActionUserTags(comment.by);
                    }
                });
                break;

            case ACTION_SHARE:
                ctx.startActivity(ShareUtils.getShareIntent(comment.id));
                break;

            case ACTION_COPY:
                ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Hacker News comment", Html.fromHtml(comment.text == null ? "" : comment.text));
                clipboard.setPrimaryClip(clip);

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Toast.makeText(ctx, "Text copied to clipboard", Toast.LENGTH_SHORT).show();
                }
                break;

            case ACTION_BOOKMARK:
                boolean newBookmarked = !oldBookmarked;
                if (oldBookmarked) {
                    Utils.removeBookmark(ctx, comment.id);
                } else {
                    Utils.addBookmark(ctx, comment.id);
                }
                if (actionView instanceof ImageButton) {
                    updateBookmarkButton((ImageButton) actionView, comment, newBookmarked, oldFavorited);
                } else if (overlay != null) {
                    bind(comment);
                }
                break;

            case ACTION_FAVORITE:
                if (!AccountUtils.hasAccountDetails(ctx)) {
                    AccountUtils.showLoginPrompt(host.getCommentActionParentFragmentManager());
                    break;
                }

                boolean newFavorited = !oldFavorited;
                FrameLayout favoriteSlot = getButtonSlot(actionView);
                favoriteLoadingIds.add(comment.id);
                if (favoriteSlot != null && actionView instanceof ImageButton) {
                    showFavoriteLoading(favoriteSlot, (ImageButton) actionView, newFavorited);
                } else if (overlay != null) {
                    bind(comment);
                }
                UserActions.setFavorite(ctx, comment.id, newFavorited, host.getCommentActionParentFragmentManager(), new UserActions.ActionCallback() {
                    @Override
                    public void onSuccess(Response response) {
                        favoriteLoadingIds.remove(comment.id);
                        showFavoriteButton(favoriteSlot, comment, true);
                    }

                    @Override
                    public void onFailure(String summary, String response) {
                        Utils.setFavorite(ctx, comment.id, oldFavorited);
                        favoriteLoadingIds.remove(comment.id);
                        showFavoriteButton(favoriteSlot, comment, true);
                        UserActions.showFailureDetailDialog(ctx, summary, response);
                        Toast.makeText(ctx, "Couldn't update favorite", Toast.LENGTH_SHORT).show();
                    }
                });
                break;

            case ACTION_UPVOTE:
            case ACTION_DOWNVOTE:
            case ACTION_UNVOTE:
                performVoteAction(action, comment, actionView);
                break;

            case ACTION_REPLY:
                if (!AccountUtils.hasAccountDetails(ctx)) {
                    AccountUtils.showLoginPrompt(host.getCommentActionParentFragmentManager());
                    return;
                }
                if (Utils.timeInSecondsMoreThanTwoWeeksAgo(comment.time)) {
                    Toast.makeText(ctx, "This comment is too old to reply to", Toast.LENGTH_SHORT).show();
                    return;
                }

                Intent replyIntent = new Intent(ctx, ComposeActivity.class);
                replyIntent.putExtra(ComposeActivity.EXTRA_ID, comment.id);
                replyIntent.putExtra(ComposeActivity.EXTRA_PARENT_TEXT, comment.text);
                replyIntent.putExtra(ComposeActivity.EXTRA_POST_TITLE, host.getCommentActionReplyPostTitle());
                replyIntent.putExtra(ComposeActivity.EXTRA_USER, comment.by);
                replyIntent.putExtra(ComposeActivity.EXTRA_TYPE, ComposeActivity.TYPE_COMMENT_REPLY);
                ctx.startActivity(replyIntent);
                break;
        }
    }

    private void performVoteAction(int action, Comment comment, @Nullable View actionView) {
        if (!host.isCommentActionHostAdded()) {
            return;
        }

        Context ctx = host.requireCommentActionContext();
        if (!AccountUtils.hasAccountDetails(ctx)) {
            AccountUtils.showLoginPrompt(host.getCommentActionParentFragmentManager());
            return;
        }
        if (isVoteAction(getVoteLoadingAction(comment.id))) {
            return;
        }

        boolean wasUpvoted = Utils.isUpvoted(ctx, comment.id, true);
        boolean wasDownvoted = !wasUpvoted && downvotedIds.contains(comment.id);
        FrameLayout voteSlot = getButtonSlot(actionView);
        ImageButton button = actionView instanceof ImageButton ? (ImageButton) actionView : null;

        voteLoadingActions.put(comment.id, action);
        if (voteSlot != null && button != null) {
            showVoteLoading(voteSlot, button, action);
        } else if (overlay != null) {
            bind(comment);
        }

        UserActions.ActionCallback cb = new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                applyVoteState(ctx, comment.id, action == ACTION_UPVOTE, action == ACTION_DOWNVOTE);
                voteLoadingActions.remove(comment.id);
                showVoteButton(voteSlot, comment, action, true);
            }

            @Override
            public void onFailure(String summary, String response) {
                applyVoteState(ctx, comment.id, wasUpvoted, wasDownvoted);
                voteLoadingActions.remove(comment.id);
                showVoteButton(voteSlot, comment, action, true);
            }
        };

        switch (action) {
            case ACTION_UPVOTE:
                UserActions.upvote(ctx, comment.id, host.getCommentActionParentFragmentManager(), cb);
                break;

            case ACTION_DOWNVOTE:
                UserActions.downvote(ctx, comment.id, host.getCommentActionParentFragmentManager(), cb);
                break;

            case ACTION_UNVOTE:
                UserActions.unvote(ctx, comment.id, host.getCommentActionParentFragmentManager(), cb);
                break;
        }
    }

    private void applyVoteState(Context ctx, int commentId, boolean upvoted, boolean downvoted) {
        Utils.setUpvoted(ctx, commentId, true, upvoted);
        if (downvoted && !upvoted) {
            downvotedIds.add(commentId);
        } else {
            downvotedIds.remove(commentId);
        }
    }

    private void showVoteLoading(FrameLayout voteSlot, ImageButton button, int action) {
        String label = getVoteLabel(action, false, false, true);
        setVoteButtonsEnabled(false);
        button.setContentDescription(label);
        TooltipCompat.setTooltipText(button, label);
        animateViewOut(button, () -> showLoadingIndicator(voteSlot, label, true));
    }

    private void showVoteButton(@Nullable FrameLayout voteSlot,
                                Comment comment,
                                int action,
                                boolean animate) {
        if (!host.isCommentActionHostAdded() || visibleCommentId != comment.id) {
            return;
        }

        Context ctx = host.requireCommentActionContext();
        boolean bookmarked = SettingsUtils.shouldUseBookmarks(ctx) && Utils.isBookmarked(ctx, comment.id);
        boolean favorited = Utils.isFavorited(ctx, comment.id);
        boolean upvoted = Utils.isUpvoted(ctx, comment.id, true);
        boolean downvoted = !upvoted && downvotedIds.contains(comment.id);
        if (voteSlot == null || !ViewCompat.isAttachedToWindow(voteSlot)) {
            if (overlay != null) {
                bind(comment);
            }
            return;
        }

        ActionItem actionItem = new ActionItem(
                action,
                getVoteLabel(action, upvoted, downvoted, false),
                getVoteIconRes(action, upvoted, downvoted),
                false,
                false);
        View outgoing = voteSlot.getChildCount() > 0 ? voteSlot.getChildAt(0) : null;
        Runnable showVoteButton = () -> {
            setIconButton(voteSlot, actionItem, comment, bookmarked, favorited, false);
            View incoming = voteSlot.getChildCount() > 0 ? voteSlot.getChildAt(0) : null;
            Runnable afterLoading = () -> {
                setVoteButtonsEnabled(true);
                updateVoteButtons(comment, bookmarked, favorited, action, upvoted, downvoted, animate);
            };
            if (animate && incoming != null) {
                animateViewIn(incoming, afterLoading);
            } else {
                afterLoading.run();
            }
        };

        if (animate && outgoing != null) {
            animateViewOut(outgoing, showVoteButton);
        } else {
            showVoteButton.run();
        }
    }

    private void updateVoteButtons(Comment comment,
                                   boolean bookmarked,
                                   boolean favorited,
                                   int completedAction,
                                   boolean upvoted,
                                   boolean downvoted,
                                   boolean animate) {
        updateVoteButton(comment, bookmarked, favorited, ACTION_UPVOTE, completedAction, upvoted, downvoted, animate);
        updateVoteButton(comment, bookmarked, favorited, ACTION_DOWNVOTE, completedAction, upvoted, downvoted, animate);
        updateVoteButton(comment, bookmarked, favorited, ACTION_UNVOTE, completedAction, upvoted, downvoted, animate);
    }

    private void updateVoteButton(Comment comment,
                                  boolean bookmarked,
                                  boolean favorited,
                                  int action,
                                  int completedAction,
                                  boolean upvoted,
                                  boolean downvoted,
                                  boolean animate) {
        FrameLayout voteSlot = findButtonSlot(action);
        if (voteSlot == null || voteSlot.getChildCount() == 0 || !(voteSlot.getChildAt(0) instanceof ImageButton)) {
            return;
        }

        ImageButton button = (ImageButton) voteSlot.getChildAt(0);
        int iconRes = getVoteIconRes(action, upvoted, downvoted);
        String label = getVoteLabel(action, upvoted, downvoted, false);
        Runnable updateListener = () -> button.setOnClickListener(v ->
                performAction(action, comment, bookmarked, favorited, button));

        button.setEnabled(true);
        if (action == completedAction || isButtonShowingIcon(button, iconRes)) {
            button.setImageResource(iconRes);
            button.setTag(iconRes);
            button.setContentDescription(label);
            TooltipCompat.setTooltipText(button, label);
            updateListener.run();
            return;
        }

        if (animate) {
            animateIconChange(button, iconRes, label, updateListener);
        } else {
            button.setImageResource(iconRes);
            button.setTag(iconRes);
            button.setContentDescription(label);
            TooltipCompat.setTooltipText(button, label);
            updateListener.run();
        }
    }

    private boolean isButtonShowingIcon(ImageButton button, int iconRes) {
        Object tag = button.getTag();
        return tag instanceof Integer && (Integer) tag == iconRes;
    }

    private void setVoteButtonsEnabled(boolean enabled) {
        setVoteButtonEnabled(ACTION_UPVOTE, enabled);
        setVoteButtonEnabled(ACTION_DOWNVOTE, enabled);
        setVoteButtonEnabled(ACTION_UNVOTE, enabled);
    }

    private void setVoteButtonEnabled(int action, boolean enabled) {
        FrameLayout voteSlot = findButtonSlot(action);
        if (voteSlot == null || voteSlot.getChildCount() == 0) {
            return;
        }

        View child = voteSlot.getChildAt(0);
        if (child instanceof ImageButton) {
            child.setEnabled(enabled);
        }
    }

    @Nullable
    private FrameLayout findButtonSlot(int action) {
        if (binding == null) {
            return null;
        }

        return findButtonSlot(binding.commentActionActions, action);
    }

    @Nullable
    private FrameLayout findButtonSlot(ViewGroup parent, int action) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof FrameLayout && child.getTag() instanceof Integer && (Integer) child.getTag() == action) {
                return (FrameLayout) child;
            }
            if (child instanceof ViewGroup) {
                FrameLayout slot = findButtonSlot((ViewGroup) child, action);
                if (slot != null) {
                    return slot;
                }
            }
        }
        return null;
    }

    private void updateBookmarkButton(ImageButton button,
                                      Comment comment,
                                      boolean bookmarked,
                                      boolean oldFavorited) {
        int iconRes = bookmarked ? R.drawable.ic_bookmark_filled : R.drawable.ic_bookmark;
        String label = bookmarked ? "Remove bookmark" : "Bookmark";
        animateIconChange(button, iconRes, label, () ->
                button.setOnClickListener(v -> performAction(
                        ACTION_BOOKMARK,
                        comment,
                        bookmarked,
                        oldFavorited,
                        button)));
    }

    private void showFavoriteLoading(FrameLayout favoriteSlot,
                                     ImageButton button,
                                     boolean favorite) {
        String label = favorite ? "Adding favorite" : "Removing favorite";
        button.setEnabled(false);
        button.setContentDescription(label);
        TooltipCompat.setTooltipText(button, label);
        animateViewOut(button, () -> showLoadingIndicator(favoriteSlot, label, true));
    }

    private void showFavoriteButton(@Nullable FrameLayout favoriteSlot,
                                    Comment comment,
                                    boolean animate) {
        if (!host.isCommentActionHostAdded() || visibleCommentId != comment.id) {
            return;
        }

        Context ctx = host.requireCommentActionContext();
        boolean bookmarked = SettingsUtils.shouldUseBookmarks(ctx) && Utils.isBookmarked(ctx, comment.id);
        boolean favorited = Utils.isFavorited(ctx, comment.id);
        if (favoriteSlot == null || !ViewCompat.isAttachedToWindow(favoriteSlot)) {
            if (overlay != null) {
                bind(comment);
            }
            return;
        }

        ActionItem actionItem = new ActionItem(
                ACTION_FAVORITE,
                favorited ? "Remove favorite" : "Favorite",
                favorited ? R.drawable.ic_star_filled : R.drawable.ic_star);
        View outgoing = favoriteSlot.getChildCount() > 0 ? favoriteSlot.getChildAt(0) : null;
        if (animate && outgoing != null) {
            animateViewOut(outgoing, () ->
                    setIconButton(favoriteSlot, actionItem, comment, bookmarked, favorited, true));
        } else {
            setIconButton(favoriteSlot, actionItem, comment, bookmarked, favorited, animate);
        }
    }

    @Nullable
    private FrameLayout getButtonSlot(@Nullable View actionView) {
        if (actionView != null && actionView.getParent() instanceof FrameLayout) {
            return (FrameLayout) actionView.getParent();
        }
        return null;
    }

    private void animateIconChange(ImageButton button,
                                   int iconRes,
                                   String label,
                                   @Nullable Runnable afterIconSet) {
        button.setEnabled(false);
        animateViewOut(button, () -> {
            button.setImageResource(iconRes);
            button.setTag(iconRes);
            button.setContentDescription(label);
            TooltipCompat.setTooltipText(button, label);
            if (afterIconSet != null) {
                afterIconSet.run();
            }
            animateViewIn(button, () -> button.setEnabled(true));
        });
    }

    private void animateViewOut(View view, Runnable afterOut) {
        view.animate().cancel();
        if (!ViewCompat.isAttachedToWindow(view)) {
            view.setAlpha(0f);
            view.setScaleX(ICON_SWAP_MIN_SCALE);
            view.setScaleY(ICON_SWAP_MIN_SCALE);
            afterOut.run();
            return;
        }

        view.animate()
                .alpha(0f)
                .scaleX(ICON_SWAP_MIN_SCALE)
                .scaleY(ICON_SWAP_MIN_SCALE)
                .setDuration(ICON_SWAP_OUT_DURATION_MS)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        view.animate().setListener(null);
                        afterOut.run();
                    }
                })
                .start();
    }

    private void animateViewIn(View view, @Nullable Runnable afterIn) {
        view.animate().cancel();
        view.setAlpha(0f);
        view.setScaleX(ICON_SWAP_MIN_SCALE);
        view.setScaleY(ICON_SWAP_MIN_SCALE);
        if (!ViewCompat.isAttachedToWindow(view)) {
            view.setAlpha(1f);
            view.setScaleX(1f);
            view.setScaleY(1f);
            if (afterIn != null) {
                afterIn.run();
            }
            return;
        }

        view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(ICON_SWAP_IN_DURATION_MS)
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

    private void configureOverlayInsets(View content) {
        int baseLeft = content.getPaddingLeft();
        int baseTop = content.getPaddingTop();
        int baseRight = content.getPaddingRight();
        int baseBottom = content.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(content, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                v.setPadding(
                        baseLeft + insets.left,
                        baseTop + insets.top,
                        baseRight + insets.right,
                        baseBottom + insets.bottom);
                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(content);
    }

    private void configureCardWidth(MaterialCardView card) {
        Context ctx = host.requireCommentActionContext();
        int maxCardWidth = Utils.pxFromDpInt(ctx.getResources(), Utils.isTablet(ctx.getResources()) ? 640 : 520);
        int horizontalPadding = Utils.pxFromDpInt(ctx.getResources(), 40);
        int hostWidth = ctx.getResources().getDisplayMetrics().widthPixels;
        if (card.getParent() instanceof View) {
            int parentWidth = ((View) card.getParent()).getWidth();
            if (parentWidth > 0) {
                hostWidth = parentWidth;
            }
        }
        int availableWidth = Math.max(Utils.pxFromDpInt(ctx.getResources(), 280),
                hostWidth - horizontalPadding);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) card.getLayoutParams();
        params.width = Math.min(maxCardWidth, availableWidth);
        card.setLayoutParams(params);
    }

    private void resizeDialogScroll() {
        if (binding == null) {
            return;
        }

        NestedScrollView cardScroll = binding.commentActionCardScroll;
        View content = binding.commentActionContent;
        if (cardScroll == null || content == null) {
            return;
        }

        cardScroll.post(() -> {
            if (binding == null) {
                return;
            }

            int availableHeight = content.getHeight() - content.getPaddingTop() - content.getPaddingBottom();
            if (availableHeight <= 0 || cardScroll.getChildCount() == 0) {
                return;
            }

            int minHeight = Utils.pxFromDpInt(content.getResources(), 160);
            int maxHeight = Math.max(minHeight, availableHeight);
            View child = cardScroll.getChildAt(0);
            int contentHeight = child.getHeight() + cardScroll.getPaddingTop() + cardScroll.getPaddingBottom();
            if (contentHeight <= 0) {
                return;
            }

            boolean needsScrolling = contentHeight > maxHeight;
            ViewGroup.LayoutParams params = cardScroll.getLayoutParams();
            params.height = needsScrolling ? maxHeight : ViewGroup.LayoutParams.WRAP_CONTENT;
            cardScroll.setLayoutParams(params);
            cardScroll.setVerticalFadingEdgeEnabled(needsScrolling);
            cardScroll.setOverScrollMode(needsScrolling ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
        });
    }

    private void resizeTextBox(NestedScrollView textScroll, HtmlTextView commentText) {
        textScroll.post(() -> {
            if (overlay == null) {
                return;
            }

            int maxHeight = Utils.pxFromDpInt(textScroll.getResources(), TEXT_MAX_HEIGHT_DP);
            int contentHeight = commentText.getHeight();
            if (contentHeight <= 0) {
                return;
            }

            int paddedContentHeight = contentHeight + textScroll.getPaddingTop() + textScroll.getPaddingBottom();
            boolean needsScrolling = paddedContentHeight > maxHeight;

            ViewGroup.LayoutParams params = textScroll.getLayoutParams();
            params.height = needsScrolling ? maxHeight : paddedContentHeight;
            textScroll.setLayoutParams(params);
            textScroll.setVerticalScrollBarEnabled(needsScrolling);
            textScroll.setScrollbarFadingEnabled(true);
            textScroll.setVerticalFadingEdgeEnabled(needsScrolling);
            textScroll.setOverScrollMode(needsScrolling ? View.OVER_SCROLL_IF_CONTENT_SCROLLS : View.OVER_SCROLL_NEVER);
            resizeDialogScroll();
        });
    }

    private MaterialContainerTransform createTransform(ViewGroup drawingView, View startView, View endView, int direction) {
        MaterialContainerTransform transform = new MaterialContainerTransform();
        transform.setStartView(startView);
        transform.setEndView(endView);
        transform.setDuration(TRANSFORM_DURATION_MS);
        transform.setScrimColor(Color.TRANSPARENT);
        transform.setDrawingViewId(ensureDrawingViewId(drawingView));
        transform.setTransitionDirection(direction);
        transform.setFadeMode(MaterialContainerTransform.FADE_MODE_THROUGH);
        transform.setFitMode(MaterialContainerTransform.FIT_MODE_AUTO);
        transform.setStartShapeAppearanceModel(createShape(startView));
        transform.setEndShapeAppearanceModel(createShape(endView));
        transform.setScaleMaskProgressThresholds(createProgressThresholds());
        transform.setShapeMaskProgressThresholds(createProgressThresholds());
        transform.setElevationShadowEnabled(true);
        transform.setStartContainerColor(getContainerColor(startView));
        transform.setEndContainerColor(getContainerColor(endView));
        transform.setStartElevation(getContainerElevation(startView));
        transform.setEndElevation(getContainerElevation(endView));

        return transform;
    }

    private MaterialContainerTransform.ProgressThresholds createProgressThresholds() {
        return new MaterialContainerTransform.ProgressThresholds(
                TRANSFORM_START_PROGRESS,
                TRANSFORM_END_PROGRESS);
    }

    private ShapeAppearanceModel createShape(View view) {
        int cornerRadiusDp;
        if (view == card) {
            cornerRadiusDp = CARD_CORNER_RADIUS_DP;
        } else if (view instanceof MaterialCardView) {
            cornerRadiusDp = CARD_SOURCE_CORNER_RADIUS_DP;
        } else {
            cornerRadiusDp = STANDARD_SOURCE_CORNER_RADIUS_DP;
        }
        return ShapeAppearanceModel.builder()
                .setAllCornerSizes(Utils.pxFromDpInt(view.getResources(), cornerRadiusDp))
                .build();
    }

    private int getContainerColor(View view) {
        if (view instanceof MaterialCardView) {
            return ((MaterialCardView) view).getCardBackgroundColor().getDefaultColor();
        }
        return getCardBackgroundColor(view.getContext());
    }

    private int getCardBackgroundColor(Context ctx) {
        if (host.shouldUseCommentActionCardStyle(ctx)) {
            return MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorSurfaceContainerHigh, Color.TRANSPARENT);
        }
        return ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx));
    }

    private float getContainerElevation(View view) {
        if (view instanceof MaterialCardView) {
            return ((MaterialCardView) view).getCardElevation();
        }
        return view.getElevation();
    }

    void dismiss(boolean animate) {
        dismiss(animate, null);
    }

    private void dismiss(boolean animate, @Nullable Runnable afterDismiss) {
        if (overlay == null) {
            if (afterDismiss != null) {
                afterDismiss.run();
            }
            return;
        }
        if (dismissing) {
            return;
        }
        if (binding == null || card == null) {
            finishDismiss(afterDismiss);
            return;
        }

        dismissing = true;
        predictiveBackActive = false;
        ViewGroup overlayHost = host.getCommentActionOverlayHost();
        View scrim = binding.commentActionScrim;
        View endView = resolveSourceView(visibleCommentId);

        pendingCommentId = NO_COMMENT_ID;
        visibleCommentId = NO_COMMENT_ID;
        host.setCommentActionLinksDisabled(false);
        host.syncCommentActionBackState();

        if (animate && overlayHost != null && card != null && isUsableTransition(overlayHost, card, endView)) {
            MaterialContainerTransform transform = createTransform(
                    overlayHost,
                    card,
                    endView,
                    MaterialContainerTransform.TRANSITION_DIRECTION_RETURN);
            transform.addTarget(endView);
            transform.addListener(new TransitionListenerAdapter() {
                private boolean finished;

                @Override
                public void onTransitionEnd(@NonNull Transition transition) {
                    finish();
                }

                @Override
                public void onTransitionCancel(@NonNull Transition transition) {
                    finish();
                }

                private void finish() {
                    if (finished) {
                        return;
                    }
                    finished = true;
                    finishDismiss(afterDismiss);
                }
            });
            TransitionManager.beginDelayedTransition(overlayHost, transform);
            scrim.animate().alpha(0f).setDuration(TRANSFORM_DURATION_MS).start();
            setSourceVisible(endView, true);
            card.setVisibility(View.INVISIBLE);
        } else {
            card.animate()
                    .alpha(0f)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(TRANSFORM_DURATION_MS)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            finishDismiss(afterDismiss);
                        }
                    });
            scrim.animate().alpha(0f).setDuration(TRANSFORM_DURATION_MS).start();
        }
    }

    private void finishDismiss(@Nullable Runnable afterDismiss) {
        removeNow();
        if (afterDismiss != null) {
            afterDismiss.run();
        }
    }

    void removeNow() {
        if (overlay == null) {
            return;
        }

        View content = binding != null ? binding.commentActionContent : null;
        if (content != null) {
            ViewCompat.setOnApplyWindowInsetsListener(content, null);
        }
        setSourceVisible(sourceView, true);
        if (overlay.getParent() instanceof ViewGroup) {
            ((ViewGroup) overlay.getParent()).removeView(overlay);
        }

        overlay = null;
        binding = null;
        card = null;
        sourceView = null;
        visibleCommentId = NO_COMMENT_ID;
        dismissing = false;
        host.setCommentActionLinksDisabled(false);
        host.syncCommentActionBackState();
        host.onCommentActionOverlayRemoved();
    }

    void startPredictiveBack(@NonNull BackEventCompat backEvent) {
        if (card == null || binding == null || dismissing) {
            return;
        }

        predictiveBackActive = true;
        card.animate().cancel();
        View scrim = binding.commentActionScrim;
        if (scrim != null) {
            scrim.animate().cancel();
        }
        updatePredictiveBack(backEvent);
    }

    void updatePredictiveBack(@NonNull BackEventCompat backEvent) {
        if (card == null || binding == null || dismissing) {
            return;
        }

        predictiveBackActive = true;
        float progress = Math.max(0f, Math.min(1f, backEvent.getProgress()));
        float easedProgress = 1f - ((1f - progress) * (1f - progress));
        float scale = 1f - ((1f - PREDICTIVE_BACK_MIN_SCALE) * easedProgress);
        float edgeDirection = backEvent.getSwipeEdge() == BackEventCompat.EDGE_RIGHT ? -1f : 1f;

        card.setPivotX(edgeDirection > 0f ? 0f : card.getWidth());
        card.setPivotY(backEvent.getTouchY() > 0f
                ? Math.max(0f, Math.min(card.getHeight(), backEvent.getTouchY() - card.getTop()))
                : card.getHeight() / 2f);
        card.setScaleX(scale);
        card.setScaleY(scale);
        card.setTranslationX(edgeDirection
                * Utils.pxFromDpInt(card.getResources(), PREDICTIVE_BACK_TRANSLATION_X_DP)
                * easedProgress);
        card.setTranslationY(Utils.pxFromDpInt(card.getResources(), PREDICTIVE_BACK_TRANSLATION_Y_DP)
                * easedProgress);

        View scrim = binding.commentActionScrim;
        if (scrim != null) {
            scrim.setAlpha(1f - ((1f - PREDICTIVE_BACK_MIN_SCRIM_ALPHA) * easedProgress));
        }
    }

    void cancelPredictiveBack() {
        if (card == null || binding == null || !predictiveBackActive) {
            return;
        }

        predictiveBackActive = false;
        card.animate()
                .translationX(0f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(TRANSFORM_DURATION_MS)
                .setListener(null)
                .start();

        View scrim = binding.commentActionScrim;
        if (scrim != null) {
            scrim.animate()
                    .alpha(1f)
                    .setDuration(TRANSFORM_DURATION_MS)
                    .start();
        }
    }

    void commitPredictiveBack() {
        if (overlay == null || card == null || binding == null || dismissing) {
            return;
        }

        predictiveBackActive = false;
        card.animate().cancel();
        dismiss(true);
    }

    private void setSourceVisible(@Nullable View sourceView, boolean visible) {
        if (isUsableTransitionView(sourceView)) {
            sourceView.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
    }

    void restorePending() {
        if (pendingCommentId == NO_COMMENT_ID || overlay != null) {
            return;
        }

        Comment comment = host.findCommentActionComment(pendingCommentId);
        if (comment == null) {
            return;
        }

        int restoredCommentId = pendingCommentId;
        pendingCommentId = NO_COMMENT_ID;
        show(comment, host.findCommentActionSourceView(restoredCommentId), false);
    }

    @Nullable
    private View resolveSourceView(int commentId) {
        if (isUsableTransitionView(sourceView)) {
            return sourceView;
        }
        return host.findCommentActionSourceView(commentId);
    }

    private boolean isUsableTransitionView(@Nullable View view) {
        return view != null && ViewCompat.isAttachedToWindow(view) && view.getWidth() > 0 && view.getHeight() > 0;
    }

    private boolean isUsableTransition(@Nullable ViewGroup drawingView, @Nullable View startView, @Nullable View endView) {
        return isUsableTransitionView(drawingView)
                && isUsableTransitionView(startView)
                && isUsableTransitionView(endView)
                && isDescendantOf(startView, drawingView)
                && isDescendantOf(endView, drawingView);
    }

    private int ensureDrawingViewId(@NonNull ViewGroup drawingView) {
        if (drawingView.getId() == View.NO_ID) {
            drawingView.setId(View.generateViewId());
        }
        return drawingView.getId();
    }

    private boolean isDescendantOf(@Nullable View view, @NonNull ViewGroup ancestor) {
        View current = view;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private static class ActionItem {
        final int action;
        final String label;
        final int iconRes;
        final boolean loading;
        final boolean enabled;

        ActionItem(int action, String label, int iconRes) {
            this(action, label, iconRes, false);
        }

        ActionItem(int action, String label, int iconRes, boolean loading) {
            this(action, label, iconRes, loading, true);
        }

        ActionItem(int action, String label, int iconRes, boolean loading, boolean enabled) {
            this.action = action;
            this.label = label;
            this.iconRes = iconRes;
            this.loading = loading;
            this.enabled = enabled;
        }
    }
}
