package com.simon.harmonichackernews.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fleeksoft.ksoup.Ksoup
import com.simon.harmonichackernews.network.HtmlDescriptionExtractor
import com.simon.harmonichackernews.utils.CollectedReferenceLinks
import com.simon.harmonichackernews.utils.HtmlTextUtils
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production text paths with parsing measured separately from already parsed extraction. */
@RunWith(AndroidJUnit4::class)
class CoreTextPerformanceBenchmark {
    @get:Rule val benchmarkRule = BenchmarkRule()
    @Volatile private var sink: Any? = null

    private val paragraph = "A detailed explanation of the implementation offers useful examples for every reader. " +
        "The article explores memory allocation, efficient data access, and careful verification of results. " +
        "These practical techniques apply to many applications and keep their behavior consistent."
    private val articleHtml = "<article>" +
        (0 until 400).joinToString("") { "<p>$paragraph Item $it.</p>" } + "</article>"
    private val articleDocument = Ksoup.parse(articleHtml)
    private val mainDocument = Ksoup.parse(
        "<main><p>This shorter paragraph has useful information for every curious reader.</p>" +
            (0 until 100).joinToString("") { "<p>$paragraph Item $it.</p>" } + "</main>",
    )
    private val asciiText = "A short plain comment with useful information and example number 42."
    private val markupText = "<p>Unicode sample 42 &amp; café: 中文, Ελληνικά, 😀.</p>"
    private val bareReferences = (1..30).joinToString("") { "<p>[$it] https://example.com/article/$it</p>" }
    private val dotlessCode = "<pre>" + "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".repeat(60) + "</pre>"

    @Test fun descriptionArticle400() = benchmarkRule.measureRepeated {
        sink = HtmlDescriptionExtractor.chooseDescription(null, articleDocument, "Title", null)
    }

    @Test fun descriptionParseAndExtract400() = benchmarkRule.measureRepeated {
        sink = HtmlDescriptionExtractor.chooseDescription(null, Ksoup.parse(articleHtml), "Title", null)
    }

    @Test fun descriptionMain100() = benchmarkRule.measureRepeated {
        sink = HtmlDescriptionExtractor.chooseDescription(null, mainDocument, "Title", null)
    }

    @Test fun plainTextAscii() = benchmarkRule.measureRepeated {
        sink = HtmlTextUtils.plainText(asciiText)
    }

    @Test fun plainTextMarkup() = benchmarkRule.measureRepeated {
        sink = HtmlTextUtils.plainText(markupText)
    }

    @Test fun bareReferences30() {
        check(CollectedReferenceLinks.parse(bareReferences).links.size == 30)
        benchmarkRule.measureRepeated { sink = CollectedReferenceLinks.parse(bareReferences) }
    }

    @Test fun dotlessCode2160() {
        check(CollectedReferenceLinks.parse(dotlessCode).bodyHtml == dotlessCode)
        benchmarkRule.measureRepeated { sink = CollectedReferenceLinks.parse(dotlessCode) }
    }
}
