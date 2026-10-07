package com.simon.harmonichackernews.ui.reader

import com.simon.harmonichackernews.presentation.ReaderModeScriptProtocol
import com.simon.harmonichackernews.presentation.ReaderModeSourceAssembler
import kotlin.math.roundToInt

/** Trusted local document. No remote content, requests, links, or platform-dependent text units. */
object ReaderPreviewDocument {
    const val Title = "Article preview"
    const val Text = "Reader mode brings the words into focus. Adjust the font and text size to find a comfortable way to read your next story."

    fun html(font: String, fontFaceCss: String, background: Int, foreground: Int, heightChanged: String): String {
        val fallback = ReaderModeScriptProtocol.fontFamily(font)
        val family = if (fontFaceCss.isBlank()) fallback else "'HarmonicReaderFont', $fallback"
        return """
            <!doctype html><html><head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; font-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline'">
            <style>
            $fontFaceCss
            html,body{margin:0;padding:0;background:${ReaderModeSourceAssembler.cssColor(background)};
                color:${ReaderModeSourceAssembler.cssColor(foreground)};-webkit-text-size-adjust:100%;}
            #preview{box-sizing:border-box;max-width:760px;margin:0 auto;padding:20px;overflow-wrap:anywhere;font-family:$family;}
            h1{font:700 var(--reader-title,32px)/1.15 $family;margin:0 0 12px;}
            p{font-size:var(--reader-size,17px);line-height:var(--reader-line-height,1.68);margin:0;}
            </style></head><body><main id="preview"><h1>$Title</h1><p>$Text</p></main>
            <script>new ResizeObserver(function(){
                var height=document.getElementById('preview').getBoundingClientRect().height;
                $heightChanged
            }).observe(document.getElementById('preview'));</script></body></html>
        """.trimIndent()
    }

    fun sizeScript(size: Int, lineHeight: Double = 1.68): String = """
        document.documentElement.style.setProperty('--reader-line-height', '$lineHeight');
        document.documentElement.style.setProperty('--reader-size', '${size}px');
        document.documentElement.style.setProperty('--reader-title', '${(size * 1.78).roundToInt()}px');
    """.trimIndent()
}
