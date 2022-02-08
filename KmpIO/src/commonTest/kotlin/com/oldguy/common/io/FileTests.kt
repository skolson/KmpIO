package com.oldguy.common.io

import kotlin.test.assertEquals
import kotlin.test.fail

class FileTests(val testDirPath: String) {
    val testDirectory = File(testDirPath)

    fun filesBasics() {
        val list = testDirectory.listFiles
        assertEquals(0, list.size)
        assertEquals(true, testDirectory.isDirectory)

        val testFileName = "Test.txt"
        val textFile = File(testDirectory, testFileName)
        assertEquals(false, textFile.exists)
        assertEquals(".txt", textFile.extension)
        assertEquals(testFileName, textFile.name)
        assertEquals("Test", textFile.nameWithoutExtension)
        assertEquals(false, textFile.isDirectory)
        assertEquals("any", textFile.fullPath)
        assertEquals(testFileName, textFile.path)

        val subDir = testDirectory.resolve("testDir")
        assertEquals(true, subDir.exists)
    }

    fun textFileBasics(charset: Charset) {
        val subDir = testDirectory.resolve("testDir")
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
                1 -> assertEquals(line1+eol, it)
                2 -> assertEquals(line2+eol, it)
                3 -> assertEquals(line3+eol, it)
                4,5 -> assertEquals(eol, it)
                6 -> assertEquals("Line6", it)
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