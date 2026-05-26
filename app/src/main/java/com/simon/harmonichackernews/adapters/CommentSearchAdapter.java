package com.simon.harmonichackernews.adapters;

import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_COMMENT = 1;
    public static final int TYPE_COMMENT_CARD = 2;
    private static final Object SEARCH_TERM_CHANGED_PAYLOAD = new Object();
    private final List<Comment> comments;
    private final List<Comment> visibleComments = new ArrayList<>();
    private final boolean cardStyle;
    private final int preferredTextSize;

    private String searchTerm = "";
    public ItemClickListener itemClickListener;

    private String markedColor;

    public CommentSearchAdapter(List<Comment> comments,
                                boolean shouldUseCardStyle,
                                int prefTextSize) {
        this.comments = comments;
        cardStyle = shouldUseCardStyle;
        preferredTextSize = prefTextSize;
        visibleComments.addAll(comments);
        setHasStableIds(true);
    }

    public void setSearchTerm(String searchTerm) {
        String previousSearchTerm = this.searchTerm;
        String nextSearchTerm = searchTerm == null ? "" : searchTerm;
        List<Comment> previousVisibleComments = new ArrayList<>(visibleComments);
        List<Comment> nextVisibleComments = filterComments(nextSearchTerm);

        this.searchTerm = nextSearchTerm;
        visibleComments.clear();
        visibleComments.addAll(nextVisibleComments);

        DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() {
                return previousVisibleComments.size();
            }

            @Override
            public int getNewListSize() {
                return nextVisibleComments.size();
            }

            @Override
            public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
                return previousVisibleComments.get(oldItemPosition).id == nextVisibleComments.get(newItemPosition).id;
            }

            @Override
            public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
                Comment oldComment = previousVisibleComments.get(oldItemPosition);
                Comment newComment = nextVisibleComments.get(newItemPosition);
                return oldComment == newComment && previousSearchTerm.equals(nextSearchTerm);
            }

            @Override
            public Object getChangePayload(int oldItemPosition, int newItemPosition) {
                return SEARCH_TERM_CHANGED_PAYLOAD;
            }
        }).dispatchUpdatesTo(this);
    }

    public int getVisibleCommentCount() {
        return visibleComments.size();
    }

    public void setItemClickListener(ItemClickListener clickListener) {
        itemClickListener = clickListener;
    }

    public class CommentViewHolder extends RecyclerView.ViewHolder {
        public HtmlTextView commentText;
        public TextView commentBy;
        public TextView commentByTime;
        public TextView commentHiddenCount;
        public TextView commentHiddenText;
        public View commentIndentIndicator;
        public View commentCard;
        public View commentContentContainer;

        public CommentViewHolder(View view) {
            super(view);
            commentText = view.findViewById(R.id.comment_body);
            commentBy = view.findViewById(R.id.comment_by);
            commentByTime = view.findViewById(R.id.comment_by_time);
            commentHiddenCount = view.findViewById(R.id.comment_hidden_count);
            commentHiddenText = view.findViewById(R.id.comment_hidden_short);
            commentIndentIndicator = view.findViewById(R.id.comment_indent_indicator);
            commentCard = view.findViewById(R.id.comment_card);
            commentContentContainer = commentText == null ? null : (View) commentText.getParent();

            if (commentCard == null) {
                itemView.setBackgroundResource(resolveSelectableItemBackground(view));
            }

            View clickTarget = getClickTarget();
            View.OnClickListener clickListener = v -> {
                int position = getAbsoluteAdapterPosition();
                if (position != RecyclerView.NO_POSITION && itemClickListener != null) {
                    itemClickListener.onItemClick(visibleComments.get(position));
                }
            };

            itemView.setOnClickListener(clickListener);
            clickTarget.setOnClickListener(clickListener);

            markedColor = ThemeUtils.isDarkMode(view.getContext()) ? "#fce205" : "#cc7722";

            // this is illegal according to some but works according to all
            // the issue is that HtmlTextView hijacks clicks heavily
            final RippleDrawable rippleDrawable = clickTarget.getBackground() instanceof RippleDrawable
                    ? (RippleDrawable) clickTarget.getBackground()
                    : null;

            commentText.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            if (rippleDrawable != null) {
                                rippleDrawable.setHotspot(event.getX(), event.getY());
                            }
                            clickTarget.setPressed(true);
                            return true;
                        case MotionEvent.ACTION_UP:
                            // Manually trigger click event
                            clickTarget.performClick();
                            clickTarget.setPressed(false);
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            clickTarget.setPressed(false);
                            return true;
                    }
                    return false;
                }
            });

        }

        private View getClickTarget() {
            return commentCard != null ? commentCard : itemView;
        }

        private int resolveSelectableItemBackground(View view) {
            TypedValue typedValue = new TypedValue();
            view.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true);
            return typedValue.resourceId;
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(
                viewType == TYPE_COMMENT_CARD ? R.layout.comments_item_card : R.layout.comments_item,
                parent,
                false);
        return new CommentViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        bindCommentViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        bindCommentViewHolder(holder, position);
    }

    private void bindCommentViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (!(holder instanceof CommentSearchAdapter.CommentViewHolder)) {
            return;
        }
        final CommentViewHolder commentViewHolder = (CommentViewHolder) holder;

        Comment comment = visibleComments.get(position);
        applyItemMargins(commentViewHolder.itemView);
        applyCardContentPadding(commentViewHolder);
        commentViewHolder.commentIndentIndicator.setVisibility(View.GONE);

        String text = Utils.expandShortenedAnchorText(comment.text == null ? "" : comment.text);

        StringBuffer sb = new StringBuffer(); // To hold the modified string

        if (!TextUtils.isEmpty(searchTerm)) {
            Pattern pattern = Pattern.compile("(?i)" + Pattern.quote(searchTerm));
            Matcher matcher = pattern.matcher(text);

            while (matcher.find()) {
                // Retains the original case of the matching substring and adds the red color
                String replacement = "<b><font color='" + markedColor + "'>" + matcher.group() + "</font></b>";
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }

            // Append the remainder of the string
            matcher.appendTail(sb);
            text = sb.toString();
        }

        commentViewHolder.commentText.setHtml(text);
        commentViewHolder.commentBy.setText(comment.by);
        commentViewHolder.commentByTime.setText(comment.getTimeFormatted());
        commentViewHolder.commentBy.setContentDescription("Comment by " + comment.by);
        commentViewHolder.commentByTime.setContentDescription("Posted " + comment.getTimeFormatted());
        commentViewHolder.commentHiddenCount.setVisibility(View.GONE);
        commentViewHolder.commentHiddenText.setVisibility(View.GONE);
        commentViewHolder.commentText.setVisibility(View.VISIBLE);

        FontUtils.setTypeface(commentViewHolder.commentText, false, preferredTextSize);
        commentViewHolder.commentBy.setTypeface(FontUtils.activeBold);
        commentViewHolder.commentByTime.setTypeface(FontUtils.activeRegular);
    }

    private void applyItemMargins(View itemView) {
        RecyclerView.LayoutParams params;
        if (itemView.getLayoutParams() instanceof RecyclerView.LayoutParams) {
            params = (RecyclerView.LayoutParams) itemView.getLayoutParams();
        } else {
            params = new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT);
        }

        int horizontalMargin = Utils.pxFromDpInt(itemView.getResources(), 16);
        int topMargin = Utils.pxFromDpInt(itemView.getResources(), cardStyle ? 4 : 6);
        int bottomMargin = Utils.pxFromDpInt(itemView.getResources(), cardStyle ? 4 : 6);
        int cardShadowPadding = cardStyle
                ? itemView.getResources().getDimensionPixelSize(R.dimen.comment_card_shadow_padding)
                : 0;

        if (cardStyle) {
            itemView.setPadding(
                    0,
                    cardShadowPadding,
                    0,
                    cardShadowPadding);
            params.setMargins(
                    Math.max(0, horizontalMargin - cardShadowPadding),
                    Math.max(0, topMargin - cardShadowPadding),
                    Math.max(0, horizontalMargin - cardShadowPadding),
                    Math.max(0, bottomMargin - cardShadowPadding));
        } else {
            itemView.setPadding(
                    Utils.pxFromDpInt(itemView.getResources(), 20),
                    Utils.pxFromDpInt(itemView.getResources(), 3),
                    Utils.pxFromDpInt(itemView.getResources(), 20),
                    Utils.pxFromDpInt(itemView.getResources(), 3));
            params.setMargins(0, 0, 0, 0);
        }
        itemView.setLayoutParams(params);
    }

    private void applyCardContentPadding(CommentViewHolder commentViewHolder) {
        if (!cardStyle || commentViewHolder.commentContentContainer == null) {
            return;
        }

        View contentContainer = commentViewHolder.commentContentContainer;
        int verticalPadding = contentContainer.getPaddingTop();
        contentContainer.setPadding(
                verticalPadding,
                contentContainer.getPaddingTop(),
                verticalPadding,
                contentContainer.getPaddingBottom());
    }

    @Override
    public int getItemCount() {
        return visibleComments.size();
    }

    @Override
    public int getItemViewType(int position) {
        return cardStyle ? TYPE_COMMENT_CARD : TYPE_COMMENT;
    }

    @Override
    public long getItemId(int position) {
        return visibleComments.get(position).id;
    }

    private List<Comment> filterComments(String searchTerm) {
        List<Comment> filteredComments = new ArrayList<>();
        for (Comment comment : comments) {
            if (matchesSearchTerm(comment, searchTerm)) {
                filteredComments.add(comment);
            }
        }
        return filteredComments;
    }

    private boolean matchesSearchTerm(Comment comment, String searchTerm) {
        if (TextUtils.isEmpty(searchTerm)) {
            return true;
        }
        if (comment == null || comment.text == null) {
            return false;
        }
        return comment.text.toLowerCase(Locale.ROOT).contains(searchTerm.toLowerCase(Locale.ROOT));
    }

    public interface ItemClickListener {
        void onItemClick(Comment comment);
    }

}
