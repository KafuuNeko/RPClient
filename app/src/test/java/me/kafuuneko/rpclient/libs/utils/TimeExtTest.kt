package me.kafuuneko.rpclient.libs.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class TimeExtTest {
    @Test
    fun defaultChatTitleUsesCompactCreationTime() {
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            set(2026, Calendar.JUNE, 10, 10, 19, 0)
            set(Calendar.MILLISECOND, 0)
        }

        assertEquals("20260610-1019", calendar.timeInMillis.toDefaultChatTitle())
    }
}
