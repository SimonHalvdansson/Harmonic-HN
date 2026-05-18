package com.simon.harmonichackernews.adapters;

import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.utils.ThemeUtils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_COMMENT = 1;
    private static final Object SEARCH_TERM_CHANGED_PAYLOAD = new Object();
    private final List<Comment> comments;
    private final List<Comment> visibleComments = new ArrayList<>();

    private String searchTerm = "";
    public ItemClickListener itemClickListener;

    private String markedColor;

    public CommentSearchAdapter(List<Comment> comments) {
        this.comments = comments;
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
        public LinearLayout container;

        public CommentViewHolder(View view) {
            super(view);
            commentText = view.findViewById(R.id.comment_search_item_text);
            commentBy = view.findViewById(R.id.comment_search_item_by);
            container = view.findViewById(R.id.comment_search_item_container);

            container.setOnClickListener((v) -> {
                int position = getAbsoluteAdapterPosition();
                if (position != RecyclerView.NO_POSITION && itemClickListener != null) {
                    itemClickListener.onItemClick(visibleComments.get(position));
                }
            });

            markedColor = ThemeUtils.isDarkMode(view.getContext()) ? "#fce205" : "#cc7722";

            // this is illegal according to some but works according to all
            // the issue is that HtmlTextView hijacks clicks heavily
            final RippleDrawable rippleDrawable = (RippleDrawable) container.getBackground();

            commentText.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            rippleDrawable.setHotspot(event.getX(), event.getY());
                            container.setPressed(true);
                            return true;
                        case MotionEvent.ACTION_UP:
                            // Manually trigger click event
                            container.performClick();
                            container.setPressed(false);
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            container.setPressed(false);
                            return true;
                    }
                    return false;
                }
            });

        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.comments_search_item, parent, false);
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
        String text = comment.text == null ? "" : comment.text;

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
    }

    @Override
    public int getItemCount() {
        return visibleComments.size();
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
