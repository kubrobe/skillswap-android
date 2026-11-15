package uz.skillswap.app.ui.call

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

/**
 * Draws one WebRTC video track. The renderer is created once per place on screen and released when it leaves
 * the screen; the track is attached and detached around it, so no frames go to a released view.
 *
 * [overlay] = drawn ON TOP of another video (my small picture over the other person's big one).
 * [cornerRadius] clips the corners where the phone supports clipping a video surface (otherwise square corners).
 */
@Composable
fun VideoSurface(
    track: VideoTrack?,
    egl: EglBase.Context,
    modifier: Modifier = Modifier,
    mirror: Boolean = false,
    overlay: Boolean = false,
    fill: Boolean = true,
    cornerRadius: Dp = 0.dp
) {
    val context = LocalContext.current
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val view = remember(egl) {
        SurfaceViewRenderer(context).apply {
            init(egl, null)
            setEnableHardwareScaler(true)
            if (overlay) setZOrderMediaOverlay(true)
            if (radiusPx > 0f) {
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(v: View, outline: Outline) = outline.setRoundRect(0, 0, v.width, v.height, radiusPx)
                }
                clipToOutline = true
            }
        }
    }
    DisposableEffect(view) {
        onDispose { view.release() }
    }
    DisposableEffect(view, track) {
        track?.let { runCatching { it.addSink(view) } }
        onDispose {
            // the track may already be disposed by the call (it ended first): nothing left to detach then
            track?.let { runCatching { it.removeSink(view) } }
            view.clearImage()
        }
    }
    AndroidView(
        factory = { view },
        modifier = modifier,
        update = {
            it.setMirror(mirror)
            it.setScalingType(if (fill) RendererCommon.ScalingType.SCALE_ASPECT_FILL else RendererCommon.ScalingType.SCALE_ASPECT_FIT)
        }
    )
}
