package com.oldguy.common.io

import kotlinx.datetime.LocalDateTime

actual class TimeZones {
    actual companion object {
        actual fun getDefaultId(): String {
            return AppleTimeZones.getDefaultId()
        }
    }
}

actual class File actual constructor(filePath: String, val platformFd: FileDescriptor?)
    : AppleFile(filePath, platformFd){

    actual constructor(parentDirectory: String, name: String) : this(makePath(parentDirectory, name), null)
    actual constructor(parentDirectory: File, name: String) : this(makePath(parentDirectory.path, name), null)
    actual constructor(fd: FileDescriptor) : this("", fd)
    actual override val name: String get() = super.name
    actual override val nameWithoutExtension: String get() = super.nameWithoutExtension
    actual override val extension: String get() = super.extension
    actual override val path: String get() = super.path
    actual override val fullPath: String get() = super.fullPath
    actual override val directoryPath: String get() = super.directoryPath
    actual override val isDirectory: Boolean get() = super.isDirectory
    actual override val listNames: List<String> get() = super.listNames
    actual override val listFiles: List<File> get() = super.listFiles
    actual override val listFilesTree: List<File> get() = super.listFilesTree
    actual override val exists: Boolean get() = super.exists
    actual override val isUri: Boolean get() = super.isUri
    actual override val isUriString: Boolean get() = super.isUriString
    actual override val size: ULong get() = super.size
    actual override val lastModifiedEpoch: Long get() = super.lastModifiedEpoch
    actual override val lastModified: LocalDateTime get() = super.lastModified
    actual override val createdTime: LocalDateTime get() = super.createdTime
    actual override val lastAccessTime: LocalDateTime get() = super.lastAccessTime

    actual override suspend fun delete(): Boolean {
        return super.delete()
    }

    actual override suspend fun copy(destinationPath: String): File {
        return super.copy(destinationPath)
    }

    /**
     * Determine if subdirectory exists. If not create it.
     * @param directoryName subdirectory of current filePath
     * @return File with path of new subdirectory
     */
    actual override suspend fun resolve(directoryName: String): File {
        return super.resolve(directoryName)
    }

    actual companion object {
        actual val pathSeparator = "/"
    }
}

actual suspend fun <T : Closeable?, R> T.use(body: suspend (T) -> R): R {
    return try {
        body(this)
    } finally {
        this?.close()
    }
}

actual class RawFile actual constructor(
    fileArg: File,
    mode: FileMode,
    source: FileSource
): Closeable, AppleRawFile(fileArg, mode)
{
    actual override suspend fun close() {
        super.close()
    }

    actual override val file: File get() = super.file
    actual override var position: ULong
        get() = super.position
        set(value) {
            super.position = value
        }
    actual override val size: ULong get() = super.size
    actual override var blockSize: UInt = super.blockSize

    actual override suspend fun read(buf: ByteBuffer, newPos: ULong): UInt {
        return super.read(buf, newPos)
    }
    actual override suspend fun read(buf: ByteBuffer): UInt {
        return super.read(buf)
    }
    actual override suspend fun read(buf: UByteBuffer, newPos: ULong): UInt {
        return super.read(buf, newPos)
    }
    actual override suspend fun read(buf: UByteBuffer): UInt {
        return super.read(buf)
    }
    actual override suspend fun write(buf: ByteBuffer, newPos: ULong) {
        super.write(buf, newPos)
    }
    actual override suspend fun write(buf: ByteBuffer) {
        super.write(buf)
    }
    actual override suspend fun write(buf: UByteBuffer, newPos: ULong) {
        super.write(buf, newPos)
    }
    actual override suspend fun write(buf: UByteBuffer) {
        super.write(buf)
    }

    actual override suspend fun copyTo(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)?
    ): ULong {
        return super.copyTo(destination, blockSize, transform)
    }
    actual override suspend fun copyToU(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)?
    ): ULong {
        return super.copyToU(destination, blockSize, transform)
    }
    actual override suspend fun transferFrom(
        source: RawFile,
        startIndex: ULong,
        length: ULong
    ): ULong {
        return super.transferFrom(source, startIndex, length)
    }

    /**
     * Truncate the current file to the specified size.  Not usable on Mode.Read files.
     */
    actual override suspend fun truncate(size: ULong) {
        super.truncate(size)
    }

    /**
     * Sets the length of the file in bytes. Ony usable during FileMode.Write.
     * @param length If current file size is less than [length], file will be expanded.  If current
     * file size is greater than [length] file will be shrunk.
     */
    actual suspend fun setLength(length: ULong) {
    }
}

actual class TextFile actual constructor(
    actual val file: File,
    actual override val charset: Charset,
    mode: FileMode,
    source: FileSource
) : Closeable, AppleTextFile(file, charset, mode) {
    actual constructor(
        filePath: String,
        charset: Charset,
        mode: FileMode,
        source: FileSource
    ) : this(File(filePath, null), charset, mode, source)

    actual override suspend fun close() {
        super.close()
    }

    actual override suspend fun readLine(): String {
        return super.readLine()
    }

    actual override suspend fun forEachLine(action: (count: Int, line: String) -> Boolean) {
        super.forEachLine(action)
    }

    actual override suspend fun forEachBlock(maxSizeBytes: Int, action: (text: String) -> Boolean) {
        super.forEachBlock(maxSizeBytes, action)
    }

    actual override suspend fun read(maxSizeBytes: Int): String {
        return super.read(maxSizeBytes)
    }

    actual override suspend fun write(text: String) {
        super.write(text)
    }

    actual override suspend fun writeLine(text: String) {
        super.writeLine(text)
    }
}

actual interface Closeable {
    actual suspend fun close()
}