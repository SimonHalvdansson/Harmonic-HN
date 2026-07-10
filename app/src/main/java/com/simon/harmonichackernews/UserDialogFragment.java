package com.simon.harmonichackernews;

import static android.view.View.GONE;
import static com.simon.harmonichackernews.SubmissionsActivity.KEY_USER;

import android.app.Dialog;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentManager;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.simon.harmonichackernews.databinding.UserDialogBinding;
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.network.RepliesChecker;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.Calendar;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserDialogFragment extends AppCompatDialogFragment {

    public static String TAG = "tag_user_dialog";
    public final static String EXTRA_USER_NAME = "com.simon.harmonichackernews.EXTRA_USER_NAME";
    private static final long USER_CONTENT_ENTER_DURATION_MS = 280L;
    private static final long USER_LOADING_EXIT_DURATION_MS = 150L;
    private static final PathInterpolator EMPHASIZED_DECELERATE = new PathInterpolator(0.2f, 0f, 0f, 1f);

    private TextView nameTextview;
    private TextView metaTextview;
    private HtmlTextView aboutTextview;
    private Button submissionsButton;
    private Button tagButton;
    private Button blockButton;
    private Button reportButton;
    private MaterialButton notificationsButton;
    private TextView notificationsExplanation;
    private LinearProgressIndicator notificationsLoading;
    private TextView notificationsStatus;
    private ShimmerFrameLayout loadingProgress;
    private LinearLayout errorLayout;
    private LinearLayout container;
    private LinearLayout detailsContainer;
    private RequestQueue queue;
    private final Object requestTag = new Object();
    private UserDialogCallback setTagCallback;
    private ActivityResultLauncher<String> notificationPermissionLauncher;
    private String pendingNotificationUsername;
    private boolean notificationActionLoading;

    public void setCallback(UserDialogCallback callback) {
        this.setTagCallback = callback;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!isAdded()) {
                        pendingNotificationUsername = null;
                        return;
                    }
                    if (granted && !TextUtils.isEmpty(pendingNotificationUsername)) {
                        activateNotifications(pendingNotificationUsername);
                    } else {
                        setNotificationsStatus("Notification permission denied.");
                    }
                    pendingNotificationUsername = null;
                }
        );
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        UserDialogBinding binding = UserDialogBinding.inflate(requireActivity().getLayoutInflater());
        builder.setView(binding.getRoot());
        AlertDialog dialog = builder.create();

        queue = NetworkComponent.getRequestQueueInstance(requireContext());

        nameTextview = binding.userName;
        metaTextview = binding.userMeta;
        aboutTextview = binding.userAbout;
        submissionsButton = binding.userSubmissionsButton;
        tagButton = binding.userTagButton;
        reportButton = binding.userReportButton;
        blockButton = binding.userBlockButton;
        notificationsButton = binding.userNotificationsButton;
        notificationsExplanation = binding.userNotificationsExplanation;
        notificationsLoading = binding.userNotificationsLoading;
        notificationsStatus = binding.userNotificationsStatus;
        loadingProgress = binding.userLoading;
        errorLayout = binding.userError;
        Button retryButton = binding.userRetry;
        container = binding.userContainer;
        detailsContainer = binding.userDetails;

        Bundle bundle = getArguments();
        final String userName = (bundle != null && !TextUtils.isEmpty(bundle.getString(EXTRA_USER_NAME))) ? bundle.getString(EXTRA_USER_NAME) : null;

        if (userName != null) {
            nameTextview.setText(userName);
            // lets create a request and fill in the data when we have it
            String url = "https://hacker-news.firebaseio.com/v0/user/" + userName + ".json";

            StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                    response -> {
                        if (!hasBoundViews()) {
                            return;
                        }
                        try {
                            // lets try to parse the response
                            JSONObject jsonObject = new JSONObject(response);
                            nameTextview.setText(jsonObject.getString("id"));
                            int karma = jsonObject.getInt("karma");
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(new Date(jsonObject.getInt("created") * 1000L));

                            // Users who have never submitted before do not receive this key as part of their response.
                            JSONArray submitted;
                            if (jsonObject.has("submitted")) {
                                submitted = jsonObject.getJSONArray("submitted");
                            } else {
                                submitted = new JSONArray();
                                jsonObject.put("submitted", submitted);
                            }

                            if (submitted.length() == 0) {
                                submissionsButton.setVisibility(GONE);
                            } else {
                                submissionsButton.setText("Submissions");
                                submissionsButton.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        Intent submissionsIntent = new Intent(view.getContext(), SubmissionsActivity.class);
                                        try {
                                            submissionsIntent.putExtra(KEY_USER, jsonObject.getString("id"));
                                        } catch (JSONException e) {
                                            e.printStackTrace();
                                        }
                                        startActivity(submissionsIntent);
                                    }
                                });
                            }

                            String month = getResources().getStringArray(R.array.months)[cal.get(Calendar.MONTH)];

                            metaTextview.setText(Utils.getThousandSeparatedString(karma) + " karma since " + month + " " + cal.get(Calendar.DAY_OF_MONTH) + ", " + (cal.get(Calendar.YEAR)));

                            if (jsonObject.has("about") && !TextUtils.isEmpty(jsonObject.getString("about"))) {
                                setLinkifiedText(Html.fromHtml(jsonObject.getString("about")).toString().trim(), aboutTextview);
                            } else {
                                aboutTextview.setVisibility(GONE);
                            }

                            aboutTextview.setOnClickATagListener(new OnClickATagListener() {
                                    @Override
                                    public boolean onClick(View widget, String spannedText, @Nullable String href) {
                                        Utils.openLinkMaybeHN(widget.getContext(), href);
                                        return true;
                                }
                            });

                            String currentTag = Utils.getUserTag(getContext(), userName);
                            tagButton.setOnClickListener(v -> showTagDialog(userName, Utils.getUserTag(getContext(), userName)));

                            tagButton.setText("Set tag" + (TextUtils.isEmpty(currentTag) ? "" : " (" + currentTag + ")"));

                            if (isOwnProfile(userName)) {
                                reportButton.setVisibility(GONE);
                                blockButton.setVisibility(GONE);
                                tagButton.setVisibility(GONE);
                                setupNotificationButton(userName);
                            } else {
                                boolean isBlocked = Utils.getFilteredUsers(getContext()).contains(userName);
                                blockButton.setText(isBlocked ? "Unblock" : "Block");

                                reportButton.setVisibility(View.VISIBLE);
                                blockButton.setVisibility(View.VISIBLE);
                                tagButton.setVisibility(View.VISIBLE);
                                notificationsButton.setVisibility(GONE);
                                notificationsExplanation.setVisibility(GONE);
                                notificationsLoading.setVisibility(GONE);
                                notificationsStatus.setVisibility(GONE);
                            }




                            reportButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    String subject = "Reporting user " + userName;
                                    String uriText = "mailto:hn@ycombinator.com" +
                                            "?subject=" + Uri.encode(subject);
                                    Uri mailUri = Uri.parse(uriText);
                                    Intent intent = new Intent(Intent.ACTION_SENDTO, mailUri);

                                    if (intent.resolveActivity(getContext().getPackageManager()) != null) {
                                        Intent chooser = Intent.createChooser(intent, "Send report via");
                                        getContext().startActivity(chooser);
                                    }
                                }
                            });

                            blockButton.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    boolean isBlocked = Utils.getFilteredUsers(getContext()).contains(userName);

                                    if (isBlocked) {
                                        if (Utils.removeFilteredUser(getContext(), userName)) {
                                            Toast.makeText(getContext(), "Unblocked " + userName, Toast.LENGTH_SHORT).show();
                                            if (blockButton != null) {
                                                blockButton.setText("Block");
                                            }
                                        }
                                    } else {
                                        if (Utils.addFilteredUser(getContext(), userName)) {
                                            Toast.makeText(getContext(), "You will no longer see posts or comments from " + userName, Toast.LENGTH_SHORT).show();
                                            dialog.dismiss();
                                        }
                                    }
                                }
                            });

                            showLoadedContent();
                        } catch (Exception e) {
                            showErrorState();

                            e.printStackTrace();
                        }
                    }, error -> {
                error.printStackTrace();
                showErrorState();
            });

            stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                    15000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            stringRequest.setTag(requestTag);
            queue.add(stringRequest);
        }

        retryButton.setOnClickListener(view1 -> {
            dialog.dismiss();
            showUserDialog(requireActivity().getSupportFragmentManager(), userName);
        });

        return dialog;
    }

    @Override
    public void onDestroyView() {
        if (loadingProgress != null) {
            loadingProgress.animate().cancel();
            loadingProgress.stopShimmer();
        }

        if (container != null) {
            container.animate().cancel();
        }
        if (detailsContainer != null) {
            detailsContainer.animate().cancel();
        }
        if (queue != null) {
            queue.cancelAll(requestTag);
            queue = null;
        }
        if (submissionsButton != null) {
            submissionsButton.setOnClickListener(null);
        }
        if (tagButton != null) {
            tagButton.setOnClickListener(null);
        }
        if (reportButton != null) {
            reportButton.setOnClickListener(null);
        }
        if (blockButton != null) {
            blockButton.setOnClickListener(null);
        }
        if (notificationsButton != null) {
            notificationsButton.setOnClickListener(null);
        }

        nameTextview = null;
        metaTextview = null;
        aboutTextview = null;
        submissionsButton = null;
        tagButton = null;
        reportButton = null;
        blockButton = null;
        notificationsButton = null;
        notificationsExplanation = null;
        notificationsLoading = null;
        notificationsStatus = null;
        loadingProgress = null;
        errorLayout = null;
        container = null;
        detailsContainer = null;

        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        setTagCallback = null;
        super.onDestroy();
    }

    private boolean isOwnProfile(String userName) {
        return !TextUtils.isEmpty(userName)
                && !TextUtils.isEmpty(AccountUtils.getAccountUsername(getContext()))
                && userName.equalsIgnoreCase(AccountUtils.getAccountUsername(getContext()));
    }

    private boolean hasBoundViews() {
        return isAdded()
                && nameTextview != null
                && metaTextview != null
                && aboutTextview != null
                && submissionsButton != null
                && tagButton != null
                && reportButton != null
                && blockButton != null
                && notificationsButton != null
                && notificationsExplanation != null
                && notificationsLoading != null
                && notificationsStatus != null
                && loadingProgress != null
                && errorLayout != null
                && container != null
                && detailsContainer != null;
    }

    private void setupNotificationButton(String userName) {
        notificationsButton.setVisibility(View.VISIBLE);
        notificationsExplanation.setVisibility(View.VISIBLE);
        notificationsButton.setOnClickListener(v -> {
            if (notificationsActiveForUser(userName)) {
                RepliesChecker.disable(requireContext());
                notificationActionLoading = false;
                setNotificationsStatus("");
                updateNotificationsButton(userName);
            } else {
                maybeActivateNotifications(userName);
            }
        });
        updateNotificationsButton(userName);
    }

    private void maybeActivateNotifications(String userName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingNotificationUsername = userName;
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
            return;
        }

        activateNotifications(userName);
    }

    private void activateNotifications(String userName) {
        notificationActionLoading = true;
        setNotificationsStatus("");
        updateNotificationsButton(userName);
        RepliesChecker.enable(requireContext(), userName, success -> {
            if (getContext() == null) {
                return;
            }
            notificationActionLoading = false;
            if (success) {
                setNotificationsStatus("");
            } else {
                setNotificationsStatus("Could not activate reply notifications.");
            }
            updateNotificationsButton(userName);
        });
    }

    private boolean notificationsActiveForUser(String userName) {
        return !TextUtils.isEmpty(userName)
                && userName.equalsIgnoreCase(RepliesChecker.getConfiguredUsername(requireContext()));
    }

    private void updateNotificationsButton(String userName) {
        if (notificationsButton == null || notificationsLoading == null) {
            return;
        }

        boolean active = notificationsActiveForUser(userName);
        notificationsButton.setText(active ? "Deactivate notifications" : "Activate notifications");
        notificationsButton.setEnabled(!notificationActionLoading);
        notificationsLoading.setVisibility(notificationActionLoading ? View.VISIBLE : GONE);
    }

    private void setNotificationsStatus(String status) {
        if (notificationsStatus == null) {
            return;
        }

        if (TextUtils.isEmpty(status)) {
            notificationsStatus.setVisibility(GONE);
            notificationsStatus.setText("");
        } else {
            notificationsStatus.setVisibility(View.VISIBLE);
            notificationsStatus.setText(status);
        }
    }

    private void showLoadedContent() {
        if (!isAdded()) {
            return;
        }

        if (loadingProgress != null) {
            loadingProgress.animate().cancel();
            loadingProgress.animate()
                    .alpha(0f)
                    .scaleX(0.92f)
                    .scaleY(0.92f)
                    .setDuration(USER_LOADING_EXIT_DURATION_MS)
                    .setInterpolator(EMPHASIZED_DECELERATE)
                    .withEndAction(() -> {
                        if (!isAdded()) {
                            return;
                        }

                        if (loadingProgress != null) {
                            loadingProgress.stopShimmer();
                            loadingProgress.setVisibility(GONE);
                            loadingProgress.setAlpha(1f);
                            loadingProgress.setScaleX(1f);
                            loadingProgress.setScaleY(1f);
                        }
                        animateLoadedContentIn();
                    })
                    .start();
        } else {
            animateLoadedContentIn();
        }
    }

    private void animateLoadedContentIn() {
        if (!isAdded()) {
            return;
        }

        if (errorLayout != null) {
            errorLayout.setVisibility(GONE);
        }

        if (container == null || detailsContainer == null) {
            return;
        }

        container.setVisibility(View.VISIBLE);
        detailsContainer.animate().cancel();
        detailsContainer.setVisibility(View.VISIBLE);
        detailsContainer.setAlpha(0f);
        detailsContainer.setTranslationY(Utils.pxFromDpInt(detailsContainer.getResources(), 4));
        detailsContainer.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(USER_CONTENT_ENTER_DURATION_MS)
                .setInterpolator(EMPHASIZED_DECELERATE)
                .start();
    }

    private void showErrorState() {
        if (loadingProgress != null) {
            loadingProgress.animate().cancel();
            loadingProgress.stopShimmer();
            loadingProgress.setVisibility(GONE);
            loadingProgress.setAlpha(1f);
            loadingProgress.setScaleX(1f);
            loadingProgress.setScaleY(1f);
        }

        if (container != null) {
            container.animate().cancel();
            container.setVisibility(GONE);
            container.setAlpha(1f);
            container.setTranslationY(0f);
            container.setScaleX(1f);
            container.setScaleY(1f);
        }

        if (detailsContainer != null) {
            detailsContainer.animate().cancel();
            detailsContainer.setVisibility(GONE);
            detailsContainer.setAlpha(1f);
            detailsContainer.setTranslationY(0f);
        }

        if (errorLayout != null) {
            errorLayout.setVisibility(View.VISIBLE);
        }
    }

    public interface UserDialogCallback {
        void onResult(boolean accepted);
    }

    public static void showUserDialog(FragmentManager fm, String name, UserDialogCallback callback) {
        UserDialogFragment userDialogFragment = new UserDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString(UserDialogFragment.EXTRA_USER_NAME, name);
        userDialogFragment.setArguments(bundle);
        userDialogFragment.show(fm, UserDialogFragment.TAG);

        if (callback != null) {
            userDialogFragment.setCallback(callback);
        }
    }

    public static void showUserDialog(FragmentManager fm, String name) {
        showUserDialog(fm, name, null);
    }

    public void setLinkifiedText(String text, TextView textView) {
        SpannableString spannableString = new SpannableString(text);

        Pattern urlPattern = Pattern.compile("(https?://\\S+)");
        Matcher urlMatcher = urlPattern.matcher(text);

        while (urlMatcher.find()) {
            String url = urlMatcher.group(1);

            spannableString.setSpan(new ClickableSpan() {
                @Override
                public void onClick(@NonNull View widget) {
                    Utils.openLinkMaybeHN(widget.getContext(), url);

                }
            }, urlMatcher.start(1), urlMatcher.end(1), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        textView.setMovementMethod(LinkMovementMethod.getInstance());

        textView.setText(spannableString);
    }

    private void showTagDialog(String userName, String currentTag) {
        UserTagDialogFragment.show(getParentFragmentManager(), userName, currentTag, tag -> {
            if (tagButton != null) {
                tagButton.setText("Set tag" + (TextUtils.isEmpty(tag) ? "" : " (" + tag + ")"));
            }

            if (setTagCallback != null) {
                setTagCallback.onResult(true);
            }
        });
    }

}
