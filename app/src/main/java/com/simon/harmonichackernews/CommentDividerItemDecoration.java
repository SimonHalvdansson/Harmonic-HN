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

import java.util.Objects;

final class CommentDividerItemDecoration extends RecyclerView.ItemDecoration {

    private static final int HORIZONTAL_INSET_DP = 4;
    private static final int INTER_COMMENT_SPACING_DP = 4;

    private final CommentsRecyclerViewAdapter adapter;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int cachedDensityDpi = -1;
    private int cachedUiMode = -1;
    private String cachedTheme;
    private int horizontalInset;
    private int cardShadowPadding;
    private int interCommentSpacing;
    private int contentInsetLeft;
    private int contentInsetRight;

    CommentDividerItemDecoration(CommentsRecyclerViewAdapter adapter) {
        this.adapter = adapter;
    }

    boolean setContentSideInsets(int left, int right) {
        int safeLeft = Math.max(0, left);
        int safeRight = Math.max(0, right);
        if (contentInsetLeft == safeLeft && contentInsetRight == safeRight) {
            return false;
        }

        contentInsetLeft = safeLeft;
        contentInsetRight = safeRight;
        return true;
    }

    @Override
    public void onDrawOver(
            @NonNull Canvas canvas,
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state) {
        if (!adapter.showDividers) {
            return;
        }

        ensureStyle(parent);
        int cardInset = adapter.cardStyle
                ? cardShadowPadding
                : 0;
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION
                    || !CommentsRecyclerViewAdapter.isCommentViewType(adapter.getItemViewType(position))) {
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
        if (position > 0) {
            outRect.left = contentInsetLeft;
            outRect.right = contentInsetRight;
        }
        if (adapter.showDividers
                && position != RecyclerView.NO_POSITION
                && CommentsRecyclerViewAdapter.isCommentViewType(adapter.getItemViewType(position))
                && hasFollowingVisibleComment(position)) {
            ensureStyle(parent);
            outRect.bottom = interCommentSpacing;
        }
    }

    private void ensureStyle(@NonNull RecyclerView parent) {
        int densityDpi = parent.getResources().getConfiguration().densityDpi;
        int uiMode = parent.getResources().getConfiguration().uiMode;
        if (densityDpi == cachedDensityDpi
                && uiMode == cachedUiMode
                && Objects.equals(cachedTheme, adapter.theme)) {
            return;
        }

        cachedDensityDpi = densityDpi;
        cachedUiMode = uiMode;
        cachedTheme = adapter.theme;
        paint.setColor(MaterialColors.getColor(
                parent,
                R.attr.commentDividerColor,
                Color.TRANSPARENT));
        paint.setStrokeWidth(Utils.pxFromDpInt(parent.getResources(), 1));
        horizontalInset = Utils.pxFromDpInt(
                parent.getResources(),
                HORIZONTAL_INSET_DP);
        cardShadowPadding = parent.getResources().getDimensionPixelSize(
                R.dimen.comment_card_shadow_padding);
        interCommentSpacing = Utils.pxFromDpInt(
                parent.getResources(),
                INTER_COMMENT_SPACING_DP);
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
