## KmpIO

This is a Kotlin multiplatform (KMP) library for basic Text file, Binary file, and zip/archive file IO. It was initially implemented with the android target, now implementations for Apple targets are being built. 

**2/9/2022** - Both UTF-8 and UTF-16 Charsets pass text file unit tests. Raw file IO basic unit tests are passing. Starting on Zip file support for Apple.

**2/8/2022** - The MacOS text file (UTF-8) unit tests are passing now, but today's commit has a purposefully failing assertEquals as a reproducer for Kotlin Gradle issue https://youtrack.jetbrains.com/issue/KT-51217. RawFle and ZipFile will be in next commit

**2/4/2022** - This is a brand new publish even though the code has been used for a long time in an Android app. The Mac and IOS support is new.

## Reason for Existence

Kotlin multiplatform code needing basic IO file support should not need platform-specific code to do so. This library provides:

- Text file reading/writing, by line breaks or by text blocks, with selectable charset encode/decode
- Binary file reading and writing, with random access by file position
- Zip/archive file writing or reading, with Kotlin friendly syntax for handling zip entries.
- Bitset class similar to java's Bitset, but KMP only.
- Base64 encoding KMP only code
- ByteBuffer support using KMP-only code, similar to Java's ByteBuffer, with endian support. Syntax is a little more Kotlin friendly. Both ByteBuffer and UByteBuffer are available for use.
- Very basic Charset encode/decode for a limited number of charsets 

Supported platforms (KMM targets) all 64 bit only:

- Android Arm, X64
- linuxX64
- macosX64
- iosArm64
- iosX64 Simulator
- mingw64 currently not supported bu is **easy** to add.

# Dependencies

Kotlin only is used for the KMP code.
- Kotlin 1.6.10
- Kotlin atomicfu

## Usage

This library has been used extensively in one app, so has not so far been published to maven. It can be easily published to mavenLocal using the gradle "publishToMavenLocal" task.

At some point the library may be published to the public Maven repository.

Use the gradle Publish task 'publishToMavenLocal' to run a build and publish the artifacts produced to a local maven repository.

Define the library as a gradle dependency (assumes mavenLocal is defined as a repo in your build.gradle scripts):

```
    dependencies {
        implementation("com.oldguy.kmpsc:kmp-io:0.1.0")
    }  
```

### BitSet

This is basically the same functionality as java.util.BitSet using just Kotlin code - there is no expect/actual setup for this class.  Can be constructed from a ByteBuffer or ByteArray and provides array operations for treating the BitSet as an array of bits. Example use cases include:

- low level binary file making extensive use of bit-level flags
- bit flipping/testing without using masks can in some cases improve readability of code

## ByteBuffer and UByteBuffer

These Kotlin-only implementations offer similar functionality to java.nio.ByteBuffer, except any ability to control the memory used.  It offers essentially an enhanced ByteArray (or UByteArray) with endian support and kotlin friendly syntax for reading and writing basic types using the buffer. Endian support defaults to little endian, but big endian is selectable at constructor time. A position property is provided, along with kotlin properties for each of the basic types. Like Bitset there is no expect/actual setup here, just Kotlin code that should build and operate identically on any platform supports by KMP.

## Charset

This is a wrapper for platform-specific implementations of a few basic Charsets supporting encoding and decoding as desired. Native implementations of each Charset are used with the standard expect/actual setup. Charsets currently included are:

- UTF-8
- ISO8859_1 - similar but not identical to UTF-8
- UTF-16LE - two bytes, little endian
- UTF-16BE - two bytes, big endian
- US-ASCII - I never used this one but its in there

## Base64

Pure Kotlin implementation of Base 64 encoding/decoding, without use of platform-specific implementations.

## File 

This is a simple KMP wrapper for an underlying File, with platform-specific implementations provided using the expect/actual support in KMP.  Java platforms (like Android) use the java File object, Apple platforms use the standard Foundation library. an overview of the basic capabilities:

- File/directory path setting/querying
- Simple copies
- Resolve
- Delete
- for directories, listing content

Again this was built before more robust libraries like OKIO existed. It is a simple helper to allow KMP code to do most file operations without rquiring platform-specific code.

## RawFile

Similar to file but for random access by byte position, and no help for encoding. Typical usage is for reading/writing ByteBuffers (blocks) of data at a specified position and doing manual encoding/decoding.

## TextFile

Help for reading/decoding and/or writing/encoding files using a specified Charset and standard line separators.  Platform-specific implementations using expect/actual setup are used to provide basic text processing with kotlin-friendly syntax.

## ZipFile

Used for reading or writing Zip/archive files using Kotlin-friendly, platform-independent syntax. Platform-specific implementations using expect/actual setup provide entry-based processing. Each entry has a name and optional properties that ZipFile supports, as well as content.

## Extensions

Extension functions are added to ByteArray using pure kotlin (no expect/actual) that overlap some of the functions. There are a number of these, examples include:

- toHex for String represenation of binary data
- basic endian-aware get/put ops for Short, UShort, Int, UInt, Long, ULong

## Coroutine support

Since pretty much all File I/O should by definition be done using Dispatchers.IO or similar, TextFile and RawFile classes use suspend functions and suspend lambdas to encourage coroutine usage.

Also note that the Kotlin Native support in this library is using the new memory model support available with 1.6.x

## SourceSet structure

- commonMain has most of the code in one package.
- commonTest has the unit tests that are platform-independent
- androidMain has the platform-specific implementations using Android's java support. Depends on commonMain
- androidTest has the platform-specific unit tests using Android's java support. For example, the KMP ByteBuffer functionality is compared to the equivalenyt usage of Android's java.nio.ByteBuffer. Depends on commonTest
- appleNativeMain has the apple-specific implementations that are common across Mac, IOS and IOS Simulator,  invoked with Kotlin Native
- iosArm64Main and iosX64Main has IOS-specific code invoked with Kotlin Native. Depends on appleNativeMain and commonMain
- macosX64Main has any mac-specific code invoked with Kotlin Native. Depends on appleNativeMain and commonMain


# Example Usage

This section will be added in the near future.  The library has been used mostly on Android for use cases that include:
- reading JSON encoded asset files from an app (for use with Kotlinx Serialization library)
- reading external text files of various types (XML, JSON, OFX (modified SGML), text) with standard character set encodings
- reading external binary files using heavily encoded file formats like Microsoft Access files, using KMP code - see the [KmpJillcess](https://github.com/skolson/KmpJillcess) repo for usage
- encrypting/decrypting files - see the [KmpCryptography](https://github.com/skolson/KmpCryptography) repo for usage

**2022-02-02 note** the two repos mentioned above have existed for quite a while but are just now being published to Github - should be available in the next couple weeks.

Examples for each use case are shown below:

*doc will be added during first part of February 2022*
