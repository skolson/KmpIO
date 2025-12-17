package com.oldguy.common.io

import android.content.Context
import android.net.Uri
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.util.*
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
actual class TimeZones {
    actual val defaultId: String = TimeZone.getDefault().id
    actual val kotlinxTz: kotlinx.datetime.TimeZone = if (kotlinx.datetime.TimeZone.availableZoneIds.contains(defaultId))
        kotlinx.datetime.TimeZone.of(defaultId)
    else {
        println("No such zoneId $defaultId in kotlinx tz")
        kotlinx.datetime.TimeZone.UTC
    }

    actual fun localFromEpochMilliseconds(epochMilliseconds: Long): LocalDateTime {
        return Instant
            .fromEpochMilliseconds(epochMilliseconds)
            .toLocalDateTime(kotlinxTz)
    }

    actual companion object {
        actual val default: kotlinx.datetime.TimeZone = TimeZones().kotlinxTz
    }
}

@OptIn(ExperimentalTime::class)
actual class File actual constructor(filePath: String, platformFd: FileDescriptor?) {
    actual constructor(parentDirectory: String, name: String) :
            this(parentDirectory + name, null)

    actual constructor(parentDirectory: File, name: String) :
            this("${parentDirectory.fullPath}$pathSeparator$name", null)

    actual constructor(fd: FileDescriptor) : this("", fd)

    private val javaFile: java.io.File = java.io.File(filePath)

    actual val name: String = javaFile.name
    actual val nameWithoutExtension: String = javaFile.nameWithoutExtension
    actual val extension: String = javaFile.extension.ifEmpty { "" }
    actual val path: String = javaFile.path.trimEnd(pathSeparator)
    actual val fullPath: String = filePath.trimEnd(pathSeparator)
    actual val directoryPath: String = path.replace(name, "").trimEnd(pathSeparator)
    actual val isParent = directoryPath.isNotEmpty()
    actual val isDirectory get() = javaFile.isDirectory
    actual val exists get() = javaFile.exists()
    actual val platformFd: FileDescriptor? = platformFd
    actual val isUri = platformFd?.code == 1 && platformFd.descriptor is Uri
    actual val isUriString = platformFd?.code == 2 && platformFd.descriptor is String
    actual val size: ULong get() = javaFile.length().toULong()
    actual val lastModifiedEpoch: Long get() {
        if (!exists)
            throw IllegalStateException("File $fullPath does not exist")
        return Files.getLastModifiedTime(Paths.get(fullPath)).toMillis()
    }

    private val defaultTimeZone = TimeZones.default
    actual val lastModified: LocalDateTime? get() =
        Instant.fromEpochMilliseconds(lastModifiedEpoch)
            .toLocalDateTime(defaultTimeZone)
    actual val createdTime: LocalDateTime? get() {
        if (!exists)
            throw IllegalStateException("File $fullPath does not exist")
        val fileTime = Files.getAttribute(Paths.get(fullPath), "creationTime") as FileTime
        return Instant
            .fromEpochMilliseconds(fileTime.toMillis())
            .toLocalDateTime(defaultTimeZone)
    }
    actual val lastAccessTime: LocalDateTime? get() {
        if (!exists)
            throw IllegalStateException("File $fullPath does not exist")
        val fileTime = Files.getAttribute(Paths.get(fullPath), "lastAccessTime") as FileTime
        return Instant
            .fromEpochMilliseconds(fileTime.toMillis())
            .toLocalDateTime(TimeZones.default)
    }

    actual fun newFile() = File(fullPath)

    actual suspend fun directoryList(): List<String> {
        val list = mutableListOf<String>()
        return if (isDirectory) {
            val x = javaFile.listFiles()
            x?.map { it.name } ?: emptyList()
        } else
            list
    }

    actual suspend fun directoryFiles(): List<File> = directoryList().map { File(this,it) }

    val fd: Uri? =
        if (platformFd != null && platformFd.code == 1)
            platformFd.descriptor as Uri
        else
            null

    actual suspend fun delete(): Boolean {
        return javaFile.delete()
    }

    actual suspend fun makeDirectory(): File {
        return if (javaFile.mkdir())
            File(fullPath)
        else
            this
    }

    actual suspend fun resolve(directoryName: String, make: Boolean): File {
        if (!this.isDirectory)
            throw IllegalArgumentException("Only invoke resolve on a directory")
        if (directoryName.isBlank()) return this
        val directory = File(this, directoryName)
        if (!directory.javaFile.exists() && make)
            directory.makeDirectory()
        return directory
    }

    actual suspend fun copy(destinationPath: String): File {
        val dest = java.io.File(destinationPath)
        runCatching {
            FileOutputStream(dest).use { outputStream ->
                FileInputStream(javaFile).use { inStream ->
                    val buf = ByteArray(4096)
                    var bytesRead: Int
                    while (inStream.read(buf).also { bytesRead = it } > 0) {
                        outputStream.write(buf, 0, bytesRead)
                    }
                }
            }
        }.onFailure {
            throw IOException("Copy failed: ${it.message}", it)
        }
        return File(dest.absolutePath, null)
    }

    actual fun up(): File {
        return File(Path(fullPath).up())
    }

    actual companion object {
        actual val pathSeparator = '/'
        lateinit var appContext: Context

        actual fun tempDirectoryPath(): String {
            return appContext.cacheDir.absolutePath
        }
        actual fun tempDirectoryFile(): File = File(tempDirectoryPath())

        actual fun workingDirectory(): File {
            return File(appContext.filesDir.absolutePath
                ?: throw IllegalStateException("Cannot access appContext filesDir")
            )
        }

        actual val defaultTimeZone = TimeZones()
    }
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
