package com.simon.harmonichackernews.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.network.FaviconLoader;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.SettingsUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class StoryRecyclerViewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<Story> stories;
    private ClickListener typeClickListener;
    private ClickListener linkClickListener;
    private ClickListener commentClickListener;
    private ClickListener commentRepliesClickListener;
    private ClickListener commentStoryClickListener;
    private SearchListener storiesSearchListener;
    private RefreshListener refreshListener;
    private View.OnClickListener moreClickListener;
    private LongClickCoordinateListener longClickListener;
    private final boolean atSubmissions;
    private final String submitter;

    private static final int TYPE_HEADER_MAIN = 0;
    private static final int TYPE_HEADER_SUBMISSIONS = 1;
    private static final int TYPE_STORY = 2;
    private static final int TYPE_COMMENT = 3;

    public boolean loadingFailed = false;
    public boolean loadingFailedServerError = false;

    public boolean showPoints;
    public boolean showCommentsCount;
    public boolean compactView;
    public boolean thumbnails;
    public boolean showIndex;
    public boolean compactHeader;
    public boolean leftAlign;
    public String faviconProvider;
    public int hotness;
    public int type;
    public boolean searching = false;

    public String lastSearch = "";

    public StoryRecyclerViewAdapter(List<Story> items,
                                    boolean shouldShowPoints,
                                    boolean shouldShowCommentsCount,
                                    boolean shouldUseCompactView,
                                    boolean shouldShowThumbnails,
                                    boolean shouldShowIndex,
                                    boolean shouldUseCompactHeader,
                                    boolean shouldLeftAlign,
                                    int preferredHotness,
                                    String faviconProv,
                                    String submissionsUserName,
                                    int wantedType) {
        stories = items;
        showPoints = shouldShowPoints;
        showCommentsCount = shouldShowCommentsCount;
        compactView = shouldUseCompactView;
        thumbnails = shouldShowThumbnails;
        showIndex = shouldShowIndex;
        compactHeader = shouldUseCompactHeader;
        leftAlign = shouldLeftAlign;
        hotness = preferredHotness;
        faviconProvider = faviconProv;
        type = wantedType;

        atSubmissions = !TextUtils.isEmpty(submissionsUserName);
        submitter = submissionsUserName;
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_STORY) {
            return new StoryViewHolder(LayoutInflater.from(parent.getContext()).inflate(leftAlign ? R.layout.story_list_item_left : R.layout.story_list_item, parent, false));
        } else if (viewType == TYPE_HEADER_MAIN) {
            return new MainHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.stories_header, parent, false));
        } else if (viewType == TYPE_HEADER_SUBMISSIONS) {
            return new SubmissionsHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.submissions_header, parent, false));
        } else {
            return new CommentViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.submissions_comment, parent, false));
        }
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NotNull final RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof StoryViewHolder) {
            final StoryViewHolder storyViewHolder = (StoryViewHolder) holder;
            final Context ctx = storyViewHolder.itemView.getContext();

            storyViewHolder.story = stories.get(position);
            if (showIndex) {
                storyViewHolder.indexTextView.setText(position + ".");

                if (storyViewHolder.story.clicked) {
                    storyViewHolder.indexTextView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.storyColorDisabled));
                } else {
                    storyViewHolder.indexTextView.setTextColor(Utils.getColorViaAttr(ctx, R.attr.storyColorNormal));
                }

                if (position < 100) {
                    storyViewHolder.indexTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    storyViewHolder.indexTextView.setPadding(0, Utils.pxFromDpInt(ctx.getResources(), 2.2f), 0, 0);
                } else {
                    storyViewHolder.indexTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
                    storyViewHolder.indexTextView.setPadding(0, Utils.pxFromDpInt(ctx.getResources(), 5.3f), 0, 0);
                }
            }

            storyViewHolder.indexTextView.setVisibility(showIndex ? View.VISIBLE : View.GONE);

            if (storyViewHolder.story.loaded || storyViewHolder.story.loadingFailed) {
                if (!TextUtils.isEmpty(storyViewHolder.story.pdfTitle)) {
                    SpannableStringBuilder sb = new SpannableStringBuilder(storyViewHolder.story.pdfTitle + " ");

                    ImageSpan imageSpan = new ImageSpan(ctx, storyViewHolder.story.clicked ? R.drawable.ic_action_pdf_clicked : R.drawable.ic_action_pdf);
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
                } else {
                    storyViewHolder.metaView.setText(host + " • " + storyViewHolder.story.getTimeFormatted());
                }

                if (thumbnails) {
                    FaviconLoader.loadFavicon(storyViewHolder.story.url, storyViewHolder.metaFavicon, ctx, faviconProvider);
                }

                storyViewHolder.commentsIcon.setImageResource(hotness > 0 && storyViewHolder.story.score + storyViewHolder.story.descendants > hotness ? R.drawable.ic_action_whatshot : R.drawable.ic_action_comment);

                FontUtils.setTypeface(storyViewHolder.titleView, true, 17.5f, 18, 16, 17, 17, 18);
                FontUtils.setTypeface(storyViewHolder.metaView, false, 13, 13, 12, 12, 13, 13);
                FontUtils.setTypeface(storyViewHolder.commentsView, true, 14, 13, 13, 14, 14, 14);

                if (storyViewHolder.story.clicked && type != SettingsUtils.getBookmarksIndex(ctx.getResources())) {
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
                storyViewHolder.linkLayoutView.setClickable(false);
                storyViewHolder.commentLayoutView.setClickable(false);
                storyViewHolder.commentsIcon.setAlpha(storyViewHolder.story.clicked ? 0.6f : 1.0f);
            }
        } else if (holder instanceof MainHeaderViewHolder) {
            final MainHeaderViewHolder headerViewHolder = (MainHeaderViewHolder) holder;
            final Context ctx = headerViewHolder.itemView.getContext();

            if (compactHeader) {
                headerViewHolder.container.setPadding(0, Utils.pxFromDpInt(ctx.getResources(), 20), 0, Utils.pxFromDpInt(ctx.getResources(), 10));
            } else {
                headerViewHolder.container.setPadding(0, Utils.pxFromDpInt(ctx.getResources(), 40), 0, Utils.pxFromDpInt(ctx.getResources(), 26));
            }

            headerViewHolder.moreButton.setVisibility(searching ? View.GONE : View.VISIBLE);
            headerViewHolder.spinnerContainer.setVisibility(searching ? View.GONE : View.VISIBLE);

            headerViewHolder.searchButton.setImageResource(searching ? R.drawable.ic_action_cancel : R.drawable.ic_action_search);

            headerViewHolder.searchEditText.setVisibility(searching ? View.VISIBLE : View.GONE);
            headerViewHolder.searchEditText.setText(stories.get(0).title);

            if (searching) {
                headerViewHolder.loadingIndicator.setVisibility(View.GONE);
                headerViewHolder.searchEditText.requestFocus();
                headerViewHolder.searchEditText.setText(lastSearch);
                headerViewHolder.searchEditText.setSelection(lastSearch.length());

                headerViewHolder.searchEmptyContainer.setVisibility(stories.size() == 1 ? View.VISIBLE : View.GONE);
                headerViewHolder.noBookmarksLayout.setVisibility(View.GONE);
            } else {
                headerViewHolder.noBookmarksLayout.setVisibility((stories.size() == 1 && type == SettingsUtils.getBookmarksIndex(ctx.getResources())) ? View.VISIBLE : View.GONE);
                headerViewHolder.searchEmptyContainer.setVisibility(View.GONE);

                headerViewHolder.loadingIndicator.setVisibility(stories.size() == 1 && !loadingFailed && !loadingFailedServerError && (type != SettingsUtils.getBookmarksIndex(ctx.getResources())) ? View.VISIBLE : View.GONE);
            }

            headerViewHolder.typeSpinner.setSelection(type);

            TooltipCompat.setTooltipText(headerViewHolder.searchButton, searching ? "Close" : "Search");
            TooltipCompat.setTooltipText(headerViewHolder.moreButton, "More");

            headerViewHolder.loadingFailedLayout.setVisibility(loadingFailed ? View.VISIBLE : View.GONE);
            headerViewHolder.loadingFailedAlgoliaLayout.setVisibility(loadingFailedServerError ? View.VISIBLE : View.GONE);
        } else if (holder instanceof SubmissionsHeaderViewHolder) {
            final SubmissionsHeaderViewHolder submissionsHeaderViewHolder = (SubmissionsHeaderViewHolder) holder;

            submissionsHeaderViewHolder.headerText.setText(submitter + "'s submissions");

        } else if (holder instanceof CommentViewHolder) {
            final CommentViewHolder commentViewHolder = (CommentViewHolder) holder;

            Story story = stories.get(position);

            commentViewHolder.headerText.setText("On \"" + story.commentMasterTitle + "\" " + Utils.getTimeAgo(story.time));
            commentViewHolder.bodyText.setHtml(story.text);

            commentViewHolder.bodyText.post(new Runnable() {
                @Override
                public void run() {
                    commentViewHolder.scrim.setVisibility(ViewUtils.isTextTruncated(commentViewHolder.bodyText) ? View.VISIBLE : View.GONE);
                }
            });

        }
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return atSubmissions ? TYPE_HEADER_SUBMISSIONS : TYPE_HEADER_MAIN;
        } else {
            if (atSubmissions) {
                return stories.get(position).isComment ? TYPE_COMMENT : TYPE_STORY;
            } else {
                return TYPE_STORY;
            }
        }
    }

    @Override
    public int getItemCount() {
        return stories.size();
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
            indexTextView = view.findViewById(R.id.story_index);

            linkLayoutView.setOnClickListener(v -> linkClickListener.onItemClick(getAbsoluteAdapterPosition()));
            commentLayoutView.setOnClickListener(v -> commentClickListener.onItemClick(getAbsoluteAdapterPosition()));

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
            }
        }
    }

    public class MainHeaderViewHolder extends RecyclerView.ViewHolder {
        public final Spinner typeSpinner;
        public final LinearLayout container;
        public final LinearLayout loadingFailedLayout;
        public final TextView loadingFailedAlgoliaLayout;
        public final LinearLayout noBookmarksLayout;
        public final LinearLayout spinnerContainer;
        public final LinearLayout searchEmptyContainer;
        public final RelativeLayout loadingIndicator;
        public final EditText searchEditText;
        public final ImageButton moreButton;
        public final ImageButton searchButton;
        public final Button retryButton;

        public ArrayAdapter<CharSequence> typeAdapter;

        public MainHeaderViewHolder(View view) {
            super(view);

            final Context ctx = view.getContext();

            loadingFailedLayout = view.findViewById(R.id.stories_header_loading_failed);
            loadingFailedAlgoliaLayout = view.findViewById(R.id.stories_header_loading_failed_algolia);
            container = view.findViewById(R.id.stories_header_container);
            typeSpinner = view.findViewById(R.id.stories_header_spinner);
            noBookmarksLayout = view.findViewById(R.id.stories_header_no_bookmarks);
            searchEditText = view.findViewById(R.id.stories_header_search_edittext);
            moreButton = view.findViewById(R.id.stories_header_more);
            spinnerContainer = view.findViewById(R.id.stories_header_spinner_container);
            searchButton = view.findViewById(R.id.stories_header_search_button);
            searchEmptyContainer = view.findViewById(R.id.stories_header_search_empty_container);
            retryButton = view.findViewById(R.id.stories_header_retry_button);
            loadingIndicator = view.findViewById(R.id.stories_header_loading_indicator);

            retryButton.setOnClickListener((v) -> refreshListener.onRefresh());

            moreButton.setOnClickListener(moreClickListener);

            searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                    if (actionId != EditorInfo.IME_ACTION_SEARCH) {
                        return false;
                    }

                    doSearch();
                    if (textView != null) {
                        InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                    }
                    return true;
                }
            });

            searchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    searching = !searching;
                    storiesSearchListener.onSearchStatusChanged();

                    InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (searching) {
                        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0);
                    } else {
                        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        lastSearch = "";
                    }
                }
            });

            String[] sortingOptions = ctx.getResources().getStringArray(R.array.sorting_options);
            ArrayList<CharSequence> typeAdapterList = new ArrayList<>(Arrays.asList(sortingOptions));
            typeAdapter = new ArrayAdapter<>(ctx, R.layout.spinner_top_layout, R.id.selection_dropdown_item_textview, typeAdapterList);
            typeAdapter.setDropDownViewResource(R.layout.spinner_item_layout);

            typeSpinner.setAdapter(typeAdapter);
            typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    if (i != type) {
                        typeClickListener.onItemClick(i);
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });
        }

        private void doSearch() {
            storiesSearchListener.onQueryTextSubmit(searchEditText.getText().toString());
        }
    }


    public static class SubmissionsHeaderViewHolder extends RecyclerView.ViewHolder {

        public final TextView headerText;

        public SubmissionsHeaderViewHolder(View view) {
            super(view);
            headerText = view.findViewById(R.id.submissions_header_text);
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
                    new int[]{Color.TRANSPARENT, ContextCompat.getColor(ctx, ThemeUtils.getBackgroundColorResource(ctx))});

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

    public void setOnTypeClickListener(ClickListener clickListener) {
        typeClickListener = clickListener;
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

    public void setOnRefreshListener(RefreshListener listener) {
        refreshListener = listener;
    }

    public void setOnMoreClickListener(View.OnClickListener listener) {
        moreClickListener = listener;
    }

    public interface ClickListener {
        void onItemClick(int position);
    }

    public void setSearchListener(SearchListener searchListener) {
        storiesSearchListener = searchListener;
    }

    public interface SearchListener {
        void onQueryTextSubmit(String query);

        void onSearchStatusChanged();
    }

    public interface RefreshListener {
        void onRefresh();
    }

    public interface LongClickCoordinateListener {
        boolean onLongClick(View v, int position, int x, int y);
    }

}