package com.oldguy.common.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class Base64Tests {
    val utf8 = Charset(Charsets.Utf8)
    val testVal1 = "3MF5qZLl/e0shTV0MAcVeg=="
    val result1 = byteArrayOf(-36, -63, 121, -87, -110, -27, -3, -19, 44, -123, 53, 116, 48, 7, 21, 122)

    val hello = "hello, world"
    val helloEnc = "aGVsbG8sIHdvcmxk"
    val hello2 = "hello, world?!"
    val hello3 = "hello, world."

    @Test
    fun decodeTest() {
        val enc = Base64.encode(result1)
        val encStr = utf8.decode(enc)
        assertEquals(testVal1, encStr)
        assertEquals(testVal1, Base64.encodeToString(result1))

        val de = Base64.decodeToBytes(encStr)
        assertContentEquals(result1, de)

        assertEquals(hello, Base64.decode(helloEnc))
        assertEquals(hello, Base64.decode(" aGVs bG8s IHdv cmxk  "))
        assertEquals(hello, Base64.decode(" aGV sbG8 sIHd vcmx k "))
        assertEquals(hello, Base64.decode(" aG VsbG 8sIH dvcm xk "))
        assertEquals(hello, Base64.decode(" a GVsb G8sI Hdvc mxk "))
        assertEquals(hello, Base64.decode(" a G V s b G 8 s I H d v c m x k "))
        assertEquals(hello, Base64.decode("_a*G_V*s_b*G_8*s_I*H_d*v_c*m_x*k_"))

        assertEquals(hello2, Base64.decode("aGVsbG8sIHdvcmxkPyE="))
        assertEquals(hello2, Base64.decode("aGVsbG8sIHdvcmxkPyE"))
        assertEquals(hello2, Base64.decode("aGVsbG8sIHdvcmxkPy E="))
        assertEquals(hello2, Base64.decode("aGVsbG8sIHdvcmxkPy E"))
        assertEquals(hello2, Base64.decode("aGVsbG8sIHdvcmxkPy E ="))
        assertEquals(hello2, Base64.decode("aGVsbG8sIHdvcmxkPy E "))
        assertEquals(hello2, Base64.decode("aGVsbG8sIHdvcmxkPy E = "))
        assertEquals(hello2, Base64.decode("aGVsbG8sIHdvcmxkPy E   "))

        assertEquals(hello3, Base64.decode("aGVsbG8sIHdvcmxkLg=="))
        assertEquals(hello3, Base64.decode("aGVsbG8sIHdvcmxkLg"))
        assertEquals(hello3, Base64.decode("aGVsbG8sIHdvcmxkL g=="))
        assertEquals(hello3, Base64.decode("aGVsbG8sIHdvcmxkL g"))
        assertEquals(hello3, Base64.decode("aGVsbG8sIHdvcmxkL g =="))
        assertEquals(hello3, Base64.decode("aGVsbG8sIHdvcmxkL g "))
        assertEquals(hello3, Base64.decode("aGVsbG8sIHdvcmxkL g = = "))
        assertEquals(hello3, Base64.decode("aGVsbG8sIHdvcmxkL g   "))
    }
}