package com.oldguy.common.io

actual class Charset actual constructor(set: Charsets)
    : AppleCharset(set){
    actual val charset:Charsets = set

    actual override fun decode(bytes: ByteArray): String {
        return super.decode(bytes)
    }

    actual override fun encode(inString: String): ByteArray {
        return super.encode(inString)
    }
}

actual class TimeZones {
    actual companion object {
        actual fun getDefaultId(): String {
            return AppleTimeZones.getDefaultId()
        }
    }
}

actual class File actual constructor(filePath: String, platformFd: FileDescriptor?)
    : AppleFile(filePath, platformFd){

    actual constructor(parentDirectory: String, name: String) : this(parentDirectory + name, null)
    actual constructor(parentDirectory: File, name: String) : this(parentDirectory.resolve(name).fullPath, null)
    actual constructor(fd: FileDescriptor) : this("", fd)
    actual override val name: String get() = super.name
    actual override val nameWithoutExtension: String get() = super.nameWithoutExtension
    actual override val extension: String get() = super.extension
    actual override val path: String get() = super.path
    actual override val fullPath: String get() = super.fullPath
    actual override val isDirectory: Boolean get() = super.isDirectory
    actual override val listFiles: List<File> get() = super.listFiles
    actual override val exists: Boolean get() = super.exists
    actual override val isUri: Boolean get() = super.isUri
    actual override val isUriString: Boolean get() = super.isUriString
    actual override val size: ULong get() = super.size

    actual override fun delete(): Boolean {
        return super.delete()
    }

    actual override fun copy(destinationPath: String): File {
        return super.copy(destinationPath)
    }

    /**
     * Determine if subdirectory exists. If not create it.
     * @param subdirectory of current filePath
     * @return File with path of new subdirectory
     */
    actual override fun resolve(directoryName: String): File {
        return super.resolve(directoryName)
    }
}

actual inline fun <T : Closeable?, R> T.use(body: (T) -> R): R {
    return body(this)
}

actual class RawFile actual constructor(
    fileArg: File,
    mode: FileMode,
    source: FileSource
): Closeable, AppleRawFile(fileArg, mode, source)
{
    actual override fun close() {
        super.close()
    }

    actual override val file: File get() = super.file
    actual override var position: ULong = super.position
    actual override val size: ULong get() = super.size
    actual override var blockSize: UInt = super.blockSize

    actual override fun read(buf: ByteBuffer, position: Long): UInt {
        return super.read(buf, position)
    }
    actual override fun read(buf: UByteBuffer, position: Long): UInt {
        return super.read(buf, position)
    }
    actual override fun write(buf: ByteBuffer, position: Long) {
        super.write(buf, position)
    }
    actual override fun write(buf: UByteBuffer, position: Long) {
        super.write(buf, position)
    }
    actual override fun copyTo(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: ByteBuffer, lastBlock: Boolean) -> ByteBuffer)?
    ): ULong {
        return super.copyTo(destination, blockSize, transform)
    }
    actual override fun copyToU(
        destination: RawFile,
        blockSize: Int,
        transform: ((buffer: UByteBuffer, lastBlock: Boolean) -> UByteBuffer)?
    ): ULong {
        return super.copyToU(destination, blockSize, transform)
    }
    actual override fun transferFrom(
        source: RawFile,
        startIndex: ULong,
        length: ULong
    ): ULong {
        return super.transferFrom(source, startIndex, length)
    }

    /**
     * Truncate the current file to the specified size.  Not usable on Mode.Read files.
     */
    actual override fun truncate(size: ULong) {
        super.truncate(size)
    }
}

actual class TextFile actual constructor(
    file: File,
    charset: Charset,
    mode: FileMode,
    source: FileSource
) : Closeable, AppleTextFile(file, charset, mode, source) {
    actual constructor(
        filePath: String,
        charset: Charset,
        mode: FileMode,
        source: FileSource
    ) : this(File(filePath, null), charset, mode, source)

    actual override fun close() {
        super.close()
    }

    actual override fun readLine(): String {
        return super.readLine()
    }

    actual override fun forEachLine(action: (line: String) -> Unit) {
        super.forEachLine(action)
    }

    actual override fun write(text: String) {
        super.write(text)
    }

    actual override fun writeLine(text: String) {
        super.writeLine(text)
    }
}

actual interface Closeable {
    actual fun close()
}