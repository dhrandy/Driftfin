package tv.jetflix.phone

/** Test-only stall evidence. Armed in @Before, it sleeps; when a test is still running after [afterMs] it prints, once,
 *  what the main thread is executing and which messages sit in the main looper queue, then ends. A test that finishes
 *  sooner interrupts it with [disarm], so green runs print nothing. It changes no wait, timeout or assertion. */
class IdleStallDump(private val label: String, private val afterMs: Long = 25_000L) {
    private var thread: Thread? = null

    fun arm() {
        val t = Thread {
            try { Thread.sleep(afterMs) } catch (e: InterruptedException) { return@Thread }
            System.err.println(report())
        }
        t.isDaemon = true
        t.name = "idle-stall-dump"
        t.start()
        thread = t
    }

    fun disarm() { thread?.interrupt(); thread = null }

    private fun report(): String {
        val sb = StringBuilder("IDLE-STALL-DUMP $label after ${afterMs}ms\n")
        val main = android.os.Looper.getMainLooper()
        try {
            sb.append("main thread ${main.thread.name} state=${main.thread.state}\n")
            main.thread.stackTrace.take(70).forEach { sb.append("  at ").append(it).append('\n') }
        } catch (e: Throwable) { sb.append("stack failed: $e\n") }
        try {
            sb.append("main looper queue:\n")
            main.dump(android.util.Printer { sb.append("  ").append(it).append('\n') }, "")
        } catch (e: Throwable) { sb.append("looper dump failed: $e\n") }
        try {
            sb.append("other threads (name state top frames):\n")
            Thread.getAllStackTraces().forEach { (th, st) ->
                if (th !== main.thread && th.name != "idle-stall-dump") {
                    sb.append("  ${th.name} ${th.state} ").append(st.take(3).joinToString(" < ")).append('\n')
                }
            }
        } catch (e: Throwable) { sb.append("threads failed: $e\n") }
        return sb.toString()
    }
}
