package com.accel.loader

import java.io.File
import java.security.MessageDigest

/**
 * A module already on disk is kept only when it still matches the server copy.
 * A different file is dropped so the old bytes are not reused.
 */
object ModuleUpdatePolicy {

    enum class Decision {
        KEEP,
        DROP_AND_PROMPT,
        MISSING
    }

    fun decide(
        localExists: Boolean,
        localSize: Long,
        localSha: String,
        savedSha: String,
        remoteSha: String,
        remoteSize: Long
    ): Decision {
        if (!localExists || localSize <= 0L) return Decision.MISSING
        if (remoteSha.isNotBlank() && savedSha.isNotBlank()) {
            return if (remoteSha.equals(savedSha, ignoreCase = true)) Decision.KEEP else Decision.DROP_AND_PROMPT
        }
        if (remoteSha.isNotBlank() && localSha.isNotBlank()) {
            return if (remoteSha.equals(localSha, ignoreCase = true)) Decision.KEEP else Decision.DROP_AND_PROMPT
        }
        if (remoteSha.isBlank() && remoteSize <= 0L) return Decision.KEEP
        if (remoteSize > 0L && localSize != remoteSize) return Decision.DROP_AND_PROMPT
        return Decision.KEEP
    }

    /** Git blob id, same value GitHub returns as `sha` for a file. */
    fun gitBlobSha1(file: File): String {
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${file.length()}\u0000".toByteArray(Charsets.US_ASCII))
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
