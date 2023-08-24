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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.core.text.TextUtilsCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.sufficientlysecure.htmltextview.HtmlTextView;

import java.util.List;

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
        LinearLayout bottomContainer = findViewById(R.id.compose_bottom_container);
        LinearLayout container = findViewById(R.id.compose_container);

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

        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);

        ViewCompat.setOnApplyWindowInsetsListener(container, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());

                bottomContainer.setPadding(0, 0, 0, insets.bottom);
                container.setPadding(0, insets.top, 0, 0);

                return windowInsets;
            }
        });

        ViewCompat.setWindowInsetsAnimationCallback(
                bottomContainer,
                new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onProgress(
                            @NonNull WindowInsetsCompat insets,
                            @NonNull List<WindowInsetsAnimationCompat> runningAnimations
                    ) {
                        WindowInsetsAnimationCompat imeAnimation = null;
                        for (WindowInsetsAnimationCompat animation : runningAnimations) {
                            if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                                imeAnimation = animation;
                                break;
                            }
                        }
                        if (imeAnimation != null) {
                            // Offset the view based on the interpolated fraction of the IME animation.
                            bottomContainer.setTranslationY(-(startBottom - endBottom)
                                    * (1 - imeAnimation.getInterpolatedFraction()));
                        }

                        return insets;
                    }

                    float startBottom;

                    @Override
                    public void onPrepare(
                            @NonNull WindowInsetsAnimationCompat animation
                    ) {

                        startBottom = bottomContainer.getPaddingBottom();
                        Utils.log(startBottom);
                    }

                    float endBottom;

                    @NonNull
                    @Override
                    public WindowInsetsAnimationCompat.BoundsCompat onStart(
                            @NonNull WindowInsetsAnimationCompat animation,
                            @NonNull WindowInsetsAnimationCompat.BoundsCompat bounds
                    ) {
                        endBottom = bottomContainer.getPaddingBottom();
                        Utils.log(endBottom);
                        return bounds;
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
        int dp160 = Utils.pxFromDpInt(getResources(), 160);
        int screenHeightThird = Resources.getSystem().getDisplayMetrics().heightPixels / 3;
        if (dp160 > screenHeightThird) {
            layout.height = screenHeightThird;
        } else {
            layout.height = dp160;
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
