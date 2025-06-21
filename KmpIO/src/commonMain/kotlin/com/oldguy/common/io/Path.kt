package com.oldguy.common.io

/**
 * Parse a file path into its various components.
 */
open class Path(filePath: String, val pathSeparator: Char = '/') {
    /*
        True if path starts with a path separator character or a letter and a colon (windows),
        otherwise false
     */
    val isAbsolute = filePath.startsWith(pathSeparator) ||
            (filePath.length > 2 && filePath[0].isLetter() && filePath[1] == ':')
    /*
        True if name starts with a period, else false
     */
    val isHidden: Boolean
    /*
        Name of file or directory without the path that owns it
     */
    val name: String
    /*
        If the name has a period in it, then this contains the string after the last period. Otherwise
        it is empty
     */
    val extension: String
    /*
        Contains the value of name, without the trailing period and extension, if there is one
     */
    val nameWithoutExtension: String
    /*
        Contains the path portion, or owning directory path, of the file or directory
     */
    @Deprecated("Use directoryPath for the owner, or fullPath instead")
    val path: String
    /*
        The full path to the file or directory.
     */
    val fullPath: String
    /*
        The full path to the parent directory.
     */
    val directoryPath: String
    val isUri = false
    val isUriString = false

    init {
        if (filePath.isBlank() || filePath.isEmpty())
            throw IllegalArgumentException("Path value cannot be blank")
        val index = filePath.indexOfLast { it == pathSeparator }
        if (index < 0) {
            name = filePath
            path = filePath
        } else {
            name = filePath.substring(index + 1)
            path = filePath.take(index).trimEnd(pathSeparator)
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
            directoryPath = up()
        }
    }

    /**
     * Returns the parent directory of the current path.
     * If the current path does not have a parent, returns the path itself.
     * If the current path is a file, then the file's parent directory is returned.
     *
     * @return A new `Path` instance representing the parent directory, or empty string if no parent directory.
     */
    fun up(): String {
        val index = fullPath.lastIndexOf(pathSeparator)
        return if (index > 1) {
            fullPath.substring(0, index)
        } else
            ""
    }

    /**
     * Computes the relative path from the specified base path to this path.
     * If this path does not start with the base path, the method returns this path.
     *
     * @param path The base path to compute the relative path against.
     * @return A new `Path` instance representing the relative path, or this path if it does not start with the base path.
     */
    fun relativeTo(path: Path): Path {
        return if (!fullPath.startsWith(path.fullPath))
            this
        else
            Path(fullPath.substring(path.fullPath.length))
    }

    companion object {
        fun newPath(parentPath: String, name: String, pathSeparator: Char): String
            = if (parentPath.endsWith(pathSeparator))
                "$parentPath$name"
            else
                "$parentPath$pathSeparator$name"

    }
}