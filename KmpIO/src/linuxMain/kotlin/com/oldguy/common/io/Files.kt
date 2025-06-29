package com.oldguy.common.io

import kotlinx.cinterop.*
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.posix.*
import kotlin.*
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
actual class TimeZones {
    actual val defaultId: String = TimeZone.currentSystemDefault().id
    actual val kotlinxTz: TimeZone = if (TimeZone.availableZoneIds.contains(defaultId))
        TimeZone.of(defaultId)
    else {
        TimeZone.UTC
    }

    actual fun localFromEpochMilliseconds(epochMilliseconds: Long): LocalDateTime {
        return Instant
            .fromEpochMilliseconds(epochMilliseconds)
            .toLocalDateTime(kotlinxTz)
    }

    actual companion object {
        actual val default: TimeZone = TimeZones().kotlinxTz
    }
}

/**
 * Represents a file or directory on the filesystem, providing various utility methods and properties
 * to interact with file paths, directories, and file metadata.
 *
 * This class is platform-specific and contains functionality for file manipulation,
 * directory creation, file copying, and retrieving file attributes such as permissions and timestamps.
 * It is a combination of platform-independent methods and Linux-specific features such
 * as POSIX file permissions.
 *
 * Note: Instances are immutable, with one exception - the newPermissions property,  So properties are
 * all set at constructor time and do not change as a result of using methods. See the newFile() function
 * as a convenience for getting a new current File instance using the same full path to get new state when desired.
 *
 * @constructor Creates a `File` instance using the given file path or `FileDescriptor`.
 * Platform-specific implementation details may vary for certain methods.
 *
 * @property name Name of the file or directory.
 * @property nameWithoutExtension Name of the file without its extension.
 * @property extension The file's extension (if any).
 * @property path The normalized file path.
 * @property fullPath The full absolute path of the file or directory.
 * @property directoryPath Path to the parent directory of the file or the path itself if it's a directory.
 * @property isUri Indicates whether the file is represented by a URI.
 * @property isUriString Checks if the file path is formatted as a URI string.
 * @property isDirectory Whether the path points to a directory.
 * @property exists Whether the file or directory exists on the filesystem.
 * @property size Size of the file in bytes.
 * @property lastModifiedEpoch The last modification timestamp of the file in seconds since the Unix epoch.
 * @property lastModified The last modification time as a `LocalDateTime` object.
 * @property createdTime The creation time of the file as a `LocalDateTime` object (if available).
 * @property lastAccessTime The last access time of the file as a `LocalDateTime` object (if available).
 * @property ownerPermissions File permission bits for the owner of the file (Linux-specific).
 * @property groupPermissions File permission bits for the group associated with the file (Linux-specific).
 * @property otherPermissions File permission bits for others (Linux-specific).
 * @property newPermissions Default permissions to use for newly created files and directories, represented as an octal string.
 */
@OptIn(ExperimentalTime::class, ExperimentalForeignApi::class)
actual class File actual constructor(filePath: String, val platformFd: FileDescriptor?)
{
    actual constructor(parentDirectory: String, name: String) :
            this(Path.newPath(parentDirectory, name, pathSeparator), null)

    actual constructor(parentDirectory: File, name: String) :
            this(Path.newPath(parentDirectory.fullPath, name, pathSeparator), null)

    actual constructor(fd: FileDescriptor) : this("", fd)

    private val p = Path(filePath, pathSeparator)
    actual val name = p.name
    actual val nameWithoutExtension = p.nameWithoutExtension
    actual val extension = p.extension
    actual val path = p.path
    actual val fullPath = p.fullPath
    actual val directoryPath = p.directoryPath
    actual val isParent = directoryPath.isNotEmpty()
    actual val isUri = p.isUri
    actual val isUriString = p.isUriString
    actual val isDirectory: Boolean
    actual val exists: Boolean
    actual val size: ULong
    actual val lastModifiedEpoch: Long
    actual val lastModified: LocalDateTime?
    actual val createdTime: LocalDateTime?
    actual val lastAccessTime: LocalDateTime?

    // Linux-specific properties
    val ownerPermissions: UInt
    val groupPermissions: UInt
    val otherPermissions: UInt
    /**
     * Set this to an octal three digit string specifying the permissions to be used for new files and directories
     */
    var newPermissions: String = "777"
        set(value) {
            val mode = value.toUInt(8)
            if (mode in 0u.."777".toUInt(8)) {
                field = value
                newMode = newPermissions.toUInt(8)
            } else
                throw IllegalArgumentException("File permission must be between 000 and 777 octal")
        }
    private var newMode = newPermissions.toUInt(8)

    init {
        memScoped {
            val statBuf = alloc<stat>()
            val result = stat(filePath, statBuf.ptr)
            exists = if (result == 0)
                true
            else if (errno == 2) false
            else
                throw IOException("stat function result: $result, errorno: $errno on $filePath", null)

            val tz = TimeZone.currentSystemDefault()
            if (exists) {
                size = statBuf.st_size.toULong()
                statBuf.st_mode.also {
                    isDirectory = (it and isDirectoryMask) == isDirectoryValue
                    otherPermissions = (it and permissionMask)
                    groupPermissions = (it and permissionMask.shl(3)).shr(3)
                    ownerPermissions = (it and permissionMask.shl(6)).shr(6)
                }
                lastModifiedEpoch = statBuf.st_mtim.tv_sec
                lastModified = Instant
                    .fromEpochMilliseconds(lastModifiedEpoch * 1000)
                    .toLocalDateTime(tz)
                createdTime = Instant
                    .fromEpochMilliseconds(statBuf.st_ctim.tv_sec * 1000)
                    .toLocalDateTime(tz)
                lastAccessTime = Instant
                    .fromEpochMilliseconds(statBuf.st_atim.tv_sec * 1000)
                    .toLocalDateTime(tz)
            } else {
                size = 0u
                isDirectory = false
                lastModifiedEpoch = 0
                lastModified = null
                createdTime = null
                lastAccessTime = null
                ownerPermissions = 0u
                groupPermissions = 0u
                otherPermissions = 0u
            }
        }
    }

    actual fun newFile() = File(fullPath)

    actual suspend fun directoryList(): List<String> {
        val list = mutableListOf<String>()
        if (isDirectory && exists) {
            val dir = opendir(fullPath)
            if (dir != null) {
                try {
                    var ep = readdir(dir)
                    while (ep != null) {
                        val path = ep.pointed.d_name.toKString()
                        if (path != "." && path != "..")
                            list.add(ep.pointed.d_name.toKString())
                        ep = readdir(dir)
                    }
                } finally {
                    closedir(dir)
                }
            }
        }
        return list
    }

    actual suspend fun directoryFiles(): List<File> = directoryList().map { File(this,it) }

    actual suspend fun delete(): Boolean {

        return remove(fullPath) == 0
    }

    actual suspend fun makeDirectory(): File {
        newFile().apply{
            if (isDirectory && exists) return this
            if (exists)
                throw IllegalArgumentException("Path exists and is not a directory")
        }
        val rc = mkdir(fullPath, newMode)
        if (rc == 0)
            return File(fullPath)
        throw IllegalStateException("Failed to create directory: $fullPath, errno = $errno")
    }

    actual suspend fun resolve(directoryName: String, make: Boolean): File {
        if (!this.isDirectory)
            throw IllegalArgumentException("Only invoke resolve on a directory")
        if (directoryName.isBlank()) return this
        val d = File(this, directoryName)
        return if (make) d.makeDirectory() else d
    }

    /**
     * POSIX has no low-level file copy, so this does explicit copy
     */
    actual suspend fun copy(destinationPath: String): File {
        val dest = File(destinationPath)
        copyFile(this, dest)
        return dest
    }

    actual fun up(): File {
        return File(Path(fullPath).up())
    }

    actual companion object {
        actual val pathSeparator = '/'

        actual fun tempDirectoryPath(): String {
            return getenv("TMPDIR")?.toKString() ?: "/tmp"
        }
        actual fun tempDirectoryFile(): File = File(tempDirectoryPath())

        actual fun workingDirectory(): File {
            memScoped {
                val bufferLength = 4096uL
                val buffer = allocArray<ByteVar>(bufferLength.toInt())

                if (getcwd(buffer, bufferLength) == null)
                    throw IllegalStateException("getcwd() returned error: $errno")
                return File(buffer.toKString())
            }
        }

        private val noTime = Instant.DISTANT_PAST
        // these values used for decoding the st_mode value from stat()
        private val isDirectoryMask = "170000".toUInt(8)
        private val isDirectoryValue = "40000".toUInt(8)
        private val permissionMask = "7".toUInt(8)
        actual val defaultTimeZone = TimeZones()
    }
}

internal val defaultBufferSize = 4096uL
internal val byteSz = 1.toULong()

/**
 * utility function for copying one file to another.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun copyFile(source: File,
                      dest: File,
                      transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)? = null)
    :ULong
{
    var bytesRead = 0uL
    var destF: CPointer<_IO_FILE>? = null
    var sourceF: CPointer<_IO_FILE>? = null
    try {
        destF = fopen(dest.fullPath, "w")
        if (destF == null)
            throw IOException("destination file: ${dest.fullPath} could not be opened. errno: $errno")
        sourceF = fopen(source.fullPath, "r")
        if (sourceF == null)
            throw IOException("source file: ${source.fullPath} could not be opened. errno: $errno")
        val bufArray = ByteArray(defaultBufferSize.toInt())
        val buf = bufArray.refTo(0)
        var n = 0uL
        var outCount = 0uL
        do {
            n = fread(buf, byteSz, defaultBufferSize, sourceF)
            if (n > 0uL) {
                bytesRead += n
                transform?.let {
                    val inBuf = ByteBuffer(bufArray)
                    val newBuf = it(inBuf, n < defaultBufferSize)
                    n = newBuf.remaining.toULong()
                    outCount = fwrite(newBuf.getBytes().refTo(0), byteSz, n, destF)
                } ?: run {
                    outCount = fwrite(buf, byteSz, n, destF)
                }
                if (outCount != n)
                    throw IOException("Copy failed: tried to write $n bytes, wrote $outCount")
            }
        } while (n > 0uL)
    } finally {
        destF?.let { fclose(it) }
        sourceF?.let { fclose(it) }
    }
    return bytesRead
}

actual interface Closeable {
    actual suspend fun close()
}

actual suspend fun <T : Closeable?, R> T.use(body: suspend (T) -> R): R {
    return try {
        body(this)
    } finally {
        this?.close()
    }
}