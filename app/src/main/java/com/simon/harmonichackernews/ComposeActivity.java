package com.simon.harmonichackernews;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.core.text.TextUtilsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import okhttp3.Response;


public class ComposeActivity extends AppCompatActivity {
    public final static String EXTRA_ID = "com.simon.harmonichackernews.EXTRA_ID";
    public final static String EXTRA_PARENT_TEXT = "com.simon.harmonichackernews.EXTRA_PARENT_TEXT";
    public final static String EXTRA_USER = "com.simon.harmonichackernews.EXTRA_USER";

    private EditText editText;
    private Button submitButton;
    private HtmlTextView replyingTextView;
    private ScrollView replyingScrollView;
    private TextView topCommentTextView;

    private int id;
    private String parentText;
    private String user;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false, false);

        setContentView(R.layout.activity_compose);

        editText = findViewById(R.id.compose_edittext);
        submitButton = findViewById(R.id.compose_submit);
        replyingTextView = findViewById(R.id.compose_replying_text);
        replyingScrollView = findViewById(R.id.compose_replying_scrollview);
        topCommentTextView = findViewById(R.id.compose_top_comment);

        Intent intent = getIntent();
        id = intent.getIntExtra(EXTRA_ID, -1);
        parentText = intent.getStringExtra(EXTRA_PARENT_TEXT);
        user = intent.getStringExtra(EXTRA_USER);

        if (id == -1) {
            Toast.makeText(this, "Invalid comment id", Toast.LENGTH_SHORT).show();
            finish();
        }

        if (TextUtils.isEmpty(user)) {
            replyingScrollView.setVisibility(View.GONE);
            topCommentTextView.setVisibility(View.VISIBLE);
            topCommentTextView.setText("Commenting on: " + parentText);
        } else {
            replyingScrollView.setVisibility(View.VISIBLE);
            topCommentTextView.setVisibility(View.GONE);
            replyingTextView.setHtml("<p>Replying to " + user + "'s comment:</p>" + parentText);
        }

        submitButton.setEnabled(!TextUtils.isEmpty(editText.getText().toString()));

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                submitButton.setEnabled(!TextUtils.isEmpty(editable.toString()));
            }
        });

        OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (TextUtils.isEmpty(editText.getText().toString())) {
                    finish();
                } else {
                    AlertDialog dialog = new MaterialAlertDialogBuilder(editText.getContext())
                            .setTitle("Discard draft?")
                            .setMessage("It will not be saved")
                            .setPositiveButton("Discard", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int whichButton) {
                                    finish();
                                }})
                            .setNegativeButton("Cancel", null).create();

                    dialog.show();
                }
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        //Make sure scrollview never takes up more than 1/3 of screen height
        ViewGroup.LayoutParams layout = replyingScrollView.getLayoutParams();
        float dp160 = Utils.pxFromDp(getResources(), 160);
        int screenHeightThird = Resources.getSystem().getDisplayMetrics().heightPixels / 3;
        if (dp160 > screenHeightThird) {
            layout.height = screenHeightThird;
        } else {
            layout.height = Math.round(dp160);
        }
        replyingScrollView.setLayoutParams(layout);
    }

    public void infoClick(View view) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Formatting information")
                .setMessage("Blank lines separate paragraphs.\n\nText surrounded by asterisks is italicized, if the character after the first asterisk isn't whitespace.\n\nText after a blank line that is indented by two or more spaces is reproduced verbatim. (This is intended for code.)\n\nUrls become links, except in the text field of a submission.")
                .setNegativeButton("Dismiss", null).create();

        dialog.show();
    }

    public void submit(View view) {
        ProgressDialog dialog = ProgressDialog.show(this, "",
                "Posting...", true);

        dialog.getWindow().setBackgroundDrawableResource(ThemeUtils.getBackgroundColorResource(view.getContext()));

        UserActions.comment(String.valueOf(id), editText.getText().toString(), view.getContext(), new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                dialog.cancel();
                Toast.makeText(view.getContext(), "Comment posted, it might take a minute to show up", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onFailure(String response) {
                dialog.cancel();
                Toast.makeText(view.getContext(), "Comment post unsuccessful, error: " + response, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
