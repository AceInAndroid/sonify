package com.sonify.player.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 资源一致性门禁单测：
 * 确保 `res/values/strings.xml`（English 基准）与所有 `res/values-<locale>/strings.xml` 的
 * Key 集合 100% 齐平且所有格式占位符（%1$s / %2$d 等）完全匹配，防止运行期崩溃或漏翻。
 */
class I18nResourceConsistencyTest {

    private val placeholderRegex = Regex("""%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]""")
    private val validKeyRegex = Regex("""^[a-z][a-z0-9_]*$""")

    private fun resolveResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
        )
        return candidates.first { it.isDirectory }
    }

    private fun parseStrings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = LinkedHashMap<String, String>(nodes.length)
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            val name = el.getAttribute("name").trim()
            assertTrue("Duplicate key '$name' in ${file.path}", !map.containsKey(name))
            assertTrue("Invalid key naming '$name' in ${file.path}", validKeyRegex.matches(name))
            val value = el.textContent
            assertTrue("Empty string value for '$name' in ${file.path}", value.isNotBlank())
            map[name] = value
        }
        return map
    }

    private fun placeholdersOf(value: String): List<String> =
        placeholderRegex.findAll(value.replace("%%", "")).map { it.value }.sorted().toList()

    @Test
    fun allLocales_haveExactKeyAndPlaceholderParity() {
        val resDir = resolveResDir()
        val baseFile = File(resDir, "values/strings.xml")
        assertTrue("Base values/strings.xml must exist", baseFile.isFile)
        val base = parseStrings(baseFile)
        assertTrue("Base strings.xml should have migrated keys", base.size >= 200)

        val localeFiles = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values-") }
            .orEmpty()
            .map { File(it, "strings.xml") }
            .filter { it.isFile }
        assertTrue("At least values-zh/strings.xml must exist", localeFiles.isNotEmpty())

        for (locFile in localeFiles) {
            val loc = parseStrings(locFile)
            val missing = base.keys - loc.keys
            val extra = loc.keys - base.keys
            val dirName = locFile.parentFile?.name ?: locFile.name
            assertEquals("Missing keys in $dirName: $missing", emptySet<String>(), missing)
            assertEquals("Extra unknown keys in $dirName: $extra", emptySet<String>(), extra)

            for ((k, baseVal) in base) {
                val locVal = loc.getValue(k)
                assertEquals(
                    "Placeholder mismatch for '$k' in $dirName",
                    placeholdersOf(baseVal),
                    placeholdersOf(locVal),
                )
            }
        }
    }
}
