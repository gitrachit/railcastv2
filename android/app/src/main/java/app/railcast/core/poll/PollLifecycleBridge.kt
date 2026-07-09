package app.railcast.core.poll

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * Drives the PollController from a Lifecycle: polling runs only between
 * ON_START and ON_STOP, so it never fires while the app is backgrounded
 * (background = push only, NFR-3).
 */
class PollLifecycleBridge(private val controller: PollController) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = controller.onForeground()
    override fun onStop(owner: LifecycleOwner) = controller.onBackground()
}

/** Attach the controller to a lifecycle; polling follows start/stop. */
fun PollController.bindTo(lifecycle: Lifecycle) {
    lifecycle.addObserver(PollLifecycleBridge(this))
}
