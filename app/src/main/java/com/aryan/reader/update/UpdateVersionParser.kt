package com.aryan.reader.update

object UpdateVersionParser {
    private val TAG_PATTERN = Regex("personal-b(\\d+)")

    fun buildNumberFromTag(tag: String): Int? =
        TAG_PATTERN.find(tag)?.groupValues?.get(1)?.toIntOrNull()

    fun isNewer(remoteBuildNumber: Int, localBuildNumber: Int): Boolean =
        remoteBuildNumber > localBuildNumber
}
