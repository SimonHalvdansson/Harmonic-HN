package com.simon.harmonichackernews.ui.submissions

import android.net.Uri
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.StringRequest
import com.simon.harmonichackernews.MainActivity
import com.simon.harmonichackernews.adapters.StoryDisplaySettings
import com.simon.harmonichackernews.data.Story
import com.simon.harmonichackernews.network.BackgroundJSONParser
import com.simon.harmonichackernews.network.BackgroundJSONParser.AlgoliaParseCallback
import com.simon.harmonichackernews.network.JSONParser
import com.simon.harmonichackernews.network.NetworkComponent
import com.simon.harmonichackernews.utils.SettingsUtils
import com.simon.harmonichackernews.utils.Utils
import java.util.concurrent.Future
import org.json.JSONException

/** Networking and filtering state for a Compose submissions destination.  */
class SubmissionsCoordinator(
    private val activity: MainActivity,
    private val userName: String,
    private val navigator: Navigator,
) {
    fun interface Navigator {
        fun openStory(story: Story, showWebsite: Boolean)
    }

    private val submissions = mutableListOf<Story>()
    private val allSubmissions = mutableListOf<Story>()
    private val queue: RequestQueue = NetworkComponent.getRequestQueueInstance(activity)
    private val requestTag = Any()
    val composeController: SubmissionsComposeController
    private var submissionsParseTask: Future<*>? = null
    private var initialLoadFinished = false
    private var submissionsLoading = false
    private var submissionsLoadedSuccessfully = false
    private var submissionsRequestGeneration = 0
    private var submissionsHitsPerPage = ALGOLIA_HITS_INCREMENT
    private var submissionsCanLoadMore = false
    private var submissionFilter = SubmissionFilter.BOTH

    init {
        composeController = SubmissionsComposeController.create(
            activity,
            userName,
            submissionFilter,
            object : SubmissionsComposeController.Listener {
                override fun onFilterSelected(filter: SubmissionFilter) {
                    if (submissionFilter == filter) return
                    submissionFilter = filter
                    applySubmissionFilter()
                }

                override fun onRefresh() {
                    loadSubmissions(true)
                }

                override fun onStoryLinkClick(story: Story) {
                    if (story.isLink) {
                        if (SettingsUtils.shouldUseIntegratedWebView(activity)) {
                            openComments(story, true)
                        } else {
                            Utils.launchCustomTab(activity, story.url)
                        }
                    } else {
                        openComments(story, false)
                    }
                }

                override fun onStoryCommentsClick(story: Story) {
                    openComments(story, false)
                }

                override fun onCommentStoryClick(story: Story) {
                    openCommentMasterStory(story)
                }

                override fun onCommentRepliesClick(story: Story) {
                    openComments(story, false)
                }

                override fun onLoadMore() {
                    if (submissionsLoading || !submissionsCanLoadMore) return
                    submissionsHitsPerPage += ALGOLIA_HITS_INCREMENT
                    loadSubmissions(false)
                }
            })
        composeController.updateDisplaySettings(
            StoryDisplaySettings.from(activity).withShowIndex(false)
        )
        loadSubmissions(true)
    }

    fun close() {
        submissionsRequestGeneration++
        cancelSubmissionsParseTask()
        queue.cancelAll(requestTag)
    }

    private fun applySubmissionFilter() {
        submissions.clear()
        allSubmissions.filterTo(submissions, ::shouldShowStoryForSubmissionFilter)
        composeController.updateContent(
            submissions.toList(),
            submissionFilter,
            allSubmissions.isNotEmpty(),
            submissionsCanLoadMore,
            submissionsLoadedSuccessfully,
            emptyViewText,
        )
    }

    private fun shouldShowStoryForSubmissionFilter(story: Story): Boolean =
        when (submissionFilter) {
            SubmissionFilter.STORIES -> !story.isComment
            SubmissionFilter.COMMENTS -> story.isComment
            SubmissionFilter.BOTH -> true
        }

    private fun openCommentMasterStory(story: Story) {
        val masterStory = story.toCommentMasterStory()
        if (masterStory == null) {
            openComments(story, false)
            return
        }
        if (masterStory.loaded) {
            openComments(masterStory, false)
            return
        }

        val url = "https://hacker-news.firebaseio.com/v0/item/${masterStory.id}.json"
        val request = StringRequest(
            Request.Method.GET, url,
            Response.Listener { response: String? ->
                try {
                    JSONParser.updateCommentMasterStoryWithHNJson(story, response)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
                if (submissions.contains(story)) composeController.refreshStoryRows()
                val refreshed = story.toCommentMasterStory()
                openComments(refreshed ?: masterStory, false)
            }, Response.ErrorListener { _: VolleyError? -> openComments(masterStory, false) })
        request.setTag(requestTag)
        queue.add(request)
    }

    private fun openComments(story: Story, showWebsite: Boolean) {
        navigator.openStory(story, showWebsite)
    }

    private fun loadSubmissions(resetResultLimit: Boolean) {
        cancelSubmissionsParseTask()
        if (resetResultLimit) submissionsHitsPerPage = ALGOLIA_HITS_INCREMENT
        val requestGeneration = ++submissionsRequestGeneration
        submissionsLoading = true
        val showInitialLoading = !initialLoadFinished
        composeController.updateLoading(
            true,
            showInitialLoading,
            !showInitialLoading && resetResultLimit
        )
        updateEmptyView()

        val url = Uri.parse("https://hn.algolia.com/api/v1/search_by_date")
            .buildUpon()
            .appendQueryParameter("tags", "author_$userName")
            .appendQueryParameter("hitsPerPage", submissionsHitsPerPage.toString())
            .build()
            .toString()

        val request = StringRequest(
            Request.Method.GET, url,
            Response.Listener { response: String? ->
                submissionsParseTask = BackgroundJSONParser.parseAlgoliaJson(
                    response,
                    object : AlgoliaParseCallback {
                        override fun onParseSuccess(parsedStories: MutableList<Story>) {
                            if (requestGeneration != submissionsRequestGeneration) return
                            submissionsParseTask = null
                            finishLoading(requestGeneration)
                            submissionsCanLoadMore =
                                parsedStories.size >= submissionsHitsPerPage
                            submissionsLoadedSuccessfully = true
                            allSubmissions.clear()
                            allSubmissions.addAll(parsedStories)
                            applySubmissionFilter()
                        }

                        override fun onParseError(error: JSONException) {
                            if (requestGeneration != submissionsRequestGeneration) return
                            submissionsParseTask = null
                            finishLoading(requestGeneration)
                            error.printStackTrace()
                        }
                    })
            },
            Response.ErrorListener { error: VolleyError? ->
                error?.printStackTrace()
                finishLoading(requestGeneration)
            })
        request.setTag(requestTag)
        queue.add(request)
    }

    private fun cancelSubmissionsParseTask() {
        submissionsParseTask?.cancel(true)
        submissionsParseTask = null
    }

    private fun finishLoading(requestGeneration: Int) {
        if (requestGeneration != submissionsRequestGeneration) return
        submissionsLoading = false
        initialLoadFinished = true
        composeController.updateLoading(false, false, false)
        updateEmptyView()
    }

    private fun updateEmptyView() {
        composeController.updateContent(
            submissions.toList(),
            submissionFilter,
            allSubmissions.isNotEmpty(),
            submissionsCanLoadMore,
            submissionsLoadedSuccessfully,
            emptyViewText,
        )
    }

    private val emptyViewText: String
        get() = when {
            allSubmissions.isEmpty() -> "No submissions"
            submissionFilter == SubmissionFilter.STORIES -> "No stories"
            submissionFilter == SubmissionFilter.COMMENTS -> "No comments"
            else -> "No submissions"
        }

    companion object {
        private const val ALGOLIA_HITS_INCREMENT = 200
    }
}
