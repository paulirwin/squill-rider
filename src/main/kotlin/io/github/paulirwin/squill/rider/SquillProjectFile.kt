package io.github.paulirwin.squill.rider

import com.intellij.openapi.vfs.VirtualFile

/** Reads a VirtualFile's text as a String using its charset. */
internal fun VirtualFile.readTextViaCharset(): String =
    String(contentsToByteArray(), charset)

/**
 * Pure parsing helpers for `.squillproj` project files. Kept free of IntelliJ VFS/PSI types so the
 * logic can be unit-tested against raw XML strings.
 */
object SquillProjectFile {

    const val EXTENSION = ".squillproj"

    private val PROVIDER_NAME_REGEX =
        Regex("<SquillProviderName>\\s*([^<]*?)\\s*</SquillProviderName>", RegexOption.IGNORE_CASE)

    /**
     * Extracts the trimmed text of the first `<SquillProviderName>` element from `.squillproj`
     * content, or null if the element is absent or empty. Uses a tolerant regex rather than a full
     * XML parse: the property is a simple leaf element and this avoids pulling in an XML parser and
     * having to reason about MSBuild's `Condition`/import structure.
     */
    fun readProviderName(content: String): String? =
        PROVIDER_NAME_REGEX.find(content)
            ?.groupValues?.get(1)
            ?.takeIf { it.isNotBlank() }

    /** Resolves the [SquillProvider] declared by `.squillproj` content, defaulting when absent. */
    fun resolveProvider(content: String): SquillProvider =
        SquillProvider.fromProviderName(readProviderName(content)) ?: SquillProvider.DEFAULT
}
