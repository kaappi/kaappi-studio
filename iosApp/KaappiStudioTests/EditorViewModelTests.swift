import XCTest
@testable import KaappiStudio

/// Unit tests for the `loadCode` readiness queue on `EditorViewModel`.
///
/// `loadCode` must never drop a load that arrives before the page posted its
/// `ready` event (`window.kaappiAPI` does not exist until the async init
/// completes) — it queues it, and `onReady` applies it. The readiness-first
/// guard order makes the queue testable without a real `WKWebView`.
@MainActor
final class EditorViewModelTests: XCTestCase {

    func test_loadCodeBeforeReady_isQueuedUntilOnReady() {
        let vm = EditorViewModel()

        XCTAssertFalse(vm.isReady)
        vm.loadCode("(display 1)")

        XCTAssertEqual(vm.pendingLoad, "(display 1)", "a pre-ready load must be queued, not dropped")

        vm.onReady()

        XCTAssertTrue(vm.isReady)
        XCTAssertNil(vm.pendingLoad, "onReady must drain the queued load")
    }

    func test_loadCodeBeforeReady_lastCallWins() {
        let vm = EditorViewModel()

        vm.loadCode("(display 1)")
        vm.loadCode("(display 2)")

        XCTAssertEqual(vm.pendingLoad, "(display 2)")
    }

    func test_onReadyWithoutPendingLoad_isANoOp() {
        let vm = EditorViewModel()

        vm.onReady()

        XCTAssertTrue(vm.isReady)
        XCTAssertNil(vm.pendingLoad)
    }
}
