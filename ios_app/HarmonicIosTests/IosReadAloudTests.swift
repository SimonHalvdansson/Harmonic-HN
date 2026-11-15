import XCTest
import AVFoundation
import MediaPlayer
import HarmonicKit
@testable import HarmonicIos

@MainActor
final class IosReadAloudTests: XCTestCase {
    func testSharedSessionPublishesPlaybackAndAcceptsSystemPause() async throws {
        let session = IosReadAloudSession(host: IosReadAloud())
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
        let host = IosReadAloud()
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
