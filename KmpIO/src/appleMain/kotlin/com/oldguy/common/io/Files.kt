package com.oldguy.common.io

import kotlinx.cinterop.*
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDictionary
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone
import platform.Foundation.fileCreationDate
import platform.Foundation.fileModificationDate
import platform.Foundation.fileSize
import platform.Foundation.temporaryDirectory
import platform.Foundation.timeIntervalSince1970
import platform.posix.errno
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual class TimeZones {
    actual val defaultId: String = NSTimeZone.defaultTimeZone.name
    actual val kotlinxTz: TimeZone = if (TimeZone.availableZoneIds.contains(defaultId))
        TimeZone.of(defaultId)
    else {
        println("No such zoneId $defaultId in kotlinx tz")
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

@OptIn(BetaInteropApi::class, ExperimentalForeignApi::class)
actual class File actual constructor(filePath: String, val platformFd: FileDescriptor?)
{
    actual constructor(parentDirectory: String, name: String) :
            this(Path.newPath(parentDirectory, name, pathSeparator), null)

    actual constructor(parentDirectory: File, name: String) :
            this(Path.newPath(parentDirectory.fullPath, name, pathSeparator), null)

    actual constructor(fd: FileDescriptor) : this("", fd)
    private val fm get() = NSFileManager.defaultManager
    private val p = Path(filePath, pathSeparator)
    actual val name = p.name
    actual val nameWithoutExtension = p.nameWithoutExtension
    actual val extension = p.extension
    actual val path = p.path
    actual val fullPath = p.fullPath
    actual val directoryPath = p.directoryPath
    actual val isParent = directoryPath.isNotEmpty()
    actual val isDirectory: Boolean
    actual val exists: Boolean
    actual val isUri: Boolean
    actual val isUriString: Boolean
    actual val size: ULong
    actual val lastModifiedEpoch: Long
    actual val lastModified: LocalDateTime?
    actual val createdTime: LocalDateTime?
    actual val lastAccessTime: LocalDateTime?

    init {
        exists = pathExists(fullPath)
        if (exists) {
            size = throwError {
                 (fm.attributesOfItemAtPath(fullPath, it) as NSDictionary?)
                    ?.fileSize() ?: 0uL
            }
            lastModifiedEpoch = throwError { error ->
                (fm.attributesOfItemAtPath(fullPath, error) as NSDictionary?)
                    ?.let { dict ->
                        dict.fileModificationDate()?.let {
                            (it.timeIntervalSince1970 * 1000.0).toLong()
                        }
                    } ?: 0L
            }
            isDirectory = throwError { error ->
                memScoped {
                    val isDirPointer = alloc<BooleanVar>()
                    fm.fileExistsAtPath(fullPath, isDirPointer.ptr)
                    isDirPointer.value
                }
            }
            lastModified = defaultTimeZone.localFromEpochMilliseconds(lastModifiedEpoch)
            createdTime = throwError { cPointer ->
                var epoch = 0L
                (fm.attributesOfItemAtPath(fullPath, cPointer) as NSDictionary?)
                    ?.let { dict ->
                        dict.fileCreationDate()?.let {
                            epoch = (it.timeIntervalSince1970 * 1000.0).toLong()
                        }
                    }
                if (epoch > 0L) {
                    defaultTimeZone.localFromEpochMilliseconds(epoch)
                } else
                    null
            }
            lastAccessTime = lastModified
        } else {
            size = 0u
            isDirectory = false
            lastModifiedEpoch = 0
            lastModified = null
            createdTime = null
            lastAccessTime = null
        }
        isUri = false
        isUriString = false
    }

    actual suspend fun copy(destinationPath: String): File {
        throwError {
            fm.copyItemAtPath(fullPath, destinationPath, it)
        }
        return File(destinationPath, null)
    }

    @Throws(NSErrorException::class, CancellationException::class)
    actual suspend fun delete(): Boolean {
        return if (exists) {
            throwError {
                fm.removeItemAtPath(fullPath, it)
            }
        } else
            false
    }

    /**
     * List directory content. Apple implementation explicitly excludes .DS_Store file used by Finder
     */
    actual suspend fun directoryList(): List<String> {
        val list = mutableListOf<String>()
        throwError {
            fm.contentsOfDirectoryAtPath(fullPath, it)?.let { names ->
                names.forEach { name ->
                    name?.let { ptr ->
                        (ptr as String).apply {
                            if (isNotEmpty() && !this.endsWith(DS_STORE, true))
                                list.add(this)
                        }
                    }
                }
            }
        }
        return list
    }

    actual suspend fun directoryFiles(): List<File> = directoryList().map { File(it) }
    actual fun newFile() = File(fullPath)

    /**
     * Determine if subdirectory exists. If not create it.
     * @param directoryName name of a subdirectory of the current File
     * @return File with path of new subdirectory
     */
    actual suspend fun resolve(directoryName: String, make: Boolean): File {
        if (!isDirectory)
            throw IllegalArgumentException("Only invoke resolve on a directory")
        if (directoryName.isBlank()) return newFile()
        if (Path(directoryName).isAbsolute)
            throw IllegalArgumentException("resolve requires $directoryName to be not empty and must be relative")
        return File("$fullPath/$directoryName", null).apply {
            if (make) makeDirectory()
        }
    }

    actual fun up(): File {
        return File(Path(fullPath).up())
    }

    actual suspend fun makeDirectory(): File {
        if (pathExists(fullPath)) return File(fullPath)
        var rc = false
        throwError { errorPointer ->
            rc = fm.createDirectoryAtPath(
                fullPath,
                false,
                null,
                errorPointer
            )
        }
        if (rc) return File(fullPath)
        throw IllegalStateException("Failed to create directory: $fullPath, errno = $errno")
    }

    private fun pathExists(path: String): Boolean {
        if (path.isEmpty() || path.isBlank())
            return false
        memScoped {
            val isDirPointer = alloc<BooleanVar>()
            return fm.fileExistsAtPath(path, isDirPointer.ptr)
        }
    }

    actual companion object {
        actual val pathSeparator = '/'
        actual val defaultTimeZone = TimeZones()
        actual fun tempDirectoryPath(): String
            = NSFileManager.defaultManager().temporaryDirectory.path
            ?: throw IOException("temporaryDirectory.path is null")
        actual fun tempDirectoryFile(): File = File(tempDirectoryPath())

        actual fun workingDirectory(): File =
            File(NSFileManager.defaultManager().currentDirectoryPath)

        private const val DS_STORE = ".DS_Store"
        /**
         * Creates a pointer to an NSError pointer for use by File-based operations, and invokes [block] with it. If an error
         * is produced by the [block] invocation, it is converted to an NSErrorException and thrown.
         * @param block lambda that typically uses the errorPointer argument in an Apple API that requires an NSError**
         */
        fun <T> throwError(block: (errorPointer: CPointer<ObjCObjectVar<NSError?>>) -> T): T {
            val errorPointer: ObjCObjectVar<NSError?> = nativeHeap.alloc()
            try {
                val result: T = block(errorPointer.ptr)
                errorPointer.value?.let {
                    val appleError = NSErrorException(it)
                    println("Attempting throw NSErrorException:")
                    println(appleError.toString())
                    throw appleError
                }
                return result
            } finally {
                nativeHeap.free(errorPointer)
            }
        }

    }
}

actual suspend fun <T : Closeable?, R> T.use(body: suspend (T) -> R): R {
    return try {
        body(this)
    } finally {
        this?.close()
    }
}

actual interface Closeable {
    actual suspend fun close()
}