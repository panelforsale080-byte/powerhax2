package com.accel.loader

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuKeyListTest {

    private val list = """
        key1 CODM-129293 2026-12-30
        key2 CODM-917339 2026/12/30

        key3 CODM-000111 2026-01-01
    """.trimIndent()

    @Test
    fun readsEveryKeyLine() {
        val entries = MenuKeyList.parse(list)
        assertEquals(3, entries.size)
        assertEquals("key1", entries[0].name)
        assertEquals("CODM-129293", entries[0].key)
        assertEquals(LocalDate.of(2026, 12, 30), entries[0].until)
        assertEquals(LocalDate.of(2026, 12, 30), entries[1].until)
    }

    @Test
    fun validKeyShowsItsDate() {
        val result = MenuKeyList.check(list, "CODM-129293", LocalDate.of(2026, 11, 16))
        assertEquals(MenuKeyList.Status.VALID, result.status)
        assertEquals("key1 valid until 2026-12-30", MenuKeyList.label(result))
    }

    @Test
    fun pastedLineStillMatchesTheKey() {
        val result = MenuKeyList.check(list, "key2 CODM-917339 2026/12/30", LocalDate.of(2026, 11, 16))
        assertEquals(MenuKeyList.Status.VALID, result.status)
        assertEquals("key2", result.name)
    }

    @Test
    fun expiredAndUnknownKeys() {
        val expired = MenuKeyList.check(list, "codm-000111", LocalDate.of(2026, 11, 16))
        assertEquals(MenuKeyList.Status.EXPIRED, expired.status)
        assertEquals(MenuKeyList.Status.INVALID, MenuKeyList.check(list, "CODM-999999", LocalDate.of(2026, 11, 16)).status)
        assertEquals(MenuKeyList.Status.EMPTY, MenuKeyList.check(list, "  ", LocalDate.of(2026, 11, 16)).status)
    }

    @Test
    fun addressDecodesToTheKeyList() {
        assertEquals(
            "https://raw.githubusercontent.com/Axcelpogi190/PasswordAndLicense/main/CodmPass",
            MenuKeyClient.listAddress()
        )
    }
}
