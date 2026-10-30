package tv.jetflix.phone

import android.app.Application

class DriftfinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashNote.install(this)
        Graph.init(this)
    }
}

/** Keeps the last crash on this phone so the next launch can show it. Nothing leaves the phone. */
object CrashNote {
    private fun file(c: android.content.Context) = java.io.File(c.filesDir, "last_crash.txt")

    fun install(c: android.content.Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { file(c).writeText(sanitized(e)) }
            previous?.uncaughtException(t, e)
        }
    }

    /** Class names and app code locations only. Exception messages can hold addresses or tokens, so they are dropped. */
    internal fun sanitized(e: Throwable): String {
        val out = StringBuilder()
        var t: Throwable? = e; var depth = 0
        while (t != null && depth < 3) {
            out.append(t.javaClass.name).append('\n')
            t.stackTrace.filter { it.className.startsWith("tv.jetflix") }.take(4).forEach { out.append("  at ${it.className}.${it.methodName}:${it.lineNumber}\n") }
            t = t.cause; depth++
        }
        return out.toString().take(600)
    }

    fun read(c: android.content.Context): String? = file(c).takeIf { it.exists() }?.readText()
    fun clear(c: android.content.Context) { file(c).delete() }
}
