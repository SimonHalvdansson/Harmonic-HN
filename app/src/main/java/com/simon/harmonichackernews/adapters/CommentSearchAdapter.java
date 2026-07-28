package com.simon.harmonichackernews.adapters;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.databinding.CommentsItemBinding;
import com.simon.harmonichackernews.databinding.CommentsItemCardBinding;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.sufficientlysecure.htmltextview.HtmlFormatter;
import org.sufficientlysecure.htmltextview.HtmlFormatterBuilder;
import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommentSearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public static final int TYPE_COMMENT = 1;
    public static final int TYPE_COMMENT_CARD = 2;
    private static final Object SEARCH_TERM_CHANGED_PAYLOAD = new Object();
    private final List<Comment> comments;
    private final List<Comment> visibleComments = new ArrayList<>();
    private final Map<Comment, RenderedCommentText> renderedCommentTexts =
            new IdentityHashMap<>();
    private final boolean cardStyle;
    private final boolean cardBorder;
    private final boolean highlightCommentMeta;
    private final float preferredTextSize;

    private String searchTerm = "";
    private String normalizedSearchTerm = "";
    private Pattern searchHighlightPattern;
    public ItemClickListener itemClickListener;

    private int markedColor;

    public CommentSearchAdapter(List<Comment> comments,
                                boolean shouldUseCardStyle,
                                boolean shouldShowCardBorder,
                                boolean shouldHighlightCommentMeta,
                                float prefTextSize) {
        this.comments = comments == null ? new ArrayList<>() : new ArrayList<>(comments);
        cardStyle = shouldUseCardStyle;
        cardBorder = shouldShowCardBorder;
        highlightCommentMeta = shouldHighlightCommentMeta;
        preferredTextSize = prefTextSize;
        for (Comment comment : this.comments) {
            getRenderedCommentText(comment);
        }
        visibleComments.addAll(this.comments);
        setHasStableIds(true);
    }

    public void setSearchTerm(String searchTerm) {
        String previousSearchTerm = this.searchTerm;
        String nextSearchTerm = searchTerm == null ? "" : searchTerm;
        if (nextSearchTerm.equals(previousSearchTerm)) {
            return;
        }

        String previousNormalizedSearchTerm = normalizedSearchTerm;
        String nextNormalizedSearchTerm = nextSearchTerm.toLowerCase(Locale.ROOT);
        Pattern nextHighlightPattern = TextUtils.isEmpty(nextSearchTerm)
                ? null
                : Pattern.compile(
                        Pattern.quote(nextSearchTerm),
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        List<Comment> previousVisibleComments = new ArrayList<>(visibleComments);
        List<Comment> filterSource =
                !TextUtils.isEmpty(previousNormalizedSearchTerm)
                        && nextNormalizedSearchTerm.startsWith(previousNormalizedSearchTerm)
                        ? previousVisibleComments
                        : comments;
        List<Comment> nextVisibleComments =
                filterComments(filterSource, nextNormalizedSearchTerm);

        this.searchTerm = nextSearchTerm;
        normalizedSearchTerm = nextNormalizedSearchTerm;
        searchHighlightPattern = nextHighlightPattern;
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
        }, false).dispatchUpdatesTo(this);
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
        public View commentMetaContainer;
        public TextView commentHiddenCount;
        public TextView commentHiddenText;
        public View commentIndentIndicator;
        public View commentCard;
        public View commentContentContainer;

        public CommentViewHolder(CommentsItemBinding binding) {
            this(
                    binding.getRoot(),
                    binding.commentBody,
                    binding.commentBy,
                    binding.commentByTime,
                    binding.commentMetaContainer,
                    binding.commentHiddenCount,
                    binding.commentHiddenShort,
                    binding.commentIndentIndicator,
                    null);
        }

        public CommentViewHolder(CommentsItemCardBinding binding) {
            this(
                    binding.getRoot(),
                    binding.commentBody,
                    binding.commentBy,
                    binding.commentByTime,
                    binding.commentMetaContainer,
                    binding.commentHiddenCount,
                    binding.commentHiddenShort,
                    binding.commentIndentIndicator,
                    binding.commentCard);
        }

        private CommentViewHolder(View view,
                                  HtmlTextView text,
                                  TextView by,
                                  TextView byTime,
                                  View metaContainer,
                                  TextView hiddenCount,
                                  TextView hiddenText,
                                  View indentIndicator,
                                  View card) {
            super(view);
            commentText = text;
            commentBy = by;
            commentByTime = byTime;
            commentMetaContainer = metaContainer;
            commentHiddenCount = hiddenCount;
            commentHiddenText = hiddenText;
            commentIndentIndicator = indentIndicator;
            commentCard = card;
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

            markedColor = Color.parseColor(
                    ThemeUtils.isDarkMode(view.getContext()) ? "#fce205" : "#cc7722");

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
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_COMMENT_CARD) {
            return new CommentViewHolder(CommentsItemCardBinding.inflate(inflater, parent, false));
        }
        return new CommentViewHolder(CommentsItemBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        bindCommentViewHolder(holder, position);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (holder instanceof CommentViewHolder
                && hasOnlySearchTermChangedPayloads(payloads)) {
            bindHighlightedCommentText(
                    (CommentViewHolder) holder,
                    visibleComments.get(position));
            return;
        }
        bindCommentViewHolder(holder, position);
    }

    private static boolean hasOnlySearchTermChangedPayloads(List<Object> payloads) {
        if (payloads.isEmpty()) {
            return false;
        }
        for (Object payload : payloads) {
            if (payload != SEARCH_TERM_CHANGED_PAYLOAD) {
                return false;
            }
        }
        return true;
    }

    private void bindCommentViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (!(holder instanceof CommentSearchAdapter.CommentViewHolder)) {
            return;
        }
        final CommentViewHolder commentViewHolder = (CommentViewHolder) holder;

        Comment comment = visibleComments.get(position);
        applyItemMargins(commentViewHolder.itemView);
        applyCardChrome(commentViewHolder);
        applyCardContentPadding(commentViewHolder);
        applyCommentMetaHighlight(commentViewHolder);
        commentViewHolder.commentIndentIndicator.setVisibility(View.GONE);

        bindHighlightedCommentText(commentViewHolder, comment);
        commentViewHolder.commentBy.setText(comment.by);
        String formattedTime = comment.getTimeFormatted();
        commentViewHolder.commentByTime.setText(formattedTime);
        commentViewHolder.commentBy.setContentDescription("Comment by " + comment.by);
        commentViewHolder.commentByTime.setContentDescription("Posted " + formattedTime);
        commentViewHolder.commentHiddenCount.setVisibility(View.GONE);
        commentViewHolder.commentHiddenText.setVisibility(View.GONE);
        commentViewHolder.commentText.setVisibility(View.VISIBLE);

        FontUtils.setCommentTextTypeface(commentViewHolder.commentText, preferredTextSize);
        commentViewHolder.commentBy.setTypeface(FontUtils.activeBold);
        commentViewHolder.commentByTime.setTypeface(FontUtils.activeRegular);
    }

    private void bindHighlightedCommentText(
            CommentViewHolder commentViewHolder,
            Comment comment) {
        RenderedCommentText renderedCommentText = getRenderedCommentText(comment);
        SpannableStringBuilder text = new SpannableStringBuilder(
                renderedCommentText == null ? "" : renderedCommentText.renderedText);

        Pattern highlightPattern = searchHighlightPattern;
        if (highlightPattern != null) {
            Matcher matcher = highlightPattern.matcher(text);

            while (matcher.find()) {
                text.setSpan(
                        new ForegroundColorSpan(markedColor),
                        matcher.start(),
                        matcher.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(
                        new StyleSpan(Typeface.BOLD),
                        matcher.start(),
                        matcher.end(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }

        commentViewHolder.commentText.setHtml(text);
    }

    private void applyCommentMetaHighlight(CommentViewHolder commentViewHolder) {
        int horizontalPadding = highlightCommentMeta
                ? Utils.pxFromDpInt(commentViewHolder.commentMetaContainer.getResources(), 7)
                : 0;
        int verticalPadding = highlightCommentMeta
                ? Utils.pxFromDpInt(commentViewHolder.commentMetaContainer.getResources(), 2)
                : 0;
        commentViewHolder.commentMetaContainer.setBackgroundResource(
                highlightCommentMeta ? R.drawable.comment_meta_highlight_background : 0);
        commentViewHolder.commentMetaContainer.setPadding(
                horizontalPadding,
                verticalPadding,
                horizontalPadding,
                verticalPadding);

        int textColor = MaterialColors.getColor(
                commentViewHolder.commentMetaContainer,
                highlightCommentMeta ? R.attr.storyColorNormal : R.attr.storyColorDisabled,
                Color.BLACK);
        commentViewHolder.commentBy.setTextColor(textColor);
        commentViewHolder.commentByTime.setTextColor(textColor);
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

    private void applyCardChrome(CommentViewHolder commentViewHolder) {
        if (!(commentViewHolder.commentCard instanceof MaterialCardView)) {
            return;
        }

        MaterialCardView card = (MaterialCardView) commentViewHolder.commentCard;
        card.setStrokeWidth(cardBorder ? Utils.pxFromDpInt(card.getResources(), 1) : 0);
        card.setStrokeColor(cardBorder
                ? MaterialColors.getColor(card, R.attr.commentDividerColor, Color.TRANSPARENT)
                : Color.TRANSPARENT);
        card.setCardElevation(cardBorder ? Utils.pxFromDpInt(card.getResources(), 1) : 0f);
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

    private List<Comment> filterComments(
            List<Comment> sourceComments,
            String normalizedSearchTerm) {
        List<Comment> filteredComments = new ArrayList<>(sourceComments.size());
        for (Comment comment : sourceComments) {
            if (matchesSearchTerm(comment, normalizedSearchTerm)) {
                filteredComments.add(comment);
            }
        }
        return filteredComments;
    }

    private boolean matchesSearchTerm(Comment comment, String normalizedSearchTerm) {
        if (TextUtils.isEmpty(normalizedSearchTerm)) {
            return true;
        }
        if (comment == null || comment.text == null) {
            return false;
        }
        RenderedCommentText renderedCommentText = getRenderedCommentText(comment);
        return renderedCommentText != null
                && renderedCommentText.normalizedText.contains(normalizedSearchTerm);
    }

    private RenderedCommentText getRenderedCommentText(Comment comment) {
        if (comment == null || comment.text == null) {
            return null;
        }

        RenderedCommentText cachedText = renderedCommentTexts.get(comment);
        if (cachedText == null || !TextUtils.equals(cachedText.sourceText, comment.text)) {
            String expandedText = comment.getExpandedAnchorText();
            Spanned renderedText = HtmlFormatter.formatHtml(
                    new HtmlFormatterBuilder().setHtml(expandedText));
            cachedText = new RenderedCommentText(
                    comment.text,
                    renderedText,
                    renderedText.toString().toLowerCase(Locale.ROOT));
            renderedCommentTexts.put(comment, cachedText);
        }
        return cachedText;
    }

    private static final class RenderedCommentText {
        final String sourceText;
        final Spanned renderedText;
        final String normalizedText;

        RenderedCommentText(String sourceText, Spanned renderedText, String normalizedText) {
            this.sourceText = sourceText;
            this.renderedText = renderedText;
            this.normalizedText = normalizedText;
        }
    }

    public interface ItemClickListener {
        void onItemClick(Comment comment);
    }

}
