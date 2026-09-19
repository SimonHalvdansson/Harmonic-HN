// This small classic script can show a useful fallback even when an old WebView cannot parse
// the legacy PDF.js modules. The viewer and its workers always load from bundled local assets.
(function () {
  var status = document.getElementById("pdfStatus");
  var timer = setTimeout(function () { window.harmonicPdfFailure(); }, 30000);
  window.harmonicPdfFailure = function () {
    clearTimeout(timer);
    document.documentElement.setAttribute("data-pdf-state", "error");
    document.getElementById("viewerContainer").hidden = true;
    status.hidden = false;
    status.textContent = "This PDF could not be displayed. Try opening it in your browser, or update Android System WebView.";
  };
  window.harmonicPdfReady = function () {
    clearTimeout(timer);
    document.documentElement.setAttribute("data-pdf-state", "ready");
    document.getElementById("viewerContainer").hidden = false;
    status.hidden = true;
  };
  window.addEventListener("error", function (event) {
    if (event.target && event.target.tagName === "SCRIPT") window.harmonicPdfFailure();
  }, true);
  window.addEventListener("unhandledrejection", function () { window.harmonicPdfFailure(); });
  if (!("noModule" in document.createElement("script"))) window.harmonicPdfFailure();
}());
