package com.example.almanac

import com.example.almanac.data.notion.NotionDatabaseUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotionDatabaseUrlTest {

    @Test
    fun extracts_id_from_share_url_with_view() {
        val url = "https://www.notion.so/myws/Physical-abcdef0123456789abcdef0123456789?v=ffeeddccbbaa00112233445566778899"
        assertEquals(
            "abcdef01-2345-6789-abcd-ef0123456789",
            NotionDatabaseUrl.extractDatabaseId(url),
        )
    }

    @Test
    fun extracts_id_from_share_url_without_view() {
        val url = "https://www.notion.so/Physical-abcdef0123456789abcdef0123456789"
        assertEquals(
            "abcdef01-2345-6789-abcd-ef0123456789",
            NotionDatabaseUrl.extractDatabaseId(url),
        )
    }

    @Test
    fun accepts_already_dashed_uuid() {
        val input = "ABCDEF01-2345-6789-ABCD-EF0123456789"
        assertEquals(
            "abcdef01-2345-6789-abcd-ef0123456789",
            NotionDatabaseUrl.extractDatabaseId(input),
        )
    }

    @Test
    fun rejects_garbage() {
        assertNull(NotionDatabaseUrl.extractDatabaseId(""))
        assertNull(NotionDatabaseUrl.extractDatabaseId("not a url"))
        assertNull(NotionDatabaseUrl.extractDatabaseId("https://example.com/short-id"))
    }
}
