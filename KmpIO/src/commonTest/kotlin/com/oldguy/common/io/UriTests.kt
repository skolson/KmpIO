package com.oldguy.common.io

import kotlin.test.Test
import kotlin.test.assertEquals

class UriTests {

    @Test
    fun validUriParsesCorrectly() {
        val test = "https://john.doe@www.example.com:123/forum/questions/?tag=networking&order=newest#top"
        val uri = Uri(test)

        assertEquals(
            expected = test,
            actual = uri.uriString
        )
    }

    @Test
    fun uriWithNoPortParsesCorrectly() {
        val test = "https://username:password@example.com"
        val uri = Uri(test)
        assertEquals(
            expected = test,
            actual = uri.uriString
        )
    }

    @Test
    fun uriWithAllComponentsParsesCorrectly() {
        assertUri(
            uri = "https://john.doe@www.example.com:123/forum/questions/?tag=networking&order=newest#top",
            scheme = "https",
            authority = "john.doe@www.example.com:123",
            userInfo = "john.doe",
            host = "www.example.com",
            port = 123,
            path = "/forum/questions/",
            query = "tag=networking&order=newest",
            fragment = "top"
        )
    }

    @Test
    fun uriWithoutSchemeAndWithAuthorityParsesCorrectly() {
        assertUri(
            uri = "//john.doe@www.example.com:123/forum/questions/?tag=networking&order=newest#top",
            scheme = "",
            authority = "john.doe@www.example.com:123",
            userInfo = "john.doe",
            host = "www.example.com",
            port = 123,
            path = "/forum/questions/",
            query = "tag=networking&order=newest",
            fragment = "top"
        )
    }

    @Test
    fun uriWithoutSchemeAndAuthorityParsesCorrectly() {
        assertUri(
            uri = "/john.doe@www.example.com:123/forum/questions/?tag=networking&order=newest#top",
            scheme = "",
            authority = "",
            userInfo = "",
            host = "",
            port = null,
            path = "/john.doe@www.example.com:123/forum/questions/",
            query = "tag=networking&order=newest",
            fragment = "top"
        )
    }

    @Test
    fun uriWithoutSchemeAndAuthorityAndQueryParsesCorrectly() {
        assertUri(
            uri = "/john.doe@www.example.com:123/forum/questions/#top",
            scheme = "",
            authority = "",
            userInfo = "",
            host = "",
            port = null,
            path = "/john.doe@www.example.com:123/forum/questions/",
            query = "",
            fragment = "top"
        )
    }

    @Test
    fun uriOnlyWithPathParsesCorrectly() {
        assertUri(
            uri = "/john.doe@www.example.com:123/forum/questions/",
            scheme = "",
            authority = "",
            userInfo = "",
            host = "",
            port = null,
            path = "/john.doe@www.example.com:123/forum/questions/",
            query = "",
            fragment = ""
        )
    }

    @Test
    fun httpsUriParsesCorrectly() {
        assertUri(
            uri = "https://john.doe@www.example.com:123/forum/questions/?tag=networking&order=newest#top",
            scheme = "https",
            authority = "john.doe@www.example.com:123",
            userInfo = "john.doe",
            host = "www.example.com",
            port = 123,
            path = "/forum/questions/",
            query = "tag=networking&order=newest",
            fragment = "top"
        )
    }

    @Test
    fun ldapUriParsesCorrectly() {
        assertUri(
            uri = "ldap://[2001:db8::7]/c=GB?objectClass?one",
            scheme = "ldap",
            authority = "[2001:db8::7]",
            userInfo = "",
            host = "[2001:db8::7]",
            port = null,
            path = "/c=GB",
            query = "objectClass?one",
            fragment = ""
        )
    }

    @Test
    fun mailto_uri_parses_correctly() {
        assertUri(
            uri = "mailto:John.Doe@example.com",
            scheme = "mailto",
            authority = "",
            userInfo = "",
            host = "",
            port = null,
            path = "John.Doe@example.com",
            query = "",
            fragment = ""
        )
    }

    @Test
    fun news_uri_parses_correctly() {
        assertUri(
            uri = "news:comp.infosystems.www.servers.unix",
            scheme = "news",
            authority = "",
            userInfo = "",
            host = "",
            port = null,
            path = "comp.infosystems.www.servers.unix",
            query = "",
            fragment = ""
        )
    }

    @Test
    fun tel_uri_parses_correctly() {
        assertUri(
            uri = "tel:+1-816-555-1212",
            scheme = "tel",
            authority = "",
            userInfo = "",
            host = "",
            port = null,
            path = "+1-816-555-1212",
            query = "",
            fragment = ""
        )
    }

    @Test
    fun telnet_uri_parses_correctly() {
        assertUri(
            uri = "telnet://192.0.2.16:80/",
            scheme = "telnet",
            authority = "192.0.2.16:80",
            userInfo = "",
            host = "192.0.2.16",
            port = 80,
            path = "/",
            query = "",
            fragment = ""
        )
    }

    @Test
    fun urn_uri_parses_correctly() {
        assertUri(
            uri = "urn:oasis:names:specification:docbook:dtd:xml:4.1.2",
            scheme = "urn",
            authority = "",
            userInfo = "",
            host = "",
            port = null,
            path = "oasis:names:specification:docbook:dtd:xml:4.1.2",
            query = "",
            fragment = ""
        )
    }

    @Test
    fun builderSimpleTest() {
        val uri = Uri.Builder()
            .scheme("https")
            .authority("www.example.com")
            .path("/foo/bar")
            .build()
        assertEquals("https://www.example.com/foo/bar", uri.uriString)
        assertEquals("https", uri.scheme)
        assertEquals("www.example.com", uri.host)
        assertEquals("/foo/bar", uri.path)
    }

    @Test
    fun builderFullTest() {
        val uri = Uri.Builder()
            .scheme("https")
            .userInfo("username", "password")
            .host("www.example.com")
            .port(443)
            .path("/foo/bar")
            .parms(listOf(
                Pair("parm1", "value1"),
                Pair("parm2", "value2"),
                Pair("parm3", "value3"),
                Pair("parm4", "value4"),
            ))
            .fragment("frag")
            .build()
        assertEquals("https://username:password@www.example.com:443/foo/bar?parm1=value1&parm2=value2&parm3=value3&parm4=value4#frag", uri.uriString)
        assertEquals("https", uri.scheme)
        assertEquals("www.example.com", uri.host)
        assertEquals("/foo/bar", uri.path)
        assertEquals("username:password", uri.userInfo)
        assertEquals(443, uri.port)
        assertEquals("parm1=value1&parm2=value2&parm3=value3&parm4=value4", uri.query)
        assertEquals("frag", uri.fragment)
    }

    private fun assertUri(
        uri: String,
        scheme: String,
        authority: String,
        userInfo: String,
        host: String,
        port: Int?,
        path: String,
        query: String,
        fragment: String
    ) {
        val result = Uri(uri)

        assertEquals(expected = scheme, actual = result.scheme)
        assertEquals(expected = authority, actual = result.authority)
        assertEquals(expected = userInfo, actual = result.userInfo)
        assertEquals(expected = host, actual = result.host)
        assertEquals(expected = port, actual = result.port)
        assertEquals(expected = path, actual = result.path)
        assertEquals(expected = query, actual = result.query)
        assertEquals(expected = fragment, actual = result.fragment)
    }
}