## KmpIO

This is a Kotlin multiplatform (KMP) library for basic Text file, Binary file, and zip/archive file IO. It was initially implemented with the android target, now also does Apple targets. Library should be considered alpha quality - unit tests pass on Android, MacOS, IOS Simulator.

The implementation relies on two main sources of info:
- [Wikipedia Zip File Format article](https://en.wikipedia.org/wiki/ZIP_(file_format))
- [PKWare Zip Specification document](https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT)

Usage Note - The phrase "KMP only code" below indicates pure Kotlin code with no expect/actual usage. Expect/Actual usage was limited to these categories:

- File create/delete/attributes, low-level File IO
- Compression algorithms (used by ZipFile classes)

Supported targets:

- Android X64 and Arm64 ABIs
- macosX64
- macosArm64
- iosX64
- iosArm64
- iosSimulatorArm64


A common source set "appleMain" contains common source used by all three Apple targets. Project uses Kotlin's default hierarchy templte for source sets

## Reason for Existence

Kotlin multiplatform code needing basic IO file support should not need platform-specific code to do so. This library provides:

- Coroutine support using Dispatchers.IO
- Text file reading/writing, by line breaks or by text blocks, with selectable charset encode/decode
- Binary file reading and writing, with random access by file position
- Zip/archive file writing or reading, with Kotlin friendly syntax for handling zip entries.
  - Zip64 support
  - initially Deflate compression only on Android. Apple adds LZMA.
  - Accessors for all Zip meta-data
  - Customizable Zip extra data encoding/decoding
  - KMP-only implementation except for compression
    - Android uses Inflater/Deflater for DEFLATE
    - Apple compression library (Objective C) for DEFLATE, LZMA
- Bitset class similar to java's Bitset, but KMP only code.
- Base64 encoding KMP only code
- ByteBuffer support using KMP-only code, similar to Java's ByteBuffer, with little endian and big endian support selectable at constructor time. Syntax is more Kotlin friendly. Both ByteBuffer and UByteBuffer are available for use.
- Basic Charset encode/decode for a limited number of charsets 

# Dependencies

Kotlin only is used for the KMP code.

- atomicfu
- Coroutines
- kotlinx datetime

## Usage

Define the library as a gradle dependency (assumes mavenCentral() is defined as a repo in your build.gradle scripts):

```
    dependencies {
        implementation("io.github.skolson:kmp-io:0.1.5")
    }  
```

### BitSet

This is basically the same functionality as java.util.BitSet using just Kotlin code - there is no expect/actual setup for this class.  Can be constructed from a ByteBuffer or ByteArray and provides array operations for treating the BitSet as an array of bits. Example use cases include:

- low level binary file making extensive use of bit-level flags
- bit flipping/testing without using masks can in some cases improve readability of code

## ByteBuffer and UByteBuffer

These Kotlin-only implementations offer similar functionality to java.nio.ByteBuffer, except with no ability to control the memory used - everything is standard heap.  It offers essentially an enhanced ByteArray (or UByteArray) with endian support and kotlin friendly syntax for reading and writing basic types using the buffer. Endian support defaults to little endian, but big endian is selectable at constructor time. A position property is provided, along with kotlin properties for each of the basic types. Like Bitset there is no expect/actual setup here, just Kotlin code that should build and operate identically on any platform supports by KMP.

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

For random access reading/writing by byte position, with no help for encoding. Typical usage is for reading/writing ByteBuffers (blocks) of data either sequentially or at a specified position. RawFile supports granular control over read/write using Buffers.  It also has convenience wrappers using basic buffered source/sink design. Read/Source content is supplied by one or more buffers of any size.  Write/Sink content is written using one or more buffers of any size. A Stream-based implementation was purposely not done to keep designs simpler.

## TextFile

Similar to RawFile, with support for reading/decoding and/or writing/encoding text-only files using a specified Charset and standard line separators.  Platform-specific implementations using expect/actual setup are used to provide basic text processing with kotlin-friendly syntax.

## ZipFile

Used for reading or writing Zip/archive files using Kotlin-friendly, platform-independent syntax. Most of the code is pure Kotlin multi-platform. Platform-specific implementations of Compression schemes use expect/actual setup. Features include:

- Zip and Zip64 support. Has been tested with a 5MB file that expands to 5+GB
- Support classes visible (but not normally used by users) include
  - Crc32 for cyclical redundancy calculation
  - ZipTime converts arcane MSDOS time format used by zip specification to/from kotlinx LocalDateTime instances
  - ZipDirectoryRecord and ZipLocalRecord for full visibility into content
  - Extensible ZipExtra class for parsing custom extra data
    - ZipExtraZip64 subclass for the Zip64 reserved extra data
    - ZipExtraNtfs subclass for the NTFS reserved extra data segment
    - Pluggable parser setup for encoding/decoding extra data with custom ZipExtra subclasses
  - Zip specification record classes of various types
    - End of Central Directory record
    - Zip64 End of Central Directory record and locator record
    - others

## Compression

Compress/de-compress content using selected Algorithms. This support is using expect/actual setup. Design is similar to File support in that all compression algorithms are implemented using the same basic buffered source/sink design. Input/Source content is supplied by one or more buffers of any size.  Output/Sink content is produced using one or more buffers of any size, until Source input is complete.

- java/Android using Inflater/Deflater classes for Zip specification DEFLATE support
- Apple using built in Objective-C compression wrapper over zLib for Zip specification DEFLATE support
  - Apple also supports LZMA
  

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

## Utilities

### BitSet

```
    // Create a 2 byte BitSet, can be any number of bits
    val bitset = BitSet(byteArrayOf(0x80, 0x80)
    // or
    val bitset2 = BitSet(16)
    var isBit0 get() = bitSet[0]
        set(value) {
            bitSet[0] = value
        } 
```
### ByteBuffer, UByteBuffer

Basic example of encoding basic types with the specified order, getting the results as a ByteArray, decoding values out
of a ByteBuffer.
```
    val littleEndianBuf = ByteBuffer(ByteArray(1024))  // little endian is default
    val bigEndianBuf = ByteBuffer(ByteArray(1024), order = ByteOrder.BigEndian)
    val test = "anytext"
    val utf8 = Charset(Charsets.Utf8)
    
    // encode various basic types into the ByteBuffer,  
    littleEndianBuf.apply {
        byte = 0x1.toByte()
        // position = 1 here, limit is 1024, remaining is 1023
        short = 0x0101
        // position = 3 here, limit is 1024, remaining is 1021
        int = 0x01010101
        // position = 7 here, limit is 1024, remaining is 1017
        long = 0x0101010101010101L
        ulong = 0x0101010101010101UL
        uint = 0x0101
        float = 1.01f
        double = 1.01
        put(utf8.encode(test))
        val x = remaining   // number of bytes left before limit is reached
        flip()  // sets position to 0, limit to former position. 
        var encodedBytes = getBytes()   // little endian encoded byte array with data above
    }
    bigEndianBuf.apply {
        byte = 0x1
        // position = 1 here, limit is 1024, remaining is 1023
        short = 0x0101
        // position = 3 here, limit is 1024, remaining is 1021
        int = 0x01010101
        // position = 7 here, limit is 1024, remaining is 1017
        long = 0x0101010101010101L
        float = 1.01f
        double = 1.01
        put(utf8.encode(test))
        ...
        flip()  // sets position to 0, limit to former position. 
        var encodedBytes = getBytes()   // big endian encoded byte array with data above
    }
    littleEndianBuf.apply {
        assertEquals(0x1.toByte(), byte)
        position = 7
        assertEquals(0x0101010101010101L, long)
        assertEquals(0x0101010101010101UL, ulong)
    }
```

### Charset
```
    val utf16 = Charset(Charsets.Utf16LE) // See Charsets enum for supported character sets
    val test = "anytext"
    val bytes = utf16.encode(test)
    assertEquals(test, utf16.decode(bytes)
```

### Base64
```
    Base64().apply {
        val bytes = encode("any UTF-8 text) // resulting bytes are base-64 encoded
        val text = decode(bytes)
    }
```

## Files

Classes for file handling. Note no streams model in use, just simple buffered reading and writing for random access binary files and for text files.

### File

Examples shown exercise properties available on any platform supported by the library
```
    val directory = File("/var/tmp/anydir")
    val shallowList = directory.listFiles() // lists files in sub-directories in directory, no subdirectory content
    val deepList = directory.listFilesTree() // lists ALL files in directory and all of its sub-directories
    val subDirectory = directory.resolve("anysubdir") // finds a subdirectory of directory, makes it if it doesn't exist
    if (subDirectory.exists) {
        File(subDirectory, "anyfile.txt").apply {
            /* some of the properties available
                name: String
                nameWithoutExtension: String
                extension: String
                path: String            // include file name
                directoryPath: String   // just the path of the directory containing this file
                isDirectory: Boolean
                listNames: List<String>
                exists: Boolean
                size: ULong
                lastModified: LocalDateTime // internal time converted to kotlinx equivalent using system TimeZone 
           */
        }
    }
```

### RawFile
Example of creating a file, then reading with change of position
```
        val buf = ByteBuffer(4096)
        val fil = File(subDir, "Somefile.dat")
        RawFile(fil, FileMode.Write).use {
            it.write(ByteBuffer(hexContent))
        }
        RawFile(fil).use {
            var count = it.read(buf)
            it.position = it.size - 12u  // position to last 12 bytes of file
            buf.clear()
            count = it.read(buf) // read last 12 bytes
        }
    
```

### TextFile
Example of creating a text file with ISO-8859-1 encoding, then reading. Same basic setup as RawFile with encoding/decoding of text.
```
        val fil = File(subDir, "Text${charset.charset.charsetName}.txt")
        TextFile(
            fil,
            Charset(Charsets.Iso8859_1),
            FileMode.Write
        ).use {
            it.writeLine(textContent)
        }
        TextFile(
            fil,
            Charset(Charsets.Iso8859_1)
        ).use {
            val textContent = it.readLine()
        }

```

## ZipFiles

Zip file entries can be directories only with no data, or with data of any size.  Content sizes > Int.MAX_VALUE require setting `isZip64 = true`. Properties are available for accessing all zip metadata; directory records, local directory records, Zip64 metadata. Support for custom extra data encoding/decoding.

### Class Structure

**ZipFile** read-only, or read/write/create

- Owns zero or more **ZipEntry** instances accessible via map keyed by name, or by list
- Zip64 support
- convenience methods for merging a zip file into an existing zip, zipping directory trees, extract all to files, etc
- comment: String any length allowed by the Zip specification. encode/decode using UTF-8 Charset. Zip64 allows a comment to be HUGE, library imposes an arbitrary limit of 2MB to defend against excessive memory usage.

**ZipEntry** represents one entry in a ZipFile

- ZipDirectory instance contains all metadata about the ZipEntry per the Zip specification 
- name: String - any length allowed by the Zip specification. encode/decode using UTF-8 Charset
- comment: String - any length allowed by the Zip specification. encode/decode using UTF-8 Charset
- CompressionAlgorithms enum instance. Currently limited to None and Deflate

**ZipDirectory**

- ZipExtraParser associated with a ZipDirectoryRecord contains all the metadata for the directory entry per the Zip Specification. ZipExtraParser is typically defaulted, used for decoding/encoding extra data when needed conforming to the Zip spec.
- ZipExtraParser associated with a ZipLocalDirectory contains all the metadata for the local directory entry per the Zip Specification, typically a subset of the directory record. ZipExtra instances used by the parser can exnoce/decode extra data differently for the local record. ZipExtraZip64 is an example. 

  - Parser classes decode extra data to a List<ZipExtra> instances, encode a List<ZipExtra> to extra data per Zip specification.
  - ZipExtra subclasses; ZipExtraZip64, ZipExtraNtfs, ZipExtraGeneral

- ZipTime instances convert to/from kotlinx.datetime.LocalDateTime and Zip MSDOS encoded times.
  - lastModificationTime. Also lastAccessTime, createdTime when available, when a ZipExtraNtfs instance is present.
- Properties for compressedSize, uncompressedSize, and localHeaderOffset independent of Zip64 support

**ZipDirectoryRecord** subclass of ZipDirectoryCommon

- Holds low-level metadata per the Zip Specification.
- ByteBuffer instances used for encode/decode, zip spec requires little-endian encoding for numbers, UTF-8 encoding for strings (no null terminations).
- ZipVersion for version and minimum version per zip spec.

**ZipLocalRecord** subclass of ZipDirectoryCommon is typically a subset or partial copy of the ZipDirectoryRecord. See the Zip specification.

- Holds low-level metadata per the Zip Specification.
- ByteBuffer instances used for encode/decode, zip spec requires little-endian encoding for numbers, UTF-8 encoding for strings (no null terminations).
- ZipVersion for version and minimum version per zip spec.

**ZipDirectoryCommon** abstract base class of fields shared by ZipDirectoryRecord and ZipLocalRecord.

#### Supporting classes

**ZipGeneralPurpose** ZipDirectoryCommon owns a two-byte Bitset that is a mask of various feature flags.

- Boolean properties for the various common usages supported by the class, not comprehensive.

**ZipVersion** Simple encode/decode of the version number scheme used by the Zip spec.

**ZipTime** Simple conversions to/from the MSDOS date/time used by the Zip spec and koltinx.dataetime.LocalDateTime instances. These use the default system time zone of the host running the code, as the zip spec has no support for time zone encoding.

### Create a zip file

Create a new Zip file, add an entry from a file, add same file again under new name, add entries for all files in a directory tree that match the specified filter, add an entry with 100 lines of the same text encoded with UTF-16LE that also uses a custom last modified time.

```
      val dir = File("/anydir")
      val sourceDirectory = File("/anyPathToaDirectoryTreeToZip")
      val zip = File(dir, "TestFile.zip")
      val dataFile = File("SomethingToZip.dat")
      ZipFile(zip, FileMode.Write).use {
          it.isZip64 = true  // default is false, but true doesn't require large content or LOTS of entries, just supports them if needed.
          it.zipFile(dataFile)
          it.zipFile(dataFile, "Copy${dataFile.name}")
          it.zipDirectory(sourceDirectory,
              shallow = false,
          ) { name -> name.endsWith(".txt") }
          ZipEntry(
              name = "someData.dat",
              comment = "Anything legal here, up to Int.MAX_VALUE length if isZip64 = false",
              lastModTime = LocalDateTime(year = 2022, monthNumber = 1, dayOfMonth = 1, hour = 12
          ).apply {
              var count = 0
              val dataLine = Charset(Charsets.Utf16Le).encode("Any old stuff from anywhere")
              it.addEntry(this) {
                  // this lambda will be called repeatedly until it returns an empty ByteArray()
                  // provide uncompressed data in ByteArray instances of any size until done
                  if (count++ < 100) dataline else ByteArray(0)
              }
          }
      }
```

### Read a zip file

Each ZipEntry returned has properties that can be used to see all the data in the directory record, the local directory record, and other zip-spec-internal stuff. Not normally useful, but available.

```
      ZipFile(File("anything.zip").use { zip ->
          // zip.map is a map keyed by name of all ZipEntry instances
          // zip.entries is the map values as a List
          zip.entries
            .filter { it.name.contains(".txt"}
            .apply {
                val extraRecords: List<ZipExtra> = it.extras
                zip.readEntry(it) { _, content, count ->
                    // this block is called repeatedly until all uncompressed data has been passed, then ends
                    // first arg is ZipEntry, second is a ByteArray of content, third arg is byteCount, will be same as content size.
                    // Max content size for any one call is zip.bufferSize
                }
            }
      }
```

### ZipEntry Extra data

The Zip specification supports extra data as a ByteArray associated with the entry. The zip specification dictates a general required structure for each entry in the extra data. Extra data is a set of logical records. Each record has a signature, a length, and some bytes of data.  Low number signature values are reserved by the spec. This library has a ZipExtra class that is the base class defining each record. There is a `ZipExtraParser` class that provides encoding and decoding of ZipExtra instances in a List<ZipExtra> to/from ByteArrays conforming to the Zip Spec. Three subclasses of ZipExtra are provided for default support:

- ZipExtraZip64 - reserved signature 0x0001 designates Zip64 data in the extra record - see the zip spec
- ZipExtraNtfs - reserved signature 0x000a designates NTFS extra file data (dates) - see the zip spec
- ZipExtraGeneral - any other instance of extra data records
  - contains signature, length, and ByteArray(length) with any content desired.
  
The default ZipExtraParser supports the above. ZipExtra can be subclassed as much as desired for supporting different signature values especially custom (non-reserved) ones.  A subclass of ZipExtraParser is the factory logic that produces the correct subclass for a given signature during decoding of an extra data ByteArray into a List<ZipExtra>. 