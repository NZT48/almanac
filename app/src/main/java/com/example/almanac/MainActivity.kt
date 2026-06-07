package com.example.almanac

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.almanac.ui.main.MainScreen
import com.example.almanac.ui.main.MainViewModel
import com.example.almanac.ui.newentry.EditEntryScreen
import com.example.almanac.ui.newentry.EntryWizardViewModel
import com.example.almanac.ui.newentry.NewEntryScreen
import com.example.almanac.ui.physical.PhysicalEntriesScreen
import com.example.almanac.ui.settings.SettingsScreen
import com.example.almanac.ui.theme.AlmanacTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AlmanacTheme {
                val navController = rememberNavController()
                // Single shared MainViewModel instance — so returning from Settings
                // keeps the picker / result panel state, and we can refresh permissions on resume.
                val mainViewModel: MainViewModel = viewModel(factory = MainViewModel.Factory)

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    mainViewModel.refreshState()
                }

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenPhysicalEntries = { navController.navigate("physical_entries") },
                            onOpenNewEntry = { navController.navigate("new_entry") },
                            viewModel = mainViewModel,
                        )
                    }
                    composable("settings") {
                        SettingsScreen(onBack = { navController.popBackStack() })
                    }
                    composable("physical_entries") {
                        PhysicalEntriesScreen(
                            onBack = { navController.popBackStack() },
                            onEdit = { id -> navController.navigate("edit_entry/$id") },
                        )
                    }
                    composable("new_entry") {
                        NewEntryScreen(
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = "edit_entry/{${EntryWizardViewModel.ENTRY_ID_ARG}}",
                        arguments = listOf(
                            navArgument(EntryWizardViewModel.ENTRY_ID_ARG) { type = NavType.StringType },
                        ),
                    ) {
                        EditEntryScreen(
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
