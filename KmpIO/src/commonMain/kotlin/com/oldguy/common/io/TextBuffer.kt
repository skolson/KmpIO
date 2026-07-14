package com.oldguy.common.io

import com.oldguy.common.io.charsets.Charset
import com.oldguy.common.io.charsets.MultiByteDecodeException

/**
 * Platform-neutral text buffering for simple text file read (or other source) operations, using blocks of
 * bytes as input. Various access methods are provided for processing decoded text.
 *
 * Source lambda supplies all bytes, in order. TextBuffer handles decoding using the specified charset,
 * including handling multibyte character sets in the edge case where a partial character is found
 * at the end of a ByteArray. If the source lambda indicates no more data by returning 0 bytes, and TextBuffer
 * determines there is an incomplete character at the end of the file, it will throw MultiByteDecodeException.
 *
 * @param charset specifies how to decode the incoming bytes from the underlying file.
 * @param blockSizeArg specifies number of bytes to request from source lambda on each call. Value is
 * rounded up to a multiple of the maximum number of bytes per character for the specified Charset.
 * @param source function should perform a read operation up to count bytes,
 * into the specified buffer which is a ByteArray. It should return the number of bytes read, or 0
 * to indicate end of file. ByteArray can be any size and does not have to end on a line break. Can
 * also end in the middle of a multibyte character, see above comments.
 */
open class TextBuffer(
    charset: Charset,
    blockSizeArg: Int = DEFAULT_BLOCK_SIZE,
    val source: (suspend (
        buffer: ByteArray,
        count: Int
    ) -> UInt )
) {
    /**
     * A Token instance is one or more leading separator characters (a separator string)
     * either preceded by or followed by all non-separator characters as the token value. Once a second
     * separator is encountered, the token is returned and the buffer is positioned at the beginning
     * of the second separator.
     * @property separator the separator string found. Empty if the special case stopOnWhitespace is true
     * @property value if non-separator character(s) are found before the separator,
     * they are set to this property. Otherwise empty
     * @property quotesFound true if value was from a call to quotedString(). false if not
     * @property line the line number where this token was located. From instance property lineCount
     * @property position the number of the character, one relative, in the current line. From
     * instance property 'linePosition'
     */
    data class Token(
        val separator: String,
        val value: String,
        val quotesFound: Boolean,
        val line: Int,
        val position: Int
    ) {
        val isBlank get() = value.isBlank() && separator.isBlank()
    }

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
     * Count of the number of bytes decoded into chars from source. Difference between bytesRead and bytesParsed is
     * the number of bytes that were read but not yet not decoded. Useful for getting the source file
     * position at any point during parsing.
     */
    val bytesDecoded get() = bytesRead - buf.remaining

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
     * Set by next(). If the last character read was whitespace, and the current character being read is
     * not, then this is true, otherwise false.
     */
    var transitionFromWhitespace = false
        private set

    /**
     * used by next to reprocess characters already read once, in the order read.
     */
    class CharFifo {
        private val list = mutableListOf<Char>()
        val isNotEmpty get() = list.isNotEmpty()
        val first get() = list.first()
        fun push(chars: String) = list.addAll(chars.toList())

        fun pop(): Char {
            return list.removeFirst()
        }
    }

    private val charFifo = CharFifo()

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
     * List of separator character Strings, used in nextUntil() which is called by token().
     * See fun nextUntil() for details. Note that
     * contents can be changed at will during parsing if one or more separator Strings are desired only in
     * specific contexts. Changes are used in subsequent calls to token()/nextUntil().
     *
     * Separators may not contain whitespace. If any are present in a set, an exception is thrown
     * and the field is unchanged
     *
     * A private backing field is used to not expose the mutable list.
     */
    private val _tokenSeparators = emptyList<String>().toMutableList()
    var tokenSeparators get() = _tokenSeparators.toList()
        set(value) {
            if (value.any { it.any { c -> c.isWhitespace() } })
                throw IllegalArgumentException("Separators may not contain whitespace")
            _tokenSeparators.clear()
            _tokenSeparators.addAll(value)
            changeSeparators()
        }

    /**
     * In grammars like ISO 32000 PDF some separators, like "obj" and "endobj", need to optionally specify that
     * they be bounded by whitespace. This prevents, in the PDF grammar for example, "obj" from
     * being identified as a separator in a String link this: "/Testobj". Testobj a valid PDF Name
     * value and "obj" at the end does not signify a new PDF Object.
     *
     * Note that if the same separator is used in both lists, tokenSeparatorsRequireWhitespace supersedes
     * tokenSeparators - the duplicated separator is treated as if it only came from
     * tokenSeparatorsRequireWhitespace.
     */
    private var _tokenSeparatorsRequireWhitespace = emptyList<String>().toMutableList()
    var tokenSeparatorsRequireWhitespace get() = _tokenSeparatorsRequireWhitespace.toList()
        set(value) {
            if (value.any { it.any { c -> c.isWhitespace() } })
                throw IllegalArgumentException("Separators may not contain whitespace. Logic in nextUntil() ensures they are delimited by whitespace before matching")
            _tokenSeparatorsRequireWhitespace.clear()
            _tokenSeparatorsRequireWhitespace.addAll(value)
            changeSeparators()
        }

    private var allSeparators = emptyMap<String, Boolean>().toMutableMap()

    private fun changeSeparators() {
        allSeparators.clear()
        tokenSeparators
            .filter { !tokenSeparatorsRequireWhitespace.contains(it)}
            .forEach { allSeparators[it] = false }
        tokenSeparatorsRequireWhitespace
            .forEach { allSeparators[it] = true }
    }

    /**
     * If true, and a token value starts with a quote character, then use fun quotedString() to read.
     * If false, treat quote like any other character.
     */
    var tokenValueQuotedString = true

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
     * Add a separator, typically for a specific context. If the separator is already in the list,
     * no change is made. If the separator is not in the list, it is added.
     * @param separator separator string to add.
     * @param requiresWhitespace true if the separator should be added to tokenSeparatorsRequireWhitespace,
     * false if to tokenSeparators.
     */
    fun addTokenSeparator(separator: String, requiresWhitespace: Boolean = false) {
        if (requiresWhitespace) {
            if (!_tokenSeparatorsRequireWhitespace.contains(separator))
                _tokenSeparatorsRequireWhitespace.add(separator)
        } else {
            if (!_tokenSeparators.contains(separator))
                _tokenSeparators.add(separator)
        }
    }

    /**
     * Remove a separator, typically when the specific context it was added for is no longer needed.
     * @param separator separator string to add.
     * @param requiresWhitespace true if the separator should be removed from tokenSeparatorsRequireWhitespace,
     * false if from tokenSeparators.
     */
    fun removeTokenSeparator(separator: String, requiresWhitespace: Boolean = false): Boolean =
        if (requiresWhitespace) {
            _tokenSeparatorsRequireWhitespace.remove(separator).apply {
                if (this) changeSeparators()
            }
        } else {
            _tokenSeparators.remove(separator).apply {
                if (this) changeSeparators()
            }
        }

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
     * decoding of multibyte character sets, each block saves any incomplete character bytes at the end
     * of the block for processing during the next call to nextBlock().
     */
    suspend fun nextBlock(): String {
        useSource()
        return if (buf.remaining == 0) ""
        else
            charset.decode(buf.getBytes())
    }

    private fun char(char: Char): Char {
        _lastChar = true
        transitionFromWhitespace = lastChar.isWhitespace() && !char.isWhitespace()
        lastChar = char
        if (lastChar == EOL_CHAR) {
            linePosition = 0
            lineCount++
        } else
            linePosition++
        return lastChar
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
            return if (peek)
                charFifo.first
            else
                char(charFifo.pop())
        }
        if (!isEndOfFile && buf.remaining < charset.bytesPerChar.last)
            useSource()
        _lastChar = true
        if (buf.remaining == 0) {
            endOfFile = true
            lineCount++
            return Char(0)
        }
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
        return if (peek)
            s[0]
        else {
            buf.position += byteCount
            char(s[0])
        }
    }

    /**
     * Use this to read raw bytes one by one.
     *
     * the most recent character read is available in lastChar
     *
     * @param peek true if byte should be returned without advancing to the next character.
     * @return one byte. if isEndOfFile is true, returns code 0x00 character.
     */
    suspend fun nextByte(peek: Boolean = false): Byte {
        if (!isEndOfFile && buf.remaining == 0)
            useSource()
        if (buf.remaining == 0) {
            endOfFile = true
            return 0
        }
        return if (peek) buf.get(buf.position) else buf.byte
    }

    /**
     * Use this to read raw bytes, for the specified count, or until a delimiter is reached.
     *
     * @param count number of bytes desired
     * @param delimiter null if no delimiter should stop reads. if not null and a delimiter is encountered
     * before count bytes are retrieved, retrieval stops.
     * @return ByteArray with the result. if isEndOfFile is already true, result is empty. Otherwise,
     * reads bytes until count retrieved or end of file reached or a delimiter is encountered.
     * Result is sized with to the number of bytes retrieved.
     */
    suspend fun nextBytes(count: Int, delimiter: Byte? = null): ByteArray {
        if (endOfFile) return ByteArray(0)
        var retrieved = 0
        val result = ByteArray(count)
        while (retrieved < count && !isEndOfFile) {
            val b = nextByte()
            if (!isEndOfFile) {
                result[retrieved] = b
                if (delimiter != null && b == delimiter) break
                retrieved++
            }
        }
        if (retrieved == 0) return ByteArray(0)
        _lastChar = false
        lastChar = Char(0)
        return if (retrieved < count) result.copyOf(retrieved) else result
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
                if (c == EOL_CHAR || endOfFile)
                    break
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
                if (line.isEmpty() && isEndOfFile) break
                if (!action(lineCount, line))
                    break
                if (isEndOfFile) break
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
        while (!isEndOfFile && next(true).isWhitespace()) {
            count++
            next()
        }
        return count
    }

    /**
     * Verifies that the current position is a quote character. If it is, retrieves characters and
     * builds a String until the next quote character is seen. If an escape is specified for an
     * enclose quote, handle the escape as well. If end of input is reached before the closing quote,
     * all characters since the last quote are returned.
     *
     * See variable "quote" for quote character to look for. defaults to "
     * See variable escapedQuote String to match as an escape for quote. If empty, no escape processing happens
     * See variable "singleQuote" for quote character to look for. defaults to '
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
     * tokenSeparators = listOf("<", ">", "/>", "<?", "?>", "<!--", "--!>"). Note that for XML, "="
     * is only a separator during node tags for parsing attributes. So it could be added for parsing
     * attributes, and removed at end of tag. See the addTokenSeparator and removeTokenSeparator functions.
     *
     * @param stopOnWhitespace set this true if value parsing encountering a whitespace character
     * should stop parsing and return text read so far. This is a special case separator as multiple
     * characters qualify as whitespace.
     * @param maxSize maximum number of characters in a Token instance leadingSeparators. Also, the
     * maximum number of characters in the Token value
     * @return Token instance containing any non-separator characters found, and the separator string found
     */
    suspend fun token(
        stopOnWhitespace: Boolean = false,
        maxSize: Int = 1024
    ) : Token {
        if (endOfFile)
            return Token("", "", false, lineCount, linePosition)
        val match = nextUntil(allSeparators, stopOnWhitespace, maxSize)
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

    private fun testMatch(
        separators: List<String>,
        sep: String
    ): MatchResult {
        return if (endOfFile) {
            if (separators.contains(sep)) {
                MatchResult.Match
            } else
                MatchResult.NoMatch
        } else {
            val count = separators.count { it.startsWith(sep) }
            when {
                count > 1 -> MatchResult.Matching
                count == 1 -> {
                    if (separators.contains(sep))
                        MatchResult.Match
                    else
                        MatchResult.Matching
                }
                else -> MatchResult.NoMatch
            }
        }
    }
    /**
     * Using the specified separators, reads characters until one of the separators is found, or the
     * size limit is reached. Returns result with the separator found, and the characters before the
     * separator. Separators cannot contain whitespace. But separators can be defined that require
     * delimiting by whitespace. See Lists; {@link #tokenSeparators} and
     * {@link #tokenSeparatorsRequireWhitespace}.
     *
     * Since separators are multiple characters, and there can be multiple separators that
     * start with the same substring, match only occurs when only one of the separators is a complete
     * match. A separatorBuf tracks characters encountered that might be a separator, until a
     * complete separator is found. Last character read is next one after the end of the separator.
     * @param separators Map of one or more non-empty separator strings. Each separator string is the
     * key in the map. The Boolean value is true if the separator string requires leading whitespace
     * to match.
     * @param stopOnWhitespace set this true if value parsing (not separators) encountering a
     * whitespace character should stop parsing. Whitespace as a special case of separator. If false,
     * characters are captured until the next separator, including whitespace
     * @param maxSize maximum number of characters in a Match instance. If this is reached before
     * a match is found, all read characters are returned.
     * @return a Match instance containing the result of the match, the separator string matched (if any),
     * and the characters before the separator.
     * If end of file or the size limit is reached first, all read characters are returned,
     * with a NoMatch and empty separator.
     */
    suspend fun nextUntil(
        separators: Map<String, Boolean>,
        stopOnWhitespace: Boolean = false,
        maxSize: Int = 1024
    ): Match {
        var currentSeparator = next().toString()
        val content = StringBuilder(maxSize)
        var status = MatchResult.NoMatch
        var quotesFound = false
        var candidateSeparators = emptyList<String>()

        while (!isEndOfFile && content.length < maxSize && status != MatchResult.Match) {
            if (currentSeparator.length == 1) {
                candidateSeparators = if (transitionFromWhitespace) {
                    separators
                        .map { it.key }
                        .filter { it.startsWith(currentSeparator) }
                } else {
                    separators
                        .filter { !it.value }
                        .map { it.key }
                        .filter { it.startsWith(currentSeparator) }
                }
            }
            status = testMatch(candidateSeparators, currentSeparator)
            when {
                status == MatchResult.Match -> {
                    break
                }
                status == MatchResult.Matching -> {
                    status = testMatch(
                        candidateSeparators,
                        currentSeparator + next(true)
                    )
                    when (status) {
                        MatchResult.Matching -> {
                            currentSeparator += next()
                        }
                        MatchResult.Match -> {
                            currentSeparator += next()
                            break
                        }
                        MatchResult.NoMatch -> {
                            if (candidateSeparators.contains(currentSeparator)) {
                                status = MatchResult.Match
                            } else {
                                content.append(currentSeparator)
                                currentSeparator = next().toString()
                            }
                        }
                    }
                }
                lastChar.isWhitespace() && stopOnWhitespace -> {
                    skipWhitespace()
                    currentSeparator = lastChar.toString()
                    status = MatchResult.Match
                }
                tokenValueQuotedString && isQuoteChar -> {
                    content.append(quotedString())
                    quotesFound = true
                    currentSeparator = lastChar.toString()
                 }
                else -> {
                    content.append(lastChar)
                    currentSeparator = next().toString()
                }
            }
        }

        return Match(
            status,
            currentSeparator,
            content.toString(),
            quotesFound
        )
    }

    fun reset() {
        buf.clear()
        buf.limit = 0
        endOfFile = false
        noMoreSource = false
        readLock = false
        remainder = ByteArray(charset.bytesPerChar.last)
        partial = ByteArray(0)
        lineCount = 1
        linePosition = 0
        bytesRead = 0
        _lastChar = false
    }

    companion object {
        const val EOL = "\n"
        const val EOL_CHAR = EOL[0]
        const val DEFAULT_BLOCK_SIZE = 4096
    }
}