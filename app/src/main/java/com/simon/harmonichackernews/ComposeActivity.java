package com.simon.harmonichackernews;

import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
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
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.OnApplyWindowInsetsListener;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsAnimationCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.simon.harmonichackernews.databinding.ActivityComposeBinding;
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
    public final static String EXTRA_POST_TITLE = "com.simon.harmonichackernews.EXTRA_POST_TITLE";
    public final static String EXTRA_USER = "com.simon.harmonichackernews.EXTRA_USER";
    public final static String EXTRA_TYPE = "com.simon.harmonichackernews.EXTRA_TYPE";

    public final static int TYPE_TOP_COMMENT = 0;
    public final static int TYPE_COMMENT_REPLY = 1;
    public final static int TYPE_POST = 2;

    private static final int FLOATING_CONTROL_ELEVATION_DP = 4;
    private static final int FAB_HOVER_TRANSLATION_Z_DP = 2;
    private static final int TOP_COMMENT_FIELD_TOP_MARGIN_DP = 8;
    private static final int REPLY_FIELD_TOP_MARGIN_DP = 12;

    private EditText editText;
    private TextInputEditText editTextTitle;
    private TextInputEditText editTextUrl;
    private TextInputEditText editTextText;
    private TextInputLayout titleContainer;
    private TextInputLayout urlContainer;
    private TextInputLayout textContainer;
    private TextInputLayout commentContainer;
    private FloatingActionButton submitButton;
    private LoadingIndicator submitLoadingIndicator;
    private HtmlTextView replyingTextView;
    private TextView replyingHeaderTextView;
    private ScrollView replyingScrollView;
    private MaterialToolbar topAppBar;
    private LinearLayout composeContainer;
    private View bottomInsetSpacer;
    private int id;
    private String parentText;
    private String postTitle;
    private String user;
    private int type;
    private int titleMaxLength;
    private boolean submitButtonLoading;
    private Boolean submitButtonEnabledState;
    private ValueAnimator submitButtonStateAnimator;
    private int submitEnabledBackgroundColor;
    private int submitDisabledBackgroundColor;
    private int submitEnabledContentColor;
    private int submitDisabledContentColor;
    private int currentSubmitBackgroundColor;
    private int currentSubmitContentColor;
    private int currentBottomInset;
    private int targetBottomInset;
    private int startBottomInset;
    private int endBottomInset;
    private boolean bottomInsetAnimationRunning;

    private OnBackPressedCallback backPressedCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ThemeUtils.setupTheme(this, false, false);

        ActivityComposeBinding binding = ActivityComposeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        editText = binding.composeEdittext;
        editTextTitle = binding.composeEdittextTitle;
        editTextUrl = binding.composeEdittextUrl;
        editTextText = binding.composeEdittextText;
        titleContainer = binding.composeTitleContainer;
        urlContainer = binding.composeUrlContainer;
        textContainer = binding.composeTextContainer;
        commentContainer = binding.composeCommentContainer;
        submitButton = binding.composeSubmit;
        submitLoadingIndicator = binding.composeSubmitLoading;
        replyingTextView = binding.composeReplyingText;
        replyingHeaderTextView = binding.composeReplyingTextHeader;
        replyingScrollView = binding.composeReplyingScrollview;
        topAppBar = binding.composeTopAppBar;
        topAppBar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        ViewCompat.setAccessibilityHeading(topAppBar, true);
        ViewCompat.setAccessibilityHeading(replyingHeaderTextView, true);
        LinearLayout bottomContainer = binding.composeBottomContainer;
        bottomInsetSpacer = binding.composeBottomInsetSpacer;
        composeContainer = binding.composeContainer;

        binding.composeFormatItalic.setOnClickListener(v -> applyItalicFormatting());
        binding.composeFormatCode.setOnClickListener(v -> applyCodeBlockFormatting());
        binding.composeFormattingInfo.setOnClickListener(this::infoClick);
        submitButton.setOnClickListener(this::submit);
        TooltipCompat.setTooltipText(binding.composeFormatItalic, "Italic");
        TooltipCompat.setTooltipText(binding.composeFormatCode, "Code block");
        TooltipCompat.setTooltipText(binding.composeFormattingInfo, "Information");
        TooltipCompat.setTooltipText(submitButton, "Submit");
        setupSubmitButtonColors();
        setupFloatingControlElevation(binding.composeBottomToolbar);

        titleMaxLength = getResources().getInteger(R.integer.title_max_length);

        Intent intent = getIntent();
        id = intent.getIntExtra(EXTRA_ID, -1);
        parentText = intent.getStringExtra(EXTRA_PARENT_TEXT);
        postTitle = intent.getStringExtra(EXTRA_POST_TITLE);
        user = intent.getStringExtra(EXTRA_USER);
        type = intent.getIntExtra(EXTRA_TYPE, TYPE_POST);


        if (type != TYPE_POST && id == -1) {
            Toast.makeText(this, "Invalid comment id", Toast.LENGTH_SHORT).show();
            finish();
        }

        switch (type) {
            case TYPE_TOP_COMMENT:
                replyingScrollView.setVisibility(View.GONE);
                configureTopAppBar("Top level comment", parentText);
                commentContainer.setHint("Comment");
                setCommentContainerTopMargin(TOP_COMMENT_FIELD_TOP_MARGIN_DP);
                setEditTextAccessibilityHint("Comment text");
                break;
            case TYPE_COMMENT_REPLY:
                replyingScrollView.setVisibility(View.VISIBLE);
                configureTopAppBar("Posting reply", postTitle);
                replyingHeaderTextView.setText("Replying to " + user + "'s comment:");
                commentContainer.setHint("Reply");
                setCommentContainerTopMargin(REPLY_FIELD_TOP_MARGIN_DP);
                setEditTextAccessibilityHint("Reply text");
                replyingTextView.setHtml(parentText);

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
                configureTopAppBar("New post", null);

                titleContainer.setVisibility(View.VISIBLE);
                urlContainer.setVisibility(View.VISIBLE);
                textContainer.setVisibility(View.VISIBLE);

                commentContainer.setVisibility(View.GONE);
                break;
        }
        updateReplyingPreviewHeight();

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

        ViewCompat.setOnApplyWindowInsetsListener(composeContainer, new OnApplyWindowInsetsListener() {
            @NonNull
            @Override
            public WindowInsetsCompat onApplyWindowInsets(@NonNull View v, @NonNull WindowInsetsCompat windowInsets) {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                Insets bottomInsets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime() | WindowInsetsCompat.Type.displayCutout());
                int sideMargin = getResources().getDimensionPixelSize(R.dimen.single_view_side_margin);

                targetBottomInset = bottomInsets.bottom;
                if (!bottomInsetAnimationRunning) {
                    setBottomInsetSpacerHeight(targetBottomInset);
                }
                composeContainer.setPadding(insets.left + sideMargin, insets.top, insets.right + sideMargin, 0);

                return windowInsets;
            }
        });
        ViewUtils.requestApplyInsetsWhenAttached(composeContainer);

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
                            int animatedBottomInset = Math.round(startBottomInset
                                    + (endBottomInset - startBottomInset) * imeAnimation.getInterpolatedFraction());
                            setBottomInsetSpacerHeight(animatedBottomInset);
                        }

                        return insets;
                    }

                    @Override
                    public void onPrepare(@NonNull WindowInsetsAnimationCompat animation) {
                        if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                            bottomInsetAnimationRunning = true;
                            startBottomInset = currentBottomInset;
                        }
                    }

                    @NonNull
                    @Override
                    public WindowInsetsAnimationCompat.BoundsCompat onStart(@NonNull WindowInsetsAnimationCompat animation, @NonNull WindowInsetsAnimationCompat.BoundsCompat bounds) {
                        if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                            endBottomInset = targetBottomInset;
                        }
                        return bounds;
                    }

                    @Override
                    public void onEnd(@NonNull WindowInsetsAnimationCompat animation) {
                        if ((animation.getTypeMask() & WindowInsetsCompat.Type.ime()) != 0) {
                            bottomInsetAnimationRunning = false;
                            setBottomInsetSpacerHeight(targetBottomInset);
                        }
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
        submitButton.post(this::updateEnabledStatuses);
    }

    private void setEditTextAccessibilityHint(String hint) {
        ViewCompat.setAccessibilityDelegate(editText, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(@NonNull View host, @NonNull AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setHintText(hint);
            }
        });
    }

    private void configureTopAppBar(@NonNull String title, @Nullable String subtitle) {
        topAppBar.setTitle(title);
        topAppBar.setSubtitle(TextUtils.isEmpty(subtitle) ? null : subtitle);
    }

    private void setCommentContainerTopMargin(int marginDp) {
        ViewGroup.LayoutParams layoutParams = commentContainer.getLayoutParams();
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
            marginLayoutParams.topMargin = Utils.pxFromDpInt(getResources(), marginDp);
            commentContainer.setLayoutParams(marginLayoutParams);
        }
    }

    private void updateReplyingPreviewHeight() {
        ViewGroup.LayoutParams layout = replyingScrollView.getLayoutParams();
        int maxPreviewHeight = Utils.pxFromDpInt(getResources(), 180);
        int minPreviewHeight = Utils.pxFromDpInt(getResources(), 112);
        int screenHeightThird = getResources().getDisplayMetrics().heightPixels / 3;
        layout.height = Math.max(minPreviewHeight, Math.min(maxPreviewHeight, screenHeightThird));
        replyingScrollView.setLayoutParams(layout);
    }

    private void setBottomInsetSpacerHeight(int bottomInset) {
        currentBottomInset = bottomInset;
        ViewGroup.LayoutParams layoutParams = bottomInsetSpacer.getLayoutParams();
        if (layoutParams.height == bottomInset) {
            return;
        }

        layoutParams.height = bottomInset;
        bottomInsetSpacer.setLayoutParams(layoutParams);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        updateReplyingPreviewHeight();
        if (composeContainer != null) {
            ViewCompat.requestApplyInsets(composeContainer);
        }
    }

    public void infoClick(View view) {
        StringBuilder message = new StringBuilder();
        if (type == TYPE_POST) {
            message.append("Leave URL blank to submit a question for discussion. If there is no URL, text will appear at the top of the thread. If there is a URL, text is optional.\n\n");
        }
        message.append("Blank lines separate paragraphs.\n\n")
                .append("Text surrounded by asterisks is italicized, if the character after the first asterisk isn't whitespace.\n\n")
                .append("Text after a blank line that is indented by two or more spaces is reproduced verbatim. (This is intended for code.)\n\n")
                .append("URLs become links, except in the text field of a submission.");

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Information")
                .setMessage(message)
                .setNegativeButton("Dismiss", null).create();

        dialog.show();
    }

    private void applyItalicFormatting() {
        EditText target = getFormattingEditText();
        Editable text = target.getText();
        if (text == null) {
            return;
        }

        target.requestFocus();
        int start = Math.max(0, target.getSelectionStart());
        int end = Math.max(0, target.getSelectionEnd());
        if (start > end) {
            int previousStart = start;
            start = end;
            end = previousStart;
        }

        if (start == end) {
            text.insert(start, "**");
            target.setSelection(start + 1);
        } else {
            text.insert(end, "*");
            text.insert(start, "*");
            target.setSelection(start + 1, end + 1);
        }
    }

    private void applyCodeBlockFormatting() {
        EditText target = getFormattingEditText();
        Editable text = target.getText();
        if (text == null) {
            return;
        }

        target.requestFocus();
        int start = Math.max(0, target.getSelectionStart());
        int end = Math.max(0, target.getSelectionEnd());
        if (start > end) {
            int previousStart = start;
            start = end;
            end = previousStart;
        }

        if (start == end) {
            String prefix = getCodeBlockPrefix(text, start);
            String suffix = getCodeBlockSuffix(text, start);
            String snippet = prefix + "  code" + suffix;
            text.insert(start, snippet);
            target.setSelection(start + prefix.length() + 2, start + prefix.length() + 6);
            return;
        }

        int lineStart = findLineStart(text, start);
        int lineEnd = findLineEnd(text, end);
        String prefix = getCodeBlockPrefix(text, lineStart);
        String indented = indentAsCode(text.subSequence(lineStart, lineEnd));
        String suffix = getCodeBlockSuffix(text, lineEnd);
        text.replace(lineStart, lineEnd, prefix + indented + suffix);

        int selectionStart = lineStart + prefix.length();
        target.setSelection(selectionStart, selectionStart + indented.length());
    }

    private EditText getFormattingEditText() {
        if (type == TYPE_POST) {
            return editTextText;
        }

        return editText;
    }

    private int findLineStart(@NonNull Editable text, int position) {
        int lineStart = Math.min(position, text.length());
        while (lineStart > 0 && text.charAt(lineStart - 1) != '\n') {
            lineStart--;
        }
        return lineStart;
    }

    private int findLineEnd(@NonNull Editable text, int position) {
        int lineEnd = Math.min(position, text.length());
        while (lineEnd < text.length() && text.charAt(lineEnd) != '\n') {
            lineEnd++;
        }
        return lineEnd;
    }

    private String getCodeBlockPrefix(@NonNull Editable text, int position) {
        if (position == 0) {
            return "";
        }

        int newlinesBefore = countNewlinesBefore(text, position);
        if (newlinesBefore >= 2) {
            return "";
        } else if (newlinesBefore == 1) {
            return "\n";
        } else {
            return "\n\n";
        }
    }

    private String getCodeBlockSuffix(@NonNull Editable text, int position) {
        if (position >= text.length()) {
            return "";
        }

        int newlinesAfter = countNewlinesAfter(text, position);
        if (newlinesAfter >= 2) {
            return "";
        } else if (newlinesAfter == 1) {
            return "\n";
        } else {
            return "\n\n";
        }
    }

    private int countNewlinesBefore(@NonNull Editable text, int position) {
        int count = 0;
        for (int i = position - 1; i >= 0 && text.charAt(i) == '\n'; i--) {
            count++;
        }
        return count;
    }

    private int countNewlinesAfter(@NonNull Editable text, int position) {
        int count = 0;
        for (int i = position; i < text.length() && text.charAt(i) == '\n'; i++) {
            count++;
        }
        return count;
    }

    private String indentAsCode(@NonNull CharSequence text) {
        String[] lines = text.toString().split("\n", -1);
        StringBuilder builder = new StringBuilder(text.length() + lines.length * 2);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append("  ").append(lines[i]);
        }
        return builder.toString();
    }

    private void setupSubmitButtonColors() {
        submitEnabledBackgroundColor = MaterialColors.getColor(submitButton, androidx.appcompat.R.attr.colorAccent);
        submitEnabledContentColor = MaterialColors.getColor(submitButton, com.google.android.material.R.attr.colorOnPrimary);

        int disabledBackgroundTarget = ThemeUtils.isDarkMode(this)
                ? android.graphics.Color.BLACK
                : android.graphics.Color.WHITE;
        submitDisabledBackgroundColor = blendColors(submitEnabledBackgroundColor, disabledBackgroundTarget, 0.72f);
        submitDisabledContentColor = submitEnabledBackgroundColor;

        currentSubmitBackgroundColor = submitDisabledBackgroundColor;
        currentSubmitContentColor = submitDisabledContentColor;
        setSubmitButtonColors(currentSubmitBackgroundColor, currentSubmitContentColor);
    }

    private void setupFloatingControlElevation(@NonNull View bottomToolbar) {
        float elevation = Utils.pxFromDpInt(getResources(), FLOATING_CONTROL_ELEVATION_DP);
        bottomToolbar.setStateListAnimator(null);
        bottomToolbar.setElevation(elevation);
        bottomToolbar.setTranslationZ(0f);

        submitButton.setElevation(elevation);
        submitButton.setStateListAnimator(null);
        submitButton.setTranslationZ(0f);
        submitButton.setOnHoverListener((view, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_HOVER_ENTER:
                case MotionEvent.ACTION_HOVER_MOVE:
                    if (Boolean.TRUE.equals(submitButtonEnabledState) && !submitButtonLoading) {
                        view.setTranslationZ(Utils.pxFromDpInt(getResources(), FAB_HOVER_TRANSLATION_Z_DP));
                    }
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                default:
                    view.setTranslationZ(0f);
                    break;
            }
            return false;
        });
    }

    private void applySubmitButtonElevation() {
        submitButton.setElevation(Utils.pxFromDpInt(getResources(), FLOATING_CONTROL_ELEVATION_DP));
        submitButton.setTranslationZ(0f);
    }

    private void setSubmitButtonEnabledState(boolean enabled, boolean animate) {
        if (submitButtonLoading) {
            submitButton.setEnabled(true);
            submitButton.setClickable(false);
            return;
        }

        boolean changed = submitButtonEnabledState == null || submitButtonEnabledState != enabled;
        submitButtonEnabledState = enabled;
        submitButton.setEnabled(true);
        submitButton.setClickable(enabled);
        applySubmitButtonElevation();
        if (!changed) {
            int targetBackground = enabled ? submitEnabledBackgroundColor : submitDisabledBackgroundColor;
            int targetContent = enabled ? submitEnabledContentColor : submitDisabledContentColor;
            setSubmitButtonColors(targetBackground, targetContent);
            return;
        }

        int targetBackground = enabled ? submitEnabledBackgroundColor : submitDisabledBackgroundColor;
        int targetContent = enabled ? submitEnabledContentColor : submitDisabledContentColor;

        if (submitButtonStateAnimator != null) {
            submitButtonStateAnimator.cancel();
        }

        if (!animate) {
            setSubmitButtonColors(targetBackground, targetContent);
            return;
        }

        final int startBackground = currentSubmitBackgroundColor;
        final int startContent = currentSubmitContentColor;
        submitButtonStateAnimator = ValueAnimator.ofFloat(0f, 1f);
        submitButtonStateAnimator.setDuration(180);
        submitButtonStateAnimator.addUpdateListener(animation -> {
            float progress = (float) animation.getAnimatedValue();
            setSubmitButtonColors(
                    blendColors(startBackground, targetBackground, progress),
                    blendColors(startContent, targetContent, progress));
        });
        submitButtonStateAnimator.start();
    }

    private void setSubmitButtonColors(int backgroundColor, int contentColor) {
        currentSubmitBackgroundColor = backgroundColor;
        currentSubmitContentColor = contentColor;
        ColorStateList contentColors = ColorStateList.valueOf(contentColor);
        submitButton.setSupportBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        submitButton.setSupportImageTintList(contentColors);
        submitButton.refreshDrawableState();
        submitButton.invalidate();
    }

    private int blendColors(int from, int to, float progress) {
        float clampedProgress = Math.max(0f, Math.min(1f, progress));
        int fromA = (from >> 24) & 0xff;
        int fromR = (from >> 16) & 0xff;
        int fromG = (from >> 8) & 0xff;
        int fromB = from & 0xff;
        int toA = (to >> 24) & 0xff;
        int toR = (to >> 16) & 0xff;
        int toG = (to >> 8) & 0xff;
        int toB = to & 0xff;

        int a = Math.round(fromA + (toA - fromA) * clampedProgress);
        int r = Math.round(fromR + (toR - fromR) * clampedProgress);
        int g = Math.round(fromG + (toG - fromG) * clampedProgress);
        int b = Math.round(fromB + (toB - fromB) * clampedProgress);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void setSubmitLoading(boolean loading) {
        if (submitButtonLoading == loading) {
            return;
        }

        submitButtonLoading = loading;
        submitButton.animate().cancel();
        submitLoadingIndicator.animate().cancel();

        if (loading) {
            submitButton.setEnabled(true);
            submitButton.setClickable(false);
            applySubmitButtonElevation();
            submitButton.animate()
                    .alpha(0f)
                    .setDuration(120)
                    .withEndAction(() -> submitButton.setVisibility(View.INVISIBLE))
                    .start();

            submitLoadingIndicator.setAlpha(0f);
            submitLoadingIndicator.setVisibility(View.VISIBLE);
            submitLoadingIndicator.animate()
                    .alpha(1f)
                    .setDuration(160)
                    .start();
            return;
        }

        submitLoadingIndicator.animate()
                .alpha(0f)
                .setDuration(120)
                .withEndAction(() -> submitLoadingIndicator.setVisibility(View.GONE))
                .start();

        submitButton.setVisibility(View.VISIBLE);
        submitButton.setAlpha(0f);
        submitButton.animate()
                .alpha(1f)
                .setDuration(160)
                .start();
        updateEnabledStatuses();
    }

    private void updateEnabledStatuses() {
        final boolean enable;

        if (type == TYPE_POST) {
            final String title = editTextTitle.getText().toString();
            final boolean titleTooLong = title.length() > titleMaxLength;

            if (titleTooLong) {
                titleContainer.setError("Title must be " + titleMaxLength + " characters or less");
            } else {
                titleContainer.setError(null);
            }

            final boolean validTitle = !TextUtils.isEmpty(title) && !titleTooLong;
            final boolean hasUrl = !TextUtils.isEmpty(editTextUrl.getText().toString());
            final boolean hasText = !TextUtils.isEmpty(editTextText.getText().toString());

            enable = validTitle && (hasText || hasUrl);
        } else {
            enable = !TextUtils.isEmpty(editText.getText().toString());
        }

        backPressedCallback.setEnabled(enable);
        setSubmitButtonEnabledState(enable, submitButtonEnabledState != null);
    }

    public void submit(View view) {
        if (submitButtonLoading || !Boolean.TRUE.equals(submitButtonEnabledState)) {
            return;
        }

        if (!Utils.isNetworkAvailable(this)) {
            showSubmissionFailure(null, null, getCommentDraft());
            return;
        }

        setSubmitLoading(true);

        if (type == TYPE_POST) {
            final String postTitle = editTextTitle.getText().toString();
            final String postText = editTextText.getText().toString();
            final String postUrl = editTextUrl.getText().toString();

            UserActions.submit(postTitle, postText, postUrl, view.getContext(), new UserActions.ActionCallback() {
                @Override
                public void onSuccess(Response response) {
                    Toast.makeText(view.getContext(), "Post submitted, it might take a minute to show up", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onFailure(String summary, String response) {
                    resetSubmitButton();
                    showSubmissionFailure(summary, response, null);
                }

                @Override
                public void onCaptchaRequired(UserActions.CaptchaChallenge challenge) {
                    UserActions.ActionCallback callback = this;
                    CaptchaDialogFragment.show(getSupportFragmentManager(), challenge, new CaptchaDialogFragment.Listener() {
                        @Override
                        public void onCaptchaResponse(UserActions.CaptchaChallenge challenge, String captchaResponse) {
                            if (challenge.isLoginChallenge()) {
                                UserActions.submitAfterLoginCaptcha(postTitle, postText, postUrl, ComposeActivity.this, challenge, captchaResponse, callback);
                            } else {
                                UserActions.continueCaptchaAction(ComposeActivity.this, challenge, captchaResponse, callback);
                            }
                        }

                        @Override
                        public void onCaptchaCancelled() {
                            resetSubmitButton();
                            Toast.makeText(view.getContext(), "Post submission requires completing the HN captcha", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        } else {
            final String commentText = editText.getText().toString();

            UserActions.comment(String.valueOf(id), commentText, view.getContext(), new UserActions.ActionCallback() {
                @Override
                public void onSuccess(Response response) {
                    Toast.makeText(view.getContext(), "Comment posted, it might take a minute to show up", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onFailure(String summary, String response) {
                    resetSubmitButton();
                    showSubmissionFailure(summary, response, commentText);
                }

                @Override
                public void onCaptchaRequired(UserActions.CaptchaChallenge challenge) {
                    UserActions.ActionCallback callback = this;
                    CaptchaDialogFragment.show(getSupportFragmentManager(), challenge, new CaptchaDialogFragment.Listener() {
                        @Override
                        public void onCaptchaResponse(UserActions.CaptchaChallenge challenge, String captchaResponse) {
                            UserActions.continueCaptchaAction(ComposeActivity.this, challenge, captchaResponse, callback);
                        }

                        @Override
                        public void onCaptchaCancelled() {
                            resetSubmitButton();
                            Toast.makeText(view.getContext(), "Comment posting requires completing the HN captcha", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });
        }
    }

    @Nullable
    private String getCommentDraft() {
        return type == TYPE_POST ? null : editText.getText().toString();
    }

    private void showSubmissionFailure(@Nullable String summary,
                                       @Nullable String response,
                                       @Nullable String commentDraft) {
        final boolean isPost = type == TYPE_POST;
        final String draftName = isPost ? "post" : "comment";
        final String title;
        final String message;

        if (!Utils.isNetworkAvailable(this)) {
            title = "No internet connection";
            message = "Connect to the internet, then try again. Your " + draftName
                    + " is still here.";
        } else {
            title = isPost ? "Couldn't submit post" : "Couldn't post comment";
            StringBuilder messageBuilder = new StringBuilder();
            if (!TextUtils.isEmpty(summary)) {
                messageBuilder.append(summary);
            }
            if (!TextUtils.isEmpty(response)) {
                if (messageBuilder.length() > 0) {
                    messageBuilder.append("\n\n");
                }
                messageBuilder.append(response);
            }
            if (messageBuilder.length() > 0) {
                messageBuilder.append("\n\n");
            }
            messageBuilder.append("Your ").append(draftName)
                    .append(" is still here, so you can edit it or try again.");
            message = messageBuilder.toString();
        }

        UserActions.showFailureDetailDialog(this, title, message, commentDraft);
    }

    private void resetSubmitButton() {
        setSubmitLoading(false);
    }
}
