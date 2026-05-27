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
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.io.Serializable;
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
    private final CommentSelectedListener listener;
    private TextWatcher searchWatcher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            comments = (List<Comment>) getArguments().getSerializable(EXTRA_SEARCHABLE_COMMENTS);
        }
    }

    public CommentsSearchDialogFragment(CommentSelectedListener commentSelectedListener) {
        this.listener = commentSelectedListener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.comments_search_dialog, null);

        searchBarBackground = rootView.findViewById(R.id.comments_search_bar);
        searchBar = rootView.findViewById(R.id.comments_search_edittext);
        searchBarBackground.setElevation(0f);
        searchBar.bringToFront();
        recyclerView = rootView.findViewById(R.id.comments_search_recyclerview);
        matchesText = rootView.findViewById(R.id.comments_search_matches);

        if (getArguments() != null) {
            comments = (List<Comment>) getArguments().getSerializable(EXTRA_SEARCHABLE_COMMENTS);
            comments = comments.subList(1, comments.size());
        }

        updateMatches(null);

        adapter = new CommentSearchAdapter(
                comments,
                SettingsUtils.shouldUseCardCommentDisplayStyle(requireContext()),
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
        adapter = null;
        super.onDestroyView();
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
