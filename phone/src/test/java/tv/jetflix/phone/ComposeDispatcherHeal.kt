package tv.jetflix.phone

import android.os.Looper
import androidx.compose.ui.platform.AndroidUiDispatcher
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import kotlin.coroutines.ContinuationInterceptor

/**
 * Test harness only. compose-ui 1.7.6 keeps AndroidUiDispatcher.Main as a process-wide lazy singleton. Its dispatch() sets
 * scheduledTrampolineDispatch / scheduledFrameDispatch and posts a callback to the main Handler and the Choreographer; the
 * flags are cleared only when that callback runs. Robolectric resets the main looper between tests, which can drop the
 * posted callback while the flags stay true. A flag set before a test shows earlier work left it that way, not by itself that the callback was dropped. Later tests in the same sandbox then never get their coroutines resumed on
 * that dispatcher, including GlobalSnapshotManager's apply notifier, and Compose never goes idle (robolectric/robolectric
 * issue 7055 describes this mechanism and a reflection workaround). This rule drains the trampoline once before each test (main thread only).
 *
 * It reads private fields of AndroidUiDispatcher by reflection, so it is pinned to the compose-ui version in the BOM
 * (field names checked against the 1.7.6 sources). If a field is missing it does nothing and prints one line. It prints a
 * line only when it actually finds the flags set, which also confirms the mechanism. No assertion, wait or timeout changes.
 * Place it OUTSIDE the compose rule: RuleChain.outerRule(ComposeDispatcherHeal()).around(compose)...
 */
class ComposeDispatcherHeal : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            heal(description)
            base.evaluate()
        }
    }

    private fun heal(description: Description) {
        try {
            val dispatcher = AndroidUiDispatcher.Main[ContinuationInterceptor] as? AndroidUiDispatcher ?: return
            val cls = AndroidUiDispatcher::class.java
            fun flag(name: String): Boolean = cls.getDeclaredField(name).apply { isAccessible = true }.getBoolean(dispatcher)
            val trampoline = flag("scheduledTrampolineDispatch")
            val frame = flag("scheduledFrameDispatch")
            if (trampoline) {
                // Only on the main looper thread, which is where the dispatcher's tasks must run; otherwise do nothing.
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    println("COMPOSE-HEAL skipped: test thread is not the main looper thread")
                    return
                }
                // run() is the Runnable half of dispatchCallback: it drains the trampolined tasks only. It never runs
                // queued frame callbacks, so no carried awaiter sees a real-clock frame time next to the test's virtual clock.
                (cls.getDeclaredField("dispatchCallback").apply { isAccessible = true }.get(dispatcher) as Runnable).run()
                println("COMPOSE-HEAL ${description.className}.${description.methodName}: trampoline flag was set before the test (frame=$frame) -> after (trampoline=${flag("scheduledTrampolineDispatch")} frame=${flag("scheduledFrameDispatch")})")
            } else if (frame) {
                println("COMPOSE-HEAL ${description.className}.${description.methodName}: frame flag set, trampoline clear; left alone")
            }
        } catch (t: Throwable) {
            println("COMPOSE-HEAL skipped: ${t.javaClass.simpleName}")
        }
    }
}
