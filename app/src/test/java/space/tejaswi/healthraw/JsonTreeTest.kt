package space.tejaswi.healthraw

import org.junit.Assert.*
import org.junit.Test

class JsonTreeTest {
    private val root = JsonTree.parse("""{"data":{"samples":[{"bpm":71},{"bpm":85}],"missing":null,"enabled":false},"a/b~c":"value"}""")
    @Test fun collapsedContainersHideSamples() {
        val rows = JsonTree.visible(root, setOf("", "/data"), "")
        assertTrue(rows.any { it.path == "/data/samples" })
        assertFalse(rows.any { it.path == "/data/samples/0" })
        assertEquals("2 items", rows.first { it.path == "/data/samples" }.display)
    }
    @Test fun searchRevealsOnlyMatchingBranchAndAncestors() {
        val rows = JsonTree.visible(root, emptySet(), "85")
        assertEquals(listOf("", "/data", "/data/samples", "/data/samples/1", "/data/samples/1/bpm"), rows.map { it.path })
        assertTrue(JsonTree.visible(root, emptySet(), "no such value").isEmpty())
    }
    @Test fun preservesNullBooleanAndEscapesPointerPaths() {
        val rows = JsonTree.visible(root, setOf("", "/data"), "")
        assertEquals("null", rows.first { it.key == "missing" }.display)
        assertEquals("false", rows.first { it.key == "enabled" }.display)
        assertEquals("/a~1b~0c", rows.first { it.key == "a/b~c" }.path)
    }
}
