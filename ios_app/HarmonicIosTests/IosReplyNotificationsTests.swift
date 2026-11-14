import BackgroundTasks
import HarmonicKit
import UserNotifications
import XCTest
@testable import HarmonicIos

@MainActor
final class IosReplyNotificationsTests: XCTestCase {
    private let date = Date(timeIntervalSince1970: 1_000)

    private func schedule(_ interval: Int64 = 1_800_000, delay: Int64? = nil) -> ReplyNotificationSchedule {
        ReplyNotificationSchedule(intervalMillis: interval, flexMillis: 300_000, wifiOnly: false,
                                  delayMillis: delay.map { KotlinLong(longLong: $0) })
    }
    private func payload(_ id: Int32 = 150) -> ReplyNotificationPayload {
        ReplyNotificationPayload(id: id, title: "New reply from qa_fixture", body: "A local reply fixture",
                                 deepLink: "https://news.ycombinator.com/item?id=100#\(id)", author: "qa_fixture")
    }

    func testSchedulingHonorsIntervalQuietHoursAndDoesNotPostponeOnResume() {
        let scheduler = FakeReplyScheduler()
        let service = IosReplyNotifications(center: FakeReplyCenter(), scheduler: scheduler, now: { self.date })
        service.scheduleChecks(schedule: schedule())
        XCTAssertEqual(scheduler.requests.single?.earliestBeginDate, date.addingTimeInterval(1_800))
        service.scheduleChecks(schedule: schedule())
        XCTAssertEqual(scheduler.submissions, 1)
        service.scheduleChecks(schedule: schedule(900_000, delay: 7_200_000))
        XCTAssertEqual(scheduler.requests.single?.earliestBeginDate, date.addingTimeInterval(7_200))
        scheduler.requests = [] // iOS consumed this request at wakeup.
        service.scheduleChecks(schedule: schedule(900_000))
        XCTAssertEqual(scheduler.requests.single?.earliestBeginDate, date.addingTimeInterval(900))
    }

    func testLongScanFallbackRequiresNetworkAndDoesNotDuplicateOrSurviveDisable() {
        let scheduler = FakeReplyScheduler()
        let service = IosReplyNotifications(center: FakeReplyCenter(), scheduler: scheduler)
        service.scheduleChecks(schedule: schedule())
        service.scheduleCatchUp()
        service.scheduleCatchUp()
        let catchUps = scheduler.requests.compactMap { $0 as? BGProcessingTaskRequest }
        XCTAssertEqual(catchUps.count, 1)
        XCTAssertTrue(catchUps[0].requiresNetworkConnectivity)
        XCTAssertFalse(catchUps[0].requiresExternalPower)
        service.cancelChecks()
        service.scheduleCatchUp()
        XCTAssertTrue(scheduler.requests.isEmpty)
    }

    func testLatePendingCallbackCannotRescheduleAfterDisable() {
        let scheduler = FakeReplyScheduler()
        scheduler.deferPending = true
        let center = FakeReplyCenter()
        let service = IosReplyNotifications(center: center, scheduler: scheduler)
        service.scheduleChecks(schedule: schedule())
        service.cancelChecks()
        scheduler.pendingCallback?([])
        XCTAssertEqual(scheduler.submissions, 0)
        XCTAssertEqual(scheduler.cancellations, 1)
    }

    func testPublishingGroupsRepliesWithoutDuplicatingSummaryAndWaitsForNativeDelivery() {
        let center = FakeReplyCenter()
        center.deferAdd = true
        let service = IosReplyNotifications(center: center, scheduler: FakeReplyScheduler())
        var completed = false
        let batch = ReplyNotificationBatch(notifications: [payload(), payload(151)], summary: payload(98373))
        service.publish(batch: batch) { error in XCTAssertNil(error); completed = true }
        XCTAssertFalse(completed)
        XCTAssertEqual(center.requests.count, 1)
        center.addCallback?(nil)
        XCTAssertFalse(completed)
        XCTAssertEqual(center.requests.count, 2)
        center.addCallback?(nil)
        XCTAssertTrue(completed)
        XCTAssertEqual(center.requests.map(\.identifier), ["harmonic.reply.150", "harmonic.reply.151"])
        XCTAssertEqual(center.requests[0].content.threadIdentifier, "harmonic.replies")
        XCTAssertEqual(center.requests[0].content.body, "A local reply fixture")
        XCTAssertEqual(center.requests[0].content.userInfo["replyLink"] as? String, "https://news.ycombinator.com/item?id=100#150")
    }

    func testDeniedPermissionSuppressesAlertsAndRefreshReflectsSettingsChanges() {
        let center = FakeReplyCenter()
        center.allowed = false
        let service = IosReplyNotifications(center: center, scheduler: FakeReplyScheduler())
        service.refresh { XCTAssertFalse($0.boolValue) }
        service.requestPermission { XCTAssertFalse($0.boolValue) }
        service.publish(batch: ReplyNotificationBatch(notifications: [payload()], summary: nil)) { XCTAssertNil($0) }
        XCTAssertTrue(center.requests.isEmpty)
        center.allowed = true
        service.refresh { XCTAssertTrue($0.boolValue) }
    }

    func testDisablingDuringDeliveryStopsTheRemainingBatch() {
        let center = FakeReplyCenter()
        center.deferAdd = true
        let service = IosReplyNotifications(center: center, scheduler: FakeReplyScheduler())
        var completions = 0
        service.publish(batch: ReplyNotificationBatch(notifications: [payload(), payload(151)], summary: nil)) { _ in completions += 1 }
        service.cancelChecks()
        center.addCallback?(nil)
        XCTAssertEqual(center.requests.count, 1)
        XCTAssertEqual(completions, 1)
    }

    func testPublicationFailureIsReturnedToTheScanner() {
        let center = FakeReplyCenter()
        center.error = NSError(domain: "fixture", code: 1)
        let service = IosReplyNotifications(center: center, scheduler: FakeReplyScheduler())
        var failed = false
        service.publish(batch: ReplyNotificationBatch(notifications: [payload(), payload(151)], summary: nil)) { failed = $0 != nil }
        XCTAssertTrue(failed)
        XCTAssertEqual(center.requests.count, 1)
    }

    func testColdLaunchQueuesReplyAndDeduplicatesSceneAndDelegateCallbacks() {
        let service = IosReplyNotifications(center: FakeReplyCenter(), scheduler: FakeReplyScheduler())
        let info = ["replyLink": payload().deepLink]
        service.receiveReply(identifier: "harmonic.reply.150", userInfo: info)
        var links: [String] = []
        service.attachReplyOpener { links.append($0); return true }
        service.receiveReply(identifier: "harmonic.reply.150", userInfo: info)
        service.receiveReply(identifier: "harmonic.reply.151", userInfo: ["replyLink": payload(151).deepLink])
        XCTAssertEqual(links, [payload().deepLink, payload(151).deepLink])
    }

    func testDestroyedSceneQueuesReplyForItsReplacementAndRepublishedAlertCanOpenAgain() {
        let service = IosReplyNotifications(center: FakeReplyCenter(), scheduler: FakeReplyScheduler())
        service.attachReplyOpener { _ in false }
        service.receiveReply(identifier: "harmonic.reply.150", userInfo: ["replyLink": payload().deepLink], deliveredAt: date)
        var links: [String] = []
        service.attachReplyOpener { links.append($0); return true }
        service.receiveReply(identifier: "harmonic.reply.150", userInfo: ["replyLink": payload().deepLink], deliveredAt: date.addingTimeInterval(1))
        XCTAssertEqual(links.count, 2)
    }

    func testNotificationLinksRejectExternalMalformedAndNonPositiveIds() {
        for link in ["https://evil.example/item?id=1", "javascript:alert(1)",
                     "https://news.ycombinator.com/item?id=0", "https://news.ycombinator.com/item?id=1#-1",
                     "https://news.ycombinator.com@evil.example/item?id=1"] {
            XCTAssertFalse(IosReplyNotifications.validReplyLink(link), link)
        }
        XCTAssertTrue(IosReplyNotifications.validReplyLink(payload().deepLink))
    }

    func testExpiryCancelsWorkAndCompletesOnceEvenWithALateCallback() {
        var results: [Bool] = []
        var cancellations = 0
        let run = IosReplyBackgroundRun { results.append($0) }
        run.cancelWork = { cancellations += 1 }
        run.expire()
        run.finish(true)
        run.expire()
        XCTAssertEqual(results, [false])
        XCTAssertEqual(cancellations, 1)
    }

    func testSuccessfulBackgroundCompletionDoesNotCancelOrCompleteAgainOnExpiry() {
        var results: [Bool] = []
        let run = IosReplyBackgroundRun { results.append($0) }
        run.cancelWork = { XCTFail("Completed work must not be cancelled") }
        run.finish(true)
        run.expire()
        XCTAssertEqual(results, [true])
    }

    func testBackgroundTaskIsDeclaredInHostConfiguration() {
        XCTAssertTrue((Bundle.main.object(forInfoDictionaryKey: "BGTaskSchedulerPermittedIdentifiers") as? [String])?.contains(IosReplyNotifications.taskIdentifier) == true)
        XCTAssertTrue((Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String])?.contains("fetch") == true)
        XCTAssertTrue((Bundle.main.object(forInfoDictionaryKey: "UIBackgroundModes") as? [String])?.contains("processing") == true)
        XCTAssertTrue((Bundle.main.object(forInfoDictionaryKey: "BGTaskSchedulerPermittedIdentifiers") as? [String])?.contains(IosReplyNotifications.catchUpIdentifier) == true)
    }
}

private extension Array { var single: Element? { count == 1 ? first : nil } }

private final class FakeReplyCenter: ReplyNotificationCenter {
    var allowed = true
    var requests: [UNNotificationRequest] = []
    var deferAdd = false
    var addCallback: ((Error?) -> Void)?
    var error: Error?
    func deliveryAllowed(_ completion: @escaping (Bool) -> Void) { completion(allowed) }
    func requestPermission(_ completion: @escaping (Bool) -> Void) { completion(allowed) }
    func add(_ request: UNNotificationRequest, completion: @escaping (Error?) -> Void) {
        requests.append(request)
        if deferAdd { addCallback = completion } else { completion(error) }
    }
}

private final class FakeReplyScheduler: ReplyRefreshScheduler {
    var requests: [BGTaskRequest] = []
    var submissions = 0
    var cancellations = 0
    var deferPending = false
    var pendingCallback: (([BGTaskRequest]) -> Void)?
    func pending(_ completion: @escaping ([BGTaskRequest]) -> Void) {
        if deferPending { pendingCallback = completion } else { completion(requests) }
    }
    func submit(_ request: BGTaskRequest) { submissions += 1; requests.removeAll { $0.identifier == request.identifier }; requests.append(request) }
    func cancel() { cancellations += 1; requests = [] }
    func cancelCatchUp() { requests.removeAll { $0.identifier == IosReplyNotifications.catchUpIdentifier } }
}
