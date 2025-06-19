package com.oldguy.common.io

import kotlinx.cinterop.*
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDictionary
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.fileCreationDate
import platform.Foundation.fileModificationDate
import platform.Foundation.fileSize
import platform.Foundation.temporaryDirectory
import platform.Foundation.timeIntervalSince1970
import platform.posix.errno
import kotlin.coroutines.cancellation.CancellationException

actual class TimeZones {
    actual companion object {
        actual fun getDefaultId(): String {
            return AppleTimeZones.getDefaultId()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
actual class File actual constructor(filePath: String, val platformFd: FileDescriptor?)
{
    actual constructor(parentDirectory: String, name: String) :
            this(Path.newPath(parentDirectory, name, pathSeparator), null)

    actual constructor(parentDirectory: File, name: String) :
            this(Path.newPath(parentDirectory.fullPath, name, pathSeparator), null)

    actual constructor(fd: FileDescriptor) : this("", fd)
    private val fm = NSFileManager.defaultManager
    private val p = Path(filePath, pathSeparator)
    actual val name = p.name
    actual val nameWithoutExtension = p.nameWithoutExtension
    actual val extension = p.extension
    actual val path = p.path
    actual val fullPath = p.fullPath
    actual val directoryPath = p.directoryPath
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
                (fm.attributesOfItemAtPath(path, it) as NSDictionary)
                    .fileSize()
            }
            lastModifiedEpoch = throwError { error ->
                (fm.attributesOfItemAtPath(path, error) as NSDictionary?)
                    ?.let { dict ->
                        dict.fileModificationDate()?.let {
                            (it.timeIntervalSince1970 * 1000.0).toLong()
                        }
                    } ?: 0L
            }
            isDirectory = throwError { error ->
                memScoped {
                    val isDirPointer = alloc<BooleanVar>()
                    fm.fileExistsAtPath(path, isDirPointer.ptr)
                    isDirPointer.value
                }
            }
            lastModified = Instant
                .fromEpochMilliseconds(lastModifiedEpoch)
                .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
            createdTime = throwError { cPointer ->
                var epoch = 0L
                (fm.attributesOfItemAtPath(path, cPointer) as NSDictionary?)
                    ?.let { dict ->
                        dict.fileCreationDate()?.let {
                            epoch = (it.timeIntervalSince1970 * 1000.0).toLong()
                        }
                    }
                if (epoch > 0L) {
                    Instant
                        .fromEpochMilliseconds(epoch)
                        .toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
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
            fm.copyItemAtPath(path, destinationPath, it)
        }
        return File(destinationPath, null)
    }

    @OptIn(BetaInteropApi::class)
    @Throws(NSErrorException::class, CancellationException::class)
    actual suspend fun delete(): Boolean {
        return if (exists) {
            throwError {
                val rc = fm.removeItemAtPath(path, it)
                println("delete NSError: ${it.pointed.value?.localizedDescription ?: "null"}")
                rc
            }
        } else
            false
    }

    actual suspend fun directoryList(): List<String> {
        val list = mutableListOf<String>()
        throwError {
            fm.contentsOfDirectoryAtPath(path, it)?.let { names ->
                names.forEach { name ->
                    name?.let { ptr ->
                        val n = ptr as String
                        if (n.isNotEmpty())
                            list.add(n)
                    }
                }
            }
        }
        return list
    }

    actual fun newFile() = File(fullPath)

    /**
     * Determine if subdirectory exists. If not create it.
     * @param directoryName name of a subdirectory of the current File
     * @return File with path of new subdirectory
     */
    actual suspend fun resolve(directoryName: String, make: Boolean): File {
        if (!isDirectory)
            throw IllegalArgumentException("Only invoke resolve on a directory")
        if (directoryName.isBlank()) return File(path)
        if (directoryName.startsWith(pathSeparator))
            throw IllegalArgumentException("resolve requires $directoryName to be not empty and cannot start with $pathSeparator")
        return File("$path/$directoryName", null).apply {
            if (make) makeDirectory()
        }
    }

    actual fun up(): File {
        return File(Path(fullPath).up().fullPath)
    }

    actual suspend fun makeDirectory(): File {
        var rc = false
        throwError { errorPointer ->
            val withIntermediates = path.contains(pathSeparator)
            rc = fm.createDirectoryAtPath(
                fullPath,
                withIntermediates,
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
        actual fun tempDirectoryPath(): String
            = NSFileManager.defaultManager().temporaryDirectory.path
            ?: throw IOException("temporaryDirectory.path is null")
        actual fun tempDirectoryFile(): File = File(tempDirectoryPath())

        actual fun workingDirectory(): File =
            File(NSFileManager.defaultManager().currentDirectoryPath)

        /**
         * Creates a pointer to an NSError pointer for use by File-based operations, and invokes [block] with it. If an error
         * is produced by the [block] invocation, it is converted to an NSErrorException and thrown.
         * @param block lambda that typically uses the errorPointer argument in an Apple API that requires an NSError**
         */
        fun <T> throwError(block: (errorPointer: CPointer<ObjCObjectVar<NSError?>>) -> T): T {
            val errorPointer: ObjCObjectVar<NSError?> = nativeHeap.alloc()

            println("throwError. raw: ${errorPointer.rawPtr}, ptr: ${errorPointer.ptr.rawValue}, objc: ${errorPointer.objcPtr()}")
            val result: T = block(errorPointer.ptr)
            errorPointer.value?.let {
                val appleError = NSErrorException(it)
                println("Attempting throw NSErrorException:")
                println(appleError.toString())
                throw appleError
            }
            return result
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