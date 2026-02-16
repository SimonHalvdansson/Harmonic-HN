package com.simon.harmonichackernews.network;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.EncryptedSharedPreferencesHelper;
import com.simon.harmonichackernews.utils.Utils;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kotlin.Triple;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class UserActions {

private static final String BASE_WEB_URL = "https://news.ycombinator.com";
private static final String LOGIN_PATH = "login";
private static final String VOTE_PATH = "vote";
private static final String COMMENT_PATH = "comment";
private static final String SUBMIT_PATH = "submit";
private static final String ITEM_PATH = "item";
private static final String SUBMIT_POST_PATH = "r";
private static final String LOGIN_PARAM_ACCT = "acct";
private static final String LOGIN_PARAM_PW = "pw";
private static final String LOGIN_PARAM_CREATING = "creating";
private static final String LOGIN_PARAM_GOTO = "goto";
private static final String ITEM_PARAM_ID = "id";
private static final String VOTE_PARAM_ID = "id";
private static final String VOTE_PARAM_HOW = "how";
private static final String COMMENT_PARAM_PARENT = "parent";
private static final String COMMENT_PARAM_TEXT = "text";
private static final String SUBMIT_PARAM_TITLE = "title";
private static final String SUBMIT_PARAM_URL = "url";
private static final String SUBMIT_PARAM_TEXT = "text";
private static final String SUBMIT_PARAM_FNID = "fnid";
private static final String SUBMIT_PARAM_FNOP = "fnop";
private static final String VOTE_DIR_UP = "up";
private static final String VOTE_DIR_DOWN = "down";
private static final String VOTE_DIR_UN = "un";
private static final String DEFAULT_REDIRECT = "news";
private static final String CREATING_TRUE = "t";
private static final String DEFAULT_FNOP = "submit-page";
private static final String DEFAULT_SUBMIT_REDIRECT = "newest";
private static final String REGEX_INPUT = "<\\s*input[^>]*>";
private static final String REGEX_VALUE = "value[^\"]*\"([^\"]*)\"";
private static final String REGEX_CREATE_ERROR_BODY = "<body>([^<]*)";
private static final String HEADER_LOCATION = "location";
private static final String HEADER_COOKIE = "cookie";
private static final String HEADER_SET_COOKIE = "set-cookie";

    public static void voteWithDir(Context ctx, int id, FragmentManager fm, String dir) {
        UserActions.vote(String.valueOf(id), dir, ctx, fm, new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                String message = "Vote successful";
                switch (dir) {
                    case VOTE_DIR_UP: message = "Upvote successful"; break;
                    case VOTE_DIR_DOWN: message = "Downvote successful"; break;
                    case VOTE_DIR_UN: message= "Removed vote successfully"; break;
                }
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String summary, String response) {
                UserActions.showFailureDetailDialog(ctx, summary, response);
                Toast.makeText(ctx, "Vote unsuccessful, see dialog for response", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void upvote(Context ctx, int id, FragmentManager fm) {
        voteWithDir(ctx, id, fm, VOTE_DIR_UP);
    }

    public static void downvote(Context ctx, int id, FragmentManager fm) {
        voteWithDir(ctx, id, fm, VOTE_DIR_DOWN);
    }

    public static void unvote(Context ctx, int id, FragmentManager fm) {
        voteWithDir(ctx, id, fm, VOTE_DIR_UN);
    }


    public static void vote(String itemId, String direction, Context ctx, FragmentManager fm, ActionCallback cb) {
        Utils.log("Attempting to vote");
        Triple<String, String, Integer> account = AccountUtils.getAccountDetails(ctx);

        if (AccountUtils.handlePossibleError(account, fm, ctx)) {
            return;
        }

        Request request = new Request.Builder()
                .url(Objects.requireNonNull(HttpUrl.parse(BASE_WEB_URL))
                        .newBuilder()
                        .addPathSegment(VOTE_PATH)
                        .build())
                .post(new FormBody.Builder()
                        .add(LOGIN_PARAM_ACCT, account.getFirst())
                        .add(LOGIN_PARAM_PW, account.getSecond())
                        .add(VOTE_PARAM_ID, itemId)
                        .add(VOTE_PARAM_HOW, direction)
                        .build())
                .build();

        executeRequest(ctx, request, cb);
    }


    public static void comment(String itemId, String text, Context ctx, ActionCallback cb) {
        Triple<String, String, Integer> account = AccountUtils.getAccountDetails(ctx);

        if (AccountUtils.handlePossibleError(account, null, ctx)) {
            return;
        }

        Request request = new Request.Builder()
                .url(Objects.requireNonNull(HttpUrl.parse(BASE_WEB_URL))
                        .newBuilder()
                        .addPathSegment(COMMENT_PATH)
                        .build())
                .post(new FormBody.Builder()
                        .add(LOGIN_PARAM_ACCT, account.getFirst())
                        .add(LOGIN_PARAM_PW, account.getSecond())
                        .add(COMMENT_PARAM_PARENT, itemId)
                        .add(COMMENT_PARAM_TEXT, text)
                        .build())
                .build();

        executeRequest(ctx, request, cb);
    }

    /**
     * Performs a login POST to Hacker News and verifies credentials by checking for the fnid
     * input in the resulting /submit page. Calls onSuccess only if login succeeded and the
     * submit form is present.
     */
    public static void login(Context ctx, ActionCallback cb) {
        // always redirect to submit page to verify login
        String gotoPath = SUBMIT_PATH;

        // Retrieve stored account details
        Triple<String, String, Integer> account = AccountUtils.getAccountDetails(ctx);
        if (AccountUtils.handlePossibleError(account, null, ctx)) {
            cb.onFailure("Couldn't read credentials", "Check your saved login.");
            return;
        }

        // Build login form
        FormBody form = new FormBody.Builder()
                .add(LOGIN_PARAM_ACCT, account.getFirst())
                .add(LOGIN_PARAM_PW,   account.getSecond())
                .add(LOGIN_PARAM_GOTO, gotoPath)
                .build();

        Request request = new Request.Builder()
                .url(BASE_WEB_URL + "/" + LOGIN_PATH)
                .post(form)
                .build();

        Handler main = new Handler(ctx.getMainLooper());
        OkHttpClient client = NetworkComponent.getOkHttpClientInstanceWithCookies();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                main.post(() -> cb.onFailure("Login failed", e.getMessage()));
            }
            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    main.post(() -> cb.onFailure(
                            "Login failed: HTTP " + response.code(), response.toString()));
                    return;
                }
                try {
                    // Peek at a small part of the body to find fnid without consuming full stream
                    String preview = response.peekBody(8192).string();
                    Matcher matcher = Pattern.compile(
                            "<input[^>]*name=\\\"fnid\\\"[^>]*value=\\\"([^\\\"]+)\\\""
                    ).matcher(preview);
                    if (!matcher.find()) {
                        main.post(() -> cb.onFailure("Bad login", "Submit form not found"));
                    } else {
                        main.post(() -> cb.onSuccess(response));
                    }
                } catch (IOException e) {
                    main.post(() -> cb.onFailure("Login parsing error", e.getMessage()));
                }
            }
        });
    }

    /**
     * Submits a story to Hacker News: logs in (checked above), then parses fnid and posts submission.
     */
    public static void submit(String title,
                              String text,
                              String url,
                              Context ctx,
                              ActionCallback cb) {
        Handler main = new Handler(ctx.getMainLooper());

        // Login first (will check credentials and readiness of submit page)
        login(ctx, new ActionCallback() {
            @Override
            public void onFailure(String summary, String response) {
                main.post(() -> cb.onFailure(summary, response));
            }

            @Override
            public void onSuccess(Response loginResp) {
                main.post(() -> {
                    String html;
                    try {
                        html = loginResp.body().string();
                    } catch (IOException e) {
                        cb.onFailure("Error reading login response", e.getMessage());
                        return;
                    }
                    Matcher m = Pattern.compile(
                            "<input[^>]*name=\"fnid\"[^>]*value=\"([^\"]+)\""
                    ).matcher(html);
                    if (!m.find()) {
                        cb.onFailure("HN submit form parsing error", "No fnid found on /submit");
                        return;
                    }
                    String fnid = m.group(1);
                    FormBody.Builder submitForm = new FormBody.Builder()
                            .add("fnid", fnid)
                            .add("fnop", "submit-page")
                            .add("title", title)
                            .add("url", url)
                            .add("text", text);
                    Request submitReq = new Request.Builder()
                            .url(BASE_WEB_URL + "/" + SUBMIT_POST_PATH)
                            .post(submitForm.build())
                            .build();
                    executeRequest(ctx, submitReq, cb, true);
                });
            }
        });
    }

    public static void executeRequest(Context ctx, Request request, ActionCallback cb) {
        executeRequest(ctx, request, cb, false);
    }


    public static void executeRequest(Context ctx, Request request, ActionCallback cb, boolean cookies) {
        OkHttpClient client;
        if (cookies) {
            client = NetworkComponent.getOkHttpClientInstanceWithCookies();
        }
        else {
            client = NetworkComponent.getOkHttpClientInstance();
        }

        client.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(ctx.getMainLooper());

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                mainHandler.post(() -> {
                    if (!response.isSuccessful()) {
                        cb.onFailure("Unsuccessful response", response.toString());
                        return;
                    }

                    String body = "";
                    try {
                        body = response.body().string();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    if (body.contains("Unknown or expired link.")) {
                        cb.onFailure("Unknown or expired link", body);
                    } else if (body.contains("Bad login.")) {
                        AccountUtils.deleteAccountDetails(ctx);
                        cb.onFailure("Bad login",
                                "Your session has expired or credentials are invalid. Logged out.");
                    } else if (body.contains("Validation required. If this doesn't work, you can email")) {
                        cb.onFailure("Rate limit reached", "HN is temporarily requiring users to complete a CAPTCHA to proceed. Harmonic does not yet support this, apologies for the inconvenience. You can try again later or go via the official website.");
                    } else {
                        // HN will send a 302 → the new post, but OkHttp follows redirects by default.
                        cb.onSuccess(response);
                    }
                });
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                mainHandler.post(() -> cb.onFailure("Couldn't connect to HN", e.getMessage()));
            }
        });
    }


    public static void showFailureDetailDialog(Context ctx, String summary, String response) {
        // We need to try-catch this because it is called asynchronously and if the app has been
        // closed we cannot show a dialog. Instead of checking for this, we can just try-catch! :)
        try {
            AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                    .setTitle(summary)
                    .setMessage(response)
                    .setNegativeButton("Done", null).create();

            dialog.show();

            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setTextIsSelectable(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public interface ActionCallback {
        void onSuccess(Response response);
        void onFailure(String summary, String response);
    }
}