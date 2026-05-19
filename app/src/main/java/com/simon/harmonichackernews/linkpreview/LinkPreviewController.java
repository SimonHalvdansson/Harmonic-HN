package com.simon.harmonichackernews.linkpreview;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.simon.harmonichackernews.data.ArxivInfo;
import com.simon.harmonichackernews.data.GitLabInfo;
import com.simon.harmonichackernews.data.NitterInfo;
import com.simon.harmonichackernews.data.RepoInfo;
import com.simon.harmonichackernews.data.StackExchangeInfo;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.data.WikipediaInfo;
import com.simon.harmonichackernews.utils.SettingsUtils;

import java.util.List;

public class LinkPreviewController {

    private static final int NITTER_LINK_PREVIEW_MAX_ATTEMPTS = 4;
    private static final long NITTER_LINK_PREVIEW_PAGE_LOAD_TIMEOUT_MS = 6000;
    private static final long NITTER_LINK_PREVIEW_HTML_READ_TIMEOUT_MS = 2500;
    private static final long[] NITTER_LINK_PREVIEW_RETRY_DELAYS_MS = {500, 1500, 3000};

    public interface Callbacks {
        void onPreviewChanged();
    }

    private final Story story;
    private final Callbacks callbacks;
    private final Handler nitterLinkPreviewHandler = new Handler(Looper.getMainLooper());
    private int nitterLinkPreviewGeneration = 0;
    @Nullable
    private WebView activeNitterPreviewWebView;

    public LinkPreviewController(Story story, Callbacks callbacks) {
        this.story = story;
        this.callbacks = callbacks;
    }

    public void loadNetworkPreviews(@Nullable Context context) {
        if (context == null || story == null || TextUtils.isEmpty(story.url) || story.linkPreviewLoading || story.hasLoadedLinkPreview()) {
            return;
        }

        if (ArxivAbstractGetter.isValidArxivUrl(story.url) && SettingsUtils.shouldUseLinkPreviewArxiv(context)) {
            setLinkPreviewLoading(true);
            ArxivAbstractGetter.getAbstract(story.url, context, new ArxivAbstractGetter.GetterCallback() {
                @Override
                public void onSuccess(ArxivInfo arxivInfo) {
                    story.arxivInfo = arxivInfo;
                    setLinkPreviewLoading(false);
                }

                @Override
                public void onFailure(String reason) {
                    setLinkPreviewLoading(false);
                }
            });
        } else if (GitHubInfoGetter.isValidGitHubUrl(story.url) && SettingsUtils.shouldUseLinkPreviewGithub(context)) {
            setLinkPreviewLoading(true);
            GitHubInfoGetter.getInfo(story.url, context, new GitHubInfoGetter.GetterCallback() {
                @Override
                public void onSuccess(RepoInfo repoInfo) {
                    story.repoInfo = repoInfo;
                    setLinkPreviewLoading(false);
                }

                @Override
                public void onFailure(String reason) {
                    setLinkPreviewLoading(false);
                }
            });
        } else if (GitLabInfoGetter.isValidGitLabUrl(story.url) && SettingsUtils.shouldUseLinkPreviewGitLab(context)) {
            setLinkPreviewLoading(true);
            GitLabInfoGetter.getInfo(story.url, context, new GitLabInfoGetter.GetterCallback() {
                @Override
                public void onSuccess(GitLabInfo gitLabInfo) {
                    story.gitLabInfo = gitLabInfo;
                    setLinkPreviewLoading(false);
                }

                @Override
                public void onFailure(String reason) {
                    setLinkPreviewLoading(false);
                }
            });
        } else if (StackExchangeGetter.isValidStackExchangeUrl(story.url) && SettingsUtils.shouldUseLinkPreviewStackExchange(context)) {
            setLinkPreviewLoading(true);
            StackExchangeGetter.getInfo(story.url, context, new StackExchangeGetter.GetterCallback() {
                @Override
                public void onSuccess(StackExchangeInfo stackExchangeInfo) {
                    story.stackExchangeInfo = stackExchangeInfo;
                    setLinkPreviewLoading(false);
                }

                @Override
                public void onFailure(String reason) {
                    setLinkPreviewLoading(false);
                }
            });
        } else if (WikipediaGetter.isValidWikipediaUrl(story.url) && SettingsUtils.shouldUseLinkPreviewWikipedia(context)) {
            setLinkPreviewLoading(true);
            WikipediaGetter.getInfo(story.url, context, new WikipediaGetter.GetterCallback() {
                @Override
                public void onSuccess(WikipediaInfo wikipediaInfo) {
                    story.wikiInfo = wikipediaInfo;
                    setLinkPreviewLoading(false);
                }

                @Override
                public void onFailure(String reason) {
                    setLinkPreviewLoading(false);
                }
            });
        }
    }

    public boolean shouldInitializeWebViewForPreview(@Nullable Context context) {
        return context != null
                && story != null
                && NitterGetter.isConvertibleToNitter(story.url)
                && SettingsUtils.shouldUseLinkPreviewX(context);
    }

    public String prepareWebViewLoad(@Nullable Context context, @Nullable WebView webView, String url) {
        cancelPendingNitterLinkPreviewRead();
        if (context == null || webView == null) {
            return url;
        }

        if (NitterGetter.isConvertibleToNitter(url) && SettingsUtils.shouldRedirectNitter(context)) {
            url = NitterGetter.convertToNitterUrl(url);
        }

        activeNitterPreviewWebView = webView;
        boolean loadingNitterPreview = story != null
                && story.nitterInfo == null
                && shouldLoadNitterLinkPreview(context, url);
        if (loadingNitterPreview) {
            setLinkPreviewLoading(true);
            scheduleNitterLinkPreviewPageLoadTimeout(webView, url, nitterLinkPreviewGeneration);
        } else if (story != null
                && story.linkPreviewLoading
                && story.nitterInfo == null
                && !shouldLoadNitterLinkPreview(context, url)
                && shouldLoadNitterLinkPreview(context, story.url)) {
            setLinkPreviewLoading(false);
        }

        return url;
    }

    public void onWebViewPageFinished(@Nullable Context context, WebView view, String url) {
        if (context != null && shouldReadNitterLinkPreview(context, url)) {
            readNitterLinkPreviewWithRetry(context, view, url);
        }
    }

    public void onWebViewOfflineFallback(@Nullable Context context) {
        if (context != null
                && story != null
                && story.linkPreviewLoading
                && shouldLoadNitterLinkPreview(context, story.url)) {
            setLinkPreviewLoading(false);
        }
    }

    public void cancelPendingNitterLinkPreviewRead() {
        nitterLinkPreviewGeneration++;
        activeNitterPreviewWebView = null;
        nitterLinkPreviewHandler.removeCallbacksAndMessages(null);
    }

    private boolean shouldLoadNitterLinkPreview(Context context, String url) {
        return shouldReadNitterLinkPreview(context, url)
                || (NitterGetter.isConvertibleToNitter(url)
                && SettingsUtils.shouldRedirectNitter(context)
                && SettingsUtils.shouldUseLinkPreviewX(context));
    }

    private boolean shouldReadNitterLinkPreview(Context context, String url) {
        return NitterGetter.isValidNitterUrl(url)
                && SettingsUtils.shouldUseLinkPreviewX(context);
    }

    private void scheduleNitterLinkPreviewPageLoadTimeout(WebView view, String url, int generation) {
        nitterLinkPreviewHandler.postDelayed(() -> {
            if (isCurrentNitterLinkPreviewRead(view, url, generation)) {
                readNitterLinkPreviewAttempt(view.getContext(), view, url, generation, 0);
            }
        }, NITTER_LINK_PREVIEW_PAGE_LOAD_TIMEOUT_MS);
    }

    private void readNitterLinkPreviewWithRetry(Context context, WebView view, String url) {
        if (story == null || story.nitterInfo != null) {
            return;
        }
        cancelPendingNitterLinkPreviewRead();
        activeNitterPreviewWebView = view;
        setLinkPreviewLoading(true);
        readNitterLinkPreviewAttempt(context, view, url, nitterLinkPreviewGeneration, 0);
    }

    private void readNitterLinkPreviewAttempt(Context context, WebView view, String url, int generation, int attempt) {
        if (context == null || !isCurrentNitterLinkPreviewRead(view, url, generation)) {
            return;
        }
        if (!isWebViewAtNitterLinkPreviewUrl(view, url)) {
            onNitterLinkPreviewReadFailed(context, view, url, generation, attempt);
            return;
        }

        final boolean[] finished = {false};
        nitterLinkPreviewHandler.postDelayed(() -> {
            if (finished[0] || !isCurrentNitterLinkPreviewRead(view, url, generation)) {
                return;
            }
            finished[0] = true;
            onNitterLinkPreviewReadFailed(context, view, url, generation, attempt);
        }, NITTER_LINK_PREVIEW_HTML_READ_TIMEOUT_MS);

        NitterGetter.getInfo(view, context, new NitterGetter.GetterCallback() {
            @Override
            public void onSuccess(NitterInfo nitterInfo) {
                if (finished[0] || !isCurrentNitterLinkPreviewRead(view, url, generation)) {
                    return;
                }
                finished[0] = true;
                story.nitterInfo = nitterInfo;
                setLinkPreviewLoading(false);
            }

            @Override
            public void onFailure(String reason) {
                if (finished[0] || !isCurrentNitterLinkPreviewRead(view, url, generation)) {
                    return;
                }
                finished[0] = true;
                onNitterLinkPreviewReadFailed(context, view, url, generation, attempt);
            }
        });
    }

    private void onNitterLinkPreviewReadFailed(Context context, WebView view, String url, int generation, int attempt) {
        int nextAttempt = attempt + 1;
        if (nextAttempt < NITTER_LINK_PREVIEW_MAX_ATTEMPTS) {
            long delay = NITTER_LINK_PREVIEW_RETRY_DELAYS_MS[Math.min(attempt, NITTER_LINK_PREVIEW_RETRY_DELAYS_MS.length - 1)];
            nitterLinkPreviewHandler.postDelayed(() ->
                            readNitterLinkPreviewAttempt(context, view, url, generation, nextAttempt),
                    delay);
            return;
        }

        if (isCurrentNitterLinkPreviewRead(view, url, generation)) {
            setLinkPreviewLoading(false);
        }
    }

    private boolean isCurrentNitterLinkPreviewRead(WebView view, String url, int generation) {
        return generation == nitterLinkPreviewGeneration
                && view != null
                && view == activeNitterPreviewWebView
                && story != null
                && story.nitterInfo == null
                && NitterGetter.isValidNitterUrl(url);
    }

    private boolean isWebViewAtNitterLinkPreviewUrl(WebView view, String expectedUrl) {
        String currentUrl = view.getUrl();
        if (TextUtils.isEmpty(currentUrl) || !NitterGetter.isValidNitterUrl(currentUrl)) {
            return false;
        }

        String expectedStatusId = getNitterStatusId(expectedUrl);
        String currentStatusId = getNitterStatusId(currentUrl);
        if (!TextUtils.isEmpty(expectedStatusId) && !TextUtils.isEmpty(currentStatusId)) {
            return expectedStatusId.equals(currentStatusId);
        }

        return areSameNitterPage(currentUrl, expectedUrl);
    }

    @Nullable
    private String getNitterStatusId(String url) {
        try {
            List<String> segments = Uri.parse(url).getPathSegments();
            for (int i = 0; i < segments.size() - 1; i++) {
                if ("status".equals(segments.get(i))) {
                    return segments.get(i + 1);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private boolean areSameNitterPage(String firstUrl, String secondUrl) {
        try {
            Uri first = Uri.parse(firstUrl);
            Uri second = Uri.parse(secondUrl);
            return TextUtils.equals(normalizeHost(first.getHost()), normalizeHost(second.getHost()))
                    && TextUtils.equals(trimTrailingSlash(first.getPath()), trimTrailingSlash(second.getPath()));
        } catch (Exception ignored) {
            return TextUtils.equals(firstUrl, secondUrl);
        }
    }

    private String normalizeHost(@Nullable String host) {
        if (host == null) {
            return "";
        }
        host = host.toLowerCase(java.util.Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private String trimTrailingSlash(@Nullable String path) {
        if (TextUtils.isEmpty(path) || "/".equals(path)) {
            return "";
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private void setLinkPreviewLoading(boolean loading) {
        if (story == null) {
            return;
        }
        story.linkPreviewLoading = loading;
        callbacks.onPreviewChanged();
    }
}
