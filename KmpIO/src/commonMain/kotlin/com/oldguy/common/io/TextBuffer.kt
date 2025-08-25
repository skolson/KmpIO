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
    charset: Charset,
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

    var charset = charset
        private set
    /**
     * While processing text by line, this attribute is the current line count processed
     */
    var lineCount = 1
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
     * used by next to reprocess characters already read once, in the order read.
     */
    class CharFifo() {
        private val list = mutableListOf<Char>()
        val isNotEmpty get() = list.isNotEmpty()

        fun push(chars: String) = list.addAll(chars.toList())

        fun pop(): Char {
            return list.removeFirst()
        }
    }

    private val charFifo = CharFifo()

    private var separatorBuf = ""

    /**
     * The remaining vars are configuration for the various parsing operators. Useful for manual
     * configuration, or for a builder DSL that produces configured TextBuffers
     */

    /**
     * Type of quote characters used in quotedString()
     * Single uses singleQuote
     * Double uses doubleQuote
     * Both looks for either, but does not support both at the same time. Whichever quote character
     * is seen first is the one that must terminate the string.
     */
    enum class QuoteType { Single, Double, Either, None }

    /**
     * Set this for the type of quote characters used in quotedString()
     */
    var quoteType = QuoteType.Either

    /**
     * Character used to enclose quoted strings. See quotedString()
     */
    var quote: Char = '"'
    var singleQuote: Char = '\''

    /**
     * String pattern, if matched in quotedString(), is replaced by quote. If empty, no escaping
     * happens
     */
    var escapedQuote: String = "\\\""
    var escapedSingleQuote: String = "\\'"

    val isQuoteChar get() = when (quoteType) {
        QuoteType.Single -> lastChar == singleQuote
        QuoteType.Double -> lastChar == quote
        QuoteType.Either -> lastChar == quote || lastChar == singleQuote
        QuoteType.None -> false
    }
    /**
     * List of separator character Strings, used in token(). See fun token() for details. Note that
     * contents can be changed at will if one ore more separator Strings are desired only in
     * specific contexts. Changes are used in subsequent calls to next().
     *
     * A private backing field is used to not expose the mutable list.
     */
    private val _tokenSeparators = emptyList<String>().toMutableList()
    var tokenSeparators get() = _tokenSeparators.toList()
        set(value) {
            _tokenSeparators.clear()
            _tokenSeparators.addAll(value)
        }

    /**
     * If true, and a token value starts with a quote character, then use fun quotedString() to read.
     * If false, treat quote like any other character.
     */
    var tokenValueQuotedString = true

    /**
     * If true, whitespace is retained while parsing tokens. This allows whitespace to be included
     * in separators for matching. It also allows Token values to contain white space. Does not affect
     * readLine or quotedString functions which never skip whitespace. Explicit calls to skipWhitespace()
     * will still skip whitespace.
     */
    var retainWhitespace = false

    /**
     * Add a separator, typically for a specific context. If the separator is already in the list,
     * no change is made. If the separator is not in the list, it is added.
     */
    fun addTokenSeparator(separator: String) {
        if (!_tokenSeparators.contains(separator))
            _tokenSeparators.add(separator)
    }

    /**
     * Any subsequent characters read from the TextBuffer will be decoded using the new Charset.
     * Typical usage is using the constructor Charset to read enough text from a TextFile to determine
     * the encoding of the remainder of the TextFile.
     * @param newCharset replaces the constructor Charset
     */
    fun changeCharset(newCharset: Charset) {
        charset = newCharset
    }

    /**
     * Remove a separator, typically when the specific context it was added for is no longer needed.
     */
    fun removeTokenSeparator(separator: String): Boolean =
        _tokenSeparators.remove(separator)

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
        if (count <= 0)
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

    private fun char(char: Char) {
        _lastChar = true
        lastChar = char
        if (lastChar == EOL_CHAR) {
            linePosition = 0
            lineCount++
        } else
            linePosition++
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
        if (charFifo.isNotEmpty) {
            char(charFifo.pop())
            return lastChar
        }
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
        char(s[0])
        if (!peek) buf.position = buf.position + byteCount
        return lastChar
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
                if (c == EOL_CHAR || endOfFile) break
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
        var count = if (lastChar.isWhitespace()) 1 else 0
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
     * See variable "singleQuote" for quote character to look for. defaults to "'"
     * See variable escapedSingleQuote String to match as an escape for singleQuote. If empty, no escape processing happens
     *
     * @param maxSize number of characters to read before returning.
     * @return String containing characters between quote characters. If previous call to next()
     * is not a quote, throw an exception.
     */
    suspend fun quotedString(
        maxSize: Int = 1024
    ): String {
        var c = lastChar
        if (quoteType == QuoteType.None)
            throw IllegalStateException("QuoteType is None, fun quotedString is not usable")
        if (!isQuoteChar)
            throw IllegalStateException("Quoted string must start with $quoteType")
        val q = c
        val esc = if (q == singleQuote) escapedSingleQuote else escapedQuote
        return StringBuilder(maxSize).apply {
            while (true) {
                c = next()
                if (esc.isEmpty() && c == q) break
                if (esc.isNotEmpty()) {
                    var match = 0
                    var temp = ""
                    for (m in esc) {
                        if (c == m) {
                            match++
                            temp += c
                            c = next()
                        } else
                            break
                    }
                    if (match == esc.length)
                        append(q)
                    else {
                        append(temp)
                        if (c == q) break
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
     * either preceded by or followed by all non-separator characters as the token value. Once a second
     * separator is encountered, the token is returned and the buffer is positioned at the beginning
     * of the second separator.
     * @property separator the separator string found. Empty if the special case stopOnWhitespace is true
     * @property value if non-separator character(s) are found before the separator,
     * they are set to this property. Otherwise empty
     * @property quotesFound true if value was from a call to quotedString(). false if not
     * @property line the line number where this token was located. From instance property 'lineCount;
     * @property position the number of the character, one relative, in the current line. From
     * instance property 'linePosition'
     */
    data class Token(
        val separator: String,
        val value: String,
        val quotesFound: Boolean,
        val line: Int,
        val position: Int
    )

    /**
     * Reads next token of text, up to maxSize characters.
     *
     * A Token instance is zero or more non-separator characters, followed by a separator.
     *
     * So if the buffer is positioned at a separator, the token returned will contain the separator
     * with zero non-separator characters.
     * If the buffer is positioned at a non-separator character, the token returned will contain
     * all characters up to the first matching separator.
     *
     * An example of a partial tokenSeparators list for parsing an XML document would include the following:
     * tokenSeparators = listOf("<", ">", "/>", "<?", "?>", "<!--", "--!>"). Note that for xml, "="
     * is only a separator during node tags for parsing attributes. So it could be added for parsing
     * attributes, and removed at end of tag. See the addTokenSeparator and removeTokenSeparator functions.
     *
     * @param stopOnWhitespace set this true if value parsing encountering a whitespace character
     * should stop parsing and return text read so far. This is a special case separator as multiple
     * characters qualify as whitespace.
     * @param maxSize maximum number of characters in a Token instance leadingSeparators. Also the
     * maximum number of characters in the Token value
     * @return Token instance containing any non-separator characters found, and the separator string found
     */
    suspend fun token(
        stopOnWhitespace: Boolean = false,
        maxSize: Int = 1024
    ) : Token {
        if (!_lastChar) next()
        val match = nextUntil(tokenSeparators, stopOnWhitespace, maxSize)
        when(match.result) {
            MatchResult.NoMatch ->
                return Token(
                    "",
                    match.chars,
                    match.quotesFound,
                    lineCount,
                    linePosition)
            MatchResult.Match ->
                return Token(
                    match.separator,
                    match.chars,
                    match.quotesFound,
                    lineCount,
                    linePosition
                )
            MatchResult.Matching -> {
                // could only be returned if maxSize was too small or end of file during a separator match
                return Token(
                    "",
                    match.chars,
                    match.quotesFound,
                    lineCount,
                    linePosition)
            }
        }
    }

    enum class MatchResult { Matching, Match, NoMatch }
    data class Match(
        val result: MatchResult,
        val separator: String,
        val chars: String,
        val quotesFound: Boolean
    )

    /**
     * Using the specified separators, reads characters until one of the separators is found, or the
     * size limit is reached. Returns result with the separator found, and the characters before the
     * separator.
     * @param separators list of one or more non-empty separator strings.
     * @param stopOnWhitespace set this true if value parsing (not separators) encountering a
     * whitespace character should stop parsing. Whitespace as a special case of separator. If false,
     * characters are captured until the next separator, including whitespace
     * @param maxSize maximum number of characters in a Match instance.
     * @return a Match instance containing the result of the match, the separator string matched (if any),
     * and the characters before the separator.
     * If end of file or the size limit is reached first, all read characters are returned,
     * with a NoMatch and empty separator.
     */
    suspend fun nextUntil(
        separators: List<String>,
        stopOnWhitespace: Boolean = false,
        maxSize: Int = 1024
    ): Match {
        separatorBuf = lastChar.toString()
        StringBuilder(maxSize).apply {
            var c = lastChar
            while (!isEndOfFile && length < maxSize) {
                when (separators.count { it.startsWith(separatorBuf) }) {
                    0 -> {
                        if (separatorBuf.length > 1)
                            append(separatorBuf.substring(0, separatorBuf.length - 1))
                        if (c.isWhitespace() && stopOnWhitespace) {
                            skipWhitespace()
                            return Match(
                                MatchResult.Match,
                                separatorBuf,
                                toString(),
                                false
                            )
                        }
                        if (tokenValueQuotedString && isQuoteChar) {
                            append(quotedString())
                            return Match(
                                MatchResult.NoMatch,
                                "",
                                toString(),
                                true
                            )
                        } else {
                            append(c)
                            c = next()
                        }
                        separatorBuf = lastChar.toString()
                        MatchResult.NoMatch
                    }
                    1 -> if (separators.contains(separatorBuf)) {
                        next()
                        return Match(
                            MatchResult.Match,
                            separatorBuf,
                            toString(),
                            false
                        )
                        } else {
                                c = next()
                                separatorBuf += c
                                MatchResult.Matching
                        }
                    else -> {
                        c = next()
                        if (separators.count { it.startsWith(separatorBuf + c) } > 0) {
                            separatorBuf += c
                            MatchResult.Matching
                        } else
                            return Match(
                                MatchResult.Match,
                                separatorBuf,
                                toString(),
                                false
                            )
                    }
                }
            }
            return Match(
                MatchResult.NoMatch,
                "",
                toString(),
                false
            )
        }
    }

    fun reset() {
        buf.clear()
        endOfFile = false
        noMoreSource = false
        readLock = false
        remainder = ByteArray(charset.bytesPerChar.last)
        partial = ByteArray(0)
        lineCount = 1
        linePosition = 0
        bytesRead = 0
        _lastChar = false
        separatorBuf = ""
    }

    companion object {
        const val EOL = "\n"
        const val EOL_CHAR = EOL[0]
        const val DEFAULT_BLOCK_SIZE = 4096
    }
}