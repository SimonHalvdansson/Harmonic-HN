package com.simon.harmonichackernews.ui.submissions

import android.net.Uri
import androidx.annotation.NonNull
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
    activity: MainActivity,
    userName: String,
    navigator: Navigator
) {
    fun interface Navigator {
        fun openStory(story: Story, showWebsite: Boolean)
    }

    private val activity: MainActivity
    private val userName: String
    private val navigator: Navigator
    private val submissions: ArrayList<Story> = ArrayList()
    private val allSubmissions: ArrayList<Story> = ArrayList<Story>()
    private val queue: RequestQueue
    private val requestTag = Any()
    val composeController: SubmissionsComposeController
    private var submissionsParseTask: Future<*>? = null
    private var initialLoadFinished = false
    private var submissionsLoading = false
    private var submissionsLoadedSuccessfully = false
    private var submissionsRequestGeneration = 0
    private var submissionsHitsPerPage: Int = ALGOLIA_HITS_INCREMENT
    private var submissionsCanLoadMore = false
    private var submissionFilter: Int = SUBMISSION_FILTER_BOTH

    init {
        this.activity = activity
        this.userName = userName
        this.navigator = navigator
        queue = NetworkComponent.getRequestQueueInstance(activity)
        composeController = SubmissionsComposeController.create(
            activity,
            userName,
            submissionFilter,
            object : SubmissionsComposeController.Listener {
                override fun onFilterSelected(filter: Int) {
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
        for (story in allSubmissions) {
            if (shouldShowStoryForSubmissionFilter(story)) submissions.add(story)
        }
        composeController.updateContent(
            ArrayList(submissions),
            submissionFilter,
            !allSubmissions.isEmpty(),
            submissionsCanLoadMore,
            submissionsLoadedSuccessfully,
            this.emptyViewText
        )
    }

    private fun shouldShowStoryForSubmissionFilter(story: Story): Boolean {
        if (submissionFilter == SUBMISSION_FILTER_STORIES) return !story.isComment
        if (submissionFilter == SUBMISSION_FILTER_COMMENTS) return story.isComment
        return true
    }

    private fun openCommentMasterStory(story: Story) {
        val masterStory: Story? = story.toCommentMasterStory()
        if (masterStory == null) {
            openComments(story, false)
            return
        }
        if (masterStory.loaded) {
            openComments(masterStory, false)
            return
        }

        val url = "https://hacker-news.firebaseio.com/v0/item/" + masterStory.id + ".json"
        val request: StringRequest = StringRequest(
            Request.Method.GET, url,
            Response.Listener { response: String? ->
                try {
                    JSONParser.updateCommentMasterStoryWithHNJson(story, response)
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
                if (submissions.contains(story)) composeController.refreshStoryRows()
                val refreshed: Story? = story.toCommentMasterStory()
                openComments(if (refreshed != null) refreshed else masterStory, false)
            }, Response.ErrorListener { error: VolleyError? -> openComments(masterStory, false) })
        request.setTag(requestTag)
        queue.add<String?>(request)
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
            .appendQueryParameter("tags", "author_" + userName)
            .appendQueryParameter("hitsPerPage", submissionsHitsPerPage.toString())
            .build()
            .toString()

        val request: StringRequest = StringRequest(
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
        queue.add<String?>(request)
    }

    private fun cancelSubmissionsParseTask() {
        if (submissionsParseTask == null) return
        submissionsParseTask!!.cancel(true)
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
            ArrayList(submissions),
            submissionFilter,
            !allSubmissions.isEmpty(),
            submissionsCanLoadMore,
            submissionsLoadedSuccessfully,
            this.emptyViewText
        )
    }

    private val emptyViewText: String
        get() {
            if (allSubmissions.isEmpty()) return "No submissions"
            if (submissionFilter == SUBMISSION_FILTER_STORIES) return "No stories"
            if (submissionFilter == SUBMISSION_FILTER_COMMENTS) return "No comments"
            return "No submissions"
        }

    companion object {
        private const val SUBMISSION_FILTER_STORIES = 0
        private const val SUBMISSION_FILTER_BOTH = 1
        private const val SUBMISSION_FILTER_COMMENTS = 2
        private const val ALGOLIA_HITS_INCREMENT = 200
    }
}
