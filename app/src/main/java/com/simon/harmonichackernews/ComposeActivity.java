package com.simon.harmonichackernews;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.Insets;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.simon.harmonichackernews.network.UserActions;
import com.simon.harmonichackernews.utils.ThemeUtils;
import com.simon.harmonichackernews.utils.Utils;
import com.simon.harmonichackernews.utils.ViewUtils;

import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.List;

import okhttp3.Response;


public class ComposeActivity extends AppCompatActivity {
    public final static String EXTRA_ID = "com.simon.harmonichackernews.EXTRA_ID";
    public final static String EXTRA_PARENT_TEXT = "com.simon.harmonichackernews.EXTRA_PARENT_TEXT";
    public final static String EXTRA_USER = "com.simon.harmonichackernews.EXTRA_USER";
    public final static String EXTRA_TYPE = "com.simon.harmonichackernews.EXTRA_TYPE";

    public final static int TYPE_TOP_COMMENT = 0;
    public final static int TYPE_COMMENT_REPLY = 1;
    public final static int TYPE_POST = 2;

    private EditText editText;
    private TextInputEditText editTextTitle;
    private TextInputEditText editTextUrl;
    private TextInputEditText editTextText;
    private TextInputLayout titleContainer;
    private TextInputLayout urlContainer;
    private TextInputLayout textContainer;
    private Button submitButton;
    private HtmlTextView replyingTextView;
    private ScrollView replyingScrollView;
    private TextView topCommentTextView;
    private int id;
    private String parentText;
    private String user;
    private int type;

    private OnBackPressedCallback backPressedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false, false);

        setContentView(R.layout.activity_compose);

        editText = findViewById(R.id.compose_edittext);
        editTextTitle = findViewById(R.id.compose_edittext_title);
        editTextUrl = findViewById(R.id.compose_edittext_url);
        editTextText = findViewById(R.id.compose_edittext_text);
        titleContainer = findViewById(R.id.compose_title_container);
        urlContainer = findViewById(R.id.compose_url_container);
        textContainer = findViewById(R.id.compose_text_container);
        submitButton = findViewById(R.id.compose_submit);
        replyingTextView = findViewById(R.id.compose_replying_text);
        replyingScrollView = findViewById(R.id.compose_replying_scrollview);
        topCommentTextView = findViewById(R.id.compose_top_comment);
        TextView postInfo = findViewById(R.id.compose_submit_info);
        LinearLayout bottomContainer = findViewById(R.id.compose_bottom_container);
        LinearLayout container = findViewById(R.id.compose_container);



        Intent intent = getIntent();
        id = intent.getIntExtra(EXTRA_ID, -1);
        parentText = intent.getStringExtra(EXTRA_PARENT_TEXT);
        user = intent.getStringExtra(EXTRA_USER);
        type = intent.getIntExtra(EXTRA_TYPE, TYPE_POST);


        if (type != TYPE_POST && id == -1) {
            Toast.makeText(this, "Invalid comment id", Toast.LENGTH_SHORT).show();
            finish();
        }

        switch (type) {
            case TYPE_TOP_COMMENT:
                replyingScrollView.setVisibility(View.GONE);
                topCommentTextView.setVisibility(View.VISIBLE);
                topCommentTextView.setText("Commenting on: " + parentText);
                break;
            case TYPE_COMMENT_REPLY:
                replyingScrollView.setVisibility(View.VISIBLE);
                topCommentTextView.setVisibility(View.GONE);
                replyingTextView.setHtml("<p>Replying to " + user + "'s comment:</p>" + parentText);
                replyingTextView.setOnClickATagListener(new OnClickATagListener() {
                    @Override
                    public boolean onClick(View widget, String spannedText, @Nullable String href) {
                        Utils.openLinkMaybeHN(widget.getContext(), href);
                        return true;
                    }
                });
                break;
            case TYPE_POST:
                replyingScrollView.setVisibility(View.GONE);
                topCommentTextView.setVisibility(View.VISIBLE);
                topCommentTextView.setText("Submission");

                titleContainer.setVisibility(View.VISIBLE);
                urlContainer.setVisibility(View.VISIBLE);
                textContainer.setVisibility(View.VISIBLE);
                postInfo.setVisibility(View.VISIBLE);

                editText.setVisibility(View.GONE);
                break;
        }

        ViewUtils.SimpleTextWatcher updateStatusTextWatcher = new ViewUtils.SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                updateEnabledStatuses();
            }
        };

        editText.addTextChangedListener(updateStatusTextWatcher);
        editTextTitle.addTextChangedListener(updateStatusTextWatcher);
        editTextUrl.addTextChangedListener(updateStatusTextWatcher);
        editTextText.addTextChangedListener(updateStatusTextWatcher);

        WindowCompat.setDecorFitsSystemWindows(getWindow(),false);

        ViewCompat.setOnApplyWindowInsetsListener(container, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
                int sideMargin = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);

                bottomContainer.setPadding(0, 0, 0, insets.bottom);
                container.setPadding(insets.left + sideMargin, insets.top, insets.right + sideMargin, 0);

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(container);

        ViewCompat.setWindowInsetsAnimationCallback(
                bottomContainer,
                new WindowInsetsAnimationCompat.Callback(WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_STOP) {
                    @NonNull
                    @Override
                    public WindowInsetsCompat onProgress(@NonNull WindowInsetsCompat insets, @NonNull List<WindowInsetsAnimationCompat> runningAnimations) {
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
                    public void onPrepare(@NonNull WindowInsetsAnimationCompat animation) {
                        startBottom = bottomContainer.getPaddingBottom();
                    }

                    float endBottom;

                    @NonNull
                    @Override
                    public WindowInsetsAnimationCompat.BoundsCompat onStart(@NonNull WindowInsetsAnimationCompat animation, @NonNull WindowInsetsAnimationCompat.BoundsCompat bounds) {
                        endBottom = bottomContainer.getPaddingBottom();
                        return bounds;
                    }
                });

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // This thing should only be enabled when we want to show the dialog, otherwise
                // we just do the default back behavior (which is predictive back)
                AlertDialog dialog = new MaterialAlertDialogBuilder(editText.getContext())
                        .setMessage(type == TYPE_POST ? "Discard post?" : "Discard comment?")
                        .setPositiveButton("Discard", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                dialog.cancel();
                                finish();
                            }})
                        .setNegativeButton("Cancel", null).create();

                dialog.show();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, backPressedCallback);
        updateEnabledStatuses();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Make sure scrollview never takes up more than 1/3 of screen height
        ViewGroup.LayoutParams layout = replyingScrollView.getLayoutParams();
        int dp160 = Utils.pxFromDpInt(getResources(), 160);
        int screenHeightThird = Resources.getSystem().getDisplayMetrics().heightPixels / 3;
        layout.height = Math.min(dp160, screenHeightThird);
        replyingScrollView.setLayoutParams(layout);
    }

    public void infoClick(View view) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Formatting information")
                .setMessage("Blank lines separate paragraphs.\n\nText surrounded by asterisks is italicized, if the character after the first asterisk isn't whitespace.\n\nText after a blank line that is indented by two or more spaces is reproduced verbatim. (This is intended for code.)\n\nUrls become links, except in the text field of a submission.")
                .setNegativeButton("Dismiss", null).create();

        dialog.show();
    }

    private void updateEnabledStatuses() {
        boolean enable;

        if (type == TYPE_POST) {
            boolean hasTitle = !TextUtils.isEmpty(editTextTitle.getText().toString());
            boolean hasUrl = !TextUtils.isEmpty(editTextUrl.getText().toString());
            boolean hasText = !TextUtils.isEmpty(editTextText.getText().toString());

            enable = hasTitle && (hasText || hasUrl);
        } else {
            enable = !TextUtils.isEmpty(editText.getText().toString());
        }

        backPressedCallback.setEnabled(enable);
        submitButton.setEnabled(enable);
    }

    public void submit(View view) {
        MaterialButton submitButton = (MaterialButton) view;
        CircularProgressDrawable c = new CircularProgressDrawable(this);
        submitButton.setIcon(c);
        c.start();

        if (type == TYPE_POST) {
            UserActions.submit(editTextTitle.getText().toString(), editText.getText().toString(), editTextUrl.getText().toString(), view.getContext(), new UserActions.ActionCallback() {
                @Override
                public void onSuccess(Response response) {
                    submitButton.setIcon(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.ic_action_send));
                    Toast.makeText(view.getContext(), "Post submitted, it might take a minute to show up", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onFailure(String summary, String response) {
                    submitButton.setIcon(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.ic_action_send));
                    UserActions.showFailureDetailDialog(view.getContext(), summary, response);
                    Toast.makeText(view.getContext(), "Post submission unsuccessful, see dialog for details", Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            UserActions.comment(String.valueOf(id), editText.getText().toString(), getApplicationContext(), new UserActions.ActionCallback() {
                @Override
                public void onSuccess(Response response) {
                    submitButton.setIcon(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.ic_action_send));
                    Toast.makeText(view.getContext(), "Comment posted, it might take a minute to show up", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onFailure(String summary, String response) {
                    submitButton.setIcon(AppCompatResources.getDrawable(getApplicationContext(), R.drawable.ic_action_send));
                    UserActions.showFailureDetailDialog(view.getContext(), summary, response);
                    Toast.makeText(view.getContext(), "Comment post unsuccessful, see dialog for details", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
