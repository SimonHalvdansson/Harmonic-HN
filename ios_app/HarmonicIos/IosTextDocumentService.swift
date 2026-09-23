import Foundation
import UIKit
import UniformTypeIdentifiers
import HarmonicKit

final class IosTextDocumentService: NSObject, TextDocumentService, UIDocumentPickerDelegate {
    private var callback: TextDocumentCallback?
    private var exportDirectory: URL?
    private var importing = false

    func importText(callback: TextDocumentCallback) {
        DispatchQueue.main.async {
            guard self.begin(callback) else { return }
            self.importing = true
            self.present(UIDocumentPickerViewController(
                forOpeningContentTypes: [.plainText], asCopy: true
            ))
        }
    }

    func exportText(filename: String, content: String, callback: TextDocumentCallback) {
        DispatchQueue.main.async {
            guard self.begin(callback) else { return }
            self.importing = false
            do {
                let directory = FileManager.default.temporaryDirectory
                    .appendingPathComponent(UUID().uuidString, isDirectory: true)
                self.exportDirectory = directory
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                let file = directory.appendingPathComponent((filename as NSString).lastPathComponent)
                try content.write(to: file, atomically: true, encoding: .utf8)
                self.present(UIDocumentPickerViewController(forExporting: [file], asCopy: true))
            } catch {
                self.finish(error: "Could not export bookmarks: \(error.localizedDescription)")
            }
        }
    }

    private func begin(_ callback: TextDocumentCallback) -> Bool {
        guard self.callback == nil else {
            callback.complete(content: nil, errorMessage: "Finish the open Files picker first")
            return false
        }
        self.callback = callback
        return true
    }

    private func present(_ picker: UIDocumentPickerViewController) {
        guard let presenter = UIViewController.harmonicTopPresenter else {
            finish(error: "Could not open Files. Please try again.")
            return
        }
        picker.delegate = self
        picker.allowsMultipleSelection = false
        presenter.present(picker, animated: true)
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        finish()
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        guard importing else { finish(content: ""); return }
        guard let url = urls.first else { finish(); return }
        // File providers may download or coordinate access; keep that work off the UI thread.
        DispatchQueue.global(qos: .userInitiated).async {
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            var content: String?
            var failure: String?
            var coordinationError: NSError?
            NSFileCoordinator().coordinate(readingItemAt: url, options: [], error: &coordinationError) { file in
                do {
                    let size = try file.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                    guard size <= 10 * 1024 * 1024 else {
                        failure = "This file is too large. Choose a bookmark text file under 10 MB."
                        return
                    }
                    content = try String(contentsOf: file, encoding: .utf8)
                } catch {
                    failure = "Could not read bookmarks: \(error.localizedDescription)"
                }
            }
            let result = content
            let error = failure ?? coordinationError.map { "Could not read bookmarks: \($0.localizedDescription)" }
            DispatchQueue.main.async { self.finish(content: result, error: error) }
        }
    }

    private func finish(content: String? = nil, error: String? = nil) {
        let completion = callback
        callback = nil
        if let directory = exportDirectory {
            try? FileManager.default.removeItem(at: directory)
            exportDirectory = nil
        }
        completion?.complete(content: content, errorMessage: error)
    }
}
