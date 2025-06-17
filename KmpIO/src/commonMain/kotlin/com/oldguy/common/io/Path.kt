package com.oldguy.common.io

/**
 * Parse a file path into its various components.
 */
open class Path(filePath: String, val pathSeparator: Char = '/') {
    val isAbsolute = filePath.startsWith(pathSeparator)
    val isHidden: Boolean
    val name: String
    val nameWithoutExtension: String
    val extension: String
    val path: String
    val fullPath: String
    val directoryPath: String
    val isUri = false
    val isUriString = false

    init {
        if (filePath.isBlank() || filePath.isEmpty())
            throw IllegalArgumentException("Path value cannot be blank")
        val index = filePath.indexOfLast { it == File.Companion.pathSeparator[0] }
        if (index < 0) {
            name = filePath
            path = filePath
        } else {
            name = filePath.substring(index + 1)
            path = filePath.take(index).trimEnd(File.Companion.pathSeparator[0])
        }
        fullPath = filePath
        if (name == "." || name == "..") {
            isHidden = false
            extension = ""
            nameWithoutExtension = ""
            directoryPath = name
        } else {
            isHidden = name.startsWith('.')
            val extIndex = name.indexOfLast { it == '.' }
            extension = if ((extIndex <= 0) || (extIndex == name.length - 1)) "" else name.substring(extIndex + 1)
            nameWithoutExtension = if (extIndex < 0) name else name.substring(0, extIndex)
            directoryPath = if (name.isNotEmpty())
                path.replace(name, "").trimEnd(File.Companion.pathSeparator[0])
            else
                fullPath
        }
    }

    /**
     * Returns the parent directory of the current path.
     * If the current path does not have a parent, returns the path itself.
     * If the current path is a file, then the file's parent directory is returned.
     *
     * @return A new `Path` instance representing the parent directory, or the current path if no parent exists.
     */
    fun up(): Path {
        val index = fullPath.lastIndexOf(pathSeparator)
        return if (index > 1) {
            Path(fullPath.substring(0, index))
        } else
            this
    }
}