package com.simon.harmonichackernews.network;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import androidx.core.util.Pair;
import androidx.fragment.app.FragmentManager;

import com.simon.harmonichackernews.utils.AccountUtils;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;

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

    public static void upvote(Context ctx, int id, FragmentManager fm) {
        UserActions.vote(String.valueOf(id), VOTE_DIR_UP, ctx, fm, new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                Toast.makeText(ctx, "Vote successful", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String response) {
                Toast.makeText(ctx, "Vote unsuccessful, error: " + response, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void downvote(Context ctx, int id, FragmentManager fm) {
        UserActions.vote(String.valueOf(id), VOTE_DIR_DOWN, ctx, fm, new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                Toast.makeText(ctx, "Vote successful", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String response) {
                Toast.makeText(ctx, "Vote unsuccessful, error: " + response, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void unvote(Context ctx, int id, FragmentManager fm) {
        UserActions.vote(String.valueOf(id), VOTE_DIR_UN, ctx, fm, new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                Toast.makeText(ctx, "Vote successful", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String response) {
                Toast.makeText(ctx, "Vote unsuccessful, error: " + response, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void vote(String itemId, String direction, Context ctx, FragmentManager fm, ActionCallback cb) {
        Pair<String, String> account = AccountUtils.getAccountDetails(ctx);

        if (account == null || account.first == null || account.second == null) {
            AccountUtils.showLoginPrompt( fm);
            Toast.makeText(ctx, "Log in and try again", Toast.LENGTH_SHORT).show();
            return;
        }

        Request request = new Request.Builder()
                .url(Objects.requireNonNull(HttpUrl.parse(BASE_WEB_URL))
                        .newBuilder()
                        .addPathSegment(VOTE_PATH)
                        .build())
                .post(new FormBody.Builder()
                        .add(LOGIN_PARAM_ACCT, account.first)
                        .add(LOGIN_PARAM_PW, account.second)
                        .add(VOTE_PARAM_ID, itemId)
                        .add(VOTE_PARAM_HOW, direction)
                        .build())
                .build();

        executeRequest(ctx, request, cb);
    }



    public static void comment(String itemId, String text, Context ctx, ActionCallback cb) {
        Pair<String, String> account = AccountUtils.getAccountDetails(ctx);

        if (account.first == null || account.second == null) {
            return;
        }

        Request request = new Request.Builder()
                .url(Objects.requireNonNull(HttpUrl.parse(BASE_WEB_URL))
                        .newBuilder()
                        .addPathSegment(COMMENT_PATH)
                        .build())
                .post(new FormBody.Builder()
                        .add(LOGIN_PARAM_ACCT, account.first)
                        .add(LOGIN_PARAM_PW, account.second)
                        .add(COMMENT_PARAM_PARENT, itemId)
                        .add(COMMENT_PARAM_TEXT, text)
                        .build())
                .build();

        executeRequest(ctx, request, cb);
    }

    public static void executeRequest(Context ctx, Request request, ActionCallback cb) {
        OkHttpClient client = new OkHttpClient();

        client.newCall(request).enqueue(new Callback() {
            final Handler mainHandler = new Handler(ctx.getMainLooper());

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                mainHandler.post(new Runnable() {

                    @Override
                    public void run() {
                        if (!response.isSuccessful()) {
                            cb.onFailure(response.toString());
                            return;
                        }

                        try {
                            String responseBody = response.body().string();

                            if (responseBody.contains("Unknown or expired link.")) {
                                cb.onFailure("Unknown or expired link");
                                return;
                            }

                            if (responseBody.contains("Bad login.")) {
                                cb.onFailure("Bad login");
                                return;
                            }

                            if (responseBody.contains("Validation required. If this doesn't work, you can email")) {
                                cb.onFailure("Rate limit reached");
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
                        cb.onFailure("Couldn't connect to HN");
                    }
                });
            }
        });
    }

    public interface ActionCallback {
        void onSuccess(Response response);
        void onFailure(String response);
    }
}