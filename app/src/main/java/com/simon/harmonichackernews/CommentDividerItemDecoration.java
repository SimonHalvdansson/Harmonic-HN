package com.simon.harmonichackernews;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.simon.harmonichackernews.adapters.CommentsRecyclerViewAdapter;
import com.simon.harmonichackernews.utils.Utils;

final class CommentDividerItemDecoration extends RecyclerView.ItemDecoration {

    private static final int HORIZONTAL_INSET_DP = 4;
    private static final int INTER_COMMENT_SPACING_DP = 4;

    private final CommentsRecyclerViewAdapter adapter;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    CommentDividerItemDecoration(CommentsRecyclerViewAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public void onDrawOver(
            @NonNull Canvas canvas,
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state) {
        if (!adapter.showDividers) {
            return;
        }

        paint.setColor(MaterialColors.getColor(
                parent,
                R.attr.commentDividerColor,
                Color.TRANSPARENT));
        paint.setStrokeWidth(Utils.pxFromDpInt(parent.getResources(), 1));

        int horizontalInset = Utils.pxFromDpInt(
                parent.getResources(),
                HORIZONTAL_INSET_DP);
        int cardInset = adapter.cardStyle
                ? parent.getResources().getDimensionPixelSize(R.dimen.comment_card_shadow_padding)
                : 0;
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION
                    || !CommentsRecyclerViewAdapter.isCommentViewType(adapter.getItemViewType(position))
                    || !hasFollowingVisibleComment(position)) {
                continue;
            }

            View nextComment = findNextAttachedComment(parent, index, position);
            if (nextComment == null) {
                continue;
            }

            float childBottom = child.getBottom() + child.getTranslationY();
            float nextCommentTop = nextComment.getTop() + nextComment.getTranslationY();
            float y = (childBottom + nextCommentTop) / 2f;
            canvas.drawLine(
                    child.getLeft() + cardInset + horizontalInset,
                    y,
                    child.getRight() - cardInset - horizontalInset,
                    y,
                    paint);
        }
    }

    @Override
    public void getItemOffsets(
            @NonNull Rect outRect,
            @NonNull View view,
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (adapter.showDividers
                && position != RecyclerView.NO_POSITION
                && CommentsRecyclerViewAdapter.isCommentViewType(adapter.getItemViewType(position))
                && hasFollowingVisibleComment(position)) {
            outRect.bottom = Utils.pxFromDpInt(
                    parent.getResources(),
                    INTER_COMMENT_SPACING_DP);
        }
    }

    private View findNextAttachedComment(
            RecyclerView parent,
            int childIndex,
            int position) {
        for (int index = childIndex + 1; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            int childPosition = parent.getChildAdapterPosition(child);
            if (childPosition > position
                    && CommentsRecyclerViewAdapter.isCommentViewType(
                            adapter.getItemViewType(childPosition))) {
                return child;
            }
        }
        return null;
    }

    private boolean hasFollowingVisibleComment(int position) {
        for (int index = position + 1; index < adapter.getItemCount(); index++) {
            if (CommentsRecyclerViewAdapter.isCommentViewType(adapter.getItemViewType(index))) {
                return true;
            }
        }
        return false;
    }
}
