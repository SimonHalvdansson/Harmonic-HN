import XCTest
import UIKit
@testable import HarmonicIos

@MainActor
final class IosSystemGestureTests: XCTestCase {
    private final class TestWindow: UIWindow {
        var bottomInset: CGFloat = 34
        override var safeAreaInsets: UIEdgeInsets {
            UIEdgeInsets(top: 59, left: 0, bottom: bottomInset, right: 0)
        }
    }

    func testHomeIndicatorTouchesStayOutsideTheContentSubtree() {
        let (window, root, content) = makeViews()
        let boundary = window.bounds.maxY - window.safeAreaInsets.bottom
        for x in [CGFloat(1), window.bounds.midX, window.bounds.maxX - 1] {
            XCTAssertTrue(root.hitTest(CGPoint(x: x, y: boundary), with: nil) === root)
            XCTAssertTrue(root.hitTest(CGPoint(x: x, y: window.bounds.maxY - 1), with: nil) === root)
            XCTAssertTrue(root.hitTest(CGPoint(x: x, y: boundary - 1), with: nil) === content)
        }
        // Preserve content hit testing at the top and side edges, including Back.
        XCTAssertTrue(root.hitTest(CGPoint(x: 1, y: 200), with: nil) === content)
        XCTAssertTrue(root.hitTest(CGPoint(x: 100, y: 1), with: nil) === content)
    }

    func testNoBottomInsetLeavesAllContentInteractive() {
        let (window, root, content) = makeViews()
        window.bottomInset = 0
        XCTAssertTrue(root.hitTest(CGPoint(x: 100, y: window.bounds.maxY - 1), with: nil) === content)
    }

    func testUsesLiveWindowGeometryAfterRotationAndContainerMovement() {
        let (window, root, content) = makeViews()
        window.frame = CGRect(x: 0, y: 0, width: 844, height: 390)
        window.bottomInset = 21
        root.frame = CGRect(x: 30, y: 50, width: 780, height: 340)
        content.frame = root.bounds
        XCTAssertTrue(root.hitTest(CGPoint(x: 100, y: 318), with: nil) === content)
        XCTAssertTrue(root.hitTest(CGPoint(x: 100, y: 319), with: nil) === root)
        // A container ending above the Home area must not invent its own dead strip.
        root.frame.size.height = 250
        content.frame = root.bounds
        XCTAssertTrue(root.hitTest(CGPoint(x: 100, y: 249), with: nil) === content)
    }

    func testRootControllerInstallsTheSystemGestureBoundary() {
        let controller = HarmonicRootViewController(content: UIViewController())
        XCTAssertTrue(controller.view is HarmonicRootView)
    }

    private func makeViews() -> (TestWindow, HarmonicRootView, UIView) {
        let window = TestWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        let root = HarmonicRootView(frame: window.bounds)
        let content = UIView(frame: root.bounds)
        window.addSubview(root)
        root.addSubview(content)
        return (window, root, content)
    }
}
