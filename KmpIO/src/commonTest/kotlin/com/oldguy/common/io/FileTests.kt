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

    fun checkTextLines(textFile: TextFile): Int {
        var lines = 0
        textFile.forEachLine { count, it ->
            when ((count - 1) % 6) {
                0 -> assertEquals(line1 + eol, it)
                1 -> assertEquals(line2 + eol, it)
                2 -> assertEquals(line3 + eol, it)
                3, 4 -> assertEquals(eol, it)
                5 -> assertEquals("Line6$eol", it)
                else -> fail("Unexpected line $count, content:\"$it\", file ${textFile.file.name}, charset: ${textFile.charset}.")
            }
            lines = count
            true
        }
        return lines
    }

    fun textFileWriteRead(charset: Charset) {
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
        assertEquals(textContent.length * charset.charset.bytesPerChar, fil.size.toInt())

        val textFileIn = TextFile(
            fil,
            charset,
            FileMode.Read,
            FileSource.File
        )
        val lines = checkTextLines(textFileIn)
        assertEquals(6, lines)
        fil.delete()
    }

    fun biggerTextFileWriteRead(charset: Charset, copyCount: Int = 100) {
        val subDir = testDirectory.resolve(subDirName)
        val fil = File(subDir, "TextMedium${charset.charset.charsetName}.txt")
        fil.delete()
        assertEquals(false, fil.exists)
        val textFile = TextFile(
            fil,
            charset,
            FileMode.Write,
            FileSource.File
        )
        for (i in 0 until copyCount)
            textFile.write(textContent)
        textFile.close()
        assertEquals(true, fil.exists)
        assertEquals(textContent.length * charset.charset.bytesPerChar * copyCount, fil.size.toInt())

        val textFileIn = TextFile(
            fil,
            charset,
            FileMode.Read,
            FileSource.File
        )
        val lines = checkTextLines(textFileIn)
        assertEquals(6 * copyCount, lines)
        fil.delete()
    }

    companion object {
        const val eol = "\n"
        const val line1 = "Line1 ancvb568099jkhrwsiuoidsygoedyt03ohgnejbj  eo;iuwoiopww79lhzH;EndLine1"
        const val line2 = "Line2 ancvb568099jkhrwsiuoidsygoedyt03ohgnejbj  eo;iuwoiopww79lhzH;EndLine2"
        const val line3 = "Line3 ancvb568099jkhrwsiuoidsygoedytEndLine3"
        val textContent = """
            $line1
            $line2
            $line3
            
            
            Line6
            """.trimIndent() + eol
    }
}