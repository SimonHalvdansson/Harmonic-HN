package com.simon.harmonichackernews;

import static com.simon.harmonichackernews.SubmissionsActivity.KEY_USER;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.simon.harmonichackernews.adapters.CommentSearchAdapter;
import com.simon.harmonichackernews.data.Comment;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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