package com.simon.harmonichackernews;

import static android.view.View.GONE;
import static com.simon.harmonichackernews.SubmissionsActivity.KEY_USER;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialogFragment;
import androidx.fragment.app.FragmentManager;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.loadingindicator.LoadingIndicator;
import com.simon.harmonichackernews.network.NetworkComponent;
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

    private TextView nameTextview;
    private TextView metaTextview;
    private HtmlTextView aboutTextview;
    private Button submissionsButton;
    private Button tagButton;
    private Button blockButton;
    private Button reportButton;
    private LoadingIndicator loadingProgress;
    private LinearLayout errorLayout;
    private LinearLayout container;
    private UserDialogCallback setTagCallback;

    public void setCallback(UserDialogCallback callback) {
        this.setTagCallback = callback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View rootView = inflater.inflate(R.layout.user_dialog, null);
        builder.setView(rootView);
        AlertDialog dialog = builder.create();

        RequestQueue queue = NetworkComponent.getRequestQueueInstance(requireContext());

        nameTextview = rootView.findViewById(R.id.user_name);
        metaTextview = rootView.findViewById(R.id.user_meta);
        aboutTextview = rootView.findViewById(R.id.user_about);
        submissionsButton = rootView.findViewById(R.id.user_submissions_button);
        tagButton = rootView.findViewById(R.id.user_tag_button);
        reportButton = rootView.findViewById(R.id.user_report_button);
        blockButton = rootView.findViewById(R.id.user_block_button);
        loadingProgress = rootView.findViewById(R.id.user_loading);
        errorLayout = rootView.findViewById(R.id.user_error);
        Button retryButton = rootView.findViewById(R.id.user_retry);
        container = rootView.findViewById(R.id.user_container);

        Bundle bundle = getArguments();
        final String userName = (bundle != null && !TextUtils.isEmpty(bundle.getString(EXTRA_USER_NAME))) ? bundle.getString(EXTRA_USER_NAME) : null;

        if (userName != null) {
            // lets create a request and fill in the data when we have it
            String url = "https://hacker-news.firebaseio.com/v0/user/" + userName + ".json";

            StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                    response -> {
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

                            metaTextview.setText("Karma: " + Utils.getThousandSeparatedString(karma) + " • Created: " + month + " " + cal.get(Calendar.DAY_OF_MONTH) + ", " + (cal.get(Calendar.YEAR)));

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
                            tagButton.setOnClickListener(v -> showTagDialog(userName, currentTag));

                            if (userName.equals(AccountUtils.getAccountUsername(getContext()))) {
                                reportButton.setVisibility(GONE);
                                blockButton.setVisibility(GONE);
                                tagButton.setVisibility(GONE);
                            } else {
                                reportButton.setVisibility(View.VISIBLE);
                                blockButton.setVisibility(View.VISIBLE);
                                tagButton.setVisibility(View.VISIBLE);
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
                                    if (Utils.addFilteredUser(getContext(), userName)) {
                                        Toast.makeText(getContext(), "You will no longer see comments from " + userName, Toast.LENGTH_SHORT).show();
                                        dialog.dismiss();
                                    }
                                }
                            });

                            container.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            loadingProgress.setVisibility(GONE);
                            errorLayout.setVisibility(View.VISIBLE);
                            container.setVisibility(GONE);

                            e.printStackTrace();
                        }

                        loadingProgress.setVisibility(GONE);
                    }, error -> {
                error.printStackTrace();
                loadingProgress.setVisibility(GONE);
                errorLayout.setVisibility(View.VISIBLE);
                container.setVisibility(GONE);
            });

            stringRequest.setRetryPolicy(new DefaultRetryPolicy(
                    15000,
                    DefaultRetryPolicy.DEFAULT_MAX_RETRIES,
                    DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));

            queue.add(stringRequest);
        }

        retryButton.setOnClickListener(view1 -> {
            dialog.dismiss();
            showUserDialog(requireActivity().getSupportFragmentManager(), userName);
        });

        return dialog;
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
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.tag_dialog, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();

        TextInputEditText editText = view.findViewById(R.id.tag_dialog_edittext);
        Button cancel = view.findViewById(R.id.tag_dialog_cancel);
        Button save = view.findViewById(R.id.tag_dialog_save);

        editText.setText(currentTag);

        cancel.setOnClickListener(v -> dialog.dismiss());
        save.setOnClickListener(v -> {
            String tag = editText.getText() != null ? editText.getText().toString().trim() : "";
            Utils.setUserTag(getContext(), userName, tag);
            if (setTagCallback != null) {
                setTagCallback.onResult(true);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

}