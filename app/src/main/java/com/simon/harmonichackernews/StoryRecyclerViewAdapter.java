package com.simon.harmonichackernews;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.squareup.picasso.Picasso;

import org.jetbrains.annotations.NotNull;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
    private final boolean atSubmissions;
    private final String submitter;

    private static final int TYPE_HEADER_MAIN = 0;
    private static final int TYPE_HEADER_SUBMISSIONS = 1;
    private static final int TYPE_STORY = 2;
    private static final int TYPE_COMMENT = 3;

    public boolean loadingFailed = false;
    public boolean showPoints;
    public boolean compactView;
    public boolean thumbnails;
    public boolean showIndex;
    public boolean hideJobs;
    public boolean compactHeader;
    public boolean leftAlign;
    public int hotness;
    public int type = 0;
    public boolean searching = false;

    public StoryRecyclerViewAdapter(List<Story> items,
                                    boolean shouldShowPoints,
                                    boolean shouldUseCompactView,
                                    boolean shouldShowThumbnails,
                                    boolean shouldShowIndex,
                                    boolean shouldHideJobs,
                                    boolean shouldUseCompactHeader,
                                    boolean shouldLeftAlign,
                                    int preferredHotness,
                                    String submissionsUserName) {
        stories = items;
        showPoints = shouldShowPoints;
        compactView = shouldUseCompactView;
        thumbnails = shouldShowThumbnails;
        showIndex = shouldShowIndex;
        hideJobs = shouldHideJobs;
        compactHeader = shouldUseCompactHeader;
        leftAlign = shouldLeftAlign;
        hotness = preferredHotness;

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

                storyViewHolder.commentsView.setText(Integer.toString(storyViewHolder.story.descendants));
                String url = "";

                try {
                    if (storyViewHolder.story.url != null) {
                        url = Utils.getDomainName(storyViewHolder.story.url);
                    }
                } catch (Exception e) {
                    url = "Unknown";
                }

                String ptsString = " points";
                if (storyViewHolder.story.score == 1) {
                    ptsString = " point";
                }
                if (showPoints && !storyViewHolder.story.isComment) {
                    storyViewHolder.metaView.setText(storyViewHolder.story.score + ptsString + " • " + url + " • " + storyViewHolder.story.getTimeFormatted());
                } else {
                    storyViewHolder.metaView.setText(url + "  •  " + storyViewHolder.story.getTimeFormatted());
                }

                if (thumbnails) {
                    //Picasso sometimes loses its context, that should just be ignored
                    try {
                        Picasso.get()
                                .load("https://api.faviconkit.com/" + url + "/80")
                                .resize(80, 80)
                                .onlyScaleDown()
                                .placeholder(Objects.requireNonNull(ContextCompat.getDrawable(ctx, R.drawable.ic_action_web)))
                                .into(storyViewHolder.metaFavicon);
                    } catch (Exception ignored){};

                }

                storyViewHolder.commentsIcon.setImageResource(hotness > 0 && storyViewHolder.story.score + storyViewHolder.story.descendants > hotness ? R.drawable.ic_action_whatshot : R.drawable.ic_action_comment);

                FontUtils.setTypeface(storyViewHolder.titleView, true, 17.5f, 18, 16, 17, 17, 18);
                FontUtils.setTypeface(storyViewHolder.metaView, false, 13, 13, 12, 12, 13, 13);
                FontUtils.setTypeface(storyViewHolder.commentsView, true, 14, 13, 13, 14, 14, 14);

                if (storyViewHolder.story.clicked && type != getBookmarksIndex(ctx)) {
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
            //header
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
            //TODO can't get this to work, keeps resetting option
            headerViewHolder.popRecAuto.setText(headerViewHolder.currentPopRecSorting, false);
            //headerViewHolder.timeScaleAuto.setText(((MainHeaderViewHolder) holder).currentTimeSorting);

            if (searching) {
                headerViewHolder.searchEditText.requestFocus();

                headerViewHolder.searchEmptyContainer.setVisibility(stories.size() == 1 ? View.VISIBLE : View.GONE);
                headerViewHolder.noBookmarksLayout.setVisibility(View.GONE);
            } else {
                headerViewHolder.noBookmarksLayout.setVisibility((stories.size() == 1 && type == getBookmarksIndex(ctx)) ? View.VISIBLE : View.GONE);
                headerViewHolder.searchEmptyContainer.setVisibility(View.GONE);
            }

            //TODO finish search options
            //headerViewHolder.searchOptionsContainer.setVisibility(searching ? View.VISIBLE : View.GONE);

            headerViewHolder.typeSpinner.setSelection(type);
            //should collection be updated?
            if (hideJobs) {
                if (headerViewHolder.typeAdapter.getItem(8).equals("HN Jobs")) {
                    headerViewHolder.typeAdapter.remove("HN Jobs");
                }
            } else {
                if (!headerViewHolder.typeAdapter.getItem(8).equals("HN Jobs")) {
                    headerViewHolder.typeAdapter.insert("HN Jobs", 8);
                }
            }

            headerViewHolder.loadingFailedLayout.setVisibility(loadingFailed ? View.VISIBLE : View.GONE);
        } else if (holder instanceof SubmissionsHeaderViewHolder) {
            final SubmissionsHeaderViewHolder submissionsHeaderViewHolder = (SubmissionsHeaderViewHolder) holder;

            submissionsHeaderViewHolder.headerText.setText(submitter + "'s submissions:");

        } else {
            final CommentViewHolder commentViewHolder = (CommentViewHolder) holder;

            Story story = stories.get(position);

            commentViewHolder.headerText.setText("On \"" + story.commentMasterTitle + "\", " + Utils.getTimeAgo(story.time, true) + ":");
            commentViewHolder.bodyText.setHtml(story.text);
            //comment
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

        public Story story;

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

            linkLayoutView.setOnClickListener(view1 -> linkClickListener.onItemClick(getAbsoluteAdapterPosition()));
            commentLayoutView.setOnClickListener(view12 -> commentClickListener.onItemClick(getAbsoluteAdapterPosition()));
        }
    }

    public class MainHeaderViewHolder extends RecyclerView.ViewHolder {
        public final Spinner typeSpinner;
        public final LinearLayout container;
        public final LinearLayout loadingFailedLayout;
        public final LinearLayout noBookmarksLayout;
        public final LinearLayout spinnerContainer;
        public final LinearLayout searchEmptyContainer;
        public final LinearLayout searchOptionsContainer;
        public final EditText searchEditText;
        public final ImageButton moreButton;
        public final ImageButton searchButton;
        public final Button retryButton;
        public final AutoCompleteTextView popRecAuto;
        public final AutoCompleteTextView timeScaleAuto;

        public String currentPopRecSorting;
        public String currentTimeSorting;

        public ArrayAdapter<CharSequence> typeAdapter;

        public MainHeaderViewHolder(View view) {
            super(view);

            final Context ctx = view.getContext();

            loadingFailedLayout = view.findViewById(R.id.stories_header_loading_failed);
            container = view.findViewById(R.id.stories_header_container);
            typeSpinner = view.findViewById(R.id.stories_header_spinner);
            noBookmarksLayout = view.findViewById(R.id.stories_header_no_bookmarks);
            searchEditText = view.findViewById(R.id.stories_header_search_edittext);
            moreButton = view.findViewById(R.id.stories_header_more);
            spinnerContainer = view.findViewById(R.id.stories_header_spinner_container);
            searchButton = view.findViewById(R.id.stories_header_search_button);
            searchEmptyContainer = view.findViewById(R.id.stories_header_search_empty_container);
            retryButton = view.findViewById(R.id.stories_header_retry_button);
            searchOptionsContainer = view.findViewById(R.id.stories_header_search_options);
            popRecAuto = view.findViewById(R.id.search_dropdown_popularity_recent_textview);
            timeScaleAuto = view.findViewById(R.id.search_dropdown_timescale_textview);

            ArrayAdapter searchPopRecAdapter = new ArrayAdapter<>(ctx, R.layout.search_option_list_item, ctx.getResources().getStringArray(R.array.search_sorting_options));
            ArrayAdapter searchTimescaleAdapter = new ArrayAdapter<>(ctx, R.layout.search_option_list_item, ctx.getResources().getStringArray(R.array.search_sorting_timescale));

            popRecAuto.setText(ctx.getResources().getStringArray(R.array.search_sorting_options)[0], false);
            timeScaleAuto.setText(ctx.getResources().getStringArray(R.array.search_sorting_timescale)[0], false);
            currentPopRecSorting = ctx.getResources().getStringArray(R.array.search_sorting_options)[0];
            currentTimeSorting = ctx.getResources().getStringArray(R.array.search_sorting_timescale)[0];

            popRecAuto.setAdapter(searchPopRecAdapter);
            timeScaleAuto.setAdapter(searchTimescaleAdapter);

            popRecAuto.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    currentPopRecSorting = ctx.getResources().getStringArray(R.array.search_sorting_options)[i];
                    doSearch();
                }
            });

            timeScaleAuto.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                    currentTimeSorting = ctx.getResources().getStringArray(R.array.search_sorting_timescale)[i];
                    doSearch();
                }
            });

            retryButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    refreshListener.onRefresh();
                }
            });

            moreButton.setOnClickListener(moreClickListener);

            searchEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                    if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                        doSearch();

                        if (textView != null) {
                            InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
                        }
                        return true;
                    }

                    return false;
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
                    }
                }
            });

            String[] sortingOptions = ctx.getResources().getStringArray(R.array.sorting_options);

            typeAdapter = new ArrayAdapter<>(ctx, R.layout.spinner_top_layout, R.id.selection_dropdown_item_textview, new ArrayList<>(Arrays.asList(sortingOptions)));
            typeAdapter.setDropDownViewResource(R.layout.spinner_item_layout);

            typeSpinner.setAdapter(typeAdapter);
            typeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    typeClickListener.onItemClick(i);
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {

                }
            });
        }

        private void doSearch() {
            storiesSearchListener.onQueryTextSubmit(searchEditText.getText().toString(), popRecAuto.getText().equals("Popularity"), timeScaleAuto.getText().toString());
        }
    }



    public class SubmissionsHeaderViewHolder extends RecyclerView.ViewHolder {

        public final TextView headerText;

        public SubmissionsHeaderViewHolder(View view) {
            super(view);
            headerText = view.findViewById(R.id.submissions_header_text);
        }
    }

    public class CommentViewHolder extends RecyclerView.ViewHolder {

        public final TextView headerText;
        public final HtmlTextView bodyText;
        public final MaterialButton storyButton;
        public final MaterialButton repliesButton;

        public CommentViewHolder(View view) {
            super(view);
            headerText = view.findViewById(R.id.submissions_comment_header);
            bodyText = view.findViewById(R.id.submissions_comment_body);
            storyButton = view.findViewById(R.id.submissions_comment_button_story);
            repliesButton = view.findViewById(R.id.submissions_comment_button_replies);

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

    private int getBookmarksIndex(Context ctx) {
        //works as long as bookmarks is last option
        return ctx.getResources().getStringArray(R.array.sorting_options).length - (hideJobs ? 2 : 1);
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
        void onQueryTextSubmit(String query, boolean relevance, String age);
        void onSearchStatusChanged();
    }

    public interface RefreshListener {
        void onRefresh();
    }
}