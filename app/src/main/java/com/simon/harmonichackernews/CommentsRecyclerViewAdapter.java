package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.PollOption;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.squareup.picasso.Picasso;

import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.List;
import java.util.Objects;

public class CommentsRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Comment> comments;
    private HeaderClickListener headerClickListener;
    private CommentClickListener commentClickListener;
    private CommentClickListener commentLongClickListener;
    private HeaderActionClickListener headerActionClickListener;
    private RetryListener retryListener;

    public LinearLayout bottomSheet;
    public FragmentManager fragmentManager;
    public Story story;
    public boolean loadingFailed = false;
    public boolean loadingFailedServerError = false;
    public boolean commentsLoaded = false;
    public boolean collapseParent;
    public boolean showThumbnail;
    public boolean monochromeCommentDepthIndicators;
    public boolean showNavigationBar;
    public boolean showInvert;
    public boolean showExpand;
    public boolean integratedWebview;
    public boolean showTopLevelDepthIndicator;
    public String username;
    public int preferredTextSize;
    private boolean isTablet;
    public boolean darkThemeActive;
    public String font;

    public int navbarHeight = 0;

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_ITEM = 1;

    public final static int FLAG_ACTION_CLICK_USER = 0;
    public final static int FLAG_ACTION_CLICK_COMMENT = 1;
    public final static int FLAG_ACTION_CLICK_VOTE = 2;
    public final static int FLAG_ACTION_CLICK_SHARE = 4;
    public final static int FLAG_ACTION_CLICK_MORE = 5;
    public final static int FLAG_ACTION_CLICK_BACK = -1;
    public final static int FLAG_ACTION_CLICK_REFRESH = -2;
    public final static int FLAG_ACTION_CLICK_EXPAND = -3;
    public final static int FLAG_ACTION_CLICK_INVERT = -5;

    private static final int[] commentDepthColorsDark = new int[]{
            R.color.commentIndentIndicatorColor1,
            R.color.commentIndentIndicatorColor2,
            R.color.commentIndentIndicatorColor3,
            R.color.commentIndentIndicatorColor4,
            R.color.commentIndentIndicatorColor5,
            R.color.commentIndentIndicatorColor6,
            R.color.commentIndentIndicatorColor7
    };

    private static final int[] commentDepthColorsLight = new int[]{
            R.color.commentIndentIndicatorColor1light,
            R.color.commentIndentIndicatorColor2light,
            R.color.commentIndentIndicatorColor3light,
            R.color.commentIndentIndicatorColor4light,
            R.color.commentIndentIndicatorColor5light,
            R.color.commentIndentIndicatorColor6light,
            R.color.commentIndentIndicatorColor7light
    };

    public CommentsRecyclerViewAdapter(boolean useIntegratedWebview,
                                       LinearLayout sheet,
                                       FragmentManager fm,
                                       List<Comment> items,
                                       Story masterItem,
                                       boolean shouldCollapseParent,
                                       boolean shouldShowThumbnail,
                                       String usernameParam,
                                       int prefTextSize,
                                       boolean shouldUseMonochromeCommentDepthIndicators,
                                       boolean shouldShowNavigationBar,
                                       String prefFont,
                                       boolean shouldShowInvert,
                                       boolean shouldShowTopLevelDepthIndicator,
                                       boolean shouldShowExpand,
                                       boolean darkTheme) {
        integratedWebview = useIntegratedWebview;
        bottomSheet = sheet;
        fragmentManager = fm;
        comments = items;
        story = masterItem;
        collapseParent = shouldCollapseParent;
        showThumbnail = shouldShowThumbnail;
        monochromeCommentDepthIndicators = shouldUseMonochromeCommentDepthIndicators;
        showNavigationBar = shouldShowNavigationBar;
        username = usernameParam;
        preferredTextSize = prefTextSize;
        font = prefFont;
        showInvert = shouldShowInvert;
        showTopLevelDepthIndicator = shouldShowTopLevelDepthIndicator;
        showExpand = shouldShowExpand;
        darkThemeActive = darkTheme;
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        isTablet = Utils.isTablet(parent.getContext());

        if (viewType == TYPE_ITEM) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.comments_item, parent, false);
            return new ItemViewHolder(view);
        } else {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.comments_header, parent, false);

            return new CommentsRecyclerViewAdapter.HeaderViewHolder(view);
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final Context ctx = holder.itemView.getContext();
        if (holder instanceof HeaderViewHolder) {
            //Header stuff
            final HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;

            String topLine = "";

            if (story.isLink) {
                headerViewHolder.linkImage.setVisibility(View.VISIBLE);
                headerViewHolder.headerView.setClickable(true);
                if (story.url != null) {
                    try {
                        topLine = "(" + Utils.getDomainName(story.url) + ")";
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } else {
                //when not a link, we're looking at a text post probably
                headerViewHolder.linkImage.setVisibility(View.GONE);
                headerViewHolder.headerView.setClickable(false);

                if (story.text != null) {
                    topLine = story.text;
                }
            }

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
                                UserActions.upvote(ctx, pollOption.id, fragmentManager);
                            }
                        });
                        headerViewHolder.pollLayout.addView(materialButton);
                    } else {
                        ProgressBar progressBar = new ProgressBar(ctx);
                        headerViewHolder.pollLayout.addView(progressBar);
                    }
                }
            } else {
                headerViewHolder.pollLayout.setVisibility(View.GONE);
            }

            if (!TextUtils.isEmpty(story.pdfTitle)) {
                SpannableStringBuilder sb = new SpannableStringBuilder(Html.fromHtml(story.pdfTitle) + " ");
                ImageSpan imageSpan = new ImageSpan(ctx, R.drawable.ic_action_pdf_large);
                sb.setSpan(imageSpan, story.pdfTitle.length(), story.pdfTitle.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                headerViewHolder.titleView.setText(sb);
            } else {
                headerViewHolder.titleView.setText(Html.fromHtml(story.title));
            }

            if (story.loaded) {
                headerViewHolder.metaVotes.setText(String.valueOf(story.score));
                headerViewHolder.metaComments.setText(String.valueOf(story.descendants));
                headerViewHolder.metaTime.setText(story.getTimeFormatted());
                headerViewHolder.metaBy.setText(story.by);
            }

            if (story.isLink) {
                headerViewHolder.urlView.setText(topLine);
            } else {
                headerViewHolder.textView.setHtml(topLine);
            }

            headerViewHolder.metaContainer.setVisibility(story.loaded ? View.VISIBLE : View.GONE);
            headerViewHolder.urlView.setVisibility(story.isLink ? View.VISIBLE : View.GONE);
            headerViewHolder.textView.setVisibility(story.isLink ? View.GONE : View.VISIBLE);
            headerViewHolder.metaVotes.setVisibility(story.isComment ? View.GONE : View.VISIBLE);
            headerViewHolder.metaVotesIcon.setVisibility(story.isComment ? View.GONE : View.VISIBLE);

            FontUtils.setTypeface(headerViewHolder.titleView, true, 26, 26, 23, 26, 24, 26);
            FontUtils.setTypeface(headerViewHolder.textView, false, preferredTextSize);

            if (loadingFailed) {
                headerViewHolder.loadingIndicator.setVisibility(View.GONE);
                headerViewHolder.emptyView.setVisibility(View.GONE);
            } else {
                if (commentsLoaded) {
                    headerViewHolder.loadingIndicator.setVisibility(View.GONE);
                    headerViewHolder.emptyView.setVisibility(story.descendants > 0 ? View.GONE : View.VISIBLE);
                } else {
                    headerViewHolder.loadingIndicator.setVisibility(View.VISIBLE);
                    headerViewHolder.emptyView.setVisibility(View.GONE);
                }
            }

            int actionContainerPadding = Math.round(headerViewHolder.actionsContainer.getResources().getDimension(R.dimen.comments_header_action_padding));
            headerViewHolder.actionsContainer.setPadding(actionContainerPadding, 0, actionContainerPadding, 0);

            headerViewHolder.favicon.setVisibility(showThumbnail && !story.isComment && story.isLink ? View.VISIBLE : View.GONE);
            if (showThumbnail && !TextUtils.isEmpty(story.url)) {
                try {
                    Picasso.get()
                            .load("https://api.faviconkit.com/" + Utils.getDomainName(story.url) + "/80")
                            .resize(80, 80)
                            .onlyScaleDown()
                            .placeholder(Objects.requireNonNull(ContextCompat.getDrawable(ctx, R.drawable.ic_action_web)))
                            .into(headerViewHolder.favicon);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            headerViewHolder.bookmarkIcon.setBackgroundResource(
                    Utils.isBookmarked(ctx, story.id) ? R.drawable.ic_action_bookmark_filled : R.drawable.ic_action_bookmark_border);

            headerViewHolder.bookmarkLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    boolean wasBookmarked = Utils.isBookmarked(view.getContext(), story.id);

                    if (wasBookmarked) {
                        Utils.removeBookmark(view.getContext(), story.id);
                    } else {
                        Utils.addBookmark(view.getContext(), story.id);
                    }

                    notifyItemChanged(0);

                    Snackbar snackbar = Snackbar.make(
                            view,
                            wasBookmarked ? "Removed bookmark" : "Bookmarked post",
                            Snackbar.LENGTH_SHORT);

                    ViewCompat.setElevation(snackbar.getView(), Utils.pxFromDp(view.getResources(), 24));

                    if (showNavigationBar) {
                        snackbar.getView().setTranslationY(-ctx.getResources().getDimensionPixelSize(R.dimen.comments_bottom_navigation));
                    }

                    snackbar.show();
                }
            });

            headerViewHolder.emptyViewText.setText(story.isComment ? "No replies" : "No comments");
            headerViewHolder.bookmarkLayoutParent.setVisibility(story.isComment ? View.GONE : View.VISIBLE);
            headerViewHolder.commentLayoutParent.setVisibility(Utils.timeInSecondsMoreThanTwoWeeksAgo(story.time) ? View.GONE : View.VISIBLE);

            headerViewHolder.loadingFailed.setVisibility(loadingFailed ? View.VISIBLE : View.GONE);
            headerViewHolder.serverErrorLayout.setVisibility(loadingFailedServerError ? View.VISIBLE : View.GONE);

        } else if (holder instanceof ItemViewHolder) {
            final ItemViewHolder itemViewHolder = (ItemViewHolder) holder;

            itemViewHolder.comment = comments.get(position);

            if (shouldShow(itemViewHolder.comment)) {
                //only compute if visible, LayoutParams are reset inside here for depth
                holder.itemView.setVisibility(View.VISIBLE);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

                DisplayMetrics displayMetrics = new DisplayMetrics();
                ((Activity) ctx).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
                int width = displayMetrics.widthPixels;
                if (isTablet) {
                    width /= 2;
                }

                //16 is base padding, then add 13 for each comment
                params.setMargins(
                        Math.min(Utils.pxFromDpInt(ctx.getResources(), 16 + 13 * itemViewHolder.comment.depth), Math.round(((float) width) * 0.6f)),
                        Utils.pxFromDpInt(ctx.getResources(), itemViewHolder.comment.depth > 0 && !collapseParent ? 10 : 6),
                        Utils.pxFromDpInt(ctx.getResources(), 16),
                        Utils.pxFromDpInt(ctx.getResources(), 6));
                itemViewHolder.mView.setLayoutParams(params);

                if (itemViewHolder.comment.depth == 0 && !showTopLevelDepthIndicator) {
                    itemViewHolder.commentIndentIndicator.setVisibility(View.GONE);
                } else {
                    itemViewHolder.commentIndentIndicator.setVisibility(View.VISIBLE);
                    int index = (itemViewHolder.comment.depth + (showTopLevelDepthIndicator ? 0 : -1)) % 7;

                    if (monochromeCommentDepthIndicators) {
                        itemViewHolder.commentIndentIndicator.setBackgroundResource(R.color.commentIndentIndicatorColorMonochrome);
                    } else {
                        itemViewHolder.commentIndentIndicator.setBackgroundResource(darkThemeActive ? commentDepthColorsDark[index] : commentDepthColorsLight[index]);
                    }
                }

                if (!itemViewHolder.comment.text.isEmpty()) {
                    itemViewHolder.commentBody.setHtml(itemViewHolder.comment.text);

                    FontUtils.setTypeface(itemViewHolder.commentBody, false, preferredTextSize);
                }

                itemViewHolder.commentBy.setText(itemViewHolder.comment.by);
                itemViewHolder.commentByTime.setText(" • " + itemViewHolder.comment.getTimeFormatted());

                boolean byOp = story.by.equals(itemViewHolder.comment.by);
                boolean byUser = false;
                if (!TextUtils.isEmpty(username)) {
                    byUser = itemViewHolder.comment.by.equals(username);
                }

                if (byUser) {
                    itemViewHolder.commentBy.setTextColor(MaterialColors.getColor(itemViewHolder.commentBy, R.attr.selfCommentColor));
                    itemViewHolder.commentBy.setTypeface(FontUtils.activeBold);
                } else if (byOp) {
                    itemViewHolder.commentBy.setTextColor(MaterialColors.getColor(itemViewHolder.commentBy, R.attr.opCommentColor));
                    itemViewHolder.commentBy.setTypeface(FontUtils.activeBold);
                } else {
                    itemViewHolder.commentBy.setTextColor(MaterialColors.getColor(itemViewHolder.commentBy, R.attr.storyColorDisabled));
                    itemViewHolder.commentBy.setTypeface(FontUtils.activeRegular);
                }

                itemViewHolder.commentByTime.setTypeface(FontUtils.activeRegular);

                itemViewHolder.commentBody.setVisibility( (!itemViewHolder.comment.expanded && collapseParent) ? View.GONE : View.VISIBLE);

                if (itemViewHolder.comment.expanded) {
                    // if expanded, there's no need to show the subcommentcount
                    itemViewHolder.commentHiddenCount.setVisibility(View.INVISIBLE);
                } else {
                    // if not expanded, only show (and set text) if subCommentCount > 0
                    int subCommentCount = getIndexOfLastChild(itemViewHolder.comment.depth, position) - position;

                    if (subCommentCount > 0) {
                        itemViewHolder.commentHiddenCount.setVisibility(View.VISIBLE);
                        itemViewHolder.commentHiddenCount.setText("+" + subCommentCount);
                    } else {
                        itemViewHolder.commentHiddenCount.setVisibility(View.INVISIBLE);
                    }
                }
            } else {
                holder.itemView.setVisibility(View.GONE);
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
            }
        }
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
            return TYPE_ITEM;
        }
    }

    private class ItemViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        public final HtmlTextView commentBody;
        public final TextView commentBy;
        public final TextView commentByTime;
        public final TextView commentHiddenCount;
        public final View commentIndentIndicator;

        public Comment comment;

        public ItemViewHolder(View view) {
            super(view);
            mView = view;
            commentBody =  view.findViewById(R.id.comment_body);
            commentBy = view.findViewById(R.id.comment_by);
            commentByTime = view.findViewById(R.id.comment_by_time);
            commentHiddenCount = view.findViewById(R.id.comment_hidden_count);
            commentIndentIndicator = view.findViewById(R.id.comment_indent_indicator);

            mView.setOnLongClickListener(view13 -> {
                commentLongClickListener.onItemClick(comment, getAbsoluteAdapterPosition(), view13);
                return true;
            });

            commentBody.setOnLongClickListener(view14 -> {
                commentLongClickListener.onItemClick(comment, getAbsoluteAdapterPosition(), view14);
                return true;
            });

            mView.setOnClickListener(view1 ->
                    commentClickListener.onItemClick(comment, getAbsoluteAdapterPosition(), view1));

            commentBody.setOnClickListener(view12 ->
                    commentClickListener.onItemClick(comment, getAbsoluteAdapterPosition(), view12));

            commentBody.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    Utils.launchCustomTab(widget.getContext(), href);
                    return true;
                }

            });
        }
    }

    private class HeaderViewHolder extends RecyclerView.ViewHolder {

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
        public final LinearLayout emptyView;
        public final TextView emptyViewText;
        public final CircularProgressIndicator loadingIndicator;
        public final LinearLayout loadingFailed;
        public final LinearLayout serverErrorLayout;
        public final Button serverErrorSwitchApiButton;

        public final ImageView bookmarkIcon;
        public final ImageView favicon;
        public final RelativeLayout userLayout;
        public final RelativeLayout commentLayout;
        public final RelativeLayout voteLayout;
        public final RelativeLayout bookmarkLayout;
        public final RelativeLayout shareLayout;
        public final RelativeLayout moreLayout;
        public final RelativeLayout moreLayoutParent;
        public final RelativeLayout userLayoutParent;
        public final RelativeLayout commentLayoutParent;
        public final RelativeLayout bookmarkLayoutParent;
        public final RelativeLayout sheetBackButton;
        public final RelativeLayout sheetRefreshButton;
        public final RelativeLayout sheetExpandButton;
        public final RelativeLayout sheetInvertButton;
        public final LinearLayout actionsContainer;
        public final Button retryButton;
        public final LinearLayout pollLayout;
        public final LinearLayout headerView;

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
            emptyView =  view.findViewById(R.id.comments_header_empty);
            emptyViewText = view.findViewById(R.id.comments_header_empty_text);
            headerView = view.findViewById(R.id.comments_header);
            loadingIndicator = view.findViewById(R.id.comments_header_loading);
            loadingFailed = view.findViewById(R.id.comments_header_loading_failed);
            serverErrorLayout = view.findViewById(R.id.comments_header_server_error);
            serverErrorSwitchApiButton = view.findViewById(R.id.comments_header_server_error_switch_api);

            bookmarkIcon = view.findViewById(R.id.comment_button_bookmark);
            favicon = view.findViewById(R.id.comments_header_favicon);
            userLayout = view.findViewById(R.id.comments_layout_user);
            userLayoutParent = view.findViewById(R.id.comments_layout_user_parent);
            commentLayout = view.findViewById(R.id.comments_layout_comment);
            commentLayoutParent = view.findViewById(R.id.comments_layout_comment_parent);
            voteLayout = view.findViewById(R.id.comments_layout_vote);
            bookmarkLayout = view.findViewById(R.id.comments_layout_bookmark);
            bookmarkLayoutParent = view.findViewById(R.id.comments_layout_bookmark_parent);
            shareLayout = view.findViewById(R.id.comments_layout_share);
            moreLayout = view.findViewById(R.id.comments_layout_more);
            moreLayoutParent = view.findViewById(R.id.comments_layout_more_parent);
            retryButton = view.findViewById(R.id.comments_header_retry);
            pollLayout = view.findViewById(R.id.comments_header_poll_layout);
            sheetBackButton = view.findViewById(R.id.comments_sheet_layout_back);
            sheetRefreshButton = view.findViewById(R.id.comments_sheet_layout_refresh);
            sheetExpandButton = view.findViewById(R.id.comments_sheet_layout_expand);
            sheetInvertButton = view.findViewById(R.id.comments_sheet_layout_invert);
            actionsContainer = view.findViewById(R.id.comments_header_actions_container);

            final int SHEET_ITEM_HEIGHT = Utils.pxFromDpInt(view.getResources(), 56);

            retryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    retryListener.onRetry();
                }
            });

            serverErrorSwitchApiButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    //This switches off the algolia API
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(view.getContext());
                    prefs.edit().putBoolean("pref_algolia_api", false).commit();

                    Toast.makeText(view.getContext(), "Deactivated Algolia API, this can be switch back in the settings. Try reloading", Toast.LENGTH_LONG).show();
                }
            });

            userLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_USER, null);
                }
            });

            commentLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_COMMENT, null);
                }
            });

            voteLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_VOTE, view);
                }
            });

            shareLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_SHARE, view);
                }
            });

            moreLayout.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_MORE, view);
                }
            });

            sheetBackButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_BACK, view);
                }
            });

            sheetRefreshButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_REFRESH, view);
                }
            });

            sheetExpandButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_EXPAND, view);
                }
            });

            sheetInvertButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_INVERT, view);
                }
            });

	    TooltipCompat.setTooltipText(sheetBackButton, "Back");
            TooltipCompat.setTooltipText(sheetRefreshButton, "Refresh");
            TooltipCompat.setTooltipText(sheetExpandButton, "Expand");
            TooltipCompat.setTooltipText(sheetInvertButton, "Invert colors");

            if (!showInvert) {
                view.findViewById(R.id.comments_sheet_container_invert).setVisibility(View.GONE);
            }

            view.findViewById(R.id.comments_sheet_container_expand).setVisibility(showExpand ? View.VISIBLE : View.GONE);

            headerView.setOnClickListener(view1 -> headerClickListener.onItemClick(story));

            textView.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    Utils.launchCustomTab(mView.getContext(), href);
                    return true;
                }
            });

            LinearLayout sheetButtonsContainer = view.findViewById(R.id.comment_sheet_buttons_container);
            LinearLayout sheetContainer = view.findViewById(R.id.comment_sheet_container);
            BottomSheetBehavior.from(bottomSheet).addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {

                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    //0 when small, 1 when opened
                    sheetButtonsContainer.setAlpha((1-slideOffset)*(1-slideOffset)*(1-slideOffset));
                    sheetButtonsContainer.getLayoutParams().height = Math.round((1-slideOffset) * (SHEET_ITEM_HEIGHT + navbarHeight));
                    sheetButtonsContainer.requestLayout();
                    sheetContainer.setPadding(0, (int) ((slideOffset) * Utils.getStatusBarHeight(bottomSheet.getResources())), 0, 0);

                    float headerAlpha = Math.min(1, slideOffset*slideOffset*20);
                    actionsContainer.setAlpha(headerAlpha);
                    headerView.setAlpha(headerAlpha);
                }
            });

            if (integratedWebview) {
                userLayoutParent.setVisibility(View.GONE);
                if (BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    sheetButtonsContainer.setAlpha(0f);
                    sheetButtonsContainer.getLayoutParams().height = 0;
                    sheetButtonsContainer.requestLayout();
                    sheetContainer.setPadding(0, Utils.getStatusBarHeight(view.getResources()), 0, 0);
                } else {
                    //make sure we set correct height when starting on the webview
                    sheetButtonsContainer.getLayoutParams().height = SHEET_ITEM_HEIGHT + navbarHeight;
                    sheetButtonsContainer.requestLayout();
                    sheetContainer.setPadding(0, 0, 0, 0);
                }
            } else {
                moreLayoutParent.setVisibility(View.GONE);
                sheetButtonsContainer.setVisibility(View.GONE);
                view.findViewById(R.id.comments_sheet_handle).setVisibility(View.GONE);
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

    public void setRetryListener(RetryListener listener) {
        retryListener = listener;
    }

    public interface RetryListener {
        void onRetry();
    }

    public interface HeaderActionClickListener {
        void onActionClicked(int flag, View view);
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
        /*
        * Try to call shouldShow() on the parent if parent is expanded
        * if parent is not expanded the return false.
        *
        * If parent is -1 (top level) always show
        * */

        if (comment.parent == -1) {
            return true;
        }

        for (Comment c : comments) {
            if (c.id == comment.parent) {
                if (c.expanded) {
                    return shouldShow(c);
                } else {
                    return false;
                }
            }
        }
        return true;
    }
}
