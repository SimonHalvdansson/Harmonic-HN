import AVFoundation
import MediaPlayer
import HarmonicKit

/// Owned by the process graph, independently of the article's WKWebView or UIWindowScene.
final class IosReadAloud: NSObject, IosReadAloudHost, AVSpeechSynthesizerDelegate {
    private var synthesizer = AVSpeechSynthesizer()
    private var utterance: AVSpeechUtterance?
    private var completion: ((String?) -> Void)?
    private var commandHandler: ((String) -> Void)?
    private var remoteTargets: [(MPRemoteCommand, Any)] = []
    private var observations: [NSObjectProtocol] = []
    private var playing = false
    private var hasArticle = false
    private static let audioQueue = DispatchQueue(label: "Harmonic speech audio session")
    // Access only on audioQueue; activation can block while negotiating an output route.
    private var audioActive = false
    private var generation: UInt64 = 0
    private var closed = false

    override init() {
        super.init()
        synthesizer.delegate = self
        synthesizer.usesApplicationAudioSession = true
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
            Self.audioQueue.async { self.audioActive = false }
            self.send(["action": "pause"])
            self.stop()
            self.synthesizer = AVSpeechSynthesizer()
            self.synthesizer.delegate = self
            self.synthesizer.usesApplicationAudioSession = true
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
        Self.audioQueue.async { [self] in
            var activationError: String?
            do {
                let audio = AVAudioSession.sharedInstance()
                if !audioActive {
                    try audio.setCategory(.playback, mode: .spokenAudio)
                    try audio.setActive(true)
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
        synthesizer.stopSpeaking(at: .immediate)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        finish(utterance, error: nil)
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didCancel utterance: AVSpeechUtterance) {
        finish(utterance, error: "Speech was interrupted. Tap Resume to continue.")
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
        Self.audioQueue.async { [self] in
            guard audioActive else { return }
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
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
