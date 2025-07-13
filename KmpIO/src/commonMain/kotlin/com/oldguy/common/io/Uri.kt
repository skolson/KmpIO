package com.oldguy.common.io

/**
 * From https://github.com/chRyNaN/uri
 *
 * The design for this class is to parse the constructor argument into all the fields that are part
 * of the Uri RFC as described below.  A parse failure throws an IllegalArgumentException.
 * There are some companion builder functions that provide alternate ways of constructing a Uri.
 *
 * A [Uri] is a sequence of characters the uniquely identifies a particular resource, for example, a URL is a subset of
 * a [Uri].
 *
 * From [Section 3 of RFC 3986](https://datatracker.ietf.org/doc/html/rfc3986#section-3):
 *
 * > The generic URI syntax consists of a hierarchical sequence of
 * > components referred to as the scheme, authority, path, query, and
 * > fragment.
 * ```
 *           URI         = scheme ":" hier-part [ "?" query ] [ "#" fragment ]
 *
 *           hier-part   = "//" authority path-abempty
 *                       / path-absolute
 *                       / path-rootless
 *                       / path-empty
 * ```
 * > The scheme and path components are required, though the path may be
 * > empty (no characters).  When authority is present, the path must
 * > either be empty or begin with a slash ("/") character.  When
 * > authority is not present, the path cannot begin with two slash
 * > characters ("//").  These restrictions result in five different ABNF
 * > rules for a path (Section 3.3), only one of which will match any
 * > given URI reference.
 * > The following are two example URIs and their component parts:
 * ```
 *           foo://example.com:8042/over/there?name=ferret#nose
 *           \_/   \______________/\_________/ \_________/ \__/
 *            |           |            |            |        |
 *         scheme     authority       path        query   fragment
 *            |   _____________________|__
 *           / \ /                        \
 *           urn:example:animal:ferret:nose
 * ```
 *
 * @property [scheme] A [Uri] Scheme is the first portion of the [Uri] preceding the colon (':') character. It is
 * required for a valid URI, but can be omitted when the URI is actually a URI Reference (relative URI value). Common
 * URI Schemes include "http", "https", "ftp", "file", etc. The Scheme component may consist of any combination of
 * letters, digits, plus ('+'), period ('.'), or hyphen ('-'), but must begin with a letter to be considered valid.
 * This property will return the Scheme component of this [Uri] without the following colon (':'). An empty or blank
 * value will be represented as "".
 *
 * @property [authority] A [Uri] Authority is the portion of the URI after the [scheme] and the "://" sequence. It is
 * optional for the [Uri]. The Authority consists of three sub-parts of the URI: The [userInfo], [host], and [port].
 * This property will return the Authority component of this [Uri] without the preceding "://" sequence, in the
 * following form: `[userInfo]@[host]:[port]`. Note that each of the subcomponents are optional as well but if the
 * [userInfo] or [port] are included, then the [host] must be included.
 *
 * @property [userInfo] A [Uri] UserInfo subcomponent is a portion of the [authority] component. It is an optional
 * subcomponent that may consist of a username and an optional password preceded by a colon (':') and followed by a '@'
 * character. Note that if the [authority] is not present, then this will always return null. This is because this is a
 * subcomponent of the [authority] component. Even though some [path]s may have [userInfo] like components, it will not
 * be considered a [userInfo] component and this will return null. For example, for the following [Uri], the [userInfo]
 * property should return null: mailto:John.Doe@example.com. This property will return the UserInfo subcomponent
 * without the following '@' character, and in the following form: `username:password`.
 *
 * @property [host] A [Uri] Host subcomponent is a portion of the [authority] component. It is required if the
 * [authority] is present. It consists of either a registered name, such as a hostname, or an IP Address. IPv4
 * addresses must be in a dot-decimal notation, and IPv6 addresses must be enclosed in brackets. This property will
 * return the Host subcomponent without the following colon (':') character.
 *
 * @property [port] A [Uri] Port subcomponent is a portion of the [authority] component. It is an optional subcomponent
 * that comes after the [host] and a colon (':') character. It typically represents the [Int] port of the server to
 * use. This property will return the Port subcomponent without the preceding colon (':') character or the following
 * '/' character, in an [Int] form.
 *
 * @property [path] The following is taken from the Wikipedia definition of a [Uri] Path component (their definition is
 * well done so quoting instead of attempting to replicate):
 * > A path component, consisting of a sequence of path segments separated by a slash (/). A path is always defined
 * > for a URI, though the defined path may be empty (zero length). A segment may also be empty, resulting in two
 * > consecutive slashes (//) in the path component. A path component may resemble or map exactly to a file system
 * > path, but does not always imply a relation to one. If an authority component is present, then the path component
 * > must either be empty or begin with a slash (/). If an authority component is absent, then the path cannot begin
 * > with an empty segment, that is with two slashes (//), as the following characters would be interpreted as an
 * > authority component.[12] The final segment of the path may be referred to as a 'slug'.
 *
 * This property will return the Path component of the [Uri] including any slash ('/') characters. If there is an
 * [authority] then this [path] should begin with a slash ('/') character.
 *
 * @property [query] A [Uri] Query component is the portion of the [Uri] after the [path] component. It is an optional
 * component and is preceded by a question mark ('?'). It is a [String] of non-hierarchical data. Though its syntax is
 * not well-defined, by convention it is often a sequence of attribute-value pairs separated by a delimiter, typically
 * the '&' character. This property will return the Query component of the [Uri] without the preceding question mark
 * ('?') character and including any delimiters between the possible attribute-value pairs.
 *
 * @property [fragment] A [Uri] Fragment component is the portion of the URI after the [query] component. It is an
 * optional component that is preceded by a hash ('#') character. A Fragment contains a fragment identifier which links
 * to a secondary resource, such as a section header in an article. When the primary source is an HTML document,
 * typically the Fragment is an HTML identifier (id) value of a specific HTML element. This property will return this
 * Fragment component of the [Uri] without the preceding hash ('#') character.
 *
 * @property [uriString] Represents this [Uri] as a [String] value. This should be a [String] value consisting of
 * all the parts of the [Uri].
 *
 * @see [URI Specification RFC 3986](https://datatracker.ietf.org/doc/html/rfc3986)
 * @see [RFC 3305](https://datatracker.ietf.org/doc/html/rfc3305)
 * @see [URI Documentation](https://en.wikipedia.org/wiki/Uniform_Resource_Identifier)
 * @see [Reference Library](https://github.com/chRyNaN/uri)
 */
class Uri(uri: String) {

    /**
     * This is the string that was used to create this [Uri].
     */
    var uriString = uri.trim()
        private set
    /**
     * A [Uri] Scheme is the first portion of the [Uri] preceding the color (':') character. It is required for the
     * [Uri] to be considered valid. Common URI Schemes include "http", "https", "ftp", "file", etc. The Scheme
     * component may consist of any combination of letters, digits, plus ('+'), period ('.'), or hyphen ('-'), but must
     * begin with a letter to be considered valid.
     *
     * This property will return the Scheme component of this [Uri] without the following color (':').
     */
    val scheme: String

    /**
     * A [Uri] Authority is the portion of the URI after the [scheme] and the "://" sequence. It is optional for the
     * [Uri]. The Authority consists of three sub-parts of the URI: The [userInfo], [host], and [port].
     *
     * This property will return the Authority component of this [Uri] without the preceding "://" sequence, in the
     * following form: [userInfo]@[host]:[port]. Note that each of the sub components are optional as well but if the
     * [userInfo] or [port] are included, then the [host] must be included.
     */
    val authority: String

    /**
     * A [Uri] UserInfo subcomponent is a portion of the [authority] component. It is an optional subcomponent that may
     * consist of a user name and an optional password preceded by a colon (':') and followed by a '@' character.
     *
     * Note that if the [authority] is not present, then this will always return null. This is because this is a
     * subcomponent of the [authority] component. Even though some [path]s may have [userInfo] like components, it will
     * not be considered a [userInfo] component and this will return null. For example, for the following [Uri], the
     * [userInfo] property should return null: mailto:John.Doe@example.com
     *
     * This property will return the UserInfo subcomponent without the following '@' character, and in the following
     * form: username:password.
     */
    val userInfo: String

    /**
     * A [Uri] Host subcomponent is a portion of the [authority] component. It is required if the [authority] is
     * present. It consists of either a registered name, such as a hostname, or an IP Address. IPv4 addresses must be
     * in a dot-decimal notation, and IPv6 addresses must be enclosed in brackets.
     *
     * This property will return the Host subcomponent without the following colon (':') character.
     */
    val host: String

    /**
     * A [Uri] Port subcomponent is a portion of the [authority] component. It is an optional subcomponent that comes
     * after the [host] and a colon (':') character. It typically represents the [Int] port of the server to use.
     *
     * This property will return the Port subcomponent without the preceding colon (':') character or the following
     * '/' character, in an [Int] form.
     */
    val port: Int?

    /**
     * The following is taken from the Wikipedia definition of a [Uri] Path component (their definition is well done so
     * quoting instead of attempting to replicate):
     *
     * > A path component, consisting of a sequence of path segments separated by a slash (/). A path is always defined
     * for a URI, though the defined path may be empty (zero length). A segment may also be empty, resulting in two
     * consecutive slashes (//) in the path component. A path component may resemble or map exactly to a file system
     * path, but does not always imply a relation to one. If an authority component is present, then the path component
     * must either be empty or begin with a slash (/). If an authority component is absent, then the path cannot begin
     * with an empty segment, that is with two slashes (//), as the following characters would be interpreted as an
     * authority component.[12] The final segment of the path may be referred to as a 'slug'.
     * <a href="https://en.wikipedia.org/wiki/Uniform_Resource_Identifier">Wikipedia URI Source</a>
     *
     * This property will return the Path component of the [Uri] including any slash ('/') characters. If there is an
     * [authority] then this [path] should begin with a slash ('/') character.
     */
    val path: String

    /**
     * A [Uri] Query component is the portion of the [Uri] after the [path] component. It is an optional component and
     * is preceded by a question mark ('?'). It is a [String] of non-hierarchical data. Though its syntax is not well
     * defined, by convention it is often a sequence of attribute-value pairs separated by a delimiter, typically the
     * '&' character.
     *
     * This property will return the Query component of the [Uri] without the preceding question mark ('?') character
     * and including any delimiters between the possible attribute-value pairs.
     */
    public val query: String

    /**
     * A [Uri] Fragment component is the portion of the URI after the [query] component. It is an optional component
     * that is preceded by a hash ('#') character. A Fragment contains a fragment identifier which links to a secondary
     * resource, such as a section header in an article. When the primary source is an HTML document, typically the
     * Fragment is an HTML identifier (id) value of a specific HTML element.
     *
     * This property will return this Fragment component of the [Uri] without the preceding hash ('#') character.
     */
    public val fragment: String

    /**
     * A [Uri] Scheme Specific Part is the portion of the [Uri] after the [scheme] and the colon (':') character that
     * follows the [scheme]. It is the remainder of the [Uri] after "[scheme]:". It consists of the [authority], [path],
     * [query], and [fragment] components.
     *
     * This property will return the Scheme Specific Part of this [Uri].
     */
    val schemeSpecificPart: String
        get() = buildString {
            if (authority.isNotEmpty()) {
                append("//$authority")
            }

            append(path)

            if (query.isNotEmpty()) {
                append("?$query")
            }

            if (fragment.isNotEmpty()) {
                append("#$fragment")
            }
        }

    private var index = 0
    private var authorityIndex = 0

    /**
     * Parse the string from the constructor. Any errors throw an IllegalArgumentException.
     */
    init {
        uriString.forEach {
            if (it.isWhitespace())
                throw IllegalArgumentException("URI cannot contain unencoded whitespace.")
        }
        scheme = parseScheme()
        authority = parseAuthority()
        userInfo = if (authority.isEmpty()) "" else parseUserInformation()
        host = parseHost()
        port = parsePort()
        path = parsePath()
        query = parseQuery()
        fragment = parseFragment()
    }

    class Builder() {
        private var scheme = ""
        private var authority = ""
        private var user = ""
        private var password = ""
        private var host = ""
        private var port: Int? = null
        private var path = ""
        private var query = ""
        private var parms = listOf<Pair<String, String>>()
        private var fragment = ""

        fun scheme(scheme: String) = apply { this.scheme = scheme }
        fun authority(authority: String) = apply { this.authority = authority }
        fun userInfo(user: String, password: String) = apply {
            this.user = user
            this.password = password
        }
        fun host(host: String) = apply { this.host = host }
        fun port(port: Int?) = apply { this.port = port }
        fun path(path: String) = apply { this.path = path }
        fun query(query: String) = apply { this.query = query }
        fun parms(parms: List<Pair<String, String>>) = apply { this.parms = parms }
        fun fragment(fragment: String) = apply { this.fragment = fragment }
        fun build(): Uri {
            return Uri(
                buildString {
                    append(scheme)
                    if (scheme.isNotEmpty()) append("://")
                    if (authority.isEmpty()) {
                        append(user)
                        if (password.isNotEmpty()) append(":$password@")
                        append(host)
                        if (port != null) append(":$port")
                    } else {
                        append(authority)
                    }
                    append(path)
                    if (query.isEmpty() && parms.isNotEmpty()) {
                        append("?")
                        append(parms.joinToString("&") { "${it.first}=${it.second}" })
                    } else if (query.isNotEmpty()) {
                        append("?$query")
                    }
                    if (fragment.isNotEmpty()) {
                        append("#")
                        append(fragment)
                    }
                }
            )
        }
    }

    /**
     * Parse the string for the scheme portion, if there is one.
     */
    private fun parseScheme(): String {

        var schemeEndIndex = -1
        var pathStartIndex = -1

        var i = 0

        while (i < uriString.length) {
            when(uriString[i]) {
                ':' -> {
                    schemeEndIndex = i
                    break
                }
                '/' -> {
                    pathStartIndex = i
                    break
                }
            }
            i++
        }

        val scheme = when {
            schemeEndIndex != -1 && pathStartIndex != -1 && pathStartIndex < schemeEndIndex -> ""
            schemeEndIndex == -1 -> ""
            else -> {
                index = schemeEndIndex
                uriString.substring(startIndex = 0, endIndex = schemeEndIndex)
            }
        }
        return scheme.lowercase()
    }

    private fun parseAuthority(): String {
        uriString.apply {
            val startIndex = index
            val startChar = this[startIndex]
            val authorityStartIndex = when {
                startChar == ':' && startIndex + 2 < length && this[startIndex + 1] == '/' && this[startIndex + 2] == '/' -> startIndex + 3
                startChar == '/' && startIndex + 1 < length && this[startIndex + 1] == '/' -> startIndex + 2
                else -> return ""
            }

            if (authorityStartIndex == -1 || authorityStartIndex > length)
                return ""

            var authorityEndIndex = length
            var i = authorityStartIndex
            while (i < length) {
                when(this[i]) {
                    '/' -> {
                        authorityEndIndex = i
                        break
                    }
                    '?' -> {
                        authorityEndIndex = i
                        break
                    }
                    '#' -> {
                        authorityEndIndex = i
                        break
                    }
                }
                i++
            }

            index = authorityEndIndex
            return substring(startIndex = authorityStartIndex, endIndex = authorityEndIndex)
        }
    }

    private fun parseUserInformation(): String {
        val userInfoEndIndex = authority.indexOf('@')
        if (userInfoEndIndex == -1) {
            return ""
        }
        authorityIndex = userInfoEndIndex + 1
        return authority.substring(startIndex = 0, endIndex = userInfoEndIndex)
    }

    private fun parseHost(): String {
        var hostStartIndex = authority.indexOf('@')
        if (hostStartIndex < 0)
            hostStartIndex = 0
        else
            hostStartIndex++
        var hostEndIndex = authority.length

        var inBrackets = false
        var i = hostStartIndex
        while (i < authority.length) {
            when(authority[i]) {
                '[' -> {
                    inBrackets = true
                }
                ']' -> {
                    inBrackets = false
                }
                ':' -> {
                    if (!inBrackets) {
                        hostEndIndex = i
                        break
                    }
                }
                '/' -> {
                    if (!inBrackets) {
                        hostEndIndex = i
                        break
                    }
                }
            }
            i++
        }
        return authority.substring(startIndex = hostStartIndex, endIndex = hostEndIndex).lowercase()
    }

    private fun parsePort(): Int? {
        val startIndex = authority.indexOf(':', authorityIndex)
        if (startIndex < 0) return null
        val portStartIndex = startIndex + 1
        var portEndIndex = authority.length

        var i = portStartIndex
        while (i < authority.length) {
            when (authority[i]) {
                '/' -> {
                    portEndIndex = i
                    break
                }
            }
            i++
        }

        val port = authority.substring(startIndex = portStartIndex, endIndex = portEndIndex)
        return if (port.isEmpty())
            null
        else
            port.toIntOrNull()
    }

    private fun parsePath(): String
    {
        if (index >= uriString.length) return ""
        var pathEndIndex = uriString.length
        if (uriString[index] == ':') index++
        var i = index
        while (i < uriString.length) {
            when(uriString[i]) {
                '?' -> {
                    pathEndIndex = i
                    break
                }
                '#' -> {
                    pathEndIndex = i
                    break
                }
            }
            i++
        }

        val path = uriString.substring(startIndex = index, endIndex = pathEndIndex)

        if (authority.isNotEmpty()) {
            if (path.isNotEmpty() && path.first() != '/') {
                throw IllegalArgumentException("A URI value with an authority must either have an empty path or the path must begin with a forward slash character.")
            }
        } else {
            if (path.length >= 2 && path[0] == '/' && path[1] == '/') {
                throw IllegalArgumentException("A URI value without an authority must not begin with two forward slash characters.")
            }
        }
        index = pathEndIndex
        return path
    }

    private fun parseQuery(): String {
        var startIndex = uriString.indexOf('?', startIndex = index)
        if (startIndex == -1) return ""
        startIndex++
        var queryEndIndex = uriString.length

        var i = startIndex
        while (i < uriString.length) {
            when(uriString[i]) {
                '#' -> {
                    queryEndIndex = i
                    break
                }
            }
            i++
        }

        index = queryEndIndex
        val query = uriString.substring(startIndex = startIndex, endIndex = queryEndIndex)
        return if (query.isEmpty()) "" else query
    }

    private fun parseFragment(): String {
        if (index + 1 >= uriString.length) return ""
        val query = uriString.substring(startIndex = index + 1, endIndex = uriString.length)
        return query.ifEmpty { "" }
    }

    /**
     * The companion object for the [Uri] interface. This is useful for creating extension functions related to the
     * creation of a [Uri].
     */
    companion object {

        /**
         * The [Set] of reserved [Char]s defined by the URI specification.
         *
         * @see [URI Specification](https://datatracker.ietf.org/doc/html/rfc3986#section-2.2)
         */
        val reserved: Set<Char>
            get() = reservedCharacters

        /**
         * The [Set] of general delimiter [Char]s defined by the URI specification.
         *
         * @see [URI Specification](https://datatracker.ietf.org/doc/html/rfc3986#section-2.2)
         */
        val generalDelimiters: Set<Char>
            get() = generalDelimiterCharacters

        /**
         * The [Set] of sub-delimiter [Char]s defined by the URI specification.
         *
         * @see [URI Specification](https://datatracker.ietf.org/doc/html/rfc3986#section-2.2)
         */
        val subDelimiters: Set<Char>
            get() = subDelimiterCharacters

        /**
         * Determines whether this [Char] is one of the [reserved] characters defined by the URI specification.
         *
         * @see [URI Specification](https://datatracker.ietf.org/doc/html/rfc3986#section-2.2)
         */
        inline val Char.isReserved: Boolean
            inline get() = reserved.contains(this)

        /**
         * Determines whether this [Char] is one of the [generalDelimiters] characters defined by the URI
         * specification.
         *
         * @see [URI Specification](https://datatracker.ietf.org/doc/html/rfc3986#section-2.2)
         */
        inline val Char.isGeneralDelimiter: Boolean
            inline get() = generalDelimiters.contains(this)

        /**
         * Determines whether this [Char] is one of the [subDelimiters] characters defined by the URI specification.
         *
         * @see [URI Specification](https://datatracker.ietf.org/doc/html/rfc3986#section-2.2)
         */
        inline val Char.isSubDelimiter: Boolean
            inline get() = subDelimiters.contains(this)
        private val generalDelimiterCharacters = setOf(
            ':',
            '/',
            '?',
            '#',
            '[',
            ']',
            '@'
        )
        private val subDelimiterCharacters = setOf(
            '!',
            '$',
            '&',
            '\'',
            '(',
            ')',
            '*',
            '+',
            ',',
            ';',
            '='
        )
        private val reservedCharacters = generalDelimiterCharacters + subDelimiterCharacters
    }
}