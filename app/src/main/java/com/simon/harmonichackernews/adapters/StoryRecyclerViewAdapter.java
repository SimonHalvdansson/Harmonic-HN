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

import java.util.List;

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

    private static final int TYPE_HEADER_SUBMISSIONS = 1;
    private static final int TYPE_STORY = 2;
    private static final int TYPE_COMMENT = 3;
    private static final int TYPE_LOAD_MORE_BUTTON = 4;

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

    public boolean paginationMode = false;
    public static final int PAGINATION_PAGE_SIZE = 30;
    public int visibleStoryCount = 30;

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
        } else if (viewType == TYPE_HEADER_SUBMISSIONS) {
            return new SubmissionsHeaderViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.submissions_header, parent, false));
        } else if (viewType == TYPE_LOAD_MORE_BUTTON) {
            return new LoadMoreViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.load_more_button, parent, false));
        } else {
            return new CommentViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.submissions_comment, parent, false));
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

            if (showIndex) {
                // For submissions, position 0 is header so story index = position
                // For non-submissions, position 0 is first story so display index = position + 1
                int displayIndex = atSubmissions ? position : position + 1;
                storyViewHolder.indexTextView.setText(displayIndex + ".");

                if (storyViewHolder.story.clicked) {
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
        if (atSubmissions) {
            // Submissions path: position 0 is header, rest are stories/comments
            if (position == 0) {
                return TYPE_HEADER_SUBMISSIONS;
            }
            return stories.get(position).isComment ? TYPE_COMMENT : TYPE_STORY;
        }

        // Non-submissions (StoriesFragment): no header in adapter
        if (paginationMode && position == visibleStoryCount && visibleStoryCount < stories.size()) {
            return TYPE_LOAD_MORE_BUTTON;
        }

        return TYPE_STORY;
    }

    @Override
    public int getItemCount() {
        if (atSubmissions) {
            // Submissions still has header at stories[0]
            return stories.size();
        }

        // Non-submissions: stories list contains only actual stories
        if (paginationMode && visibleStoryCount < stories.size()) {
            // visible stories + Load More button
            return visibleStoryCount + 1;
        }
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

}
