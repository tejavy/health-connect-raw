package space.tejaswi.healthraw

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable fun JsonViewer(entry: Entry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = jsonColors()
    var raw by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(setOf("", "/data")) }
    var copied by remember { mutableStateOf(false) }
    val tree by produceState<JsonNode?>(null, entry.json) {
        value = withContext(Dispatchers.Default) { JsonTree.parse(entry.json) }
    }
    val rows by produceState<List<JsonNode>?>(null, tree, expanded, query) {
        value = tree?.let { root -> withContext(Dispatchers.Default) { JsonTree.visible(root, expanded, query.trim()) } }
    }
    val lines = remember(entry.json) { entry.json.lines() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, securePolicy = SecureFlagPolicy.SecureOn, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Record details", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(top = 8.dp))
                    TextButton(onClick = {
                        val clip = ClipData.newPlainText("Health Raw record", entry.json)
                        if (Build.VERSION.SDK_INT >= 33) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                        copied = true
                    }) { Text(if (copied) "Copied" else "Copy JSON") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Text(entry.source.ifBlank { "Unknown source" }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !raw, onClick = { raw = false }, label = { Text("Tree") })
                    FilterChip(selected = raw, onClick = { raw = true }, label = { Text("JSON") })
                    if (!raw && query.isBlank()) TextButton(onClick = { expanded = setOf("") }) { Text("Collapse all") }
                }
                if (!raw) OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search keys, paths or values") }, trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("Clear") } },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
                HorizontalDivider()
                if (raw) {
                    SelectionContainer(Modifier.weight(1f)) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                            items(lines.size) { index ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text("${index + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(44.dp))
                                    Text(remember(lines[index], colors) { highlightJson(lines[index], colors) }, fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                } else {
                    val visible = rows
                    if (visible == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else if (visible.isEmpty()) Text("No matching keys or values.", Modifier.padding(24.dp))
                    else LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
                        items(visible, key = { it.path }) { node ->
                            val color = when(node.value) {
                                null, org.json.JSONObject.NULL -> MaterialTheme.colorScheme.onSurfaceVariant
                                is Number -> colors.number
                                is Boolean -> colors.boolean
                                is String -> colors.string
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Column(Modifier.fillMaxWidth().then(if (node.container && query.isBlank()) Modifier.clickable {
                                expanded = if (node.path in expanded) expanded - node.path else expanded + node.path
                            } else Modifier).padding(start = (node.depth.coerceAtMost(5) * 12).dp, top = 10.dp, bottom = 10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (!node.container) "·" else if (query.isNotBlank() || node.path in expanded) "▾" else "▸", color = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(node.key, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelLarge)
                                        SelectionContainer { Text(node.display, color = color, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
                                        if (query.isNotBlank()) Text(node.path.ifEmpty { "/" }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            }
        }
    }
}

private val jsonToken = Regex("\"(?:\\\\.|[^\"\\\\])*\"|\\b(?:true|false|null)\\b|-?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
private fun highlightJson(line: String, colors: JsonColors): AnnotatedString = buildAnnotatedString {
    append(line)
    jsonToken.findAll(line).forEach { match ->
        val token = match.value
        val color = when {
            token.startsWith('"') && line.substring(match.range.last + 1).trimStart().startsWith(':') -> colors.key
            token.startsWith('"') -> colors.string
            token == "null" -> colors.nil
            token == "true" || token == "false" -> colors.boolean
            else -> colors.number
        }
        addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
    }
}
