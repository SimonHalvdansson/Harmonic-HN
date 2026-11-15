import AVFoundation
import MediaPlayer
import HarmonicKit

protocol IosSpeechSynthesizing: AnyObject {
    var onFinish: ((AVSpeechUtterance) -> Void)? { get set }
    var onCancel: ((AVSpeechUtterance) -> Void)? { get set }
    func speak(_ utterance: AVSpeechUtterance)
    func stop()
}

private final class SystemSpeechSynthesizer: NSObject, IosSpeechSynthesizing, AVSpeechSynthesizerDelegate {
    var onFinish: ((AVSpeechUtterance) -> Void)?
    var onCancel: ((AVSpeechUtterance) -> Void)?
    private let synthesizer = AVSpeechSynthesizer()

    override init() {
        super.init()
        synthesizer.delegate = self
        synthesizer.usesApplicationAudioSession = true
    }

    func speak(_ utterance: AVSpeechUtterance) { synthesizer.speak(utterance) }
    func stop() { synthesizer.stopSpeaking(at: .immediate) }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        onFinish?(utterance)
    }
    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        onCancel?(utterance)
    }
}

// Called only on the host's serial audio queue.
protocol IosSpeechAudioSession {
    func activate() throws
    func deactivate() throws
}

private struct SystemSpeechAudioSession: IosSpeechAudioSession {
    func activate() throws {
        let audio = AVAudioSession.sharedInstance()
        try audio.setCategory(.playback, mode: .spokenAudio)
        try audio.setActive(true)
    }

    func deactivate() throws {
        try AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}

/// Owned by the process graph, independently of the article's WKWebView or UIWindowScene.
final class IosReadAloud: NSObject, IosReadAloudHost {
    private var synthesizer: IosSpeechSynthesizing
    private let makeSynthesizer: () -> IosSpeechSynthesizing
    private let audioSession: IosSpeechAudioSession
    private let audioQueue: DispatchQueue
    private var utterance: AVSpeechUtterance?
    private var completion: ((String?) -> Void)?
    private var commandHandler: ((String) -> Void)?
    private var remoteTargets: [(MPRemoteCommand, Any)] = []
    private var observations: [NSObjectProtocol] = []
    private var playing = false
    private var hasArticle = false
    private static let systemAudioQueue = DispatchQueue(label: "Harmonic speech audio session")
    // Access only on audioQueue; activation can block while negotiating an output route.
    private var audioActive = false
    private var generation: UInt64 = 0
    private var closed = false

    override convenience init() {
        self.init(makeSynthesizer: { SystemSpeechSynthesizer() },
                  audioSession: SystemSpeechAudioSession(), audioQueue: Self.systemAudioQueue)
    }

    init(makeSynthesizer: @escaping () -> IosSpeechSynthesizing,
         audioSession: IosSpeechAudioSession, audioQueue: DispatchQueue) {
        self.makeSynthesizer = makeSynthesizer
        self.synthesizer = makeSynthesizer()
        self.audioSession = audioSession
        self.audioQueue = audioQueue
        super.init()
        configureSynthesizer()
        let center = MPRemoteCommandCenter.shared()
        register(center.playCommand, action: "play")
        register(center.pauseCommand, action: "pause")
        register(center.stopCommand, action: "stop")
        register(center.nextTrackCommand, action: "next")
        register(center.previousTrackCommand, action: "previous")
        register(center.togglePlayPauseCommand) { [weak self] _ in
            ["action": self?.playing == true ? "pause" : "play"]
        }
        center.changePlaybackRateCommand.supportedPlaybackRates = [0.75, 1, 1.25, 1.5, 2]
        register(center.changePlaybackRateCommand) { event in
            guard let event = event as? MPChangePlaybackRateCommandEvent else { return nil }
            return ["action": "rate", "rate": event.playbackRate]
        }
        let notifications = NotificationCenter.default
        observations.append(notifications.addObserver(
            forName: AVAudioSession.interruptionNotification, object: nil, queue: .main
        ) { [weak self] notification in
            guard let type = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  type == AVAudioSession.InterruptionType.began.rawValue else { return }
            self?.send(["action": "pause"])
        })
        observations.append(notifications.addObserver(
            forName: AVAudioSession.routeChangeNotification, object: nil, queue: .main
        ) { [weak self] notification in
            guard let reason = notification.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
                  reason == AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue else { return }
            self?.send(["action": "pause"])
        })
        observations.append(notifications.addObserver(
            forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            self.audioQueue.async { self.audioActive = false }
            self.send(["action": "pause"])
            self.stop()
            self.synthesizer = self.makeSynthesizer()
            self.configureSynthesizer()
        })
    }

    func setCommandHandler(handler: ((String) -> Void)?) {
        commandHandler = handler
    }

    func speak(text: String, rate: Float, completed: @escaping (String?) -> Void) {
        guard !closed else { return }
        stop()
        let expected = generation
        let next = AVSpeechUtterance(string: text)
        next.rate = min(AVSpeechUtteranceMaximumSpeechRate,
                        max(AVSpeechUtteranceMinimumSpeechRate, AVSpeechUtteranceDefaultSpeechRate * rate))
        utterance = next
        completion = completed
        audioQueue.async { [self] in
            var activationError: String?
            do {
                if !audioActive {
                    try audioSession.activate()
                    audioActive = true
                }
            } catch {
                activationError = "Could not start audio playback. Try again when audio is available."
            }
            let error = activationError
            DispatchQueue.main.async { [self] in
                guard !closed, expected == generation else { return }
                if let error { finish(next, error: error) }
                else { synthesizer.speak(next) }
            }
        }
    }

    func stop() {
        // Clear identity before stopping: cancellation of an old utterance must not advance a new one.
        generation &+= 1
        utterance = nil
        completion = nil
        synthesizer.stop()
    }

    private func configureSynthesizer() {
        synthesizer.onFinish = { [weak self] in self?.finish($0, error: nil) }
        synthesizer.onCancel = { [weak self] in
            self?.finish($0, error: "Speech was interrupted. Tap Resume to continue.")
        }
    }

    private func finish(_ finished: AVSpeechUtterance, error: String?) {
        // All shared-controller mutations run on UIKit's main dispatcher, including delegate callbacks.
        DispatchQueue.main.async { [weak self] in
            guard let self, !self.closed, self.utterance === finished else { return }
            let callback = self.completion
            self.utterance = nil
            self.completion = nil
            callback?(error)
        }
    }

    func update(state: ReadAloudState) {
        playing = state.playing
        hasArticle = state.count > 0
        for (command, _) in remoteTargets { command.isEnabled = hasArticle }
        let center = MPRemoteCommandCenter.shared()
        center.playCommand.isEnabled = hasArticle && !playing
        center.pauseCommand.isEnabled = playing
        center.previousTrackCommand.isEnabled = hasArticle && state.index > 0
        center.nextTrackCommand.isEnabled = hasArticle && state.index + 1 < state.count
        MPNowPlayingInfoCenter.default().nowPlayingInfo = hasArticle ? [
            MPMediaItemPropertyTitle: state.title,
            MPMediaItemPropertyArtist: "Harmonic",
            MPNowPlayingInfoPropertyMediaType: MPNowPlayingInfoMediaType.audio.rawValue,
            MPNowPlayingInfoPropertyPlaybackRate: playing ? state.rate : 0,
            MPNowPlayingInfoPropertyDefaultPlaybackRate: state.rate,
            MPNowPlayingInfoPropertyPlaybackQueueIndex: state.index,
            MPNowPlayingInfoPropertyPlaybackQueueCount: state.count,
        ] : nil
        if !playing { deactivateAudio() }
    }

    private func register(_ command: MPRemoteCommand, action: String) {
        register(command) { _ in ["action": action] }
    }

    private func register(_ command: MPRemoteCommand, payload: @escaping (MPRemoteCommandEvent) -> [String: Any]?) {
        command.isEnabled = false
        let target = command.addTarget { [weak self] event in
            DispatchQueue.main.async { [weak self] in
                guard let value = payload(event) else { return }
                self?.send(value)
            }
            return .success
        }
        remoteTargets.append((command, target))
    }

    private func send(_ payload: [String: Any]) {
        guard !closed, hasArticle,
              let data = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: data, encoding: .utf8) else { return }
        commandHandler?(json)
    }

    private func deactivateAudio() {
        audioQueue.async { [self] in
            guard audioActive else { return }
            try? audioSession.deactivate()
            audioActive = false
        }
    }

    func close() {
        guard !closed else { return }
        closed = true
        stop()
        commandHandler = nil
        for (command, target) in remoteTargets {
            command.removeTarget(target)
            command.isEnabled = false
        }
        remoteTargets.removeAll()
        observations.forEach(NotificationCenter.default.removeObserver)
        observations.removeAll()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        deactivateAudio()
    }
}
