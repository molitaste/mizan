package com.mizan.app

import android.app.Application
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mizan.app.data.Settings
import com.mizan.app.data.Store
import com.mizan.app.ui.EditorScreen
import com.mizan.app.ui.ExportScreen
import com.mizan.app.ui.HistoryScreen
import com.mizan.app.ui.ImportScreen
import com.mizan.app.ui.LibraryScreen
import com.mizan.app.ui.MizanTheme
import com.mizan.app.ui.NotesScreen
import com.mizan.app.ui.PrepScreen
import com.mizan.app.ui.ReviewScreen
import com.mizan.app.ui.SettingsScreen

class MizanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        Store.init(this)
    }
}

/** In-memory UI session state shared between screens. */
object Session {
    /** Current batch factor per recipe (Scale Batch). Not saved: scaling never changes the formula. */
    val factors = mutableStateMapOf<String, Double>()
    fun factor(id: String) = factors[id] ?: 1.0
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val s by Settings.state.collectAsState()
            LaunchedEffect(s.eink) {
                if (s.eink) enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
                    navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
                ) else enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
                    navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
            }
            MizanTheme(arabic = s.arabic, eink = s.eink, textScale = s.textScale) { AppNav() }
        }
    }

    override fun onPause() {
        super.onPause()
        Store.flush()
    }
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "library") {
        composable("library") {
            LibraryScreen(
                onOpen = { nav.navigate("editor/$it") },
                onImport = { mode -> if (mode == "manual") nav.navigate("review") else nav.navigate("import/$mode") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("import/{mode}") { e ->
            ImportScreen(
                mode = e.arguments?.getString("mode") ?: "none",
                onBack = { nav.popBackStack() },
                onReview = { nav.navigate("review") },
            )
        }
        composable("review") {
            ReviewScreen(
                onBack = { nav.popBackStack() },
                onCreated = { id ->
                    nav.popBackStack("library", inclusive = false)
                    nav.navigate("editor/$id")
                },
            )
        }
        composable("editor/{id}?edit={edit}", arguments = listOf(navArgument("edit") { type = NavType.BoolType; defaultValue = false })) { e ->
            val id = e.arguments?.getString("id") ?: ""
            EditorScreen(
                id = id,
                startInEdit = e.arguments?.getBoolean("edit") ?: false,
                onBack = { nav.popBackStack() },
                onPrep = { nav.navigate("prep/$id") },
                onNotes = { nav.navigate("notes/$id") },
                onHistory = { nav.navigate("history/$id") },
                onExport = { kind -> nav.navigate("export/$id?kind=$kind") },
            )
        }
        composable("prep/{id}") { e ->
            val id = e.arguments?.getString("id") ?: ""
            PrepScreen(id = id, onBack = { nav.popBackStack() })
        }
        composable("notes/{id}") { e ->
            NotesScreen(id = e.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
        }
        composable("history/{id}") { e ->
            val id = e.arguments?.getString("id") ?: ""
            HistoryScreen(
                id = id,
                onBack = { nav.popBackStack() },
                onOpenRecipe = { newId ->
                    nav.popBackStack("library", inclusive = false)
                    nav.navigate("editor/$newId")
                },
                onExportVersion = { vid -> nav.navigate("export/$id?version=$vid") },
            )
        }
        composable(
            "export/{id}?version={version}&kind={kind}",
            arguments = listOf(
                navArgument("version") { type = NavType.StringType; defaultValue = "" },
                navArgument("kind") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { e ->
            ExportScreen(
                id = e.arguments?.getString("id") ?: "",
                versionId = e.arguments?.getString("version") ?: "",
                preferKind = e.arguments?.getString("kind") ?: "",
                onBack = { nav.popBackStack() },
            )
        }
        composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
    }
}
