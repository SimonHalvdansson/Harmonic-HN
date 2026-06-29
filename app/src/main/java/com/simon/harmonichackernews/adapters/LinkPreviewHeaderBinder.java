package com.simon.harmonichackernews.adapters;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.simon.harmonichackernews.R;
import com.simon.harmonichackernews.data.Story;
import com.simon.harmonichackernews.utils.FontUtils;
import com.simon.harmonichackernews.utils.Utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import coil.Coil;
import coil.request.ImageRequest;
import io.noties.markwon.Markwon;
import io.noties.markwon.ext.latex.JLatexMathPlugin;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;

final class LinkPreviewHeaderBinder {

    private static final Pattern SINGLE_DOLLAR_LATEX_PATTERN =
            Pattern.compile("(?s)(?<!\\$)\\$(?![\\s$])(.+?)(?<![\\s$])\\$(?!\\$)");

    private LinkPreviewHeaderBinder() {
    }

    static void bind(Context ctx,
                     CommentsRecyclerViewAdapter.HeaderViewHolder holder,
                     Story story,
                     boolean integratedWebview,
                     LinearLayout bottomSheet) {
        holder.infoContainer.setVisibility(story.hasExtraInfo() ? View.VISIBLE : GONE);
        boolean hasLoadedLinkPreview = story.hasLoadedLinkPreview();
        boolean showLinkPreviewLoading = story.linkPreviewLoading && !hasLoadedLinkPreview;
        holder.infoHeader.setVisibility(hasLoadedLinkPreview ? VISIBLE : GONE);
        holder.linkPreviewLoadingContainer.setVisibility(showLinkPreviewLoading ? VISIBLE : GONE);
        holder.arxivContainer.setVisibility(GONE);
        holder.githubContainer.setVisibility(GONE);
        holder.gitLabContainer.setVisibility(GONE);
        holder.stackExchangeContainer.setVisibility(GONE);
        holder.wikiContainer.setVisibility(GONE);
        holder.nitterContainer.setVisibility(GONE);
        holder.nitterMediaContainer.setVisibility(GONE);
        holder.nitterImage.setVisibility(GONE);
        holder.nitterVideoLabel.setVisibility(GONE);

        if (story.arxivInfo != null) {
            bindArxivPreview(ctx, holder, story);
        }

        if (story.repoInfo != null) {
            bindGitHubPreview(holder, story);
        }

        if (story.gitLabInfo != null) {
            bindGitLabPreview(holder, story);
        }

        if (story.stackExchangeInfo != null) {
            bindStackExchangePreview(ctx, holder, story);
        }

        if (story.wikiInfo != null) {
            bindWikipediaPreview(holder, story);
        }

        if (story.nitterInfo != null) {
            bindNitterPreview(ctx, holder, story, integratedWebview, bottomSheet);
        }
    }

    private static void bindArxivPreview(Context ctx, CommentsRecyclerViewAdapter.HeaderViewHolder holder, Story story) {
        holder.arxivContainer.setVisibility(View.VISIBLE);
        holder.infoHeader.setVisibility(VISIBLE);
        holder.infoHeader.setText("ABSTRACT:");

        FontUtils.setTypeface(holder.arxivAbstract, false, 14);

        setLatexMarkdown(ctx, holder.arxivAbstract, story.arxivInfo.arxivAbstract);

        holder.arxivBy.setText(story.arxivInfo.concatNames());
        holder.arxivDate.setText(story.arxivInfo.formatDate());
        holder.arxivSubjects.setText(story.arxivInfo.formatSubjects());
        holder.arxivBy.setContentDescription("Authors: " + story.arxivInfo.concatNames());
        holder.arxivDate.setContentDescription("Published: " + story.arxivInfo.formatDate());
        holder.arxivSubjects.setContentDescription("Subjects: " + story.arxivInfo.formatSubjects());

        int byIconResource = R.drawable.ic_groups;
        if (story.arxivInfo.authors.length == 1) {
            byIconResource = R.drawable.ic_person;
        } else if (story.arxivInfo.authors.length == 2) {
            byIconResource = R.drawable.ic_group;
        }
        holder.arxivByIcon.setImageResource(byIconResource);
    }

    private static void bindGitHubPreview(CommentsRecyclerViewAdapter.HeaderViewHolder holder, Story story) {
        holder.githubContainer.setVisibility(View.VISIBLE);
        holder.infoHeader.setVisibility(VISIBLE);
        holder.infoHeader.setText(story.repoInfo.owner + " / " + story.repoInfo.name);

        holder.githubAbout.setText(story.repoInfo.about);
        holder.githubWebsite.setHtml("<a href=\"" + story.repoInfo.website + "\">" + story.repoInfo.getShortenedUrl() + "</a>");
        holder.githubLicense.setText(story.repoInfo.license);
        holder.githubLanguage.setText(story.repoInfo.language);
        holder.githubStars.setText(story.repoInfo.formatStars());
        holder.githubWatching.setText(story.repoInfo.formatWatching());
        holder.githubForks.setText(story.repoInfo.formatForks());
        holder.githubLicense.setContentDescription("License: " + story.repoInfo.license);
        holder.githubLanguage.setContentDescription("Primary language: " + story.repoInfo.language);

        holder.githubWebsiteContainer.setVisibility(TextUtils.isEmpty(story.repoInfo.website) ? GONE : View.VISIBLE);
        holder.githubLicenseContainer.setVisibility(TextUtils.isEmpty(story.repoInfo.license) ? GONE : View.VISIBLE);
        holder.githubLanguageContainer.setVisibility(TextUtils.isEmpty(story.repoInfo.language) ? GONE : View.VISIBLE);
        holder.githubAbout.setVisibility(TextUtils.isEmpty(story.repoInfo.about) ? GONE : VISIBLE);
    }

    private static void bindGitLabPreview(CommentsRecyclerViewAdapter.HeaderViewHolder holder, Story story) {
        holder.gitLabContainer.setVisibility(View.VISIBLE);
        holder.infoHeader.setVisibility(VISIBLE);
        holder.infoHeader.setText(story.gitLabInfo.namespace + " / " + story.gitLabInfo.name);

        holder.gitLabDescription.setText(story.gitLabInfo.description);
        holder.gitLabWebsite.setHtml("<a href=\"" + story.gitLabInfo.website + "\">" + story.gitLabInfo.getShortenedUrl() + "</a>");
        holder.gitLabVisibility.setText(story.gitLabInfo.formatVisibility());
        holder.gitLabLanguage.setText(story.gitLabInfo.language);
        holder.gitLabStars.setText(story.gitLabInfo.formatStars());
        holder.gitLabForks.setText(story.gitLabInfo.formatForks());
        holder.gitLabVisibility.setContentDescription("Visibility: " + story.gitLabInfo.formatVisibility());
        holder.gitLabLanguage.setContentDescription("Primary language: " + story.gitLabInfo.language);

        holder.gitLabWebsiteContainer.setVisibility(TextUtils.isEmpty(story.gitLabInfo.website) ? GONE : View.VISIBLE);
        holder.gitLabVisibilityContainer.setVisibility(TextUtils.isEmpty(story.gitLabInfo.visibility) ? GONE : View.VISIBLE);
        holder.gitLabLanguageContainer.setVisibility(TextUtils.isEmpty(story.gitLabInfo.language) ? GONE : View.VISIBLE);
        holder.gitLabDescription.setVisibility(TextUtils.isEmpty(story.gitLabInfo.description) ? GONE : VISIBLE);
    }

    private static void bindStackExchangePreview(Context ctx, CommentsRecyclerViewAdapter.HeaderViewHolder holder, Story story) {
        holder.stackExchangeContainer.setVisibility(View.VISIBLE);
        holder.infoHeader.setVisibility(VISIBLE);
        holder.infoHeader.setText("STACK EXCHANGE:");
        setStackExchangeText(ctx, holder.stackExchangeTitle, story.stackExchangeInfo.title);
        setStackExchangeText(ctx, holder.stackExchangeBy, story.stackExchangeInfo.formatBy());
        holder.stackExchangeScore.setText(story.stackExchangeInfo.formatScore());
        holder.stackExchangeAnswers.setText(story.stackExchangeInfo.formatAnswerCount());
        holder.stackExchangeViews.setText(story.stackExchangeInfo.formatViewCount());
        holder.stackExchangeAnswerState.setText(story.stackExchangeInfo.formatAnswerState());
        holder.stackExchangeAuthor.setText(story.stackExchangeInfo.formatAuthor());
        holder.stackExchangeTags.setText(story.stackExchangeInfo.formatTags());
        holder.stackExchangeTagsContainer.setVisibility(TextUtils.isEmpty(story.stackExchangeInfo.formatTags()) ? GONE : View.VISIBLE);
    }

    private static void setStackExchangeText(Context ctx, TextView textView, String text) {
        if (containsLatex(text)) {
            setLatexMarkdown(ctx, textView, text);
        } else {
            textView.setText(text);
        }
    }

    private static boolean containsLatex(String text) {
        return !TextUtils.isEmpty(text)
                && (text.contains("\\(")
                || text.contains("\\[")
                || text.contains("$$")
                || SINGLE_DOLLAR_LATEX_PATTERN.matcher(text).find());
    }

    private static void setLatexMarkdown(Context ctx, TextView textView, String text) {
        Markwon markwon = Markwon.builder(ctx)
                .usePlugin(MarkwonInlineParserPlugin.create())
                .usePlugin(JLatexMathPlugin.create(textView.getTextSize(), builder -> builder.inlinesEnabled(true)))
                .build();

        markwon.setMarkdown(textView, normalizeLatexMarkdown(text));
    }

    private static String normalizeLatexMarkdown(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\\(", "$$")
                .replace("\\)", "$$")
                .replace("\\[", "$$")
                .replace("\\]", "$$")
                .replaceAll("(?<!\\$)\\$(?!\\$)", Matcher.quoteReplacement("$$"))
                .replaceAll("\\\\textbf\\{(.*?)\\}", "**$1**")
                .replaceAll("\\\\textit\\{(.*?)\\}", "*$1*")
                .replaceAll("\\\\emph\\{(.*?)\\}", "*$1*");
    }

    private static void bindWikipediaPreview(CommentsRecyclerViewAdapter.HeaderViewHolder holder, Story story) {
        holder.wikiContainer.setVisibility(View.VISIBLE);
        holder.infoHeader.setVisibility(VISIBLE);
        holder.infoHeader.setText("WIKIPEDIA SUMMARY:");
        holder.wikiSummary.setHtml(story.wikiInfo.summary);
    }

    private static void bindNitterPreview(Context ctx,
                                          CommentsRecyclerViewAdapter.HeaderViewHolder holder,
                                          Story story,
                                          boolean integratedWebview,
                                          LinearLayout bottomSheet) {
        holder.nitterContainer.setVisibility(View.VISIBLE);
        holder.infoHeader.setText(story.nitterInfo.userName + " " + story.nitterInfo.userTag);

        holder.nitterText.setHtml(story.nitterInfo.text);
        holder.nitterDate.setText(story.nitterInfo.date);
        holder.nitterReplyCount.setText(String.valueOf(story.nitterInfo.replyCount));
        holder.nitterReposts.setText(String.valueOf(story.nitterInfo.reposts));
        holder.nitterLikes.setText(String.valueOf(story.nitterInfo.likes));
        holder.nitterDate.setContentDescription("Posted: " + story.nitterInfo.date);
        holder.nitterReplyCount.setContentDescription("Replies: " + story.nitterInfo.replyCount);
        holder.nitterReposts.setContentDescription("Reposts: " + story.nitterInfo.reposts);
        holder.nitterLikes.setContentDescription("Likes: " + story.nitterInfo.likes);

        holder.nitterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Utils.launchCustomTab(v.getContext(), story.url);
            }
        });

        boolean hasReplyCount = !TextUtils.isEmpty(story.nitterInfo.replyCount);
        holder.nitterReplyCount.setVisibility(hasReplyCount ? VISIBLE : GONE);
        holder.nitterReplyImageView.setVisibility(hasReplyCount ? VISIBLE : GONE);

        boolean hasReposts = !TextUtils.isEmpty(story.nitterInfo.reposts);
        holder.nitterReposts.setVisibility(hasReposts ? VISIBLE : GONE);
        holder.nitterRetweetImageView.setVisibility(hasReposts ? VISIBLE : GONE);

        boolean hasLikes = !TextUtils.isEmpty(story.nitterInfo.likes);
        holder.nitterLikes.setVisibility(hasLikes ? VISIBLE : GONE);
        holder.nitterLikesImageView.setVisibility(hasLikes ? VISIBLE : GONE);

        boolean hasNitterImage = story.nitterInfo.imgSrc != null;
        holder.nitterMediaContainer.setVisibility(hasNitterImage ? VISIBLE : GONE);
        holder.nitterImage.setVisibility(hasNitterImage ? VISIBLE : GONE);
        holder.nitterVideoLabel.setVisibility(story.nitterInfo.hasVideo && hasNitterImage ? VISIBLE : GONE);

        if (hasNitterImage) {
            holder.nitterImage.setContentDescription(story.nitterInfo.hasVideo ? "Tweet video" : "Tweet image");
            holder.nitterImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (integratedWebview && bottomSheet != null) {
                        BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_COLLAPSED);
                    } else {
                        Utils.launchCustomTab(v.getContext(), story.url);
                    }
                }
            });
            try {
                ImageRequest request = new ImageRequest.Builder(ctx)
                        .data(story.nitterInfo.imgSrc)
                        .target(holder.nitterImage)
                        .build();
                Coil.imageLoader(ctx).enqueue(request);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
