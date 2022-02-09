package com.oldguy.common.io

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class FileTests(val testDirPath: String) {
    val testDirectory = File(testDirPath)
    val subDirName = "kmpIOtestDir"

    fun filesBasics() {
        assertTrue(testDirectory.isDirectory)
        val subDir = testDirectory.resolve(subDirName)
        assertTrue(subDir.exists)
        assertTrue(subDir.isDirectory)

        val testFileName = "Test.txt"
        val textFile = File(testDirectory, testFileName)
        assertEquals(false, textFile.exists)
        assertEquals(".txt", textFile.extension)
        assertEquals(testFileName, textFile.name)
        assertEquals("Test", textFile.nameWithoutExtension)
        assertEquals(false, textFile.isDirectory)
        val path ="${testDirectory.path}/$testFileName"
        assertEquals(path, textFile.path)

        val tmpList = testDirectory.listFiles
        assertTrue(tmpList.isNotEmpty())
        assertEquals(1, tmpList.count {it.name == subDirName})
    }

    fun textFileBasics(charset: Charset) {
        val subDir = testDirectory.resolve(subDirName)
        val fil = File(subDir, "Text${charset.charset.charsetName}.txt")
        fil.delete()
        assertEquals(false, fil.exists)
        val textFile = TextFile(
            fil,
            charset,
            FileMode.Write,
            FileSource.File
        )
        textFile.write(textContent)
        textFile.close()
        assertEquals(true, fil.exists)
        assertEquals(textContent.length, fil.size.toInt())

        val textFileIn = TextFile(
            fil,
            charset,
            FileMode.Read,
            FileSource.File
        )
        var count = 0

        textFileIn.forEachLine {
            count++
            when (count) {
                1 -> assertEquals(line1 + eol, it)
                2 -> assertEquals(line2 + eol, it)
                3 -> assertEquals(line3 + eol, it)
                4, 5 -> assertEquals(eol, it)
                6 -> assertEquals("Line66", it)  // <== this causes unit test process to fail in
                // org.jetbrains.kotlin.gradle.internal.testing.TCServiceMessagesClient.ensureNodesClosed(TCServiceMessagesClient.kt:537)
                else -> fail("Unexpected line $count, content:\"$it\", file ${fil.name}, charset: $charset  ")
            }
        }
        assertEquals(6, count)
    }

    companion object {
        val eol = "\n"
        val line1 = "Line1 ancvb568099jkhrwsiuoidsygoedyt03ohgnejbj  eo;iuwoiopww79lhzH;EndLine1"
        val line2 = "Line2 ancvb568099jkhrwsiuoidsygoedyt03ohgnejbj  eo;iuwoiopww79lhzH;EndLine2"
        val line3 = "Line3 ancvb568099jkhrwsiuoidsygoedytEndLine3"
        val textContent = """
            $line1
            $line2
            $line3
            
            
            Line6""".trimIndent()
    }
}