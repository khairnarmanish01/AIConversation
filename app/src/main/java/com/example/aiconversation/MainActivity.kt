package com.example.aiconversation

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.aiconversation.data.UserPreferencesRepository
import com.example.aiconversation.ui.screen.ConversationScreen
import com.example.aiconversation.ui.screen.SettingsScreen
import com.example.aiconversation.ui.theme.AIConversationTheme
import java.util.Locale

/**
 * Single-activity entry point.
 *
 * Navigation graph currently has one destination — the conversation screen.
 * Extend by adding more composable() destinations to the [NavHost].
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Draw behind system bars for edge-to-edge look
        enableEdgeToEdge()

        val repository = UserPreferencesRepository(this)

        setContent {
            val language by repository.languageFlow.collectAsStateWithLifecycle(initialValue = "en")
            
            val context = LocalContext.current
            val localeContext = remember(language) {
                val locale = Locale(language)
                Locale.setDefault(locale)
                val config = Configuration(context.resources.configuration)
                config.setLocale(locale)
                config.setLayoutDirection(locale)
                context.createConfigurationContext(config)
            }

            CompositionLocalProvider(
                LocalContext provides localeContext,
                LocalConfiguration provides localeContext.resources.configuration,
                LocalActivityResultRegistryOwner provides this@MainActivity,
                LocalLifecycleOwner provides this@MainActivity,
                LocalViewModelStoreOwner provides this@MainActivity
            ) {
                AIConversationTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val navController = rememberNavController()

                        NavHost(
                            navController = navController,
                            startDestination = NavRoutes.CONVERSATION,
                            enterTransition = { slideInHorizontally(animationSpec = tween(400)) { it } },
                            exitTransition = { slideOutHorizontally(animationSpec = tween(400)) { -it } },
                            popEnterTransition = { slideInHorizontally(animationSpec = tween(400)) { -it } },
                            popExitTransition = { slideOutHorizontally(animationSpec = tween(400)) { it } }
                        ) {
                            composable(route = NavRoutes.CONVERSATION) {
                                ConversationScreen(
                                    onNavigateToSettings = {
                                        navController.navigate(NavRoutes.SETTINGS)
                                    }
                                )
                            }
                            composable(route = NavRoutes.SETTINGS) {
                                SettingsScreen(
                                    onNavigateBack = {
                                        navController.popBackStack()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Centralised navigation route constants. */
object NavRoutes {
    const val CONVERSATION = "conversation"
    const val SETTINGS = "settings"
}