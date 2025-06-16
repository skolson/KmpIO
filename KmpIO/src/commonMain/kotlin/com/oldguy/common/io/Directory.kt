package com.oldguy.common.io

/**
 * Pure Kotlin (not expect/actual platform-specific logic) methods used for traversing an existing
 * directory tree.
 */
class Directory(dirPath: String)
{
    constructor(dir: File) : this(dir.fullPath) {

    }

    val directory = File(dirPath).apply {
        check(this)
    }
    /**
     * Recursively traverses the directory tree starting from the current directory and collects all files into a list.
     *
     * @return A list of File objects representing all files within the directory tree.
     */
    suspend fun directoryTree(): List<File> {
        return listTree(directory, emptyList<File>().toMutableList())
    }

    private suspend fun listTree(dir: File, list: MutableList<File>): MutableList<File>
    {
        val content = dir.directoryList().map { dir.resolve(it, false) }
        list.addAll(content)
        content.filter { it.isDirectory }.forEach {
            listTree(it, list)
        }
        return list
    }

    /**
     * Traverses the directory tree starting at the current directory and applies the specified action to each file.
     *
     * @param action A suspendable function that takes a File as a parameter.
     *               This function is applied to each file in the directory tree.
     *               If the action returns `true`, processing may continue. If it returns `false`,
     *               function terminates.
     */
    suspend fun walkTree(action: (suspend ((file: File) -> Boolean))) {
        listTree(directory, emptyList<File>().toMutableList()).forEach {
            if (!action(it)) return
        }
    }

    companion object {
        private fun check(dir: File) {
            dir.apply {
                if (!exists)
                    throw IllegalStateException("Temp directory $fullPath does not exist")
                if (!isDirectory)
                    throw IllegalStateException("Temp directory $fullPath is not a directory")
            }
        }

        fun tempPath(): String = File.tempDirectoryPath()
        fun tempDirectory(): Directory {
            return Directory(tempPath()).apply {
                check(directory)
            }
        }
    }
}