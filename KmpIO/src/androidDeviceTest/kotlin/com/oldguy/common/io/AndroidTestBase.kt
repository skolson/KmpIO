package com.oldguy.common.io

import androidx.test.platform.app.InstrumentationRegistry
import com.oldguy.common.io.AndroidFileUnitTests.Companion.excludeAssets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.io.FileOutputStream

class AndroidTestBase {
    val workingDir: File
    val tempDir: File

    init {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File.appContext = context
        workingDir = File.workingDirectory()
        tempDir = File.tempDirectoryFile()
    }

    fun copyAsset(name: String, outputFile: File) {
        File.appContext.assets.open(name).use { inputStream ->
            val targetFile = java.io.File(outputFile.fullPath)
            FileOutputStream(targetFile).use { outputStream ->
                val bytes = inputStream.copyTo(outputStream)
                println("Copied $name to $targetFile, $bytes bytes copied")
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun copyAssetDirectory(assetDirName: String, tempDir: File) {
        val assetManager = File.appContext.assets
        val fileNames = assetManager.list(assetDirName) ?: return
        fileNames
            .filter {
                !excludeAssets.contains(it)
            }.forEach { fileName ->
                val assetPath = if (assetDirName.isEmpty()) fileName else "$assetDirName/$fileName"
                val outFile = File(tempDir, fileName)
                val subFiles = assetManager.list(assetPath)
                if (!subFiles.isNullOrEmpty()) {
                    outFile.makeDirectory()
                    copyAssetDirectory(assetPath, outFile)
                } else {
                    copyAsset(assetPath, outFile)
                }
            }
    }
}