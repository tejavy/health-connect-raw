package space.tejaswi.healthraw

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.*

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { HealthTheme { Explorer() } }
    }
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Explorer(model: ExplorerModel = viewModel()) {
        val s by model.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        var menu by remember { mutableStateOf(false) }
        var source by remember(s.kind, s.from, s.through) { mutableStateOf("") }
        var exportSnapshot by remember { mutableStateOf<String?>(null) }
        var exporting by remember { mutableStateOf(false) }
        val permissions = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { model.refreshPermissions() }
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val snapshot = exportSnapshot
            exportSnapshot = null
            if (uri != null && snapshot != null) scope.launch {
                exporting = true
                try {
                    withContext(Dispatchers.IO) { requireNotNull(contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(snapshot) } }
                    model.message("JSON saved to your selected location.")
                } catch (e: Exception) { model.message("Export failed: ${e.message}") }
                finally { exporting = false }
            }
        }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) model.refreshPermissions() }
            lifecycle.addObserver(observer); model.refreshPermissions()
            onDispose { lifecycle.removeObserver(observer) }
        }
        Scaffold(topBar = { TopAppBar(title = { Column { Text("Health Raw"); Text("Your records. In detail.", style = MaterialTheme.typography.labelMedium) } }, actions = {
            TextButton(onClick = { startActivity(Intent(this@MainActivity, PrivacyActivity::class.java)) }) { Text("Privacy") }
        }) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Raw record explorer", style = MaterialTheme.typography.titleLarge)
                            Text("Read original entries from Health Connect. Records from different apps may overlap; these are not deduplicated health totals.")
                            Text("${s.granted.intersect(recordKinds.map { it.permission }.toSet()).size} read permissions granted", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (s.status != HealthConnectClient.SDK_AVAILABLE) item {
                    Text(if (s.status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) "Install or update Health Connect to continue." else "Health Connect is unavailable on this device or profile.")
                    if (s.status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) Button(onClick = {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                    }) { Text("Get Health Connect") }
                }
                item {
                    Box {
                        OutlinedButton(onClick = { menu = true }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("${recordKinds[s.kind].name}  ▾") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                            recordKinds.forEachIndexed { i, kind -> DropdownMenuItem(text = { Text(kind.name) }, onClick = { menu = false; model.choose(i) }) }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = s.from, onValueChange = { model.dates(it, s.through) }, label = { Text("From · YYYY-MM-DD") }, singleLine = true, enabled = !s.busy, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = s.through, onValueChange = { model.dates(s.from, it) }, label = { Text("Through · YYYY-MM-DD") }, singleLine = true, enabled = !s.busy, modifier = Modifier.weight(1f))
                    }
                    Text("Dates use your local time zone. Without history access, reads normally cover only 30 days before permission was granted.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
                if (s.status == HealthConnectClient.SDK_AVAILABLE) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (recordKinds[s.kind].permission !in s.granted) Button(onClick = { permissions.launch(setOf(recordKinds[s.kind].permission)) }, modifier = Modifier.fillMaxWidth()) { Text("Allow ${recordKinds[s.kind].name.lowercase()} access") }
                        else Button(onClick = { model.load() }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text(if (s.busy) "Reading…" else "Read records") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                try { startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                                catch (_: android.content.ActivityNotFoundException) { model.message("Open Health Connect from Android Settings to manage permissions.") }
                            }) { Text("Manage access") }
                            if (s.historyAvailable && model.historyPermission !in s.granted) TextButton(onClick = { permissions.launch(setOf(model.historyPermission)) }) { Text("Allow older history") }
                        }
                    }
                }
                if (s.busy || exporting) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                s.message?.let { message -> item { Card { Text(message, Modifier.padding(16.dp)) } } }
                if (s.queried) item {
                    Text("${s.entries.size} records loaded${if (s.next != null) " · more available" else ""}", style = MaterialTheme.typography.titleMedium)
                    if (s.entries.isEmpty()) Text("No records returned. Check the date range and whether your health apps are writing this data type to Health Connect.")
                }
                if (s.entries.isNotEmpty()) item {
                    OutlinedTextField(value = source, onValueChange = { source = it }, label = { Text("Filter loaded records by source package") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedButton(enabled = !s.busy && !exporting, onClick = {
                        scope.launch {
                            exporting = true
                            try { exportSnapshot = withContext(Dispatchers.Default) { model.export(s) }; export.launch("health-raw-${recordKinds[s.kind].name.replace(' ', '-')}.json") }
                            catch (e: Exception) { model.message("Could not prepare export: ${e.message}") }
                            finally { exporting = false }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Export all ${s.entries.size} loaded records as JSON") }
                    Text("Export includes all loaded sources. Load remaining pages first for the complete query.", style = MaterialTheme.typography.bodySmall)
                }
                items(s.entries.filter { it.source.contains(source, ignoreCase = true) }, key = { it.id }) { entry ->
                    var expanded by remember(entry.id) { mutableStateOf(false) }
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.source.ifBlank { "Unknown source" }, style = MaterialTheme.typography.titleSmall)
                            Text("ID: ${entry.id}", style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide raw JSON" else "Inspect raw JSON") }
                            if (expanded) SelectionContainer { Text(entry.json, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                if (s.next != null) item { OutlinedButton(onClick = { model.load(true) }, enabled = !s.busy, modifier = Modifier.fillMaxWidth()) { Text("Load next 100 records") } }
            }
        }
    }
}
@Composable fun HealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF006C67), primaryContainer = Color(0xFFB9F1E8), background = Color(0xFFF5FAF8), surface = Color(0xFFF5FAF8)), content = content)
}
class PrivacyActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { HealthTheme { Surface(Modifier.fillMaxSize()) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Your health data stays with you", style = MaterialTheme.typography.headlineMedium)
                Text("Health Raw requests read access only for the record types you choose. It never writes or deletes Health Connect records.")
                Text("Records are held in memory while browsing. There is no server, analytics, advertising, or network permission. Android backup is disabled and the explorer blocks screenshots.")
                Text("JSON exports are saved only when you choose a destination. That destination may be a cloud document provider. Exported files contain sensitive health data; manage and delete them yourself.")
                Text("Optional history permission lets you read older records on supported devices. You can revoke access at any time through Health Connect settings.")
                Text("This app displays original SDK data, including source metadata and sample arrays. It does not interpret health results or produce medical advice.")
                Button(onClick = { finish() }) { Text("Done") }
            }
        } } }
    }
}
