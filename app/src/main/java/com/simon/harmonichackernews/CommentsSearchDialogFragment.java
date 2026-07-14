package com.simon.harmonichackernews;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.search.SearchBar;
import com.simon.harmonichackernews.adapters.CommentSearchAdapter;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.databinding.CommentsSearchDialogBinding;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CommentsSearchDialogFragment extends AppCompatDialogFragment {

    public static String TAG = "tag_search_comments";
    public final static String EXTRA_SEARCHABLE_COMMENTS = "com.simon.harmonichackernews.EXTRA_SEARCHABLE_COMMENTS";


    private SearchBar searchBarBackground;
    private EditText searchBar;
    private RecyclerView recyclerView;
    private TextView matchesText;
    private CommentSearchAdapter adapter;
    private List<Comment> comments;
    private CommentsSearchDialogBinding binding;
    @Nullable
    private CommentSelectedListener listener;
    private TextWatcher searchWatcher;

    public CommentsSearchDialogFragment() {
    }

    public CommentsSearchDialogFragment(CommentSelectedListener commentSelectedListener) {
        this.listener = commentSelectedListener;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        binding = CommentsSearchDialogBinding.inflate(inflater);
        View rootView = binding.getRoot();

        searchBarBackground = binding.commentsSearchBar;
        searchBar = binding.commentsSearchEdittext;
        searchBarBackground.setElevation(0f);
        searchBar.bringToFront();
        recyclerView = binding.commentsSearchRecyclerview;
        matchesText = binding.commentsSearchMatches;

        List<Comment> searchableComments = null;
        if (getArguments() != null) {
            searchableComments = (List<Comment>) getArguments().getSerializable(EXTRA_SEARCHABLE_COMMENTS);
        }
        if (searchableComments == null || searchableComments.size() <= 1) {
            comments = new ArrayList<>();
        } else {
            comments = new ArrayList<>(searchableComments.subList(1, searchableComments.size()));
        }

        updateMatches(null);

        adapter = new CommentSearchAdapter(
                comments,
                SettingsUtils.shouldUseCardCommentDisplayStyle(requireContext()),
                SettingsUtils.shouldShowCommentCardBorder(requireContext()),
                SettingsUtils.getPreferredCommentTextSize(requireContext()));
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                String searchTerm = editable.toString();

                adapter.setSearchTerm(searchTerm);
                updateMatches(adapter.getVisibleCommentCount());
            }
        };
        searchBar.addTextChangedListener(searchWatcher);

        builder.setView(rootView);
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> focusSearchInput());

        adapter.setItemClickListener(new CommentSearchAdapter.ItemClickListener() {
            @Override
            public void onItemClick(Comment comment) {
                if (listener != null) {
                    listener.onCommentSelected(comment);
                }
                dialog.cancel();
            }
        });

        return dialog;
    }

    private void focusSearchInput() {
        EditText currentSearchBar = searchBar;
        if (currentSearchBar == null) {
            return;
        }

        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window != null) {
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }

        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        currentSearchBar.post(() -> {
            if (searchBar != currentSearchBar) {
                return;
            }
            currentSearchBar.requestFocus();
            imm.showSoftInput(currentSearchBar, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    @Override
    public void onDestroyView() {
        if (searchBar != null && searchWatcher != null) {
            searchBar.removeTextChangedListener(searchWatcher);
        }
        if (recyclerView != null) {
            recyclerView.setAdapter(null);
            recyclerView.setLayoutManager(null);
        }
        if (adapter != null) {
            adapter.setItemClickListener(null);
        }
        searchWatcher = null;
        searchBarBackground = null;
        searchBar = null;
        recyclerView = null;
        matchesText = null;
        binding = null;
        adapter = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        listener = null;
        super.onDestroy();
    }

    private void updateMatches(String searchTerm) {
        int matchingComments = TextUtils.isEmpty(searchTerm) ? comments.size() : 0;
        if (!TextUtils.isEmpty(searchTerm)) {
            for (Comment c : comments) {
                if (c.text != null && c.text.toUpperCase().contains(searchTerm.toUpperCase())) {
                    matchingComments++;
                }
            }
        }
        updateMatches(matchingComments);
    }

    private void updateMatches(int matchingComments) {
        matchesText.setText("(" + matchingComments + (matchingComments == 1 ? " match" : " matches") + ")");
    }

    public static void showCommentSearchDialog(FragmentManager fm, List<Comment> comments, CommentSelectedListener listener) {
        CommentsSearchDialogFragment dialogFragment = new CommentsSearchDialogFragment(listener);
        Bundle bundle = new Bundle();

        // Serialize the ArrayList
        bundle.putSerializable(EXTRA_SEARCHABLE_COMMENTS, (Serializable) comments);
        dialogFragment.setArguments(bundle);
        dialogFragment.show(fm, CommentsSearchDialogFragment.TAG);
    }

    public interface CommentSelectedListener {
        void onCommentSelected(Comment comment);
        // Add more methods if needed
    }

}
