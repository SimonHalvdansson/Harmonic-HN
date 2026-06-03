package com.simon.harmonichackernews.network;

import android.content.Context;
import android.content.DialogInterface;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Build;
import android.os.Handler;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.util.Pair;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.simon.harmonichackernews.utils.AccountUtils;
import com.simon.harmonichackernews.utils.Utils;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
private static final String FAVE_PATH = "fave";
private static final String FAVORITES_PATH = "favorites";
private static final String UPVOTED_PATH = "upvoted";
private static final String COMMENT_PATH = "comment";
private static final String SUBMIT_PATH = "submit";
private static final String ITEM_PATH = "item";
private static final String SUBMIT_POST_PATH = "r";
private static final String LOGIN_PARAM_ACCT = "acct";
private static final String LOGIN_PARAM_PW = "pw";
private static final String LOGIN_PARAM_CREATING = "creating";
private static final String LOGIN_PARAM_GOTO = "goto";
private static final String ITEM_PARAM_ID = "id";
private static final String COMMENTS_PARAM = "comments";
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
private static final String TRUE_VALUE = "t";
private static final String DEFAULT_SUBMIT_REDIRECT = "newest";
private static final String REGEX_INPUT = "<\\s*input[^>]*>";
private static final String REGEX_VALUE = "value[^\"]*\"([^\"]*)\"";
private static final String REGEX_CREATE_ERROR_BODY = "<body>([^<]*)";
private static final String HEADER_LOCATION = "location";
private static final String HEADER_COOKIE = "cookie";
private static final String HEADER_SET_COOKIE = "set-cookie";
private static final String CAPTCHA_VALIDATION_TEXT = "Validation required. If this doesn't work, you can email";
private static final String CAPTCHA_RESPONSE_PARAM = "g-recaptcha-response";
private static final long MAX_RESPONSE_PREVIEW_BYTES = 1024 * 1024;
private static final int MAX_USER_ITEM_LIST_PAGES = 50;

    public static void voteWithDir(Context ctx, int id, FragmentManager fm, String dir) {
        voteWithDir(ctx, id, fm, dir, null, null);
    }

    private static void voteWithDir(Context ctx, int id, FragmentManager fm, String dir, String successMessage) {
        voteWithDir(ctx, id, fm, dir, successMessage, null);
    }

    private static void voteWithDir(Context ctx, int id, FragmentManager fm, String dir, String successMessage, ActionCallback cb) {
        UserActions.vote(String.valueOf(id), dir, ctx, fm, new UserActions.ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                String message = successMessage;
                if (TextUtils.isEmpty(message)) {
                    message = "Vote successful";
                    switch (dir) {
                        case VOTE_DIR_UP: message = "Upvote successful"; break;
                        case VOTE_DIR_DOWN: message = "Downvote successful"; break;
                        case VOTE_DIR_UN: message= "Removed vote successfully"; break;
                    }
                }
                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
                if (cb != null) {
                    cb.onSuccess(response);
                }
            }

            @Override
            public void onFailure(String summary, String response) {
                UserActions.showFailureDetailDialog(ctx, summary, response);
                Toast.makeText(ctx, "Vote unsuccessful, see dialog for response", Toast.LENGTH_SHORT).show();
                if (cb != null) {
                    cb.onFailure(summary, response);
                }
            }

            @Override
            public void onCaptchaRequired(CaptchaChallenge challenge) {
                if (cb != null) {
                    cb.onCaptchaRequired(challenge);
                } else {
                    onFailure("Captcha required", "HN requires a captcha for this action. Please try again in a browser.");
                }
            }
        });
    }

    public static void upvote(Context ctx, int id, FragmentManager fm) {
        voteWithDir(ctx, id, fm, VOTE_DIR_UP);
    }

    public static void upvote(Context ctx, int id, FragmentManager fm, ActionCallback cb) {
        voteWithDir(ctx, id, fm, VOTE_DIR_UP, null, cb);
    }

    public static void votePollOption(Context ctx, int id, FragmentManager fm) {
        voteWithDir(ctx, id, fm, VOTE_DIR_UP, "Poll vote successful");
    }

    public static void downvote(Context ctx, int id, FragmentManager fm) {
        voteWithDir(ctx, id, fm, VOTE_DIR_DOWN);
    }

    public static void unvote(Context ctx, int id, FragmentManager fm) {
        voteWithDir(ctx, id, fm, VOTE_DIR_UN);
    }

    public static void unvote(Context ctx, int id, FragmentManager fm, ActionCallback cb) {
        voteWithDir(ctx, id, fm, VOTE_DIR_UN, null, cb);
    }

    public static void setFavorite(Context ctx, int id, boolean favorite, FragmentManager fm) {
        setFavorite(ctx, id, favorite, fm, new ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                Toast.makeText(ctx, favorite ? "Added favorite" : "Removed favorite", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(String summary, String response) {
                UserActions.showFailureDetailDialog(ctx, summary, response);
                Toast.makeText(ctx, "Couldn't update favorite", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void setFavorite(Context ctx, int id, boolean favorite, FragmentManager fm, ActionCallback cb) {
        Triple<String, String, Integer> account = AccountUtils.getAccountDetails(ctx);
        if (AccountUtils.handlePossibleError(account, fm, ctx)) {
            return;
        }

        login(ctx, new ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                response.close();
                fetchFavoriteActionLink(ctx, id, favorite, cb);
            }

            @Override
            public void onFailure(String summary, String response) {
                cb.onFailure(summary, response);
            }

            @Override
            public void onCaptchaRequired(CaptchaChallenge challenge) {
                cb.onCaptchaRequired(challenge);
            }
        });
    }

    public static void fetchFavorites(Context ctx, UserItemListCallback cb) {
        fetchUserItemList(ctx, FAVORITES_PATH, "favorites", false, cb);
    }

    public static void fetchUpvoted(Context ctx, UserItemListCallback cb) {
        fetchUserItemList(ctx, UPVOTED_PATH, "upvoted", true, cb);
    }

    private static void fetchUserItemList(Context ctx,
                                          String path,
                                          String listName,
                                          boolean loginRequired,
                                          UserItemListCallback cb) {
        Triple<String, String, Integer> account = AccountUtils.getAccountDetails(ctx);
        if (AccountUtils.handlePossibleError(account, null, ctx)) {
            cb.onFailure("Login required", "Save your Hacker News login before syncing " + listName + ".");
            return;
        }

        Handler main = new Handler(ctx.getMainLooper());
        Runnable fetch = () -> {
            OkHttpClient client = loginRequired
                    ? NetworkComponent.getOkHttpClientInstanceWithCookies()
                    : NetworkComponent.getOkHttpClientInstance();
            ArrayList<Integer> itemIds = new ArrayList<>();
            ArrayList<Integer> commentIds = new ArrayList<>();

            fetchUserItemListPage(
                    client,
                    buildUserItemListUrl(path, account.getFirst(), false),
                    itemIds,
                    commentIds,
                    1,
                    false,
                    listName,
                    main,
                    new UserItemListCallback() {
                        @Override
                        public void onSuccess(List<Integer> ids, List<Integer> comments) {
                            fetchUserItemListPage(
                                    client,
                                    buildUserItemListUrl(path, account.getFirst(), true),
                                    ids,
                                    comments,
                                    1,
                                    true,
                                    listName,
                                    main,
                                    cb);
                        }

                        @Override
                        public void onFailure(String summary, String response) {
                            cb.onFailure(summary, response);
                        }
                    });
        };

        if (!loginRequired) {
            fetch.run();
            return;
        }

        login(ctx, new ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                response.close();
                fetch.run();
            }

            @Override
            public void onFailure(String summary, String response) {
                cb.onFailure(summary, response);
            }

            @Override
            public void onCaptchaRequired(CaptchaChallenge challenge) {
                cb.onFailure("Captcha required", "HN asked for a captcha before syncing " + listName + ".");
            }
        });
    }

    private static String buildUserItemListUrl(String path, String username, boolean comments) {
        HttpUrl.Builder builder = Objects.requireNonNull(HttpUrl.parse(BASE_WEB_URL))
                .newBuilder()
                .addPathSegment(path)
                .addQueryParameter("id", username);

        if (comments) {
            builder.addQueryParameter(COMMENTS_PARAM, TRUE_VALUE);
        }

        return builder.build().toString();
    }

    private static void fetchUserItemListPage(OkHttpClient client,
                                              String url,
                                              List<Integer> itemIds,
                                              List<Integer> commentIds,
                                              int page,
                                              boolean commentsPage,
                                              String listName,
                                              Handler main,
                                              UserItemListCallback cb) {
        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                main.post(() -> cb.onFailure("Couldn't sync " + listName, e.getMessage()));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    String failure = response.toString();
                    response.close();
                    main.post(() -> cb.onFailure("Couldn't sync " + listName, failure));
                    return;
                }

                try {
                    String body = response.body() == null ? "" : response.body().string();
                    Document document = Jsoup.parse(body, BASE_WEB_URL + "/");
                    for (Element item : document.select("tr.athing[id]")) {
                        String idString = item.attr("id");
                        if (TextUtils.isDigitsOnly(idString)) {
                            int id = Integer.parseInt(idString);
                            if (!itemIds.contains(id)) {
                                itemIds.add(id);
                            }
                            if (commentsPage && !commentIds.contains(id)) {
                                commentIds.add(id);
                            }
                        }
                    }

                    Element moreLink = document.selectFirst("a.morelink[href]");
                    String nextPage = moreLink == null ? null : moreLink.absUrl("href");
                    if (!TextUtils.isEmpty(nextPage) && page < MAX_USER_ITEM_LIST_PAGES) {
                        fetchUserItemListPage(client, nextPage, itemIds, commentIds, page + 1, commentsPage, listName, main, cb);
                    } else {
                        main.post(() -> cb.onSuccess(itemIds, commentIds));
                    }
                } catch (Exception e) {
                    main.post(() -> cb.onFailure("Couldn't parse " + listName, e.getMessage()));
                }
            }
        });
    }

    private static void fetchFavoriteActionLink(Context ctx, int id, boolean favorite, ActionCallback cb) {
        Handler main = new Handler(ctx.getMainLooper());
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(BASE_WEB_URL))
                .newBuilder()
                .addPathSegment(ITEM_PATH)
                .addQueryParameter(ITEM_PARAM_ID, String.valueOf(id))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .build();

        NetworkComponent.getOkHttpClientInstanceWithCookies().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                main.post(() -> cb.onFailure("Couldn't load HN item", e.getMessage()));
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) {
                if (!response.isSuccessful()) {
                    String failure = response.toString();
                    response.close();
                    main.post(() -> cb.onFailure("Couldn't load HN item", failure));
                    return;
                }

                try {
                    String body = response.body() == null ? "" : response.body().string();
                    if (body.contains("Bad login.")) {
                        AccountUtils.deleteAccountDetails(ctx);
                        main.post(() -> cb.onFailure("Bad login",
                                "Your session has expired or credentials are invalid. Logged out."));
                        return;
                    }

                    if (isCaptchaRequired(body)) {
                        CaptchaChallenge challenge = parseCaptchaChallenge(body, true);
                        if (challenge != null) {
                            main.post(() -> cb.onCaptchaRequired(challenge));
                        } else {
                            main.post(() -> cb.onFailure("Captcha parsing error", "HN asked for a captcha, but Harmonic could not read the challenge form."));
                        }
                        return;
                    }

                    FavoriteLinkResult linkResult = findFavoriteLink(body, favorite);
                    if (linkResult.alreadyDesiredState) {
                        if (favorite) {
                            Utils.addFavorite(ctx, id);
                        } else {
                            Utils.removeFavorite(ctx, id);
                        }
                        main.post(() -> cb.onSuccess(response));
                        return;
                    }

                    if (TextUtils.isEmpty(linkResult.actionUrl)) {
                        main.post(() -> cb.onFailure("Favorite unavailable", "HN did not return a favorite action for this item."));
                        return;
                    }

                    Request favoriteRequest = new Request.Builder()
                            .url(linkResult.actionUrl)
                            .build();

                    executeRequest(ctx, favoriteRequest, new ActionCallback() {
                        @Override
                        public void onSuccess(Response response) {
                            if (favorite) {
                                Utils.addFavorite(ctx, id);
                            } else {
                                Utils.removeFavorite(ctx, id);
                            }
                            cb.onSuccess(response);
                        }

                        @Override
                        public void onFailure(String summary, String response) {
                            cb.onFailure(summary, response);
                        }

                        @Override
                        public void onCaptchaRequired(CaptchaChallenge challenge) {
                            cb.onCaptchaRequired(challenge);
                        }
                    }, true);
                } catch (Exception e) {
                    main.post(() -> cb.onFailure("Couldn't parse favorite action", e.getMessage()));
                }
            }
        });
    }

    private static FavoriteLinkResult findFavoriteLink(String body, boolean favorite) {
        Document document = Jsoup.parse(body, BASE_WEB_URL + "/");
        FavoriteLinkResult result = new FavoriteLinkResult();

        for (Element link : document.select("a[href]")) {
            String href = link.attr("href");
            if (TextUtils.isEmpty(href)
                    || (!href.startsWith(FAVE_PATH) && !href.contains("/" + FAVE_PATH))) {
                continue;
            }

            String label = link.text().trim().toLowerCase(Locale.US);
            String normalizedLabel = label.replace("-", "").replace(" ", "");
            boolean linkRemovesFavorite = normalizedLabel.contains("unfavorite");
            boolean linkAddsFavorite = normalizedLabel.contains("favorite") && !linkRemovesFavorite;

            if (favorite && linkAddsFavorite) {
                result.actionUrl = link.absUrl("href");
                return result;
            }

            if (!favorite && (linkAddsFavorite || linkRemovesFavorite)) {
                result.actionUrl = buildFavoriteRemovalUrl(link.absUrl("href"));
                return result;
            }

            if (favorite && linkRemovesFavorite) {
                result.alreadyDesiredState = true;
            }
        }

        return result;
    }

    private static String buildFavoriteRemovalUrl(String actionUrl) {
        HttpUrl parsed = HttpUrl.parse(actionUrl);
        if (parsed == null) {
            return actionUrl;
        }

        if (parsed.queryParameter("un") != null) {
            return actionUrl;
        }

        return parsed.newBuilder()
                .addQueryParameter("un", TRUE_VALUE)
                .build()
                .toString();
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

        NetworkComponent.resetOkHttpClientCookieInstance();
        executeLoginRequest(ctx, request, cb);
    }

    private static void executeLoginRequest(Context ctx, Request request, ActionCallback cb) {
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
                    String preview = response.peekBody(MAX_RESPONSE_PREVIEW_BYTES).string();
                    Matcher matcher = Pattern.compile(
                            "<input[^>]*name=\\\"fnid\\\"[^>]*value=\\\"([^\\\"]+)\\\""
                    ).matcher(preview);
                    if (preview.contains("Bad login.")) {
                        main.post(() -> cb.onFailure("Bad login", "Your credentials are invalid."));
                    } else if (isCaptchaRequired(preview)) {
                        CaptchaChallenge challenge = parseCaptchaChallenge(preview, true);
                        response.close();
                        if (challenge != null) {
                            main.post(() -> cb.onCaptchaRequired(challenge));
                        } else {
                            main.post(() -> cb.onFailure("Captcha parsing error", "HN asked for a captcha, but Harmonic could not read the challenge form."));
                        }
                    } else if (!matcher.find()) {
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

    public static void continueLoginWithCaptcha(Context ctx, CaptchaChallenge challenge, String captchaResponse, ActionCallback cb) {
        executeLoginRequest(ctx, buildCaptchaRequest(challenge, captchaResponse), cb);
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
            public void onCaptchaRequired(CaptchaChallenge challenge) {
                main.post(() -> cb.onCaptchaRequired(challenge));
            }

            @Override
            public void onSuccess(Response loginResp) {
                main.post(() -> submitAfterSuccessfulLogin(title, text, url, ctx, cb, loginResp));
            }
        });
    }

    public static void submitAfterLoginCaptcha(String title,
                                               String text,
                                               String url,
                                               Context ctx,
                                               CaptchaChallenge challenge,
                                               String captchaResponse,
                                               ActionCallback cb) {
        continueLoginWithCaptcha(ctx, challenge, captchaResponse, new ActionCallback() {
            @Override
            public void onSuccess(Response response) {
                submitAfterSuccessfulLogin(title, text, url, ctx, cb, response);
            }

            @Override
            public void onFailure(String summary, String response) {
                cb.onFailure(summary, response);
            }

            @Override
            public void onCaptchaRequired(CaptchaChallenge challenge) {
                cb.onCaptchaRequired(challenge);
            }
        });
    }

    private static void submitAfterSuccessfulLogin(String title,
                                                  String text,
                                                  String url,
                                                  Context ctx,
                                                  ActionCallback cb,
                                                  Response loginResp) {
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
                    } else if (isCaptchaRequired(body)) {
                        CaptchaChallenge challenge = parseCaptchaChallenge(body, cookies);
                        if (challenge != null) {
                            cb.onCaptchaRequired(challenge);
                        } else {
                            cb.onFailure("Captcha parsing error", "HN asked for a captcha, but Harmonic could not read the challenge form.");
                        }
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

    public static void continueCaptchaAction(Context ctx, CaptchaChallenge challenge, String captchaResponse, ActionCallback cb) {
        executeRequest(ctx, buildCaptchaRequest(challenge, captchaResponse), cb, challenge.useCookies());
    }

    private static Request buildCaptchaRequest(CaptchaChallenge challenge, String captchaResponse) {
        FormBody.Builder formBuilder = new FormBody.Builder();
        for (Pair<String, String> field : challenge.getFormFields()) {
            formBuilder.add(field.first, field.second == null ? "" : field.second);
        }
        formBuilder.add(CAPTCHA_RESPONSE_PARAM, captchaResponse);

        return new Request.Builder()
                .url(challenge.getActionUrl())
                .post(formBuilder.build())
                .build();
    }

    private static boolean isCaptchaRequired(String body) {
        return body != null && body.contains(CAPTCHA_VALIDATION_TEXT) && body.contains("g-recaptcha");
    }

    private static CaptchaChallenge parseCaptchaChallenge(String body, boolean cookies) {
        Document document = Jsoup.parse(body, BASE_WEB_URL + "/");
        Element form = document.selectFirst("form[action]");
        Element captcha = document.selectFirst(".g-recaptcha[data-sitekey]");

        if (form == null || captcha == null) {
            return null;
        }

        String actionUrl = form.absUrl("action");
        if (TextUtils.isEmpty(actionUrl)) {
            HttpUrl parsedBase = HttpUrl.parse(BASE_WEB_URL);
            if (parsedBase == null) {
                return null;
            }
            actionUrl = parsedBase.newBuilder()
                    .addPathSegment(form.attr("action"))
                    .build()
                    .toString();
        }

        String siteKey = captcha.attr("data-sitekey");
        if (TextUtils.isEmpty(siteKey)) {
            return null;
        }

        ArrayList<Pair<String, String>> formFields = new ArrayList<>();
        for (Element input : form.select("input[name], textarea[name]")) {
            String name = input.attr("name");
            String type = input.attr("type");
            if (TextUtils.isEmpty(name)
                    || CAPTCHA_RESPONSE_PARAM.equals(name)
                    || "submit".equalsIgnoreCase(type)
                    || "button".equalsIgnoreCase(type)) {
                continue;
            }

            formFields.add(new Pair<>(name, input.val()));
        }

        return new CaptchaChallenge(actionUrl, siteKey, formFields, cookies);
    }

    public static class CaptchaChallenge {
        private final String actionUrl;
        private final String siteKey;
        private final ArrayList<Pair<String, String>> formFields;
        private final boolean cookies;

        private CaptchaChallenge(String actionUrl,
                                 String siteKey,
                                 ArrayList<Pair<String, String>> formFields,
                                 boolean cookies) {
            this.actionUrl = actionUrl;
            this.siteKey = siteKey;
            this.formFields = formFields;
            this.cookies = cookies;
        }

        public String getActionUrl() {
            return actionUrl;
        }

        public String getCaptchaHtml() {
            String safeSiteKey = TextUtils.htmlEncode(siteKey);
            return "<!doctype html><html><head>"
                    + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                    + "<script src=\"https://www.google.com/recaptcha/api.js\" async defer></script>"
                    + "<style>body{margin:0;padding:16px;background:#fff;font-family:sans-serif;} .wrap{min-height:420px;}</style>"
                    + "</head><body><div class=\"wrap\"><div class=\"g-recaptcha\" data-sitekey=\""
                    + safeSiteKey
                    + "\"></div></div></body></html>";
        }

        private List<Pair<String, String>> getFormFields() {
            return formFields;
        }

        private boolean useCookies() {
            return cookies;
        }

        public boolean isLoginChallenge() {
            HttpUrl url = HttpUrl.parse(actionUrl);
            return url != null
                    && url.pathSegments().size() == 1
                    && LOGIN_PATH.equals(url.pathSegments().get(0));
        }
    }


    public static void showFailureDetailDialog(Context ctx, String summary, String response) {
        showFailureDetailDialog(ctx, summary, response, null);
    }

    public static void showFailureDetailDialog(Context ctx, String summary, String response, String clipboardText) {
        // We need to try-catch this because it is called asynchronously and if the app has been
        // closed we cannot show a dialog. Instead of checking for this, we can just try-catch! :)
        try {
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(ctx)
                    .setTitle(summary)
                    .setMessage(response)
                    .setNegativeButton("Done", null);

            if (clipboardText != null) {
                builder.setNeutralButton("Copy", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("Hacker News comment", clipboardText);
                        clipboard.setPrimaryClip(clip);

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(ctx, "Comment copied to clipboard", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            AlertDialog dialog = builder.create();

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

        default void onCaptchaRequired(CaptchaChallenge challenge) {
            onFailure("Captcha required", "HN requires a captcha for this action. Please try again in a browser.");
        }
    }

    public interface UserItemListCallback {
        void onSuccess(List<Integer> itemIds, List<Integer> commentIds);
        void onFailure(String summary, String response);
    }

    private static class FavoriteLinkResult {
        String actionUrl;
        boolean alreadyDesiredState;
    }
}
