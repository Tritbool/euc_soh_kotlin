// INTEGRATION_EXAMPLE.kt
// Exemple complet d'intégration des fonctionnalités de visualisation
// Ce fichier montre comment connecter tous les composants.

package io.github.eucsoh.android.integration

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.eucsoh.android.data.model.ReqStatsResult
import io.github.eucsoh.android.ui.screens.ChartGalleryScreen
import io.github.eucsoh.android.ui.screens.FileListScreen
import kotlinx.coroutines.launch

/**
 * =============================================
 * 1. Définition des routes de navigation
 * =============================================
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object WheelList : Screen("wheel_list")
    data class WheelDetails(val wheelId: String) : Screen("wheel_details/{wheelId}") {
        fun createRoute(wheelId: String) = "wheel_details/$wheelId"
    }
    
    // NOUVEAUX ÉCRANS
    data class FileList(
        val wheelName: String,
        val dirUri: String
    ) : Screen("file_list/{wheelName}/{dirUri}") {
        fun createRoute(wheelName: String, dirUri: String) = 
            "file_list/$wheelName/${Uri.encode(dirUri)}"
    }
    
    data class ChartGallery(
        val wheelId: String
    ) : Screen("chart_gallery/{wheelId}") {
        fun createRoute(wheelId: String) = "chart_gallery/$wheelId"
    }
}

/**
 * =============================================
 * 2. Composant de navigation principal
 * =============================================
 */
@Composable
fun AppNavigation(
    database: WheelDatabase, // Votre database existante
    startDestination: String = Screen.Home.route
) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ... Routes existantes (Home, WheelList, etc.)
        
        // ROUTE 1: Liste des fichiers
        composable(
            route = "file_list/{wheelName}/{dirUri}"
        ) { backStackEntry ->
            val wheelName = backStackEntry.arguments?.getString("wheelName") ?: "Unknown"
            val dirUriString = backStackEntry.arguments?.getString("dirUri") ?: ""
            val dirUri = Uri.parse(Uri.decode(dirUriString))
            
            FileListScreen(
                wheelName = wheelName,
                wheelDirUri = dirUri,
                onBack = { navController.popBackStack() }
            )
        }
        
        // ROUTE 2: Galerie de graphiques
        composable(
            route = "chart_gallery/{wheelId}"
        ) { backStackEntry ->
            val wheelId = backStackEntry.arguments?.getString("wheelId") ?: ""
            val scope = rememberCoroutineScope()
            val context = LocalContext.current
            
            var stats by remember { mutableStateOf<List<ReqStatsResult>>(emptyList()) }
            var wheelName by remember { mutableStateOf("Loading...") }
            var isLoading by remember { mutableStateOf(true) }
            
            // Charger stats depuis database
            LaunchedEffect(wheelId) {
                scope.launch {
                    val wheel = database.wheelDao().getWheelById(wheelId)
                    wheelName = wheel?.name ?: "Unknown"
                    
                    // Récupérer tous les ReqStatsResult pour cette roue
                    stats = database.statsDao().getStatsForWheel(wheelId)
                    isLoading = false
                }
            }
            
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (stats.isEmpty()) {
                EmptyStateScreen(
                    message = "No analysis data available",
                    onBack = { navController.popBackStack() }
                )
            } else {
                ChartGalleryScreen(
                    wheelName = wheelName,
                    stats = stats,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

/**
 * =============================================
 * 3. Modification de WheelDetailsScreen
 * =============================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WheelDetailsScreenWithActions(
    wheel: WheelEntity, // Votre modèle existant
    onBack: () -> Unit,
    onNavigateToFiles: (String, String) -> Unit,
    onNavigateToCharts: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(wheel.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // === STATS EXISTANTES ===
            Text(
                text = "Current Status",
                style = MaterialTheme.typography.titleLarge
            )
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsRow("Req median", "${wheel.latestReqMedian} Ω")
                    StatsRow("Sag 95p", "${wheel.latestSag95p} V")
                    StatsRow("Total distance", "${wheel.totalKm} km")
                    // ... autres stats
                }
            }
            
            // === NOUVEAUX BOUTONS D'ACTION ===
            Text(
                text = "Actions",
                style = MaterialTheme.typography.titleLarge
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Bouton 1: Gestion fichiers
                OutlinedButton(
                    onClick = {
                        onNavigateToFiles(
                            wheel.name,
                            wheel.directoryUri.toString()
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Files",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Manage Files")
                    }
                }
                
                // Bouton 2: Graphiques
                Button(
                    onClick = {
                        onNavigateToCharts(wheel.id)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.BarChart,
                            contentDescription = "Charts",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("View Charts")
                    }
                }
            }
        }
    }
}

/**
 * =============================================
 * 4. Exemple d'utilisation dans MainActivity
 * =============================================
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val database = WheelDatabase.getInstance(applicationContext)
        
        setContent {
            EucSohTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(database = database)
                }
            }
        }
    }
}

/**
 * =============================================
 * 5. Helper composables
 * =============================================
 */
@Composable
fun StatsRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmptyStateScreen(
    message: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("No Data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

/**
 * =============================================
 * 6. CHECKLIST D'INTÉGRATION
 * =============================================
 * 
 * ☐ 1. Copier les définitions de Screen() dans votre navigation
 * ☐ 2. Ajouter les routes composable() dans NavHost
 * ☐ 3. Modifier WheelDetailsScreen pour ajouter les 2 boutons
 * ☐ 4. Vérifier que database.statsDao().getStatsForWheel() existe
 * ☐ 5. Tester navigation Files → Preview → Open
 * ☐ 6. Tester navigation Charts → Tap → Export PDF
 * ☐ 7. Vérifier permissions storage dans AndroidManifest.xml
 * ☐ 8. Tester avec une roue sans données (affiche "No data")
 * ☐ 9. Tester avec < 3 points (affiche "Insufficient data")
 * ☐ 10. Vérifier que le PDF est créé dans Documents/EUC_SoH/
 */
