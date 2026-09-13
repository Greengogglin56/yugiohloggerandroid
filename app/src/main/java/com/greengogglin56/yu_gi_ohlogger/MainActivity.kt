package com.greengogglin56.yu_gi_ohlogger // <--- Keep your package name here

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF121212)
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

val RARITIES = listOf(
    "Common", "Rare", "Super Rare", "Ultra Rare",
    "Secret Rare", "Quarter Century Secret Rare",
    "Collector's Rare", "Ultimate Rare"
)

val EDITIONS = listOf("1st Edition", "Unlimited", "Limited")

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var activeCsvUri by remember { mutableStateOf<Uri?>(null) }
    var activeFile by remember { mutableStateOf<File?>(null) }

    // Launcher to CREATE a new CSV file via System File Picker
    val createCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri?.let {
            activeCsvUri = it
        }
    }

    // Launcher to OPEN an existing CSV file via System File Picker
    val openCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            activeCsvUri = it
        }
    }

    if (activeCsvUri == null && activeFile == null) {
        // Welcome Screen
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "YU-GI-OH BINDER LOGGER",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFC107)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select or create a binder log CSV file to begin.",
                fontSize = 14.sp,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { createCsvLauncher.launch("binder_log.csv") },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Binder Log (.csv)", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { openCsvLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Open Existing Binder Log", fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = {
                    // Quick-start option: Use app's local storage default
                    activeFile = File(context.filesDir, "binder_log.csv")
                }
            ) {
                Text("Use Default Local Storage File", color = Color.Gray)
            }
        }
    } else {
        BinderLoggerScreen(
            targetUri = activeCsvUri,
            targetFile = activeFile,
            onChangeFile = {
                activeCsvUri = null
                activeFile = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinderLoggerScreen(
    targetUri: Uri?,
    targetFile: File?,
    onChangeFile: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var setMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var searchTerm by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("Ready to log cards.") }
    var statusColor by remember { mutableStateOf(Color.Gray) }

    var currentCardName by remember { mutableStateOf("") }
    var currentSetName by remember { mutableStateOf("") }
    var currentSetCode by remember { mutableStateOf("") }
    var currentImageUrl by remember { mutableStateOf<String?>(null) }
    var isPreviewVisible by remember { mutableStateOf(false) }

    // Multi-Set / Printing Dropdown State
    var availableSets by remember { mutableStateOf<List<YgoCardSetDetail>>(emptyList()) }
    var setDropdownExpanded by remember { mutableStateOf(false) }

    var qtyText by remember { mutableStateOf("1") }
    var selectedEdition by remember { mutableStateOf("1st Edition") }
    var selectedRarity by remember { mutableStateOf("Common") }
    var rarityDropdownExpanded by remember { mutableStateOf(false) }
    var editionDropdownExpanded by remember { mutableStateOf(false) }

    var historyLogs by remember { mutableStateOf<List<CardLogEntry>>(emptyList()) }

    fun writeTextToTarget(content: String) {
        if (targetUri != null) {
            context.contentResolver.openOutputStream(targetUri, "w")?.use {
                it.write(content.toByteArray())
            }
        } else targetFile?.writeText(content)
    }

    fun appendTextToTarget(content: String) {
        if (targetUri != null) {
            context.contentResolver.openOutputStream(targetUri, "wa")?.use {
                it.write(content.toByteArray())
            }
        } else targetFile?.appendText(content)
    }

    fun readLinesFromTarget(): List<String> {
        return if (targetUri != null) {
            context.contentResolver.openInputStream(targetUri)?.bufferedReader()?.use { it.readLines() } ?: emptyList()
        } else if (targetFile != null && targetFile.exists()) {
            targetFile.readLines()
        } else emptyList()
    }

    fun updateHistoryView() {
        try {
            val lines = readLinesFromTarget()
            if (lines.size > 1) {
                val entries = lines.drop(1).mapNotNull { line ->
                    val parts = line.split(",")
                    if (parts.size >= 6) {
                        CardLogEntry(
                            cardName = parts[0].removeSurrounding("\""),
                            quantity = parts[1].toIntOrNull() ?: 1,
                            rarity = parts[2].removeSurrounding("\""),
                            edition = parts[3].removeSurrounding("\""),
                            setName = parts[4].removeSurrounding("\""),
                            setCode = parts[5].removeSurrounding("\"")
                        )
                    } else null
                }
                historyLogs = entries.takeLast(5).reversed()
            } else {
                historyLogs = emptyList()
            }
        } catch (_: Exception) {
            historyLogs = emptyList()
        }
    }

    fun saveToCsv(entry: CardLogEntry) {
        val lines = readLinesFromTarget()
        val line = "\"${entry.cardName}\",${entry.quantity},\"${entry.rarity}\",\"${entry.edition}\",\"${entry.setName}\",\"${entry.setCode}\"\n"
        if (lines.isEmpty()) {
            val header = "Card Name,Card Quantity,Card Rarity,Card Edition,Card Set,Card Set Code\n"
            writeTextToTarget(header + line)
        } else {
            appendTextToTarget(line)
        }
    }

    LaunchedEffect(Unit) {
        updateHistoryView()
        withContext(Dispatchers.IO) {
            try {
                val response = YgoApiService.api.getCardSets()
                val map = mutableMapOf<String, String>()
                response.forEach { set ->
                    if (set.setCode != null && set.setName != null) {
                        map[set.setCode.uppercase()] = set.setName
                    }
                }
                setMap = map
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "YU-GI-OH BINDER LOGGER",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFC107)
            )
            IconButton(onClick = onChangeFile) {
                Icon(Icons.Default.Folder, contentDescription = "Change File", tint = Color.LightGray)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchTerm,
                onValueChange = { searchTerm = it },
                label = { Text("Set Code or Card Name") },
                placeholder = { Text("e.g. MZTM-EN039 or Blue-Eyes") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Button(
                onClick = {
                    if (searchTerm.isBlank()) {
                        statusText = "Please enter a card name or set code!"
                        statusColor = Color.Red
                        isPreviewVisible = false
                        return@Button
                    }
                    statusText = "Fetching '$searchTerm'..."
                    statusColor = Color.Cyan

                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val term = searchTerm.trim()
                            var foundCard: YgoCard? = null

                            if (term.contains("-")) {
                                val baseCode = term.split("-")[0].uppercase()
                                val fullSetName = setMap[baseCode]
                                if (fullSetName != null) {
                                    val res = YgoApiService.api.getCardBySetName(fullSetName)
                                    foundCard = res.data?.firstOrNull { card ->
                                        card.cardSets?.any { s -> s.setCode?.equals(term, ignoreCase = true) == true } == true
                                    }
                                }
                            } else if (term.all { it.isDigit() }) {
                                foundCard = YgoApiService.api.getCardById(term).data?.firstOrNull()
                            }

                            if (foundCard == null) {
                                foundCard = YgoApiService.api.getCardByFname(term).data?.firstOrNull()
                            }

                            withContext(Dispatchers.Main) {
                                if (foundCard != null) {
                                    currentCardName = foundCard.name
                                    currentImageUrl = foundCard.cardImages?.firstOrNull()?.imageUrlSmall

                                    val sets = foundCard.cardSets ?: emptyList()
                                    availableSets = sets

                                    val matchedSet = sets.firstOrNull { s ->
                                        s.setCode?.equals(term, ignoreCase = true) == true
                                    } ?: sets.firstOrNull()

                                    if (matchedSet != null) {
                                        currentSetCode = matchedSet.setCode ?: "N/A"
                                        currentSetName = matchedSet.setName ?: "N/A"
                                        selectedRarity = if (RARITIES.contains(matchedSet.setRarity)) matchedSet.setRarity!! else "Ultra Rare"
                                    } else {
                                        currentSetCode = "N/A"
                                        currentSetName = "N/A"
                                        selectedRarity = "Common"
                                    }

                                    statusText = "Found: ${foundCard.name}"
                                    statusColor = Color.Green
                                    isPreviewVisible = true
                                } else {
                                    statusText = "Card '$term' not found."
                                    statusColor = Color.Red
                                    isPreviewVisible = false
                                }
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                statusText = "Error fetching card data."
                                statusColor = Color.Red
                                isPreviewVisible = false
                            }
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Search")
            }
        }

        Text(text = statusText, color = statusColor, fontSize = 14.sp)
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)

        if (isPreviewVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (currentImageUrl != null) {
                        AsyncImage(
                            model = currentImageUrl,
                            contentDescription = "Card Image",
                            modifier = Modifier
                                .size(width = 90.dp, height = 130.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = currentCardName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(text = "$currentSetCode — $currentSetName", fontSize = 14.sp, color = Color.Cyan)
                    }
                }

                // Dropdown to pick Set printing if multiple options exist
                if (availableSets.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = setDropdownExpanded,
                        onExpandedChange = { setDropdownExpanded = !setDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = "$currentSetCode — $currentSetName",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Printing / Set") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = setDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = setDropdownExpanded,
                            onDismissRequest = { setDropdownExpanded = false }
                        ) {
                            availableSets.forEach { setItem ->
                                DropdownMenuItem(
                                    text = { Text("${setItem.setCode ?: "N/A"} — ${setItem.setName ?: "N/A"}") },
                                    onClick = {
                                        currentSetCode = setItem.setCode ?: "N/A"
                                        currentSetName = setItem.setName ?: "N/A"
                                        if (RARITIES.contains(setItem.setRarity)) {
                                            selectedRarity = setItem.setRarity!!
                                        }
                                        setDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = qtyText,
                        onValueChange = { qtyText = it },
                        label = { Text("Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(80.dp),
                        singleLine = true
                    )

                    ExposedDropdownMenuBox(
                        expanded = editionDropdownExpanded,
                        onExpandedChange = { editionDropdownExpanded = !editionDropdownExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedEdition,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Edition") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = editionDropdownExpanded) },
                            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = editionDropdownExpanded,
                            onDismissRequest = { editionDropdownExpanded = false }
                        ) {
                            EDITIONS.forEach { item ->
                                DropdownMenuItem(
                                    text = { Text(item) },
                                    onClick = {
                                        selectedEdition = item
                                        editionDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = rarityDropdownExpanded,
                    onExpandedChange = { rarityDropdownExpanded = !rarityDropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedRarity,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rarity") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rarityDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = rarityDropdownExpanded,
                        onDismissRequest = { rarityDropdownExpanded = false }
                    ) {
                        RARITIES.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item) },
                                onClick = {
                                    selectedRarity = item
                                    rarityDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        val qty = qtyText.toIntOrNull() ?: 1
                        val entry = CardLogEntry(
                            cardName = currentCardName,
                            quantity = qty,
                            rarity = selectedRarity,
                            edition = selectedEdition,
                            setName = currentSetName,
                            setCode = currentSetCode
                        )
                        saveToCsv(entry)
                        statusText = "✔ Saved '$currentCardName'!"
                        statusColor = Color.Green
                        isPreviewVisible = false
                        searchTerm = ""
                        updateHistoryView()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add to Log")
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1C1C1E))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT ENTRIES",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFC107)
                )

                TextButton(
                    onClick = {
                        val lines = readLinesFromTarget()
                        if (lines.size > 1) {
                            val newLines = lines.dropLast(1)
                            writeTextToTarget(newLines.joinToString("\n") + "\n")
                            statusText = "↩ Removed last entry."
                            statusColor = Color.Red
                            updateHistoryView()
                        } else {
                            statusText = "(!) File is already empty."
                            statusColor = Color.Red
                        }
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Undo", tint = Color.Red)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Undo ↩", color = Color.Red)
                }
            }

            HorizontalDivider(color = Color.Gray, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(8.dp))

            if (historyLogs.isEmpty()) {
                Text("No entries logged yet.", fontSize = 13.sp, color = Color.Gray)
            } else {
                historyLogs.forEach { log ->
                    Text(
                        text = "• ${log.cardName} (${log.quantity}x) — ${log.rarity} [${log.setCode}]",
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (targetFile != null && targetFile.exists()) {
                        val uri: Uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            targetFile
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share CSV Log"))
                    } else if (targetUri != null) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, targetUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share CSV Log"))
                    } else {
                        Toast.makeText(context, "No active CSV file found!", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Icon(Icons.Default.Share, contentDescription = "Share")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share CSV")
            }
        }
    }

}