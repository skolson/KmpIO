package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.MultiByteDecodeException

/**
 * Platform-neutral text buffering for simple text file read (or other source) operations, using blocks of
 * bytes as input. Various access methods are provided for processing decoded text.
 *
 * Source lambda supplies all bytes, in order. TextBuffer handles decoding using the specified charset,
 * including handling multi-byte character sets in the edge case where a partial character is found
 * at the end of a ByteArray. If the source lambda indicates no more data by returning 0 bytes, and TextBuffer
 * determines there is an incomplete character at the end of the file, it will throw MultiByteDecodeException.
 *
 * @param charset specifies how to decode the incoming bytes from the underlying file.
 * @param blockSizeArg specifies number of bytes to request from source lambda on each call. Value is
 * rounded up to a multiple of the maximum number of bytes per character for the specified Charset.
 * @param source function should perform a read operation up to count bytes,
 * into the specified buffer which is a ByteArray. It should return the number of bytes read, or 0
 * to indicate end of file. ByteArray can be any size and does not have to end on a line break. Can
 * also end in the middle of a multi-byte character, see above comments.
 */
open class TextBuffer(
    val charset: Charset,
    blockSizeArg: Int = DEFAULT_BLOCK_SIZE,
    val source: (suspend (
        buffer: ByteArray,
        count: Int
    ) -> UInt )
) {
    private val blockSize = blockSizeArg + (blockSizeArg % charset.bytesPerChar.last)
    private val bytes = ByteArray(blockSize)
    private var buf = ByteBuffer(blockSize + (charset.bytesPerChar.last * 2))
        .apply { limit = 0 }
    private var endOfFile = false
    private var noMoreSource = false
    private var readLock = false
    private var remainder = ByteArray(charset.bytesPerChar.last)
    private var partial = ByteArray(0)

    /**
     * While processing text by line, this attribute is the current line count processed
     */
    var lineCount = 0
        private set

    /**
     * While using parsing functions, this attribute is the current position on the current line, one-relative
     */
    var linePosition = 0
        private set

    /**
     * Count of the number of bytes read from source, before decoding.
     */
    var bytesRead: Long = 0
        private set

    /**
     * true if source has returned zero indicating no more data, and all characters are processed
     */
    val isEndOfFile get() = endOfFile

    /**
     * True if a read operation is in progress. Do not alter or close the underlying source while this is true.
     */
    val isReadLock get() = readLock

    private var _lastChar = false
    /**
     * Last character read from the source. If used before the first call to next(), an exception is
     * thrown.
     */
    var lastChar = Char(0)
        get() {
            if (_lastChar) return field
            throw IllegalStateException("Last character not available before first call to next()")
        }
        private set

    /**
     * The remaining vars are configuration for the various parsing operators. Useful for manual
     * configuration, or for a builder DSL that produces configured TextBuffers
     */

    /**
     * Character used to enclose quoted strings. See quotedString()
     */
    var quote: Char = '"'

    /**
     * String pattern, if matched in quotedString(), is replaced by quote. If empty, no escaping
     * happens
     */
    var escapedQuote: String = "\\\""

    /**
     * List of separator character Strings, used in token(). See fun token() for details.
     */
    var tokenSeparators = emptyList<String>()
    val separatorChars get() = tokenSeparators.flatMap { it.toCharArray().toList() }.distinct()

    /**
     * If true, and a token value starts with a quote character, then use fun quotedString() to read.
     * If false, treat quote like any other character.
     */
    val tokenValueQuotedString = true


    private suspend fun useSource(): UInt {
        if (buf.remaining > 0) {
            if (buf.remaining >= charset.bytesPerChar.last)
                throw IllegalStateException("useSource called when more than ${charset.bytesPerChar.last} bytes available: ${buf.remaining}")
            val remainder = buf.getBytes()
            buf.clear()
            buf.putBytes(remainder)
        } else {
            if (noMoreSource) {
                endOfFile = true
                return 0u
            }
            buf.clear()
        }
        val count = source(bytes, bytes.size).toInt()
        bytesRead += count.toLong()
        if (count == 0)
            noMoreSource = true
        else {
            val partialBytes = charset.checkMultiByte(bytes, count, 0, false)
            if (partial.isNotEmpty()) buf.putBytes(partial)
            val count = (count - partialBytes) + partial.size
            buf.putBytes(bytes, length = count)
            partial = ByteArray(partialBytes)
            if (partialBytes > 0) {
                bytes.copyInto(
                    partial,
                    0,
                    count - partialBytes,
                    count
                )
            }
        }
        buf.flip()
        return count.toUInt()
    }

    private fun checkBytes(position: Int): ByteArray {
        var pos = position
        return ByteArray(charset.bytesPerChar.first).apply {
            repeat(charset.bytesPerChar.first) {
                this[it] = buf.get(pos++)
            }
        }
    }

    /**
     * Use to retrieve blocks of decoded text with no parsing functionality. To ensure proper
     * decoding of multi-byte character sets, each block saves any incomplete character bytes at the end
     * of the block for processing during the next call to nextBlock().
     */
    suspend fun nextBlock(): String {
        useSource()
        return if (buf.remaining == 0) ""
        else
            charset.decode(buf.getBytes())
    }

    /**
     * Use this to read decoded character by decoded character, until isEndOfFile is true.
     *
     * the most recent character read is available in lastChar
     *
     * @param peek true if decoded character should be returned without advancing to the next character.
     * @return decoded character. if isEndOfFile is true, returns code 0x00 character.
     */
    suspend fun next(peek: Boolean = false): Char {
        if (!isEndOfFile && buf.remaining < charset.bytesPerChar.last)
            useSource()
        _lastChar = true
        if (buf.remaining == 0) return Char(0)
        val pos = buf.position
        val byteCount = charset.byteCount(checkBytes(pos))
        if (byteCount > buf.remaining)
            throw MultiByteDecodeException(
                "Missing bytes to complete indicated character at position in last block $pos, ",
                pos,
                byteCount,
                byteCount - buf.remaining,
                buf.get(pos)
            )
        for (i in pos until pos + byteCount)
            remainder[i - pos] = buf.get(i)
        val s = charset.decode(remainder, byteCount)
        if (s.length != 1)
            throw MultiByteDecodeException(
                "decode of $byteCount bytes returned $s, length = ${s.length}, should have been 1",
                pos,
                byteCount,
                -1,
                remainder[0]
            )
        lastChar = s[0]
        if (lastChar == EOL_CHAR) {
            linePosition = 0
            lineCount++
        } else
            linePosition++
        if (!peek) buf.position = buf.position + byteCount
        return s[0]
    }

    /**
     * Reads next line of text, no matter how long, which has obvious implications for memory on large files with no
     * line breaks. It uses the source function to read blocks when needed and maintains state of where next line is.
     * So only use this on files with line breaks.
     * @return a line containing any text found without a line separator. Line may be empty. After all lines have been
     * returned, subsequent calls will always be an empty string.
     */
    open suspend fun readLine(): String {
        return StringBuilder(blockSize).apply {
            while (!isEndOfFile) {
                val c = next()
                if (c == EOL_CHAR) break
                append(c)
            }
        }.toString()
    }

    /**
     * Runs the read process.
     * @param action function is called for each line. Processing continues until end of file is
     * reached and all text lines have been passed to this function. Function is called with two
     * arguments; the one-relative line number of the text, and the text without any line separator.
     * action should return false if reading should stop
     */
    open suspend fun forEachLine(
        action: (count: Int, line: String) -> Boolean
    ) {
        try {
            readLock = true
            while (true) {
                val line = readLine()
                if (isEndOfFile) break
                if (!action(lineCount, line))
                    break
            }
        } finally {
            readLock = false
        }
    }

    suspend fun next(characterCount: Int, peek: Boolean = false): String {
        return StringBuilder(characterCount).apply {
            repeat(characterCount) {
                if (!isEndOfFile)
                    append(next(peek))
            }
        }.toString()
    }

    /**
     * Reads the next character, skips any whitespace characters
     * @return number of whitespace characters skipped
     */
    suspend fun skipWhitespace(): Int {
        var count = 0
        while (!isEndOfFile && next().isWhitespace()) {
            count++
        }
        return count
    }

    /**
     * Verifies that the current position is a quote character. If it is, retrieves characters and
     * builds a String until the next quote character is seen. If an escape is specified for an
     * enclose quote, handle the escape as well. If end of input is reached before the closing quote,
     * all characters since the last quote are returned.
     *
     * See variable "quote" for quote character to look for. defaults to '"'
     * See variable escapedQuote String to match as an escape for quote. If empty, no escape processing happens
     *
     * @param maxSize number of characters to read before returning.
     * @return String containing characters between quote characters. If previous call to next()
     * is not a quote, throw an exception.
     */
    suspend fun quotedString(
        maxSize: Int = 1024
    ): String {
        var c = lastChar
        if (c != quote)
            throw IllegalStateException("Quoted string must start with quote")
        return StringBuilder(maxSize).apply {
            while (true) {
                c = next()
                if (escapedQuote.isEmpty() && c == quote) break
                if (escapedQuote.isNotEmpty()) {
                    var match = 0
                    var temp = ""
                    for (m in escapedQuote) {
                        if (c == m) {
                            match++
                            temp += c
                            c = next()
                        } else
                            break
                    }
                    if (match == escapedQuote.length)
                        append(quote)
                    else {
                        append(temp)
                        if (c == quote) break
                    }
                }
                append(c)
                if (isEndOfFile || this.length >= maxSize) break
            }
            if (!isEndOfFile) next()
        }.toString()
    }

    /**
     * A Token instance is one or more leading separator characters (a separator string)
     * followed by all non-separator, non-whitespace characters as the token value.
     * @property leadingSeparator if the first string seen matches one of the separators in the
     * configured list, the match value is set to this property. Otherwise empty.
     * @property value if non-separator, non whitespace character(s) are found after the token,
     * they are set to this property. Otherwise empty
     * @property line the line number where this token was located. From instance property 'lineCount;
     * @property position the number of the character, one relative, in the current line. From
     * instance property 'linePosition'
     */
    data class Token(
        val leadingSeparator: String,
        val value: String,
        val line: Int,
        val position: Int
    )

    /**
     * Reads next token of text, up to maxSize characters.
     *
     * A Token instance is one or more leading separator characters or whitespace, followed by all non-separator,
     * non-whitespace characters as the token value. Separator characters are specified in the tokenSeparators
     * variable, and consist of all the distinct characters across all the separator strings in the list.
     *
     * An example of a partial tokenSeparators list for parsing an XML document would include the following:
     * tokenSeparators = listOf("<", ">", "/>", "<?", "?>", "<!--", "--!>", "=")
     *
     * If the first sequence of separator characters is followed by whitespace and another separator,
     * the Token returned will contain the first separators with an empty value
     *
     * Whitespace between the separator and the token value (or the next separator) is ignored.
     *
     * @param maxSize maximum number of characters in a Token instance leadingSeparators. Also the
     * maximum number of characters in the Token value
     * @return Token instance containing any separator string found, and a token value that contains
     * all non-separator, non-whitespace characters after the separator string. If no value is found,
     * due to another separator or end of file, value is empty
     */
    suspend fun token(
        maxSize: Int = 1024
    ) : Token {
        if (!_lastChar) next()
        val leading = StringBuilder(maxSize)
        if (lastChar.isWhitespace()) skipWhitespace()
        var c = lastChar
        val l = lineCount
        val lp = linePosition
        while (!isEndOfFile && separatorChars.contains(c)) {
            leading.append(c)
            c = next()
           when (matchSeparators(leading.toString(), tokenSeparators)) {
               MatchResult.Matching -> {}
               MatchResult.NoMatch,
               MatchResult.Match -> break
           }
        }
        if (c.isWhitespace()) {
            skipWhitespace()
            c = lastChar
        }
        val value = if (tokenValueQuotedString && c == quote) {
            quotedString(maxSize)
        } else
            StringBuilder(maxSize).apply {
                while (!isEndOfFile && !separatorChars.contains(c) && !c.isWhitespace()) {
                    append(c)
                    c = next()
                }
            }.toString()
        return Token(leading.toString(), value, l, lp)
    }

    /**
     * Reads text until one of the separators is found, or end of file. Typical use is to extract
     * unparsed text verbatim until a separator is found.
     * @param separators list of one or more non-empty separator strings.
     * @return a Pair of strings, first is all characters, not including the end separator, found.
     * If end of file is reached, all remaining characters are returned. Second is the separator
     * string matched at the end, if any
     */
    suspend fun nextUntil(
        separators: List<String>,
        maxSize: Int = 1024
    ): Pair<String, String> {
        var separatorBuf = ""
        return Pair (
            StringBuilder(maxSize).apply {
                var c = lastChar
                while (!isEndOfFile && length < maxSize) {
                    if (separatorChars.contains(c)) separatorBuf += c
                    when (matchSeparators(separatorBuf, separators)) {
                        MatchResult.Matching -> {}
                        MatchResult.NoMatch -> separatorBuf = ""
                        MatchResult.Match -> break
                    }
                    c = next()
                }
                if (separatorBuf.isNotEmpty() && endsWith(separatorBuf)) {
                    deleteRange(length - separatorBuf.length, length)
                }
            }.toString(),
            separatorBuf
        )
    }

    enum class MatchResult { Matching, Match, NoMatch }
    private fun matchSeparators(chars: String, separators: List<String>): MatchResult {
        val m = separators.count { it.startsWith(chars) }
        return if (m == 0)
            MatchResult.NoMatch
        else if (m == 1 && separators.contains(chars))
            MatchResult.Match
        else
            MatchResult.Matching
    }

    companion object {
        const val EOL = "\n"
        const val EOL_CHAR = EOL[0]
        const val DEFAULT_BLOCK_SIZE = 4096
    }
}