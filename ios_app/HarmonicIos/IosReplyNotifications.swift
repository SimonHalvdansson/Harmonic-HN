import BackgroundTasks
import Foundation
import HarmonicKit
import Network
import UIKit
import UserNotifications

protocol ReplyNotificationCenter {
    func deliveryAllowed(_ completion: @escaping (Bool) -> Void)
    func requestPermission(_ completion: @escaping (Bool) -> Void)
    func add(_ request: UNNotificationRequest, completion: @escaping (Error?) -> Void)
}

private final class SystemReplyNotificationCenter: ReplyNotificationCenter {
    let center = UNUserNotificationCenter.current()

    func deliveryAllowed(_ completion: @escaping (Bool) -> Void) {
        center.getNotificationSettings { settings in
            let allowed = [.authorized, .provisional, .ephemeral].contains(settings.authorizationStatus)
            DispatchQueue.main.async { completion(allowed) }
        }
    }

    func requestPermission(_ completion: @escaping (Bool) -> Void) {
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            DispatchQueue.main.async { completion(granted) }
        }
    }

    func add(_ request: UNNotificationRequest, completion: @escaping (Error?) -> Void) {
        center.add(request) { error in DispatchQueue.main.async { completion(error) } }
    }
}

protocol ReplyRefreshScheduler {
    func pending(_ completion: @escaping ([BGTaskRequest]) -> Void)
    func submit(_ request: BGTaskRequest) throws
    func cancel()
    func cancelCatchUp()
}

private final class SystemReplyRefreshScheduler: ReplyRefreshScheduler {
    func pending(_ completion: @escaping ([BGTaskRequest]) -> Void) {
        BGTaskScheduler.shared.getPendingTaskRequests { requests in
            DispatchQueue.main.async { completion(requests) }
        }
    }
    func submit(_ request: BGTaskRequest) throws { try BGTaskScheduler.shared.submit(request) }
    func cancel() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: IosReplyNotifications.taskIdentifier)
        cancelCatchUp()
    }
    func cancelCatchUp() { BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: IosReplyNotifications.catchUpIdentifier) }
}

/// Completes each OS task exactly once, including expiry before connectivity/account loading ends.
final class IosReplyBackgroundRun {
    private(set) var finished = false
    var cancelWork: (() -> Void)?
    private let complete: (Bool) -> Void
    init(complete: @escaping (Bool) -> Void) { self.complete = complete }
    func finish(_ success: Bool) {
        guard !finished else { return }
        finished = true
        cancelWork = nil
        complete(success)
    }
    func expire() {
        guard !finished else { return }
        let cancel = cancelWork
        finish(false)
        cancel?()
    }
}

final class IosReplyNotifications: NSObject, ReplyNotificationPlatform, NotificationSettingsHost, UNUserNotificationCenterDelegate {
    static let taskIdentifier = "com.simon.harmonichackernews.ios.replies"
    static let catchUpIdentifier = "com.simon.harmonichackernews.ios.replies.catchup"
    static let notificationPrefix = "harmonic.reply."
    static let shared = IosReplyNotifications()
    private let center: ReplyNotificationCenter
    private let scheduler: ReplyRefreshScheduler
    private let now: () -> Date
    private var scheduleRevision = 0
    private var deliveryRevision = 0
    private var previousSchedule: ReplyNotificationSchedule?
    private var checks: (() -> IosReplyNotificationChecks)?
    private var openReply: ((String) -> Bool)?
    private var pendingReply: String?
    private var lastResponse: String?

    var supported: Bool { true }
    var settingsHost: NotificationSettingsHost? { self }

    init(center: ReplyNotificationCenter = SystemReplyNotificationCenter(),
         scheduler: ReplyRefreshScheduler = SystemReplyRefreshScheduler(), now: @escaping () -> Date = Date.init) {
        self.center = center
        self.scheduler = scheduler
        self.now = now
        super.init()
    }

    func registerBackgroundChecks(_ checks: @escaping () -> IosReplyNotificationChecks) {
        self.checks = checks
        UNUserNotificationCenter.current().delegate = self
        for identifier in [Self.taskIdentifier, Self.catchUpIdentifier] {
            BGTaskScheduler.shared.register(forTaskWithIdentifier: identifier, using: .main) { [weak self] task in
                guard let self else { task.setTaskCompleted(success: false); return }
                let catchUp = task.identifier == Self.catchUpIdentifier
                let run = IosReplyBackgroundRun { success in
                    task.expirationHandler = nil
                    if success { self.scheduler.cancelCatchUp() }
                    else if !catchUp { self.scheduleCatchUp() }
                    NSLog("Harmonic reply refresh completed (success: %@).", String(success))
                    task.setTaskCompleted(success: success)
                }
                task.expirationHandler = { DispatchQueue.main.async { run.expire() } }
                // Even an expiry while waiting for the first network-path callback needs a successor.
                self.rescheduleChecks()
                Self.readWifi { wifi in
                    guard !run.finished else { return }
                    guard let checks = self.checks?() else { run.finish(false); return }
                    let handle = checks.start(checkForReplies: true, wifiAvailable: wifi, timeoutMillis: catchUp ? 240_000 : 25_000) { success in
                        run.finish(success.boolValue)
                    }
                    if !run.finished {
                        run.cancelWork = { self.deliveryRevision += 1; handle.cancel() }
                    }
                }
            }
        }
    }

    func scheduleCatchUp() {
        guard previousSchedule != nil else { return }
        let revision = scheduleRevision
        scheduler.pending { requests in
            guard revision == self.scheduleRevision, self.previousSchedule != nil,
                  !requests.contains(where: { $0.identifier == Self.catchUpIdentifier }) else { return }
            let request = BGProcessingTaskRequest(identifier: Self.catchUpIdentifier)
            request.requiresNetworkConnectivity = true
            request.requiresExternalPower = false
            do { try self.scheduler.submit(request) }
            catch { NSLog("Harmonic reply catch-up could not be scheduled: %@", error.localizedDescription) }
        }
    }

    func rescheduleChecks() {
        _ = checks?().start(checkForReplies: false, wifiAvailable: false, timeoutMillis: 25_000) { _ in }
    }

    func prepareNotifications() { /* Permission is requested explicitly by Account/Debug settings. */ }

    func refresh(onResult: @escaping (KotlinBoolean) -> Void) {
        onMain { self.center.deliveryAllowed { onResult(KotlinBoolean(bool: $0)) } }
    }

    func requestPermission(onResult: @escaping (KotlinBoolean) -> Void) {
        onMain { self.center.requestPermission { onResult(KotlinBoolean(bool: $0)) } }
    }

    func openSystemSettings() {
        onMain {
            guard let url = URL(string: UIApplication.openNotificationSettingsURLString) else { return }
            UIApplication.shared.open(url)
        }
    }

    func scheduleChecks(schedule: ReplyNotificationSchedule) {
        onMain {
            self.scheduleRevision += 1
            let revision = self.scheduleRevision
            let changed = self.previousSchedule.map { !$0.isEqual(schedule) } ?? false
            self.previousSchedule = schedule
            self.scheduler.pending { requests in
                guard revision == self.scheduleRevision else { return }
                // Foreground resumes must not continually push an already queued refresh back.
                if !changed && requests.contains(where: { $0.identifier == Self.taskIdentifier }) { return }
                let request = BGAppRefreshTaskRequest(identifier: Self.taskIdentifier)
                let delay = max(15 * 60, Double(schedule.intervalMillis) / 1_000,
                                Double(schedule.delayMillis?.int64Value ?? 0) / 1_000)
                request.earliestBeginDate = self.now().addingTimeInterval(delay)
                do { try self.scheduler.submit(request) }
                catch { NSLog("Harmonic reply refresh could not be scheduled: %@", error.localizedDescription) }
            }
        }
    }

    func cancelChecks() {
        onMain {
            self.scheduleRevision += 1
            self.deliveryRevision += 1
            self.previousSchedule = nil
            self.scheduler.cancel()
        }
    }

    func publish(batch: ReplyNotificationBatch, completionHandler: @escaping (Error?) -> Void) {
        onMain {
            let revision = self.deliveryRevision
            self.center.deliveryAllowed { allowed in
                guard allowed, revision == self.deliveryRevision else { completionHandler(nil); return }
                let payloads = batch.notifications
                func next(_ index: Int) {
                    guard revision == self.deliveryRevision, index < payloads.count else { completionHandler(nil); return }
                    let payload = payloads[index]
                    guard Self.validReplyLink(payload.deepLink) else { next(index + 1); return }
                    let content = UNMutableNotificationContent()
                    content.title = payload.title
                    content.body = payload.body
                    content.sound = .default
                    content.threadIdentifier = "harmonic.replies"
                    content.userInfo = ["replyLink": payload.deepLink]
                    let request = UNNotificationRequest(identifier: Self.notificationPrefix + String(payload.id),
                                                        content: content, trigger: nil)
                    self.center.add(request) { error in
                        if let error { completionHandler(error) } else { next(index + 1) }
                    }
                }
                // iOS groups by threadIdentifier; publishing the Android summary duplicates alerts.
                next(0)
            }
        }
    }

    func attachReplyOpener(_ open: @escaping (String) -> Bool) {
        openReply = open
        if let pendingReply, open(pendingReply) { self.pendingReply = nil }
    }

    func openResponse(_ response: UNNotificationResponse) {
        guard response.actionIdentifier == UNNotificationDefaultActionIdentifier else { return }
        receiveReply(identifier: response.notification.request.identifier,
                     userInfo: response.notification.request.content.userInfo,
                     deliveredAt: response.notification.date)
    }

    func receiveReply(identifier: String, userInfo: [AnyHashable: Any], deliveredAt: Date = .distantPast) {
        let responseKey = "\(identifier):\(deliveredAt.timeIntervalSince1970)"
        guard identifier.hasPrefix(Self.notificationPrefix), responseKey != lastResponse,
              let link = userInfo["replyLink"] as? String, Self.validReplyLink(link) else { return }
        lastResponse = responseKey
        if openReply?(link) != true { pendingReply = link }
    }

    static func validReplyLink(_ link: String) -> Bool {
        guard let url = URLComponents(string: link), url.scheme == "https", url.host == "news.ycombinator.com",
              url.user == nil, url.password == nil, url.port == nil, url.path == "/item",
              let id = url.queryItems?.first(where: { $0.name == "id" })?.value,
              let item = Int32(id), item > 0 else { return false }
        return url.fragment == nil || (Int32(url.fragment ?? "").map { $0 > 0 } ?? false)
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        onMain { self.openResponse(response); completionHandler() }
    }

    private func onMain(_ work: @escaping () -> Void) {
        if Thread.isMainThread { work() } else { DispatchQueue.main.async(execute: work) }
    }

    private static func readWifi(_ completion: @escaping (Bool) -> Void) {
        let monitor = NWPathMonitor()
        var completed = false
        func finish(_ wifi: Bool) {
            guard !completed else { return }
            completed = true
            monitor.pathUpdateHandler = nil
            monitor.cancel()
            completion(wifi)
        }
        monitor.pathUpdateHandler = { path in
            DispatchQueue.main.async { finish(path.status == .satisfied && path.usesInterfaceType(.wifi)) }
        }
        monitor.start(queue: DispatchQueue(label: "Harmonic.ReplyConnectivity"))
        DispatchQueue.main.asyncAfter(deadline: .now() + 2) { finish(false) }
    }
}
