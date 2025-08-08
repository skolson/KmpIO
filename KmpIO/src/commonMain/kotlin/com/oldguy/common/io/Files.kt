package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.Utf8
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone

/**
 * Centralize TimeZone logic. Had an undiagnosed issue with kotlinx.datetime getting native aborts
 * during default time zone lookups. So this initial basic version just looks up a default TimeZone
 * in a safe native manner. If it can't find one that kotlinx supports, it uses UTC.
 */
expect class TimeZones {
    val defaultId: String
    val kotlinxTz: TimeZone

    fun localFromEpochMilliseconds(epochMilliseconds: Long): LocalDateTime

    companion object {
        val default: TimeZone
    }
}

class IOException(message: String, cause: Throwable? = null): Exception(message, cause)

/**
 * Some platform-specific file descriptors are not easily encoded to a string.  Platform specific
 * implementations of File can type-cast these based on the matching code, which must be unique.
 *
 * code = 0 is a normal file path - not platform-specific object
 *
 * Example - Android-specific URIs for Google Drive - Uri.toString does not retain all the information
 * from the android content provider.
 *
 * actual class implementations are responsible for casting the descriptor back to the appropriate
 * type for the code specified
 */
data class FileDescriptor(val code: Int = 0, val descriptor: Any)

/**
 * Represents a file ID. The filePath can be file syntax or URI or something else - actual platform
 * implementation will parse accordingly. Does assume file name at end of string may have an extension
 * of form "<path><nameWithoutExtension>.<extension>" = fullPath.
 * Note that a File object is immutable. The properties are set from the file system only at constructor time.
 * @param filePath string value identifying a file
 * @param platformFd platform-specific file descriptor. For example, android content provider URIs
 */
expect class File(filePath: String, platformFd: FileDescriptor? = null) {
    constructor(parentDirectory: String, name: String)
    constructor(parentDirectory: File, name: String)
    constructor(fd: FileDescriptor)

    /**
     * Name of the file or directory, without the owning path
     */
    val name: String
    /**
     * Name of the file or directory, without any trailing extension
     */
    val nameWithoutExtension: String
    /**
     * If there is aName of the file or directory, without any trailing extension
     */
    val extension: String
    val path: String
    val fullPath: String
    val directoryPath: String
    val isParent: Boolean
    val isDirectory: Boolean
    val exists: Boolean
    val platformFd: FileDescriptor?
    val isUri: Boolean
    val isUriString: Boolean
    val size: ULong
    val lastModifiedEpoch: Long
    val lastModified: LocalDateTime?
    val createdTime: LocalDateTime?
    val lastAccessTime: LocalDateTime?

    suspend fun delete(): Boolean
    suspend fun copy(destinationPath: String): File
    suspend fun makeDirectory(): File
    suspend fun resolve(directoryName: String, make: Boolean = true): File
    suspend fun directoryList(): List<String>
    suspend fun directoryFiles(): List<File>

    fun up(): File

    /**
     * Make a new File object with updated attributes from the same fullPath as this File instance
     */
    fun newFile(): File

    companion object {
        val pathSeparator: Char
        fun tempDirectoryPath(): String
        fun tempDirectoryFile(): File
        fun workingDirectory(): File
        val defaultTimeZone: TimeZones
    }
}

enum class FileSource {
    Asset, Classpath, File
}

expect interface Closeable {
    suspend fun close()
}

expect suspend fun <T : Closeable?, R> T.use(body: suspend (T) -> R): R

enum class FileMode {
    Read, Write
}