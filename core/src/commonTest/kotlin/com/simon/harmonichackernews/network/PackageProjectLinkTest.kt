package com.simon.harmonichackernews.network

import com.simon.harmonichackernews.data.LinkPreviewType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class PackageProjectLinkTest {
    private fun parse(type: LinkPreviewType, response: String) = PackageLinkPreview.parsePackage(
        type, response, PackagePreviewTarget(type, "sample"), "https://example.com/sample",
    )

    @Test fun npmNormalizesHttpsGitRepositoryAndShowsVersionOnlyOnce() {
        val info = parse(LinkPreviewType.NPM_PACKAGE,
            """{"version":"1.2.3","repository":{"type":"git","url":"git+https://github.com/owner/project.git"}}""")
        assertEquals("https://github.com/owner/project", info.details.single { it.label == "Project URL" }.value)
        assertEquals("npm · 1.2.3", info.subtitle)
        assertFalse(info.details.any { it.label == "Version" })
    }

    @Test fun npmFallsBackToHomepageForNonWebRepositories() {
        for (repository in listOf("git@github.com:owner/project.git", "ssh://git@example.com/project", "javascript:alert(1)")) {
            val info = parse(LinkPreviewType.NPM_PACKAGE,
                """{"repository":"$repository","homepage":"https://example.com/docs"}""")
            assertEquals("https://example.com/docs", info.details.single { it.label == "Project URL" }.value)
            assertEquals("npm", info.subtitle)
        }
    }

    @Test fun pypiPrefersProjectMetadataAndNormalizesLabels() {
        val info = parse(LinkPreviewType.PYPI_PACKAGE,
            """{"info":{"project_url":"https://pypi.org/project/sample/","home_page":"https://old.example.com","project_urls":{"Home-page":"javascript:alert(1)","Source Code":"https://github.com/owner/project","Documentation":"https://docs.example.com"}}}""")
        assertEquals("https://github.com/owner/project", info.details.single { it.label == "Project URL" }.value)
    }

    @Test fun pypiUsesLegacyHomepageButNeverRegistrySelfLink() {
        val legacy = parse(LinkPreviewType.PYPI_PACKAGE,
            """{"info":{"project_urls":null,"home_page":"https://example.com"}}""")
        assertEquals("https://example.com", legacy.details.single { it.label == "Project URL" }.value)
        val absent = parse(LinkPreviewType.PYPI_PACKAGE,
            """{"info":{"project_url":"https://pypi.org/project/sample/","home_page":"UNKNOWN"}}""")
        assertNull(absent.details.find { it.label == "Project URL" })
    }

    @Test fun cratesKeepsRepositoryPathAndFallsBackToHomepage() {
        for (field in listOf("repository", "homepage")) {
            val info = parse(LinkPreviewType.CRATES_PACKAGE,
                """{"crate":{"$field":"https://github.com/owner/project","newest_version":"2.0"}}""")
            assertEquals("https://github.com/owner/project", info.details.single { it.label == "Project URL" }.value)
            assertEquals("crates.io · 2.0", info.subtitle)
            assertFalse(info.details.any { it.label == "Version" })
        }
    }

    @Test fun homebrewPreservesFullHomepageAndRejectsNonWebUrls() {
        val valid = parse(LinkPreviewType.HOMEBREW_PACKAGE, """{"homepage":"https://example.com/project"}""")
        assertEquals("https://example.com/project", valid.details.single { it.label == "Project URL" }.value)
        for (url in listOf("/relative", "file:///tmp/page", "javascript:alert(1)")) {
            assertNull(parse(LinkPreviewType.HOMEBREW_PACKAGE, """{"homepage":"$url"}""")
                .details.find { it.label == "Project URL" })
        }
    }
}
