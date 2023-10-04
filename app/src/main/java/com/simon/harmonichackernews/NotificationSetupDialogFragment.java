package com.simon.harmonichackernews;

import static com.simon.harmonichackernews.SubmissionsActivity.KEY_USER;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import com.simon.harmonichackernews.network.NetworkComponent;
import com.simon.harmonichackernews.utils.Utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.sufficientlysecure.htmltextview.HtmlTextView;
import org.sufficientlysecure.htmltextview.OnClickATagListener;

import java.util.Calendar;
import java.util.Date;

public class NotificationSetupDialogFragment extends AppCompatDialogFragment {

    public static String TAG = "tag_user_dialog";
    public final static String EXTRA_USER_NAME = "com.simon.harmonichackernews.EXTRA_USER_NAME";

    private TextView nameTextview;
    private TextView metaTextview;
    private HtmlTextView aboutTextview;
    private Button submissionsButton;
    private ProgressBar loadingProgress;
    private LinearLayout errorLayout;
    private LinearLayout container;

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
        loadingProgress = rootView.findViewById(R.id.user_loading);
        errorLayout = rootView.findViewById(R.id.user_error);
        Button retryButton = rootView.findViewById(R.id.user_retry);
        container = rootView.findViewById(R.id.user_container);

        Bundle bundle = getArguments();
        final String userName = (bundle != null && !TextUtils.isEmpty(bundle.getString(EXTRA_USER_NAME))) ? bundle.getString(EXTRA_USER_NAME) : null;

        /*else if (Intent.ACTION_VIEW.equalsIgnoreCase(intent.getAction())) {
            if (intent.getData() != null) {
                String param = intent.getData().getQueryParameter("id");
                if (param != null && !param.equals("")) {
                    userName = param;
                }
            }
        }*/

        if (userName != null) {
            //lets create a request and fill in the data when we have it
            String url = "https://hacker-news.firebaseio.com/v0/user/" + userName + ".json";

            StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
                    response -> {
                        try {
                            //lets try to parse the response
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
                                submissionsButton.setVisibility(View.GONE);
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
                                aboutTextview.setHtml(jsonObject.getString("about"));
                            } else {
                                aboutTextview.setVisibility(View.GONE);
                            }

                            aboutTextview.setOnClickATagListener(new OnClickATagListener() {
                                @Override
                                public boolean onClick(View widget, String spannedText, @Nullable String href) {
                                    Utils.openLinkMaybeHN(widget.getContext(), href);
                                    return true;
                                }
                            });

                            container.setVisibility(View.VISIBLE);
                        } catch(Exception e) {
                            loadingProgress.setVisibility(View.GONE);
                            errorLayout.setVisibility(View.VISIBLE);
                            container.setVisibility(View.GONE);

                            e.printStackTrace();
                        }

                        loadingProgress.setVisibility(View.GONE);
                    }, error -> {
                error.printStackTrace();
                loadingProgress.setVisibility(View.GONE);
                errorLayout.setVisibility(View.VISIBLE);
                container.setVisibility(View.GONE);
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

    public static void showUserDialog(FragmentManager fm, String name) {
        UserDialogFragment userDialogFragment = new UserDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString(UserDialogFragment.EXTRA_USER_NAME, name);
        userDialogFragment.setArguments(bundle);
        userDialogFragment.show(fm, UserDialogFragment.TAG);
    }

}