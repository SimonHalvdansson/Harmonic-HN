import * as pdfjsLib from "./vendor/pdf.js/6.3.289/pdf.min.mjs";
import { EventBus, PDFLinkService, PDFViewer } from "./vendor/pdf.js/6.3.289/pdf_viewer.mjs";

const assets = new URL("./vendor/pdf.js/6.3.289/", import.meta.url);
pdfjsLib.GlobalWorkerOptions.workerSrc = new URL("pdf.worker.min.mjs", assets).href;
document.documentElement.dataset.pdfjsVersion = pdfjsLib.version;

const bridge = window.PdfAndroidJavascriptBridge;
const abortController = new AbortController();
const eventBus = new EventBus();
const linkService = new PDFLinkService({ eventBus });
const viewer = new PDFViewer({
  container: document.getElementById("viewerContainer"),
  eventBus,
  linkService,
  abortSignal: abortController.signal,
  imageResourcesPath: new URL("images/", assets).href,
  maxCanvasPixels: 2097152,
  enableDetailCanvas: false,
  annotationMode: pdfjsLib.AnnotationMode.ENABLE,
  annotationEditorMode: pdfjsLib.AnnotationEditorType.DISABLE,
  enableAutoLinking: false,
});
linkService.setViewer(viewer);

let loadingTask;
let stopped = false;

function fail(error) {
  if (stopped) return;
  stopped = true;
  console.error("Unable to display PDF", error);
  window.harmonicPdfFailure();
  bridge?.onFailure();
  loadingTask?.destroy().catch(() => {});
}

// Native reads are bounded to 256 KiB, even when PDF.js combines adjacent range requests.
// Deliver asynchronously: its stream listener must be installed before onDataRange runs.
class AndroidRangeTransport extends pdfjsLib.PDFDataRangeTransport {
  constructor(size) {
    super(size, new Uint8Array(0), true);
    this.aborted = false;
  }

  requestDataRange(begin, end) {
    setTimeout(() => {
      if (this.aborted || stopped) return;
      try {
        if (!Number.isSafeInteger(begin) || !Number.isSafeInteger(end) ||
            begin < 0 || end <= begin || end > this.length) {
          throw new Error("Invalid PDF range");
        }
        const bytes = new Uint8Array(end - begin);
        for (let offset = begin; offset < end; offset += 262144) {
          const chunkEnd = Math.min(offset + 262144, end);
          const binary = atob(bridge.getChunk(offset, chunkEnd));
          if (binary.length !== chunkEnd - offset) throw new Error("Incomplete PDF range");
          for (let i = 0; i < binary.length; i++) bytes[offset - begin + i] = binary.charCodeAt(i);
        }
        this.onDataRange(begin, bytes);
      } catch (error) {
        fail(error);
      }
    }, 0);
  }

  abort() {
    this.aborted = true;
  }
}

eventBus.on("pagesinit", () => {
  viewer.currentScaleValue = "page-width";
});
eventBus.on("pagerendered", ({ pageNumber, error }) => {
  if (error) {
    fail(error);
  } else if (!stopped) {
    document.querySelector(`.page[data-page-number="${pageNumber}"]`)?.setAttribute("data-rendered", "true");
    window.harmonicPdfReady();
  }
});
window.addEventListener("resize", () => {
  if (viewer.currentScaleValue === "page-width") viewer.currentScaleValue = "page-width";
}, { signal: abortController.signal });
window.addEventListener("pagehide", () => {
  stopped = true;
  abortController.abort();
  loadingTask?.destroy().catch(() => {});
}, { once: true });

try {
  const size = bridge.getSize();
  if (!Number.isSafeInteger(size) || size <= 0) throw new Error("The PDF is empty");
  loadingTask = pdfjsLib.getDocument({
    length: size,
    range: new AndroidRangeTransport(size),
    rangeChunkSize: 262144,
    disableAutoFetch: true,
    disableStream: true,
    cMapUrl: new URL("cmaps/", assets).href,
    cMapPacked: true,
    standardFontDataUrl: new URL("standard_fonts/", assets).href,
    wasmUrl: new URL("wasm/", assets).href,
    iccUrl: new URL("iccs/", assets).href,
    // Keep auxiliary reads on the same intercepted WebView path as the viewer itself.
    useWorkerFetch: false,
  });
  const pdfDocument = await loadingTask.promise;
  if (!stopped) {
    viewer.setDocument(pdfDocument);
    linkService.setDocument(pdfDocument);
    bridge.onLoad();
  }
} catch (error) {
  fail(error);
}
