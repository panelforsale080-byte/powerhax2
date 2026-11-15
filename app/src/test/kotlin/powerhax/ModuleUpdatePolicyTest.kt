package com.accel.loader

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleUpdatePolicyTest {

    @Test
    fun missingFileNeedsDownload() {
        assertEquals(
            ModuleUpdatePolicy.Decision.MISSING,
            ModuleUpdatePolicy.decide(false, 0L, "", "", "abc", 100L)
        )
    }

    @Test
    fun matchingSavedShaKeepsLocalFile() {
        assertEquals(
            ModuleUpdatePolicy.Decision.KEEP,
            ModuleUpdatePolicy.decide(true, 80L, "", "abc", "abc", 100L)
        )
    }

    @Test
    fun differentSavedShaDropsLocalFile() {
        assertEquals(
            ModuleUpdatePolicy.Decision.DROP_AND_PROMPT,
            ModuleUpdatePolicy.decide(true, 100L, "same", "old", "new", 100L)
        )
    }

    @Test
    fun differentFileHashDropsEvenWhenSizeMatches() {
        assertEquals(
            ModuleUpdatePolicy.Decision.DROP_AND_PROMPT,
            ModuleUpdatePolicy.decide(true, 100L, "local", "", "remote", 100L)
        )
    }

    @Test
    fun matchingFileHashKeepsLocalFile() {
        assertEquals(
            ModuleUpdatePolicy.Decision.KEEP,
            ModuleUpdatePolicy.decide(true, 4L, "abc", "", "abc", 9L)
        )
    }

    @Test
    fun sizeChangeWithoutShaDropsLocalFile() {
        assertEquals(
            ModuleUpdatePolicy.Decision.DROP_AND_PROMPT,
            ModuleUpdatePolicy.decide(true, 50L, "", "", "", 80L)
        )
    }

    @Test
    fun offlineCheckKeepsLocalFile() {
        assertEquals(
            ModuleUpdatePolicy.Decision.KEEP,
            ModuleUpdatePolicy.decide(true, 80L, "", "abc", "", 0L)
        )
    }

    @Test
    fun gitBlobShaMatchesEmptyFile() {
        val file = kotlin.io.path.createTempFile("mod", ".so").toFile()
        file.writeBytes(ByteArray(0))
        try {
            assertEquals(
                "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391",
                ModuleUpdatePolicy.gitBlobSha1(file)
            )
        } finally {
            file.delete()
        }
    }
}
