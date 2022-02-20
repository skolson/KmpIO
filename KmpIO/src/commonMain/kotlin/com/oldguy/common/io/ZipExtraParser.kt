package com.oldguy.common.io

/**
 * Stateless Factory of ZipExtra instances.
 * Decodes extra entries from the entry ByteArray content in directory records.
 * Encodes Lists of ZipExtra instances into the ByteArray content in directory records.
 * Overridable for use cases where new subclasses of ZipExtra need to be instantiated during decoding.
 * Override the [create] function to produce the correct subclass of ZipExtra for a given signature.
 * Note that create is only called for signatures not already handled by this class.
 * Reserved signatures already handled:
 * 0x0001 - [ZipExtraZip64]
 * 0x000a - [ZipExtraNtfs]
 *
 * @param directory either an instance of [ZipDirectoryRecord] or [ZipLocalRecord]
 */
open class ZipExtraParser(
    val directory: ZipDirectoryCommon
) {
    val extraContent = directory.extra
    val isLocal = directory is ZipLocalRecord

    /**
     * Given Extra content, parses it into [List<ZipExtra>]
     */
    fun decode(): List<ZipExtra> {
        val buffer = ByteBuffer(extraContent)
        val list = mutableListOf<ZipExtra>()
        val start = buffer.position
        var remaining = buffer.limit
        while (remaining > 0) {
            val sig = buffer.short
            val length = buffer.short
            when (sig) {
                zip64Signature -> ZipExtraZip64(isLocal, directory)
                ntfsSignature -> ZipExtraNtfs(length)
                else -> create(sig, length)
            }.apply {
                decode(buffer)
                list.add(this)
            }
            remaining = buffer.position - start
        }
        return list
    }

    /**
     * Called during decode to produce [ZipExtra] instances depending on [signature]
     * Override this to provide custom sub-classes of ZipExtra. By default this returns [ZipExtraGeneral]
     * @param signature two byte signature from extra data
     * @param length of content of this ZipExtra instance
     */
    open fun create(signature: Short, length: Short): ZipExtra {
        return ZipExtraGeneral(signature, length)
    }

    /**
     * Encodes the specified list into the directory's extra content.
     */
    open fun encode(list: List<ZipExtra>): ByteArray {
        ByteBuffer(contentLength(list)).apply {
            list.forEach {
                it.encode(this)
            }
            rewind()
            return getBytes(limit)
        }
    }

    companion object {
        const val zip64Signature = 0x1.toShort()
        const val ntfsSignature = 0xa.toShort()
        const val patchDescriptorSignature = 0xf.toShort()
        const val strongEncryptionSignature = 0x17.toShort()

        fun contentLength(list: List<ZipExtra>): Int {
            val l = list.sumOf { it.length.toInt() }
            if (l >= Short.MAX_VALUE)
                throw ZipException("Extra total length of $l exceeds ${Short.MAX_VALUE}")
            return l
        }
    }
}