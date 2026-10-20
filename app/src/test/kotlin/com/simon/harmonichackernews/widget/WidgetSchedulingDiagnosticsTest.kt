package com.simon.harmonichackernews.widget

import android.app.Application
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.WorkInfo
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetSchedulingDiagnosticsTest {
    private val originalTimeZone = TimeZone.getDefault()
    private val context get() = RuntimeEnvironment.getApplication()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun manualRefreshKeepsTheLastAndroidUpdateAndPreviousStartAcrossRecreation() {
        val automatic = UUID.randomUUID()
        val manual = UUID.randomUUID()
        WidgetSchedulingDiagnostics(context, 25).apply {
            recordAndroidUpdate(1_000)
            recordEnqueued(automatic, "Android update", 2_000)
            recordStarted(automatic, 0, 3_000)
            recordEnqueued(manual, "manual", 4_000)
        }

        val snapshot = WidgetSchedulingDiagnostics(context, 25).snapshot(
            listOf(work(manual, WorkInfo.State.ENQUEUED)), lastSuccess = 3_500, now = 5_000,
        )
        assertTrue(snapshot.contains("Observed: 01-01 00:00:05"))
        assertTrue(snapshot.contains("Android update: 01-01 00:00:01"))
        assertTrue(snapshot.contains("Queued: 01-01 00:00:04 · manual · ${manual.toString().take(8)}"))
        assertTrue(snapshot.contains("Started: 01-01 00:00:03 · ${automatic.toString().take(8)} · attempt 1"))
        assertTrue(snapshot.contains("Last success: 01-01 00:00:03"))
    }

    @Test
    fun showsLatestRequestInsteadOfItsCancelledPredecessorAndReportsRetryEligibility() {
        val previous = UUID.randomUUID()
        val current = UUID.randomUUID()
        val diagnostics = WidgetSchedulingDiagnostics(context, 25)
        diagnostics.recordEnqueued(current, "Android update", 1_000)
        val snapshot = diagnostics.snapshot(
            listOf(
                work(previous, WorkInfo.State.CANCELLED),
                work(current, WorkInfo.State.ENQUEUED, runs = 1, nextRun = 40_000,
                    stopReason = WorkInfo.STOP_REASON_CONSTRAINT_CONNECTIVITY),
            ), lastSuccess = null, now = 10_000,
        )
        assertTrue(snapshot.contains("Job: ENQUEUED · runs=1 · requires CONNECTED"))
        assertTrue(snapshot.contains("Eligible after: 01-01 00:00:40"))
        assertTrue(snapshot.contains("Last stop: connectivity constraint"))
        assertFalse(snapshot.contains("CANCELLED"))
    }

    @Test
    fun completedJobsDoNotImplyAnotherRunAndMissingJobsAreNotReportedAsSuccessful() {
        val id = UUID.randomUUID()
        val diagnostics = WidgetSchedulingDiagnostics(context, 25)
        diagnostics.recordEnqueued(id, "manual", 1_000)
        val completed = diagnostics.snapshot(listOf(work(id, WorkInfo.State.SUCCEEDED)), 2_000, now = 3_000)
        assertTrue(completed.contains("Job: SUCCEEDED"))
        assertFalse(completed.contains("Eligible after:"))
        assertFalse(completed.contains("Last stop:"))
        val missing = diagnostics.snapshot(emptyList(), 2_000, now = 3_000)
        assertTrue(missing.contains("Job: not found (pending enqueue or pruned)"))
        assertFalse(missing.contains("SUCCEEDED"))
    }

    @Test
    fun missingHistoryIsExplicitAndCleanupIsIsolatedToTheDeletedWidget() {
        val first = WidgetSchedulingDiagnostics(context, 25)
        val other = WidgetSchedulingDiagnostics(context, 26)
        first.recordAndroidUpdate(1_000)
        other.recordAndroidUpdate(2_000)
        first.clear()
        val empty = first.snapshot(emptyList(), null, now = 3_000)
        assertTrue(empty.contains("Android update: not recorded"))
        assertTrue(empty.contains("Queued: not recorded"))
        assertTrue(empty.contains("Started: not recorded"))
        assertTrue(empty.contains("Job: not recorded"))
        assertTrue(empty.contains("Last success: not recorded"))
        assertTrue(other.snapshot(emptyList(), null, now = 3_000).contains("Android update: 01-01 00:00:02"))
    }

    private fun work(
        id: UUID,
        state: WorkInfo.State,
        runs: Int = 0,
        nextRun: Long = Long.MAX_VALUE,
        stopReason: Int = WorkInfo.STOP_REASON_NOT_STOPPED,
    ) = WorkInfo(
        id = id,
        state = state,
        tags = emptySet(),
        runAttemptCount = runs,
        constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
        nextScheduleTimeMillis = nextRun,
        stopReason = stopReason,
    )
}
