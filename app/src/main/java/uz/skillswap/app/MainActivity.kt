package uz.skillswap.app

import uz.skillswap.app.ui.i18n.LocalStrings
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import uz.skillswap.app.ui.AppNavHost
import uz.skillswap.app.ui.SkillSwapViewModel
import uz.skillswap.app.ui.theme.SkillSwapTheme

class MainActivity : ComponentActivity() {
    private val vm: SkillSwapViewModel by viewModels { SkillSwapViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Our headers are dark graphite, so the status bar icons must be light
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (intent?.getBooleanExtra(EXTRA_OPEN_CALL, false) == true) vm.requestOpenCall()
        setContent {
            SkillSwapTheme {
                // the chosen language reaches every screen; changing it redraws the app at once
                val lang by vm.lang.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalStrings provides lang.strings) {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        AppNavHost(vm)
                    }
                }
            }
        }
    }

    /** The ongoing video lesson notification was tapped while the app was open: show the call. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_CALL, false)) vm.requestOpenCall()
    }

    // A video lesson goes on in the background with sound only: the camera stops when the app is not visible.
    override fun onStart() {
        super.onStart()
        (application as SkillSwapApp).videoRooms.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) (application as SkillSwapApp).videoRooms.onAppBackground()
    }

    // Back from another app: get fresh data if it is more than a minute old
    override fun onResume() {
        super.onResume()
        vm.onAppResumed()
    }

    override fun onPause() {
        super.onPause()
        vm.onAppPaused()
    }

    companion object {
        /** Set by the ongoing call notification: open the video lesson screen. */
        const val EXTRA_OPEN_CALL = "open_call"
    }
}
