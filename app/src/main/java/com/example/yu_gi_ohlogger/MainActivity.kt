package com.example.yu_gi_ohlogger // <--- Change this to match YOUR package name if different

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
                    BinderLoggerScreen()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BinderLoggerScreen() {
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

    var qtyText by remember { mutableStateOf("1") }
    var selectedEdition by remember { mutableStateOf("1st Edition") }
    var selectedRarity by remember { mutableStateOf("Common") }
    var rarityDropdownExpanded by remember { mutableStateOf(false) }
    var editionDropdownExpanded by remember { mutableStateOf(false) }

    var historyLogs by remember { mutableStateOf<List<CardLogEntry>>(emptyList()) }
    val csvFile = remember { File(context.filesDir, "binder_log.csv") }

    fun updateHistoryView() {
        if (!csvFile.exists()) {
            historyLogs = emptyList()
            return
        }
        try {
            val lines = csvFile.readLines()
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
        } catch (e: Exception) {
            historyLogs = emptyList()
        }
    }

    fun saveToCsv(entry: CardLogEntry) {
        val fileExists = csvFile.exists()
        val line = "\"${entry.cardName}\",${entry.quantity},\"${entry.rarity}\",\"${entry.edition}\",\"${entry.setName}\",\"${entry.setCode}\"\n"
        if (!fileExists) {
            val header = "Card Name,Card Quantity,Card Rarity,Card Edition,Card Set,Card Set Code\n"
            csvFile.writeText(header + line)
        } else {
            csvFile.appendText(line)
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
        Text(
            text = "YU-GI-OH BINDER LOGGER",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFC107)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchTerm,
                onValueChange = { searchTerm = it },
                label = { Text("Set Code or Card Name") },
                placeholder = { Text("e.g. MZTM-EN039") },
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

                                    val matchedSet = foundCard.cardSets?.firstOrNull { s ->
                                        s.setCode?.equals(term, ignoreCase = true) == true
                                    }

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
                        } catch (e: Exception) {
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
                            modifier = Modifier.menuAnchor()
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
                            .menuAnchor()
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
                        if (csvFile.exists()) {
                            val lines = csvFile.readLines()
                            if (lines.size > 1) {
                                val newLines = lines.dropLast(1)
                                csvFile.writeText(newLines.joinToString("\n") + "\n")
                                statusText = "↩ Removed last entry."
                                statusColor = Color.Red
                                updateHistoryView()
                            } else {
                                statusText = "(!) File is already empty."
                                statusColor = Color.Red
                            }
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
                    if (csvFile.exists()) {
                        val uri: Uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.provider",
                            csvFile
                        )
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/csv"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share CSV Log"))
                    } else {
                        Toast.makeText(context, "No CSV file found!", Toast.LENGTH_SHORT).show()
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