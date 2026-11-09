package uz.skillswap.app

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
        // Our headers are dark violet, so the status bar icons must be light
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            SkillSwapTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavHost(vm)
                }
            }
        }
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
}
