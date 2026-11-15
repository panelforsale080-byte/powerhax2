package com.accel.loader

import android.util.Log
import java.io.File

object ProcessFinder {

    private const val TAG = "AstralProcessFinder"

    data class Target(val pid: Int, val label: String) {
        val isUnityKills: Boolean
            get() = label.contains("UnityKills", ignoreCase = true)
    }

    /** CODM game process. Prefer the main package over :push / :sandboxed helpers. */
    fun findCodm(): Target? {
        var best: Target? = null
        var bestScore = Int.MIN_VALUE
        scanProc { pid, cmdline ->
            if (!isCodm(cmdline)) return@scanProc
            val score = codmScore(cmdline)
            if (score > bestScore) {
                bestScore = score
                best = Target(pid, cmdline)
            }
        }
        best?.let { Log.i(TAG, "CODM target pid=${it.pid} cmdline=${it.label}") }
        return best
    }

    private fun isCodm(cmdline: String): Boolean {
        return cmdline.contains("com.garena.game.codm", ignoreCase = true)
    }

    private fun codmScore(cmdline: String): Int {
        var s = 10
        if (cmdline.substringBefore('\u0000').trim() == "com.garena.game.codm") s += 100
        if (!cmdline.contains(':')) s += 40
        if (cmdline.contains(":push", true) || cmdline.contains(":google", true) ||
            cmdline.contains(":crash", true) || cmdline.contains(":sandboxed", true)
        ) {
            s -= 50
        }
        return s
    }

    fun inVirtualSpace(): Boolean {
        return try {
            File("/proc/self/maps").useLines { lines ->
                lines.any {
                    it.contains("com.gspace.android/virtual", ignoreCase = true) ||
                        it.contains("/virtual/data/user/0/", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun scanProc(block: (pid: Int, cmdline: String) -> Unit) {
        val proc = File("/proc")
        val dirs = proc.listFiles() ?: return
        for (d in dirs) {
            if (!d.isDirectory) continue
            val name = d.name
            if (name.isEmpty() || !name.all { it.isDigit() }) continue
            try {
                val cmdBytes = File(d, "cmdline").readBytes()
                val cmdline = String(cmdBytes, Charsets.UTF_8).trim('\u0000')
                block(name.toInt(), cmdline)
            } catch (e: Throwable) {
                Log.d(TAG, "Skip pid $name: ${e.message}")
            }
        }
    }

}
