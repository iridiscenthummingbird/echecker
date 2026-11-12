package ua.echecker.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {
    private val base = "https://github.com/iridiscenthummingbird/echecker/releases/download"

    private fun release(tag: String = "v1.1", assets: String = asset("echecker.apk", "$base/v1.1/echecker.apk")) = """
        {"tag_name":"$tag","html_url":"https://github.com/iridiscenthummingbird/echecker/releases/tag/$tag",
         "draft":false,"prerelease":false,"body":"Що нового","assets":[$assets]}
    """.trimIndent()

    private fun asset(name: String, url: String) = """{"name":"$name","browser_download_url":"$url"}"""

    @Test fun `parses version, apk url and release page`() {
        val info = UpdateChecker.parse(release())!!
        assertEquals("1.1", info.version)
        assertEquals("$base/v1.1/echecker.apk", info.apkUrl)
        assertEquals("https://github.com/iridiscenthummingbird/echecker/releases/tag/v1.1", info.pageUrl)
    }

    @Test fun `picks the apk among other assets`() {
        val assets = asset("checksums.txt", "$base/v1.1/checksums.txt") + "," + asset("echecker.apk", "$base/v1.1/echecker.apk")
        assertEquals("$base/v1.1/echecker.apk", UpdateChecker.parse(release(assets = assets))!!.apkUrl)
    }

    @Test fun `no apk asset means no update`() {
        assertNull(UpdateChecker.parse(release(assets = asset("notes.txt", "$base/v1.1/notes.txt"))))
        assertNull(UpdateChecker.parse(release(assets = "")))
    }

    @Test fun `apk hosted outside this repo's releases is rejected`() {
        assertNull(UpdateChecker.parse(release(assets = asset("echecker.apk", "https://evil.example/echecker.apk"))))
        assertNull(UpdateChecker.parse(release(assets = asset("echecker.apk", "http://github.com/iridiscenthummingbird/echecker/releases/download/v1.1/echecker.apk"))))
        assertNull(UpdateChecker.parse(release(assets = asset("echecker.apk", "https://github.com/someone-else/echecker/releases/download/v1.1/echecker.apk"))))
    }

    @Test fun `drafts, prereleases and odd tags are ignored`() {
        assertNull(UpdateChecker.parse(release().replace("\"draft\":false", "\"draft\":true")))
        assertNull(UpdateChecker.parse(release().replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(UpdateChecker.parse(release(tag = "nightly")))
    }

    @Test fun `malformed json yields null instead of crashing`() {
        assertNull(UpdateChecker.parse("not json"))
        assertNull(UpdateChecker.parse("{}"))
    }
}
