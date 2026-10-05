package space.tejaswi.healthraw

import org.json.JSONArray
import org.json.JSONObject

data class JsonNode(val key: String, val path: String, val depth: Int, val value: Any?, val children: List<JsonNode>) {
    val container: Boolean get() = value is JSONObject || value is JSONArray
    val display: String get() = when(value) {
        is JSONObject -> "${children.size} fields"
        is JSONArray -> "${children.size} items"
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }
}
object JsonTree {
    fun parse(json: String): JsonNode = node("Record", "", 0, JSONObject(json))
    private fun node(key: String, path: String, depth: Int, value: Any?): JsonNode {
        val children = when(value) {
            is JSONObject -> value.keys().asSequence().map { it to value.get(it) }.toList()
            is JSONArray -> (0 until value.length()).map { it.toString() to value.get(it) }
            else -> emptyList()
        }.map { (name, child) -> node(name, "$path/${name.replace("~", "~0").replace("/", "~1")}", depth + 1, child) }
        return JsonNode(key, path, depth, value, children)
    }
    fun visible(root: JsonNode, expanded: Set<String>, query: String): List<JsonNode> {
        val result = mutableListOf<JsonNode>()
        fun visit(node: JsonNode): Boolean {
            if (query.isBlank()) {
                result.add(node)
                if (node.path in expanded) node.children.forEach { visit(it) }
                return true
            }
            val before = result.size
            result.add(node)
            val childMatch = node.children.map { visit(it) }.any { it }
            val selfMatch = node.key.contains(query, true) || node.path.contains(query, true) ||
                (!node.container && node.display.contains(query, true))
            if (!selfMatch && !childMatch) result.subList(before, result.size).clear()
            return selfMatch || childMatch
        }
        visit(root)
        return result
    }
}
