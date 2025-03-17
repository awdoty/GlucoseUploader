package com.example.glucoseuploader

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.glucoseuploader.ui.theme.GlucoseUploaderTheme
import kotlinx.coroutines.launch
import java.io.FileNotFoundException
import android.content.pm.PackageManager
import androidx.compose.material3.ExperimentalMaterial3Api

class MainActivity : ComponentActivity() {
    private val tag = "MainActivity"
    private lateinit var healthConnectUploader: HealthConnectUploader
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    // Variables for handling shared files
    private var sharedFileUri: Uri? = null
    private var isHandlingSharedFile = false
    private var lastFileHandlingTime: Long = 0

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1003
        private const val STORAGE_PERMISSION_CODE = 1004
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Health Connect uploader
        healthConnectUploader = HealthConnectUploader(this)

        // Request storage permissions
        requestStoragePermissions()

        // Create permission launcher
        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val allGranted = permissions.all { it.value }
            Log.d(tag, "Permissions granted: $allGranted")

            // Open Health Connect to fully activate permissions
            if (allGranted) {
                openHealthConnectWithPermissions()
            }
        }

        // Request notification permission on Android 13+
        requestNotificationPermissionIfNeeded()

        // Log detailed Health Connect info
        lifecycleScope.launch {
            try {
                healthConnectUploader.logHealthConnectInfo()
            } catch (e: Exception) {
                Log.e(tag, "Error logging Health Connect info: ${e.message}")
            }
        }

        // Handle intent if this activity was started from a share action
        handleIntent(intent)

        setContent {
            GlucoseUploaderTheme {
                GlucoseUploaderApp(
                    healthConnectUploader = healthConnectUploader,
                    requestPermissions = { requestHealthConnectPermissions() }
                )
            }
        }
    }

    private fun requestStoragePermissions() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    STORAGE_PERMISSION_CODE
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    /**
     * Public method to request Health Connect permissions (can be called from other activities)
     */
    fun requestHealthConnectPermissions() {
        try {
            // Use the permission launcher for Android permissions
            val permissions = healthConnectUploader.getRequiredPermissions()
            Log.d(tag, "Requesting permissions: ${permissions.joinToString()}")
            permissionLauncher.launch(permissions)
        } catch (e: Exception) {
            // If permission launcher fails, fall back to requesting through Health Connect
            Log.e(tag, "Error launching permission request: ${e.message}")
            openHealthConnectWithPermissions()
        }
    }

    /**
     * Opens Health Connect app to directly manage permissions
     */
    private fun openHealthConnectWithPermissions() {
        try {
            // Try all known intent actions that might work with Health Connect
            val intents = listOf(
                Intent("android.health.connect.action.MANAGE_HEALTH_DATA").apply {
                    putExtra("android.health.connect.extra.PACKAGE_NAME", packageName)
                    putExtra("android.health.connect.extra.CATEGORY", "android.health.connect.category.BLOOD_GLUCOSE")
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
                Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                    putExtra("android.health.connect.extra.PACKAGE_NAME", packageName)
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
                Intent("android.health.connect.action.PERMISSION_CONTROLLER").apply {
                    putExtra("android.health.connect.extra.PACKAGE_NAME", packageName)
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
                Intent("android.health.connect.action.HEALTH_CONNECT_SETTINGS").apply {
                    putExtra("android.health.connect.extra.PACKAGE_NAME", packageName)
                    addCategory(Intent.CATEGORY_DEFAULT)
                }
            )

            // Try each intent in sequence until one works
            for (intent in intents) {
                try {
                    if (intent.resolveActivity(packageManager) != null) {
                        startActivity(intent)
                        return
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error with intent ${intent.action}: ${e.message}")
                    // Continue to next intent
                }
            }

            // If no intent works, fall back to opening HC directly
            lifecycleScope.launch {
                healthConnectUploader.openHealthConnectApp(this@MainActivity)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error opening Health Connect: ${e.message}")
            Toast.makeText(this, "Please manually enable permissions in Health Connect", Toast.LENGTH_LONG).show()
        }
    }

    // Handle incoming intents (for shared files)
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        // Prevent duplicate handling of the same intent
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFileHandlingTime < 1000) {
            Log.d(tag, "Skipping intent handling - too soon after previous")
            return
        }
        lastFileHandlingTime = currentTime

        Log.d(tag, "Handling intent: ${intent.action}, type: ${intent.type}")

        when (intent.action) {
            Intent.ACTION_SEND -> {
                handleSendIntent(intent)
            }
            Intent.ACTION_VIEW -> {
                handleViewIntent(intent)
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                handleSendMultipleIntent(intent)
            }
        }
    }

    private fun handleSendIntent(intent: Intent) {
        // Check if the intent has a file that matches our supported types
        if (isSupportedFileType(intent.type)) {
            // Get the URI of the shared file
            val uri = getUriFromIntent(intent)

            Log.d(tag, "File URI from intent: $uri")

            uri?.let {
                try {
                    // Validate the URI by trying to read it
                    contentResolver.openInputStream(it)?.use { stream ->
                        // File is readable
                        sharedFileUri = it
                        isHandlingSharedFile = true
                        Log.d(tag, "Received shared file: $it")

                        // Launch CSV import activity
                        launchCsvImportActivity(it)
                    }
                } catch (e: FileNotFoundException) {
                    Log.e(tag, "Cannot access file: $it", e)
                    Toast.makeText(this, "Cannot access the shared file", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(tag, "Error handling shared file: $it", e)
                    Toast.makeText(this, "Error handling the shared file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleSendMultipleIntent(intent: Intent) {
        // Check if the intent has files that match our supported types
        if (isSupportedFileType(intent.type)) {
            // Get the URIs of the shared files
            val uriList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }

            if (uriList != null && uriList.isNotEmpty()) {
                // For now, just handle the first file
                val uri = uriList[0]
                try {
                    contentResolver.openInputStream(uri)?.use {
                        sharedFileUri = uri
                        isHandlingSharedFile = true
                        Log.d(tag, "Received shared file from multiple: $uri")

                        // Launch CSV import activity
                        launchCsvImportActivity(uri)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Error handling multiple shared files", e)
                    Toast.makeText(this, "Error handling shared files", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleViewIntent(intent: Intent) {
        val uri = intent.data
        if (uri != null && isSupportedFileType(intent.type)) {
            Log.d(tag, "Received view intent with URI: $uri")

            try {
                launchCsvImportActivity(uri, shouldFinish = true)
            } catch (e: Exception) {
                Log.e(tag, "Error handling VIEW intent: ${e.message}")
                Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getUriFromIntent(intent: Intent): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri
        } ?: intent.data
    }

    private fun isSupportedFileType(type: String?): Boolean {
        return type?.let {
            it.startsWith("text/") ||
                    it == "application/vnd.ms-excel" ||
                    it == "application/octet-stream" ||
                    it.contains("spreadsheet") ||
                    it.contains("csv")
        } == true
    }

    private fun launchCsvImportActivity(uri: Uri, shouldFinish: Boolean = false) {
        val importIntent = Intent(this, CsvImportActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = uri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)  // Add this important flag
        }
        startActivity(importIntent)

        if (shouldFinish) {
            finish() // Finish this activity if needed (e.g., when directly viewing a file)
        }
    }

    // Handle new intents when app is already running
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
        setIntent(intent)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun GlucoseUploaderApp(
        healthConnectUploader: HealthConnectUploader,
        requestPermissions: () -> Unit
    ) {
        val navController = rememberNavController()

        // Define navigation items
        val navItems = listOf(
            NavItem("home", "Home", Icons.Default.Home),
            NavItem("history", "History", Icons.Default.History),
            NavItem("statistics", "Statistics", Icons.Default.BarChart),
            NavItem("settings", "Settings", Icons.Default.Settings)
        )

        // Handle shared file if present
        sharedFileUri?.let { uri ->
            var showImportScreen by remember { mutableStateOf(true) }

            if (showImportScreen) {
                CsvImportScreen(
                    uri = uri,
                    healthConnectUploader = healthConnectUploader,
                    onImportComplete = { success, message ->
                        showImportScreen = false

                        // Show toast with result
                        Toast.makeText(
                            this@MainActivity,
                            message,
                            Toast.LENGTH_LONG
                        ).show()

                        // Reset shared file state
                        sharedFileUri = null
                        isHandlingSharedFile = false
                    }
                )
                return  // Return here to only show the import screen
            }
        }

        // Regular app UI when not handling shared files
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route ?: "home"

                        // Get title based on route
                        val title = when (currentRoute) {
                            "home" -> "Glucose Uploader"
                            "history" -> "Glucose History"
                            "statistics" -> "Glucose Statistics"
                            "settings" -> "Settings"
                            else -> "Glucose Uploader"
                        }

                        Text(text = title)
                    },
                    actions = {
                        // Add app version indicator
                        Text(
                            text = "v${BuildConfig.VERSION_NAME}",
                            modifier = Modifier.padding(end = 16.dp),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentDestination = navBackStackEntry?.destination

                    navItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    // Pop up to the start destination of the graph to
                                    // avoid building up a large stack of destinations
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination
                                    launchSingleTop = true
                                    // Restore state when navigating back
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(paddingValues)
            ) {
                composable("home") {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        GlucoseUploaderScreen(
                            healthConnectUploader = healthConnectUploader,
                            requestPermissions = requestPermissions
                        )
                    }
                }
                composable("history") {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        GlucoseHistoryScreen(
                            healthConnectUploader = healthConnectUploader,
                            requestPermissions = requestPermissions
                        )
                    }
                }
                composable("statistics") {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        GlucoseStatisticsScreen(
                            healthConnectUploader = healthConnectUploader,
                            requestPermissions = requestPermissions
                        )
                    }
                }
                composable("settings") {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SettingsScreen(
                            healthConnectUploader = healthConnectUploader,
                            requestPermissions = requestPermissions
                        )
                    }
                }
            }
        }
    }
}

data class NavItem(
    val route: String,
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)