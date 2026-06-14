package com.previNet.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.previNet.android.ui.consent.ConsentSheet
import com.previNet.android.ui.result.ResultScreen
import com.previNet.android.ui.resulturl.ResultUrlScreen
import com.previNet.android.ui.submissions.MySubmissionsScreen
import com.previNet.android.ui.submit.SubmitScreen
import com.previNet.android.ui.theme.PreViNetTheme
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PreViNetTheme {
                val controller = rememberNavController()
                navController = controller
                PreViNetRoot(controller)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        navController?.handleDeepLink(intent)
    }

    override fun onStart() {
        super.onStart()
        appContainer.submissions.appInForeground = true
    }

    override fun onStop() {
        appContainer.submissions.appInForeground = false
        super.onStop()
    }
}

/** Host parsed from the API base; the App Link host must match `resultHost` in the manifest. */
private val resultHost: String = BuildConfig.API_BASE.toUri().host ?: "grapes.toliver.hu"

object Routes {
    const val SUBMIT = "submit"
    const val SAVED = "saved/{localId}"
    const val RESULT = "result/{localId}"
    const val SUBMISSIONS = "submissions"
    const val LINK = "link/{serverId}"

    fun saved(localId: Long) = "saved/$localId"
    fun result(localId: Long) = "result/$localId"
}

@Composable
private fun PreViNetRoot(navController: NavHostController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val container = remember { context.appContainer }
    val scope = rememberCoroutineScope()

    // Refresh the disease list once per process start when we get online.
    val isOnline by container.connectivity.isOnline.collectAsStateWithLifecycle()
    var diseasesRefreshed by remember { mutableStateOf(false) }
    LaunchedEffect(isOnline) {
        if (isOnline && !diseasesRefreshed) {
            diseasesRefreshed = true
            container.diseases.refresh()
        }
    }

    // Consent gate: the sheet overlays whatever screen is active until agreed or dismissed.
    val consentGiven by container.prefs.consentGiven.collectAsStateWithLifecycle(initialValue = null)
    var consentDismissed by remember { mutableStateOf(false) }
    var consentReopened by remember { mutableStateOf(false) }
    val showConsent = (consentGiven == false && !consentDismissed) || (consentGiven == false && consentReopened)

    NavHost(navController = navController, startDestination = Routes.SUBMIT) {
        composable(Routes.SUBMIT) {
            SubmitScreen(
                consentGiven = consentGiven == true,
                onRequireConsent = {
                    consentReopened = true
                    consentDismissed = false
                },
                onNavigateToSaved = { localId ->
                    navController.navigate(Routes.saved(localId))
                },
                onNavigateToHistory = { navController.navigate(Routes.SUBMISSIONS) },
            )
        }

        composable(
            route = Routes.SAVED,
            arguments = listOf(navArgument("localId") { type = NavType.LongType }),
        ) { entry ->
            val localId = entry.arguments?.getLong("localId") ?: return@composable
            ResultUrlScreen(
                localId = localId,
                onOpenResults = {
                    navController.navigate(Routes.result(localId)) {
                        popUpTo(Routes.SUBMIT)
                    }
                },
                onViewSubmissions = {
                    navController.navigate(Routes.SUBMISSIONS) {
                        popUpTo(Routes.SUBMIT)
                    }
                },
                onSubmitAnother = {
                    navController.popBackStack(Routes.SUBMIT, inclusive = false)
                },
            )
        }

        composable(
            route = Routes.RESULT,
            arguments = listOf(navArgument("localId") { type = NavType.LongType }),
        ) { entry ->
            val localId = entry.arguments?.getLong("localId") ?: return@composable
            ResultScreen(
                localId = localId,
                onNewSubmission = {
                    navController.navigate(Routes.SUBMIT) {
                        popUpTo(Routes.SUBMIT) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        composable(Routes.SUBMISSIONS) {
            MySubmissionsScreen(
                onBack = { navController.popBackStack() },
                onOpenSubmission = { localId -> navController.navigate(Routes.result(localId)) },
            )
        }

        composable(
            route = Routes.LINK,
            arguments = listOf(navArgument("serverId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "https://$resultHost/result/{serverId}" }),
        ) { entry ->
            val serverId = entry.arguments?.getString("serverId") ?: return@composable
            LaunchedEffect(serverId) {
                val resultUrl = "https://$resultHost/result/$serverId"
                val localId = container.submissions.resolveDeepLink(serverId, resultUrl)
                navController.navigate(Routes.result(localId)) {
                    popUpTo(Routes.LINK) { inclusive = true }
                }
            }
            Box(modifier = Modifier.fillMaxSize())
        }
    }

    if (showConsent) {
        ConsentSheet(
            onAgree = {
                scope.launch { container.prefs.setConsentGiven(true) }
                consentReopened = false
            },
            onDismissWithoutAgreeing = {
                consentDismissed = true
                consentReopened = false
            },
        )
    }
}
