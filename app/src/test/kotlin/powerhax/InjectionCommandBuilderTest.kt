package com.accel.loader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class InjectionCommandBuilderTest {

    @Test
    fun buildsAnInjectionCommandFromSelectedLibrary() {
        val library = SelectedLibrary(
            name = "Accel",
            architecture = "64bit",
            jniKey = "test-key",
            gamePackage = "com.example.game"
        )

        val command = InjectionCommandBuilder.build(
            cacheDirectory = File("cache"),
            library = library,
            libraryFileName = "libTool.so",
            injectionMethod = "1",
            hasRootAccess = false,
            injectTarget = "28451"
        )

        assertTrue(command.contains("28451"))
        assertTrue(command.contains("libTool.so"))
        assertTrue(command.contains("test-key"))
    }

    @Test
    fun gspacePrefersUnityCmdlineBeforePid() {
        val unity = ProcessFinder.Target(29664, "com.garena.game.codm:UnityKillsMe")
        val attempts = InjectionCommandBuilder.injectTargetAttempts(
            unity = unity,
            gamePackage = "com.garena.game.codm",
            inGspace = true,
            preferredTarget = null
        )
        assertEquals("com.garena.game.codm:UnityKillsMe", attempts.first())
        assertTrue(attempts.indexOf("29664") > attempts.indexOf("com.garena.game.codm:UnityKillsMe"))
    }
}
