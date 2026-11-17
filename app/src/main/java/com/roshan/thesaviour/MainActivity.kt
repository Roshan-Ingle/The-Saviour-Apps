package com.roshan.thesaviour

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.roshan.thesaviour.data.EmergencyContact
import com.roshan.thesaviour.data.SaviourRepository
import com.roshan.thesaviour.service.SaviourAccessibilityService
import com.roshan.thesaviour.service.SaviourForegroundService
import com.roshan.thesaviour.ui.FakeShutdownActivity
import com.roshan.thesaviour.ui.theme.TheSaviourTheme
import com.roshan.thesaviour.util.SosLocationAndSmsManager

class MainActivity : ComponentActivity() {

    private val resumeCounter = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repo = SaviourRepository(this)
        if (repo.isProtectionEnabled()) {
            SaviourForegroundService.startService(this)
        }

        setContent {
            TheSaviourTheme {
                SaviourHomeScreen(resumeTrigger = resumeCounter.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCounter.intValue++
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaviourHomeScreen(resumeTrigger: Int) {
    val context = LocalContext.current
    val repository = remember { SaviourRepository(context) }

    var isProtectionOn by remember { mutableStateOf(repository.isProtectionEnabled()) }
    var isFakeShutdownOn by remember { mutableStateOf(repository.isFakeShutdownEnabled()) }
    var contacts by remember { mutableStateOf(repository.getContacts()) }
    var lastStatus by remember { mutableStateOf(repository.getLastSosStatus()) }

    var hasRuntimePermissions by remember { mutableStateOf(checkRuntimePermissions(context)) }
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var hasAccessibilityEnabled by remember {
        mutableStateOf(SaviourAccessibilityService.isAccessibilityServiceEnabled(context))
    }

    var showAddContactDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasRuntimePermissions = checkRuntimePermissions(context)
        if (isProtectionOn) {
            SaviourForegroundService.startService(context)
        }
    }

    LaunchedEffect(resumeTrigger) {
        hasRuntimePermissions = checkRuntimePermissions(context)
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasAccessibilityEnabled =
            SaviourAccessibilityService.isAccessibilityServiceEnabled(context)
        lastStatus = repository.getLastSosStatus()
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            text = "The Saviour",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Triple Power SOS • Fake Switch-Off Guard",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddContactDialog = true },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Contact")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add Contact", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Master Protection Card
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isProtectionOn) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = if (isProtectionOn) "Saviour Guard Active" else "Protection Paused",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "3x Power Button Tap -> Auto Data + GPS + SMS",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Switch(
                                checked = isProtectionOn,
                                onCheckedChange = { enabled ->
                                    isProtectionOn = enabled
                                    repository.setProtectionEnabled(enabled)
                                    if (enabled) {
                                        SaviourForegroundService.startService(context)
                                    } else {
                                        context.stopService(
                                            Intent(context, SaviourForegroundService::class.java)
                                        )
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Status: $lastStatus",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // 2. Core Features Toggle & Test Actions
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Fake Switch-Off Protection",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Blocks real power-off menu, shows fake dead screen & sends live GPS SMS",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = isFakeShutdownOn,
                                onCheckedChange = {
                                    isFakeShutdownOn = it
                                    repository.setFakeShutdownEnabled(it)
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    SosLocationAndSmsManager.triggerEmergencySos(
                                        context = context,
                                        reason = "Manual SOS Test"
                                    )
                                    lastStatus = repository.getLastSosStatus()
                                    Toast.makeText(
                                        context,
                                        "Turning on Data/GPS & sending location SMS...",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Test SOS SMS")
                            }

                            OutlinedButton(
                                onClick = {
                                    Toast.makeText(
                                        context,
                                        "Tip: Tap top-left corner 5 times to wake from Fake Switch-Off",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    val accService = SaviourAccessibilityService.instance
                                    if (accService != null && Settings.canDrawOverlays(context)) {
                                        repository.setFakeShutdownActive(true)
                                        accService.showFakeShutdownOverlay()
                                    } else {
                                        context.startActivity(
                                            Intent(context, FakeShutdownActivity::class.java)
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preview Fake Off")
                            }
                        }

                        Text(
                            text = "Owner Secret Unlock: Tap top-left corner 5 times rapidly to exit Fake Switch-Off.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3. Permissions & System Setup Checklist
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "One-Time Setup Checklist",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        SetupItemRow(
                            title = "1. Location, SMS & Notification Permissions",
                            subtitle = if (hasRuntimePermissions) "Granted & Ready" else "Required to send live GPS SMS",
                            isDone = hasRuntimePermissions,
                            buttonText = "Grant",
                            onClick = {
                                val perms = mutableListOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.SEND_SMS,
                                    Manifest.permission.READ_PHONE_STATE
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                permissionLauncher.launch(perms.toTypedArray())
                            }
                        )

                        SetupItemRow(
                            title = "2. Appear on Top (Overlay Permission)",
                            subtitle = if (hasOverlayPermission) "Granted & Ready" else "Required for Fake Switch-Off screen",
                            isDone = hasOverlayPermission,
                            buttonText = "Enable",
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        )

                        SetupItemRow(
                            title = "3. Accessibility Guard Service",
                            subtitle = if (hasAccessibilityEnabled) {
                                "Active (Power menu interceptor + Quick Settings auto-toggle)"
                            } else {
                                "Required to intercept Power-Off & auto-enable Data/GPS tiles"
                            },
                            isDone = hasAccessibilityEnabled,
                            buttonText = "Activate",
                            onClick = {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                        )
                    }
                }
            }

            // 4. Saved Emergency Contacts Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Saved Emergency Contacts (${contacts.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (contacts.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "No emergency contacts saved yet",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ Add Contact' below to add family or trusted numbers who will receive your live GPS link.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(contacts, key = { it.id }) { contact ->
                    ContactCard(
                        contact = contact,
                        onDelete = {
                            contacts = repository.removeContact(contact.id)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(88.dp))
            }
        }
    }

    if (showAddContactDialog) {
        AddEmergencyContactDialog(
            onDismiss = { showAddContactDialog = false },
            onSave = { name, phone ->
                contacts = repository.addContact(name, phone)
                showAddContactDialog = false
            }
        )
    }
}

@Composable
private fun SetupItemRow(
    title: String,
    subtitle: String,
    isDone: Boolean,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isDone) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (!isDone) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun ContactCard(
    contact: EmergencyContact,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = contact.phone,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove contact",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun AddEmergencyContactDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Emergency Contact") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Contact Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Number (with +91 or 10 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (phone.isNotBlank()) {
                        onSave(name, phone)
                    }
                },
                enabled = phone.isNotBlank()
            ) {
                Text("Save Contact")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun checkRuntimePermissions(context: android.content.Context): Boolean {
    val fineLoc = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val sms = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED
    return fineLoc && sms
}
