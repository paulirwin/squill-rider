package io.github.paulirwin.squill.rider

import com.intellij.openapi.vfs.VirtualFile

/** Reads a VirtualFile's text as a String using its charset. */
internal fun VirtualFile.readTextViaCharset(): String =
    String(contentsToByteArray(), charset)
