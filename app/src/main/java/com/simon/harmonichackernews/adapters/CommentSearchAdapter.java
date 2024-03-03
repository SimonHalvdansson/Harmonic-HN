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
import androidx.recyclerview.widget.RecyclerView;

import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.utils.ThemeUtils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_COMMENT = 1;
    public static final int TYPE_COLLAPSED = 2;
    private final List<Comment> comments;

    private String searchTerm;
    public ItemClickListener itemClickListener;

    private String markedColor;

    public CommentSearchAdapter(List<Comment> comments) {
        this.comments = comments;
    }

    public void setSearchTerm(String searchTerm) {
        this.searchTerm = searchTerm;
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
                itemClickListener.onItemClick(comments.get(getAbsoluteAdapterPosition()));
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
        if (viewType == TYPE_COMMENT) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.comments_search_item, parent, false);
            return new CommentViewHolder(v);
        } else {
            return new RecyclerView.ViewHolder(new View(parent.getContext())) {};
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (!(holder instanceof CommentSearchAdapter.CommentViewHolder)) {
            return;
        }
        final CommentViewHolder commentViewHolder = (CommentViewHolder) holder;

        Comment comment = comments.get(position);
        String text = comment.text;

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
    public int getItemViewType(int position) {
        return TextUtils.isEmpty(searchTerm) || comments.get(position).text.toUpperCase().contains(searchTerm.toUpperCase()) ? TYPE_COMMENT : TYPE_COLLAPSED;
    }

    @Override
    public int getItemCount() {
        return comments.size();
    }

    public interface ItemClickListener {
        void onItemClick(Comment comment);
    }

}
