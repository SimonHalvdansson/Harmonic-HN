package com.simon.harmonichackernews;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.simon.harmonichackernews.adapters.CommentSearchAdapter;
import com.simon.harmonichackernews.data.Comment;

import java.io.Serializable;
import java.util.List;

public class CommentsSearchDialogFragment extends AppCompatDialogFragment {

    public static String TAG = "tag_search_comments";
    public final static String EXTRA_SEARCHABLE_COMMENTS = "com.simon.harmonichackernews.EXTRA_SEARCHABLE_COMMENTS";


    private TextInputEditText searchBar;
    private RecyclerView recyclerView;
    private TextView matchesText;
    private CommentSearchAdapter adapter;
    private List<Comment> comments;
    private final CommentSelectedListener listener;

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

        searchBar = rootView.findViewById(R.id.comments_search_edittext);
        recyclerView = rootView.findViewById(R.id.comments_search_recyclerview);
        matchesText = rootView.findViewById(R.id.comments_search_matches);

        if (getArguments() != null) {
            comments = (List<Comment>) getArguments().getSerializable(EXTRA_SEARCHABLE_COMMENTS);
            comments = comments.subList(1, comments.size());
        }

        updateMatches(null);

        adapter = new CommentSearchAdapter(comments);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        searchBar.addTextChangedListener(new TextWatcher() {
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
                adapter.notifyDataSetChanged();

                updateMatches(searchTerm);
            }
        });

        builder.setView(rootView);
        final AlertDialog dialog = builder.create();

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

    private void updateMatches(String searchTerm) {
        int matchingComments = 0;
        if (TextUtils.isEmpty(searchTerm)) {
            matchingComments = comments.size();
        } else {
            for (Comment c : comments) {
                if (c.text.toUpperCase().contains(searchTerm.toUpperCase())) {
                    matchingComments++;
                }
            }
        }

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