package android.util

/**
 * Lightweight Android Log stub for running standalone test harness on pure desktop JVM
 * without needing Android SDK jar dependencies.
 */
object Log {
    @JvmStatic fun d(tag: String, msg: String): Int { println("[$tag][DEBUG] $msg"); return 0 }
    @JvmStatic fun i(tag: String, msg: String): Int { println("[$tag][INFO] $msg"); return 0 }
    @JvmStatic fun w(tag: String, msg: String): Int { println("[$tag][WARN] $msg"); return 0 }
    @JvmStatic fun e(tag: String, msg: String, t: Throwable? = null): Int { println("[$tag][ERROR] $msg ${t?.message ?: ""}"); return 0 }
}
