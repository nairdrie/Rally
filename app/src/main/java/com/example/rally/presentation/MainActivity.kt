package com.example.rally.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.*
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.example.rally.presentation.theme.RallyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            RallyApp()
        }
    }
}

@Composable
fun RallyApp() {
    val navController = rememberSwipeDismissableNavController()
    val viewModel: RallyViewModel = viewModel()

    RallyTheme {
        AppScaffold {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = "start"
            ) {
                composable("start") {
                    StartScreen(
                        onStart = { type ->
                            viewModel.startRally(type)
                            navController.navigate("rally")
                        },
                        onViewHistory = {
                            navController.navigate("history")
                        }
                    )
                }
                composable("rally") {
                    RallyScreen(
                        count = viewModel.currentCount,
                        timerText = viewModel.formatDuration(viewModel.elapsedTimeSeconds),
                        onStop = {
                            viewModel.stopRally()
                            navController.popBackStack()
                        }
                    )
                }
                composable("history") {
                    HistoryScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun StartScreen(onStart: (String) -> Unit, onViewHistory: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            Button(
                onClick = { onStart("Serve") },
                modifier = Modifier.fillMaxWidth(0.8f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Serve", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onStart("Receive") },
                modifier = Modifier.fillMaxWidth(0.8f).height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Text("Receive", fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = onViewHistory
            ) {
                Text("History", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun RallyScreen(
    count: Int,
    timerText: String,
    onStop: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = timerText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )
            
//            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "$count",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 70.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.primary
            )

//            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.height(36.dp).width(58.dp)
            ) {
                Text("END", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun HistoryScreen(viewModel: RallyViewModel) {
    val listState = rememberScalingLazyListState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (viewModel.history.isEmpty()) {
                item {
                    Text(
                        "No rallies yet",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                }
            } else {
                items(viewModel.history, key = { it.timestamp + it.count }) { record ->
                    // Simplified SwipeToReveal for Material 3
                    SwipeToReveal(
                        onSwipePrimaryAction = {
                            viewModel.deleteRecord(record)
                            true
                        },
                        primaryAction = {
                            // Using a standard SwipeToRevealPrimaryAction if available
                            // or fallback to a stylized button
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    ) {
                        Card(
                            onClick = { },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(record.type, style = MaterialTheme.typography.labelMedium)
                                    Text("${record.timestamp} • ${record.duration}", style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    "${record.count}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@WearPreviewDevices
@Composable
fun RallyScreenPreview() {
    RallyTheme {
        RallyScreen(
            count = 14,
            timerText = "00:32",
            onStop = {}
        )
    }
}
