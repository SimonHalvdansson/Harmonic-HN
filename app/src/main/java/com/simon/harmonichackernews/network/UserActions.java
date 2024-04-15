package com.simon.harmonichackernews.network;

import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
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

    public static void submit(String title, String text, String url, Context ctx, ActionCallback cb) {
        Utils.log("Submitting");
        Triple<String, String, Integer> account = AccountUtils.getAccountDetails(ctx);

        if (AccountUtils.handlePossibleError(account, null, ctx)) {
            return;
        }

        Request request = new Request.Builder()
                .url(Objects.requireNonNull(HttpUrl.parse(BASE_WEB_URL))
                        .newBuilder()
                        .addPathSegment(SUBMIT_PATH)
                        .build())
                .post(new FormBody.Builder()
                        .add(LOGIN_PARAM_ACCT, account.getFirst())
                        .add(LOGIN_PARAM_PW, account.getSecond())
                        .add(SUBMIT_PARAM_TITLE, title)
                        .add(SUBMIT_PARAM_TEXT, text)
                        .add(SUBMIT_PARAM_URL, url)
                        .build())
                .build();

        executeRequest(ctx, request, cb);
    }

    public static void executeRequest(Context ctx, Request request, ActionCallback cb) {
        OkHttpClient client = NetworkComponent.getOkHttpClientInstance();

        client.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(ctx.getMainLooper());

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                mainHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        if (!response.isSuccessful()) {
                            cb.onFailure("Unsuccessful response", response.toString());
                            return;
                        }

                        try {
                            String responseBody = response.body().string();

                            if (responseBody.contains("Unknown or expired link.")) {
                                cb.onFailure("Unknown or expired link", responseBody);
                                return;
                            }

                            if (responseBody.contains("Bad login.")) {
                                AccountUtils.deleteAccountDetails(ctx);
                                cb.onFailure("Bad login", "Hacker News API returned a 'Bad login' error. This could be that your account details were mistyped but could also be that your saved account details were corrupted. Either way, you have been logged out. If logging in again does not solve this issue, you can try the nuclear option of clearing all data for Harmonic.");
                                return;
                            }

                            if (responseBody.contains("Validation required. If this doesn't work, you can email")) {
                                cb.onFailure("Rate limit reached", responseBody);
                                return;
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        cb.onSuccess(response);
                    }
                });
            }

            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onFailure("Couldn't connect to HN", null);
                    }
                });
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
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public interface ActionCallback {
        void onSuccess(Response response);
        void onFailure(String summary, String response);
    }
}