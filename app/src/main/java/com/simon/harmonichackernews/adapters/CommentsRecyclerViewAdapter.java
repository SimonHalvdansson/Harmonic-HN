package com.simon.harmonichackernews.adapters;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.data.PollOption;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.List;

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
    public String faviconProvider;
    public boolean integratedWebview;
    public boolean showTopLevelDepthIndicator;
    public boolean swapLongPressTap;
    public String username;
    public int preferredTextSize;
    private final boolean isTablet;
    public String theme;
    public String font;
    public boolean showUpdate = false;
    public int spacerHeight = 0;
    private int navbarHeight = 0;
    public boolean disableCommentATagClick = false;

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_COMMENT = 1;
    public static final int TYPE_COLLAPSED = 2;

    public final static int FLAG_ACTION_CLICK_USER = 0;
    public final static int FLAG_ACTION_CLICK_COMMENT = 1;
    public final static int FLAG_ACTION_CLICK_VOTE = 2;
    public final static int FLAG_ACTION_CLICK_SHARE = 4;
    public final static int FLAG_ACTION_CLICK_MORE = 5;
    public final static int FLAG_ACTION_CLICK_REFRESH = -2;
    public final static int FLAG_ACTION_CLICK_EXPAND = -3;
    public final static int FLAG_ACTION_CLICK_BROWSER = -4;
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

    private static final int[] commentDepthColorsMaterial = new int[]{
            R.color.material_you_primary60,
            R.color.material_you_secondary60,
            R.color.material_you_tertiary50,
            R.color.material_you_neutral_variant50,
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
                                       String prefTheme,
                                       boolean tablet,
                                       String favProvider,
                                       boolean shouldSwapLongPressTap) {
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
        theme = prefTheme;
        isTablet = tablet;
        faviconProvider = favProvider;
        swapLongPressTap = shouldSwapLongPressTap;
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_COMMENT) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.comments_item, parent, false);
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

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Context ctx = holder.itemView.getContext();
        if (holder instanceof HeaderViewHolder) {
            final HeaderViewHolder headerViewHolder = (HeaderViewHolder) holder;

            if (story.isLink && story.url != null) {
                try {
                    headerViewHolder.urlView.setText("(" + Utils.getDomainName(story.url) + ")");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            headerViewHolder.headerView.setClickable(story.isLink);
            headerViewHolder.linkImage.setVisibility(story.isLink && !story.isComment ? View.VISIBLE : GONE);
            headerViewHolder.textView.setVisibility(TextUtils.isEmpty(story.text) ? GONE : View.VISIBLE);
            headerViewHolder.infoContainer.setVisibility(story.hasExtraInfo() ? View.VISIBLE : GONE);

            if (!TextUtils.isEmpty(story.text)) {
                headerViewHolder.textView.setHtml(story.text);
            }

            if (story.arxivInfo != null) {
                headerViewHolder.arxivContainer.setVisibility(View.VISIBLE);
                headerViewHolder.infoHeader.setVisibility(VISIBLE);

                headerViewHolder.infoHeader.setText("ABSTRACT:");

                headerViewHolder.arxivAbstract.setHtml(story.arxivInfo.arxivAbstract);
                headerViewHolder.arxivBy.setText(story.arxivInfo.concatNames());
                headerViewHolder.arxivDate.setText(story.arxivInfo.formatDate());
                headerViewHolder.arxivSubjects.setText(story.arxivInfo.formatSubjects());

                int byIconResource = R.drawable.ic_action_group;
                if (story.arxivInfo.authors.length == 1) {
                    byIconResource = R.drawable.ic_action_person;
                } else if (story.arxivInfo.authors.length == 2) {
                    byIconResource = R.drawable.ic_action_pair;
                }
                headerViewHolder.arxivByIcon.setImageResource(byIconResource);

                FontUtils.setTypeface(headerViewHolder.arxivAbstract, false, 14);
            }

            if (story.repoInfo != null) {
                headerViewHolder.githubContainer.setVisibility(View.VISIBLE);
                headerViewHolder.infoHeader.setVisibility(VISIBLE);

                headerViewHolder.infoHeader.setText(story.repoInfo.owner + " / " + story.repoInfo.name);

                headerViewHolder.githubAbout.setText(story.repoInfo.about);
                headerViewHolder.githubWebsite.setHtml("<a href=\"" + story.repoInfo.website + "\">" + story.repoInfo.getShortenedUrl() + "</a>");
                headerViewHolder.githubLicense.setText(story.repoInfo.license);
                headerViewHolder.githubLanguage.setText(story.repoInfo.language);
                headerViewHolder.githubStars.setText(story.repoInfo.formatStars());
                headerViewHolder.githubWatching.setText(story.repoInfo.formatWatching());
                headerViewHolder.githubForks.setText(story.repoInfo.formatForks());

                headerViewHolder.githubWebsiteContainer.setVisibility(TextUtils.isEmpty(story.repoInfo.website) ? GONE : View.VISIBLE);
                headerViewHolder.githubLicenseContainer.setVisibility(TextUtils.isEmpty(story.repoInfo.license) ? GONE : View.VISIBLE);
                headerViewHolder.githubLanguageContainer.setVisibility(TextUtils.isEmpty(story.repoInfo.language) ? GONE : View.VISIBLE);
                headerViewHolder.githubAbout.setVisibility(TextUtils.isEmpty(story.repoInfo.about) ? GONE : VISIBLE);
            }

            if (story.wikiInfo != null) {
                headerViewHolder.wikiContainer.setVisibility(View.VISIBLE);
                headerViewHolder.infoHeader.setVisibility(VISIBLE);

                headerViewHolder.infoHeader.setText("WIKIPEDIA SUMMARY:");
                headerViewHolder.wikiSummary.setHtml(story.wikiInfo.summary);
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
                headerViewHolder.metaBy.setText(story.by);
            }

            headerViewHolder.metaContainer.setVisibility(story.loaded ? View.VISIBLE : GONE);
            headerViewHolder.urlView.setVisibility(story.isLink ? View.VISIBLE : GONE);
            headerViewHolder.metaVotes.setVisibility(story.isComment ? GONE : View.VISIBLE);
            headerViewHolder.metaVotesIcon.setVisibility(story.isComment ? GONE : View.VISIBLE);

            FontUtils.setMultipleTypefaces(false, 14, 13, 13, 13, 13, 13,
                    headerViewHolder.urlView,
                    headerViewHolder.metaVotes,
                    headerViewHolder.metaComments,
                    headerViewHolder.metaTime,
                    headerViewHolder.metaBy);

            FontUtils.setTypeface(headerViewHolder.titleView, true, 27, 26, 23, 26, 24, 26);
            FontUtils.setTypeface(headerViewHolder.textView, false, preferredTextSize);

            if (loadingFailed) {
                headerViewHolder.loadingIndicator.setVisibility(GONE);
                headerViewHolder.emptyView.setVisibility(GONE);
            } else {
                if (commentsLoaded) {
                    headerViewHolder.loadingIndicator.setVisibility(GONE);
                    headerViewHolder.emptyView.setVisibility(story.descendants > 0 ? GONE : View.VISIBLE);
                } else {
                    headerViewHolder.loadingIndicator.setVisibility(View.VISIBLE);
                    headerViewHolder.emptyView.setVisibility(GONE);
                }
            }

            headerViewHolder.spacer.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, spacerHeight));

            headerViewHolder.refreshButton.setVisibility(showUpdate ? View.VISIBLE : GONE);

            int actionContainerPadding = Math.round(headerViewHolder.actionsContainer.getResources().getDimension(R.dimen.comments_header_action_padding));
            headerViewHolder.actionsContainer.setPadding(actionContainerPadding, 0, actionContainerPadding, 0);

            headerViewHolder.favicon.setVisibility(showThumbnail ? View.VISIBLE : GONE);
            headerViewHolder.linkInfoContainer.setVisibility(!story.isComment && story.isLink ? View.VISIBLE : GONE);

            if (showThumbnail && !TextUtils.isEmpty(story.url)) {
                FaviconLoader.loadFavicon(story.url, headerViewHolder.favicon, ctx, faviconProvider);
            }

            headerViewHolder.bookmarkButton.setImageResource(Utils.isBookmarked(ctx, story.id) ? R.drawable.ic_action_bookmark_filled : R.drawable.ic_action_bookmark_border);

            headerViewHolder.bookmarkButton.setOnClickListener(new View.OnClickListener() {
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
            headerViewHolder.bookmarkButtonParent.setVisibility(story.isComment ? GONE : View.VISIBLE);
            headerViewHolder.commentButtonParent.setVisibility(Utils.timeInSecondsMoreThanTwoWeeksAgo(story.time) ? GONE : View.VISIBLE);

            headerViewHolder.loadingFailed.setVisibility(loadingFailed ? View.VISIBLE : GONE);
            headerViewHolder.serverErrorLayout.setVisibility(loadingFailedServerError ? View.VISIBLE : GONE);

        } else if (holder instanceof ItemViewHolder) {
            final ItemViewHolder itemViewHolder = (ItemViewHolder) holder;
            Comment comment = comments.get(position);
            itemViewHolder.comment = comments.get(position);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((Activity) ctx).getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            int width = displayMetrics.widthPixels;
            if (isTablet) {
                width /= 2;
            }

            // 16 is base padding, then add 12 for each comment
            params.setMargins(
                    Math.min(Utils.pxFromDpInt(ctx.getResources(), 16 + 12 * comment.depth), Math.round(((float) width) * 0.6f)),
                    Utils.pxFromDpInt(ctx.getResources(), comment.depth > 0 && !collapseParent ? 10 : 6),
                    Utils.pxFromDpInt(ctx.getResources(), 16),
                    Utils.pxFromDpInt(ctx.getResources(), 6));
            itemViewHolder.itemView.setLayoutParams(params);

            if (comment.depth == 0 && !showTopLevelDepthIndicator) {
                itemViewHolder.commentIndentIndicator.setVisibility(GONE);
            } else {
                itemViewHolder.commentIndentIndicator.setVisibility(View.VISIBLE);
                int index = (comment.depth + (showTopLevelDepthIndicator ? 0 : -1)) % 7;

                if (monochromeCommentDepthIndicators) {
                    itemViewHolder.commentIndentIndicator.setBackgroundResource(R.color.commentIndentIndicatorColorMonochrome);
                } else {
                    if (theme.startsWith("material")) {
                        itemViewHolder.commentIndentIndicator.setBackgroundResource(commentDepthColorsMaterial[index]);
                    } else {
                        itemViewHolder.commentIndentIndicator.setBackgroundResource(ThemeUtils.isDarkMode(ctx, theme) ? commentDepthColorsDark[index] : commentDepthColorsLight[index]);
                    }
                }
            }

            if (!comment.text.isEmpty()) {
                itemViewHolder.commentBody.setHtml(comment.text);
                if (collapseParent) {
                    itemViewHolder.commentHiddenText.setText(" • " + Html.fromHtml(comment.text.substring(0, Math.min(120, comment.text.length()))));
                }

                FontUtils.setTypeface(itemViewHolder.commentBody, false, preferredTextSize);
            }

            itemViewHolder.commentByTime.setText(comment.getTimeFormatted());

            boolean byOp = story.by.equals(comment.by);
            boolean byUser = false;
            if (!TextUtils.isEmpty(username)) {
                byUser = comment.by.equals(username);
            }

            itemViewHolder.commentBy.setText(byOp ? comment.by + " (OP)" : comment.by);

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

            itemViewHolder.commentBody.setVisibility((!comment.expanded && collapseParent) ? GONE : View.VISIBLE);
            itemViewHolder.commentHiddenText.setVisibility((!comment.expanded && collapseParent) ? View.VISIBLE : GONE);

            if (comment.expanded) {
                // if expanded, there's no need to show the subcommentcount
                itemViewHolder.commentHiddenCount.setVisibility(GONE);
            } else {
                // if not expanded, only show (and set text) if subCommentCount > 0
                // TODO should this be precomputed?
                int subCommentCount = getIndexOfLastChild(comment.depth, position) - position;

                if (subCommentCount > 0) {
                    itemViewHolder.commentHiddenCount.setVisibility(View.VISIBLE);
                    itemViewHolder.commentHiddenCount.setText("+" + subCommentCount);
                } else {
                    itemViewHolder.commentHiddenCount.setVisibility(GONE);
                }
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
            return shouldShow(comments.get(position)) ? TYPE_COMMENT : TYPE_COLLAPSED;
        }
    }

    public class ItemViewHolder extends RecyclerView.ViewHolder {
        public final HtmlTextView commentBody;
        public final TextView commentBy;
        public final TextView commentByTime;
        public final TextView commentHiddenCount;
        public final TextView commentHiddenText;
        public final View commentIndentIndicator;
        public Comment comment;

        public ItemViewHolder(View view) {
            super(view);
            commentBody = view.findViewById(R.id.comment_body);
            commentBy = view.findViewById(R.id.comment_by);
            commentByTime = view.findViewById(R.id.comment_by_time);
            commentHiddenCount = view.findViewById(R.id.comment_hidden_count);
            commentHiddenText = view.findViewById(R.id.comment_hidden_short);
            commentIndentIndicator = view.findViewById(R.id.comment_indent_indicator);

            itemView.setOnLongClickListener(v -> {
                longPressed(comment, getAbsoluteAdapterPosition(), v);
                return true;
            });

            commentBody.setOnLongClickListener(v -> {
                longPressed(comment, getAbsoluteAdapterPosition(), v);
                return true;
            });

            itemView.setOnClickListener(v ->
                    tapped(comment, getAbsoluteAdapterPosition(), v));

            commentBody.setOnClickListener(v ->
                    tapped(comment, getAbsoluteAdapterPosition(), v));

            commentBody.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    if (disableCommentATagClick) return true;

                    Utils.openLinkMaybeHN(widget.getContext(), href);
                    return true;
                }
            });
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
        public final LinearLayout infoContainer;
        public final HtmlTextView arxivAbstract;
        public final LinearLayout githubContainer;
        public final LinearLayout arxivContainer;
        public final LinearLayout wikiContainer;
        public final TextView infoHeader;
        public final LinearLayout emptyView;
        public final TextView emptyViewText;
        public final CircularProgressIndicator loadingIndicator;
        public final LinearLayout loadingFailed;
        public final LinearLayout serverErrorLayout;
        public final Button serverErrorSwitchApiButton;
        public final Button refreshButton;
        public final ImageButton userButton;
        public final ImageButton commentButton;
        public final ImageButton voteButton;
        public final ImageButton bookmarkButton;
        public final ImageButton shareButton;
        public final ImageButton moreButton;
        public final RelativeLayout userButtonParent;
        public final RelativeLayout moreButtonParent;
        public final RelativeLayout commentButtonParent;
        public final RelativeLayout bookmarkButtonParent;
        public final Space spacer;
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

        public final TextView arxivBy;
        public final TextView arxivDate;
        public final TextView arxivSubjects;
        public final ImageView arxivByIcon;
        public final Button arxivDownloadButton;

        public final HtmlTextView wikiSummary;

        public final ImageView favicon;
        public final RelativeLayout sheetRefreshButton;
        public final RelativeLayout sheetExpandButton;
        public final RelativeLayout sheetBrowserButton;
        public final RelativeLayout sheetInvertButton;
        public final LinearLayout actionsContainer;
        public final LinearLayout linkInfoContainer;
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
            arxivAbstract = view.findViewById(R.id.comments_header_arxiv_abstract);
            infoContainer = view.findViewById(R.id.comments_header_info_container);
            infoHeader = view.findViewById(R.id.comments_header_info_header);
            emptyView = view.findViewById(R.id.comments_header_empty);
            emptyViewText = view.findViewById(R.id.comments_header_empty_text);
            headerView = view.findViewById(R.id.comments_header);
            loadingIndicator = view.findViewById(R.id.comments_header_loading);
            loadingFailed = view.findViewById(R.id.comments_header_loading_failed);
            serverErrorLayout = view.findViewById(R.id.comments_header_server_error);
            serverErrorSwitchApiButton = view.findViewById(R.id.comments_header_server_error_switch_api);
            refreshButton = view.findViewById(R.id.comments_header_refresh);
            favicon = view.findViewById(R.id.comments_header_favicon);
            linkInfoContainer = view.findViewById(R.id.comments_header_link_info_container);
            userButton = view.findViewById(R.id.comments_header_button_user);
            commentButton = view.findViewById(R.id.comments_header_button_comment);
            voteButton = view.findViewById(R.id.comments_header_button_vote);
            bookmarkButton = view.findViewById(R.id.comments_header_button_bookmark);
            shareButton = view.findViewById(R.id.comments_header_button_share);
            moreButton = view.findViewById(R.id.comments_header_button_more);
            userButtonParent = view.findViewById(R.id.comments_header_button_user_parent);
            moreButtonParent = view.findViewById(R.id.comments_header_button_more_parent);
            commentButtonParent = view.findViewById(R.id.comments_header_button_comment_parent);
            bookmarkButtonParent = view.findViewById(R.id.comments_header_button_bookmark_parent);
            retryButton = view.findViewById(R.id.comments_header_retry);
            pollLayout = view.findViewById(R.id.comments_header_poll_layout);
            sheetRefreshButton = view.findViewById(R.id.comments_sheet_layout_refresh);
            sheetExpandButton = view.findViewById(R.id.comments_sheet_layout_expand);
            sheetBrowserButton = view.findViewById(R.id.comments_sheet_layout_browser);
            sheetInvertButton = view.findViewById(R.id.comments_sheet_layout_invert);
            actionsContainer = view.findViewById(R.id.comments_header_actions_container);
            spacer = view.findViewById(R.id.comments_header_spacer);
            githubContainer = view.findViewById(R.id.comments_header_github_container);
            arxivContainer = view.findViewById(R.id.comments_header_arxiv_container);
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
            arxivBy = view.findViewById(R.id.comments_header_arxiv_by);
            arxivDate = view.findViewById(R.id.comments_header_arxiv_date);
            arxivSubjects = view.findViewById(R.id.comments_header_arxiv_subjects);
            arxivByIcon = view.findViewById(R.id.comments_header_arxiv_by_icon);
            arxivDownloadButton = view.findViewById(R.id.comments_header_arxiv_download);

            final int SHEET_ITEM_HEIGHT = Utils.pxFromDpInt(view.getResources(), 56);

            retryButton.setOnClickListener((v) -> retryListener.onRetry());

            refreshButton.setOnClickListener((v) -> {
                showUpdate = false;
                retryListener.onRetry();
            });

            serverErrorSwitchApiButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // This switches off the algolia API
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(view.getContext());
                    prefs.edit().putBoolean("pref_algolia_api", false).apply();

                    Toast.makeText(view.getContext(), "Deactivated Algolia API, this can be switched back in the settings. Try reloading", Toast.LENGTH_LONG).show();
                }
            });

            arxivDownloadButton.setOnClickListener((v) -> {
                Utils.downloadPDF(v.getContext(), story.arxivInfo.getPDFURL());
            });

            userButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_USER, null));
            commentButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_COMMENT, null));
            voteButton.setOnClickListener((v) -> headerActionClickListener.onActionClicked(FLAG_ACTION_CLICK_VOTE, view));
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
            TooltipCompat.setTooltipText(bookmarkButton, "Bookmark");
            TooltipCompat.setTooltipText(shareButton, "Share");
            TooltipCompat.setTooltipText(moreButton, "More");

            if (!showInvert) {
                view.findViewById(R.id.comments_sheet_container_invert).setVisibility(GONE);
            }

            headerView.setOnClickListener(view1 -> headerClickListener.onItemClick(story));

            textView.setOnClickATagListener(new OnClickATagListener() {
                @Override
                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                    Utils.launchCustomTab(mView.getContext(), href);
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

            LinearLayout sheetButtonsContainer = view.findViewById(R.id.comment_sheet_buttons_container);
            BottomSheetBehavior.from(bottomSheet).addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
                @Override
                public void onStateChanged(@NonNull View bottomSheet, int newState) {
                }

                @Override
                public void onSlide(@NonNull View bottomSheet, float slideOffset) {
                    // 0 when small, 1 when opened
                    sheetButtonsContainer.setAlpha((1 - slideOffset) * (1 - slideOffset) * (1 - slideOffset));
                    sheetButtonsContainer.getLayoutParams().height = Math.round((1 - slideOffset) * (SHEET_ITEM_HEIGHT + navbarHeight));
                    sheetButtonsContainer.requestLayout();

                    float headerAlpha = Math.min(1, slideOffset * slideOffset * 20);
                    actionsContainer.setAlpha(headerAlpha);
                    headerView.setAlpha(headerAlpha);
                }
            });

            if (integratedWebview) {
                if (BottomSheetBehavior.from(bottomSheet).getState() == BottomSheetBehavior.STATE_EXPANDED) {
                    sheetButtonsContainer.setAlpha(0f);
                    sheetButtonsContainer.getLayoutParams().height = 0;
                    sheetButtonsContainer.requestLayout();
                } else {
                    // Make sure we set correct height when starting on the WebView
                    sheetButtonsContainer.getLayoutParams().height = SHEET_ITEM_HEIGHT + navbarHeight;
                    sheetButtonsContainer.requestLayout();
                }
            } else {
                sheetButtonsContainer.setVisibility(GONE);
                view.findViewById(R.id.comments_sheet_handle).setVisibility(GONE);
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

    public void setNavbarHeight(int navbarHeight) {
        if (this.navbarHeight != navbarHeight) {
            this.navbarHeight = navbarHeight;
            notifyItemChanged(0);
        }
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
