package com.example.almanac.data.notion

object NotionDatabaseUrl {

    private val HEX_32 = Regex("[0-9a-fA-F]{32}")

    fun extractDatabaseId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        val dashedUuid = Regex(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
        ).find(trimmed)?.value
        if (dashedUuid != null) return dashedUuid.lowercase()

        val raw = HEX_32.find(trimmed)?.value ?: return null
        return formatAsUuid(raw.lowercase())
    }

    private fun formatAsUuid(hex32: String): String =
        "${hex32.substring(0, 8)}-${hex32.substring(8, 12)}-${hex32.substring(12, 16)}-" +
            "${hex32.substring(16, 20)}-${hex32.substring(20, 32)}"
}
