import XCTest
import AVFoundation
import MediaPlayer
import HarmonicKit
@testable import HarmonicIos

@MainActor
final class IosReadAloudTests: XCTestCase {
    func testSharedSessionPublishesPlaybackAndAcceptsSystemPause() async throws {
        let fixture = SpeechFixture()
        let session = IosReadAloudSession(host: fixture.host)
        defer { session.close() }
        let controller = session.controller
        session.command(raw:
            #"{"action":"start","title":"Shared speech session","text":"This sentence is long enough to verify playback and interrupt the shared session before it completes. Another sentence follows."}"#)
        for _ in 0..<50 {
            if MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyTitle] as? String == "Shared speech session" { break }
            try await Task.sleep(nanoseconds: 20_000_000)
        }
        XCTAssertEqual(MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyTitle] as? String, "Shared speech session")
        XCTAssertTrue(try XCTUnwrap(controller.state.value as? ReadAloudState).playing)
        NotificationCenter.default.post(name: AVAudioSession.interruptionNotification, object: nil,
                                        userInfo: [AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue])
        XCTAssertFalse(try XCTUnwrap(controller.state.value as? ReadAloudState).playing)
        controller.stop()
        XCTAssertEqual(try XCTUnwrap(controller.state.value as? ReadAloudState).count, 0)
    }

    func testCompletionIsDeliveredOnce() async throws {
        let fixture = SpeechFixture()
        defer { fixture.host.close() }
        var results: [String?] = []
        fixture.host.speak(text: "First sentence.", rate: 1.5) { results.append($0) }
        await fixture.drain()
        let utterance = try XCTUnwrap(fixture.speech.spoken.first)
        XCTAssertEqual(utterance.speechString, "First sentence.")
        XCTAssertEqual(utterance.rate, AVSpeechUtteranceDefaultSpeechRate * 1.5)
        fixture.speech.onFinish?(utterance)
        fixture.speech.onFinish?(utterance)
        fixture.speech.onCancel?(utterance)
        await fixture.drain()
        XCTAssertEqual(results, [nil])
    }

    func testActiveCancellationReportsInterruptionOnce() async throws {
        let fixture = SpeechFixture()
        defer { fixture.host.close() }
        var results: [String?] = []
        fixture.host.speak(text: "Interrupted sentence.", rate: 1) { results.append($0) }
        await fixture.drain()
        let utterance = try XCTUnwrap(fixture.speech.spoken.first)
        fixture.speech.onCancel?(utterance)
        fixture.speech.onFinish?(utterance)
        await fixture.drain()
        XCTAssertEqual(results, ["Speech was interrupted. Tap Resume to continue."])
    }

    func testStopAndReplacementIgnoreStaleCallbacks() async throws {
        let fixture = SpeechFixture()
        defer { fixture.host.close() }
        var staleCalls = 0
        var resumedCalls = 0
        fixture.host.speak(text: "Old sentence.", rate: 1) { _ in staleCalls += 1 }
        await fixture.drain()
        let old = try XCTUnwrap(fixture.speech.spoken.first)
        // Also exercise a completion already queued on the main dispatcher when stop occurs.
        fixture.speech.onFinish?(old)
        let stops = fixture.speech.stopCount
        fixture.host.stop()
        XCTAssertEqual(fixture.speech.stopCount, stops + 1)
        fixture.host.speak(text: "Replacement.", rate: 1) { error in
            XCTAssertNil(error)
            resumedCalls += 1
        }
        await fixture.drain()
        let replacement = try XCTUnwrap(fixture.speech.spoken.last)
        XCTAssertFalse(old === replacement)
        fixture.speech.onFinish?(old)
        fixture.speech.onCancel?(old)
        await fixture.drain()
        XCTAssertEqual(staleCalls, 0)
        XCTAssertEqual(resumedCalls, 0)
        fixture.speech.onFinish?(replacement)
        await fixture.drain()
        XCTAssertEqual(resumedCalls, 1)
        XCTAssertEqual(staleCalls, 0)
    }

    func testPendingActivationCannotStartStoppedOrReplacedSpeech() async {
        let fixture = SpeechFixture()
        defer { fixture.host.close() }
        fixture.host.speak(text: "Stopped before activation.", rate: 1) { _ in XCTFail("Stopped callback") }
        fixture.host.stop()
        await fixture.drain()
        XCTAssertTrue(fixture.speech.spoken.isEmpty)
        fixture.host.speak(text: "Replaced before activation.", rate: 1) { _ in XCTFail("Replaced callback") }
        fixture.host.speak(text: "Current sentence.", rate: 1) { _ in }
        await fixture.drain()
        XCTAssertEqual(fixture.speech.spoken.map(\.speechString), ["Current sentence."])
    }

    func testAudioActivationFailureCompletesWithoutSpeaking() async {
        let fixture = SpeechFixture(activationFails: true)
        defer { fixture.host.close() }
        var results: [String?] = []
        fixture.host.speak(text: "Unavailable audio.", rate: 1) { results.append($0) }
        await fixture.drain()
        XCTAssertEqual(results, ["Could not start audio playback. Try again when audio is available."])
        XCTAssertTrue(fixture.speech.spoken.isEmpty)
    }

    func testCloseIgnoresLateCallbacksAndReleasesAudio() async throws {
        let fixture = SpeechFixture()
        fixture.host.speak(text: "Playing.", rate: 1) { _ in XCTFail("Closed callback") }
        await fixture.drain()
        let old = try XCTUnwrap(fixture.speech.spoken.first)
        fixture.host.close()
        fixture.speech.onFinish?(old)
        fixture.speech.onCancel?(old)
        fixture.host.speak(text: "After close.", rate: 1) { _ in XCTFail("Closed host accepted speech") }
        await fixture.drain()
        XCTAssertEqual(fixture.speech.spoken.count, 1)
        XCTAssertEqual(fixture.audio.deactivationCount, 1)
    }

    func testCloseBeforeActivationPreventsSpeech() async {
        let fixture = SpeechFixture()
        fixture.host.speak(text: "Pending.", rate: 1) { _ in XCTFail("Closed callback") }
        fixture.host.close()
        await fixture.drain()
        XCTAssertTrue(fixture.speech.spoken.isEmpty)
        XCTAssertEqual(fixture.audio.deactivationCount, 1)
    }

    func testMediaServicesResetReplacesSynthesizerAndRejectsOldCallbacks() async throws {
        let fixture = SpeechFixture()
        defer { fixture.host.close() }
        fixture.host.speak(text: "Before reset.", rate: 1) { _ in XCTFail("Stale reset callback") }
        await fixture.drain()
        let oldSpeech = fixture.speech
        let oldUtterance = try XCTUnwrap(oldSpeech.spoken.first)
        NotificationCenter.default.post(name: AVAudioSession.mediaServicesWereResetNotification, object: nil)
        XCTAssertEqual(fixture.synthesizers.count, 2)
        var completed = false
        fixture.host.speak(text: "After reset.", rate: 1) { error in
            XCTAssertNil(error)
            completed = true
        }
        await fixture.drain()
        oldSpeech.onFinish?(oldUtterance)
        oldSpeech.onCancel?(oldUtterance)
        await fixture.drain()
        XCTAssertFalse(completed)
        fixture.speech.onFinish?(try XCTUnwrap(fixture.speech.spoken.first))
        await fixture.drain()
        XCTAssertTrue(completed)
        XCTAssertEqual(fixture.audio.activationCount, 2)
    }

    // Real audio integration check: compiled in CI, executed locally with working system voices.
    func testSystemVoiceCompletesAndIgnoresCancelledUtterances() async {
        let host = IosReadAloud()
        defer { host.close() }
        let first = expectation(description: "System voice completed")
        host.speak(text: "Harmonic read aloud test.", rate: 1.5) { error in
            XCTAssertNil(error)
            first.fulfill()
        }
        await fulfillment(of: [first], timeout: 30)
        var stale = false
        host.speak(text: String(repeating: "This sentence should be stopped. ", count: 20), rate: 1) { _ in stale = true }
        try? await Task.sleep(nanoseconds: 300_000_000)
        host.stop()
        let resumed = expectation(description: "System voice resumed")
        host.speak(text: "Playback resumed.", rate: 2) { error in
            XCTAssertNil(error)
            resumed.fulfill()
        }
        await fulfillment(of: [resumed], timeout: 30)
        XCTAssertFalse(stale)
    }

    func testMediaStateAndAudioInterruptions() {
        let fixture = SpeechFixture()
        let host = fixture.host
        defer { host.close() }
        var commands: [String] = []
        host.setCommandHandler { commands.append($0) }
        func state(playing: Bool, count: Int32 = 3) -> ReadAloudState {
            ReadAloudState(title: "Test article", url: "https://example.com/article", playing: playing,
                           index: 1, count: count, rate: 1.25, error: nil)
        }
        host.update(state: state(playing: true))
        let media = MPRemoteCommandCenter.shared()
        XCTAssertTrue(media.pauseCommand.isEnabled)
        XCTAssertFalse(media.playCommand.isEnabled)
        XCTAssertTrue(media.nextTrackCommand.isEnabled)
        XCTAssertEqual(MPNowPlayingInfoCenter.default().nowPlayingInfo?[MPMediaItemPropertyTitle] as? String, "Test article")
        NotificationCenter.default.post(name: AVAudioSession.interruptionNotification, object: nil,
                                        userInfo: [AVAudioSessionInterruptionTypeKey: AVAudioSession.InterruptionType.began.rawValue])
        NotificationCenter.default.post(name: AVAudioSession.routeChangeNotification, object: nil,
                                        userInfo: [AVAudioSessionRouteChangeReasonKey: AVAudioSession.RouteChangeReason.oldDeviceUnavailable.rawValue])
        XCTAssertEqual(commands.count, 2)
        XCTAssertTrue(commands.allSatisfy { $0.contains("pause") })
        host.update(state: state(playing: false))
        XCTAssertTrue(media.playCommand.isEnabled)
        XCTAssertFalse(media.pauseCommand.isEnabled)
        host.update(state: state(playing: false, count: 0))
        XCTAssertNil(MPNowPlayingInfoCenter.default().nowPlayingInfo)
        XCTAssertFalse(media.stopCommand.isEnabled)
    }
}

private final class FakeSpeechSynthesizer: IosSpeechSynthesizing {
    var onFinish: ((AVSpeechUtterance) -> Void)?
    var onCancel: ((AVSpeechUtterance) -> Void)?
    var spoken: [AVSpeechUtterance] = []
    var stopCount = 0

    func speak(_ utterance: AVSpeechUtterance) { spoken.append(utterance) }
    func stop() {
        stopCount += 1
        // Cancellation can arrive synchronously, as well as late after replacement.
        if let utterance = spoken.last { onCancel?(utterance) }
    }
}

private final class FakeSpeechAudioSession: IosSpeechAudioSession {
    enum Failure: Error { case unavailable }
    let activationFails: Bool
    // Written on audioQueue; tests read only after SpeechFixture.drain().
    var activationCount = 0
    var deactivationCount = 0

    init(activationFails: Bool) { self.activationFails = activationFails }
    func activate() throws {
        activationCount += 1
        if activationFails { throw Failure.unavailable }
    }
    func deactivate() { deactivationCount += 1 }
}

@MainActor
private final class SpeechFixture {
    let audio: FakeSpeechAudioSession
    let audioQueue = DispatchQueue(label: "Read aloud test audio")
    var synthesizers: [FakeSpeechSynthesizer] = []
    var speech: FakeSpeechSynthesizer { synthesizers.last! }
    lazy var host = IosReadAloud(makeSynthesizer: { [unowned self] in
        let speech = FakeSpeechSynthesizer()
        synthesizers.append(speech)
        return speech
    }, audioSession: audio, audioQueue: audioQueue)

    init(activationFails: Bool = false) {
        audio = FakeSpeechAudioSession(activationFails: activationFails)
    }

    func drain() async {
        // Drain activation, its main-queue delivery, and any completion it enqueues.
        // No real audio, polling, or timing assumptions are needed.
        await withCheckedContinuation { continuation in
            audioQueue.async {
                DispatchQueue.main.async {
                    DispatchQueue.main.async { continuation.resume() }
                }
            }
        }
    }
}
