# Change Log

### 0.3.0 (in progress)

- Add skip() function to TextFile, typical use case is for skipping any detected BOM bytes.
- Add optional bufferSize argument to TextFile constructor. Specifies the minimum size of TextBuffer. Uses TextBuffer.DEFAULT_BUFFER_SIZE (4k bytes) if not specified. Previously the default of 4k on the Linux and Apple implementations was not changeable.
- Charsets have new functions to better support decoding of multi-byte characters. Now properly handles partial multi-byte characters at the end of a ByteArray.
- Significant enhancements to TextBuffer to support reading by Character or String (x number of Character), useful with complex parsers.
  - Read line functions now properly handle multi-byte characters split across source calls.
  - New properties lineCount and linePosition for tracking current position in file during parsing
- JVM and Android TextFile internal implementations have changed from java's BufferedReader to TextBuffer. Functionally no change. All targets now use TextBuffer the same way for block, line-based, or next-based (parsing) operations.
- TextFile now makes previously internal TextBuffer as an accessible property. Allows use of TextBuffer parsing functions in a consistent way across all platforms. For now there are no TextFile convenience wrappers for TextBuffer parsing functions.
- Charset.fromName is now case insensitive on name and alias matches

### 0.2.3 (2025-08)

- On Android and Jvm, if the filePath is a Uri or a relative path, the fullPath attribute gets a spurious separator added as the first character. On native platforms fullPath is correct. Fix Android and jvm fullPath attribute to work consistent with native platforms.
- 0.2.2 was published incorrectly to Maven. The linuxX64 and linuxArm64 target libraries were published without the correct metadata, preventing Gradle from resolving these.
- Gradle 9.0.0

### 0.2.2 (2025-07-15)

- Added a Uri class - pure Kotlin implementation, so works the same for all targets. This is useful for for passing file paths as Uri instances across all platforms. Thanks to https://github.com/chRyNaN/uri for the starting code. Uri class also contains a Builder class for building URI strings.
- Fix issue #23 affecting zip entries that have General Purpose bit 3 set. Some implementations set bit 3 in General Purpose bits indicating presence of a data descriptor record after the data in an entry, then don't add the data descriptor. Changed fun ZipDataDescriptor.decode(), see its comment for details on the new algorithm.  

### 0.2.1 (2025-07-11)

- Fix issue #20. The expected length for an NTFS extra field was 16 and should have been 24 bytes (three 8-byte long epoch timestamps). Also the timestamps are now being properly converted from Windows FileTime to LocalDateTime. Also made timestamps available as Instant instances.
- Gradle 8.14.3
- kotlinx-datetime 0.7.1
- Fix issue #22. Apple RawFile write functions were managing NSData pointers incorrectly causing a memory error (double free) in the IOS Simulator during GC

### 0.2.0 (2025-07)

- Kotlin 2.2.0
- Added directoryFiles() suspend function to File class, files() suspend function to Directory class
- Make baseNames the same across all apple targets. IOS was defaulting to Gradle project name, which no longer matches the framework name.
- Fix bug in jvm where java.io.File.pathSeparator ":" was being used, should have been java.io.File.separator which is "/" or "\" depending on platform
- Fix android to be consistent with other targets when using suspend fun listFiles(). So listFiles() now returns only the name of each entry, not the full path. Use suspend fun directoryFiles() to get a list that contains one File instance for each entry.
- Fix issue #4 which was causing an error during garbage collection on IOS and IOS Simulator (not unit tests).

### 0.1.9 (6/25/2025)

- Gradle rootProject.name and project name changed to match mavenArtifactID of "kmp-io". See settings.gradle.  Turns out changing to the Vannik publishing plugin somehow uses project.name instead of the maven artifact ID specified in the configuration clause when it derives the "name" value in the "files" clause of each .module file created. This leads to bad URLs in any project trying to use 0.1.8 as a dependency:
  - https://repo.maven.apache.org/maven2/io/github/skolson/KmpIO-macosx64/0.1.8/KmpIO-macosx64-0.1.8.pom gets an error since it doesn't exist. URL should have been:
  - https://repo.maven.apache.org/maven2/io/github/skolson/kmp-io-macosx64/0.1.8/kmp-io-macosx64-0.1.8.pom
  - In 0.1.8 (and all prior), "KmpIO" was used as the root project name and the project name since that was the original directory name for the project.
  - See [Vanniktech issue 1020](https://github.com/vanniktech/gradle-maven-publish-plugin/issues/1020)
- Fix issue #16
- kotlinx-datetime 0.7.0 (just released). This required changing Instant and Clock to the kotlin.time package as the ones in kotlinx-datetime are being deleted as redundant.
- Fix Issue #4 that was causing an error during garbage collection on IOS and IOS Simulator (not unit tests). 

### 0.1.8 (6/23/2025)

This release is problematic due to a maven publishing issue. See 0.1.9 above.

This release is after a long period of inactivity on the author's part (family health issues). So there is a lot of change, some of which is breaking.
- Gradle 8.14.2
- Kotlin 2.1.21
- Android build tools 36.0.0
- Dokka V2.0.0 (currently breaks signing, see note below)
- kotlinx-coroutines "1.10.2"
- kotlinx-datetime "0.6.2" (note native runs had stability issues with repeated calls to ```TimeZone.currentSystemDefault()``` so it is no longer used)
- LinuxX64 native support. This includes various basic posix functions and zlib support for zip file compression/decompression
- Added the beginnings of IOS-specific unit tests
- Breaking change to Charsets and Charset support.  Charsets are implemented in pure KMP code (no cinterop or jvm dependencies). Supported is limited to a few common charsets, including UTF8 (Kotlin), UTF16LE, UTF16BE, UTF-32, ISO-8859-1, and Windows1252. New charsets are easy to add.
- File class has had changes to make it almost entirely immutable (still one exception to this that is linux-specific). File will still need some enhancing to properly support file/directory permissions at creation time
- A pure Kotlin Path class is added to make file path handling consistent across all targets.
- the path property on the File class has been deprecated. directoryPath holds the owning directory of the File instance. fullPath holds the entire path string to the File instance.
- A pure Kotlin TextBuffer class has replaced prior logic underlying the various TextFile implementations.
- Made a new Directory class, also pure Kotlin, that uses the native File implementation to support directory contents lists, walking a directory tree, deleting populated directories, etc. Also moved path/name property parsing logic to a new pure Kotlin Path class. Actual FIle implementations now much more focused on the underlying native code required to provide the various File properties and functions.
- Moved temporary directory support to File class companion object. The previous File extension function is gone. Added new companion methods for current working directory.
- Native zip file inflate/deflate logic for Linux and Apple refactored to improve memory management. Both Linux and Apple use identical zlib logic.
- inflate now attempts to detect and correctly process zlib header bytes. These are not typical but can be present.
- The zipDirectory function has been re-written to use the new Directory class walkTree function. This corrects a prior problem with path-relative names. It also now enforces use of forward slash as a path separator no matter what platform it is run on.
- The Apple native code base as been refactored to remove the base class usage that was developed before Kotlin releases the default source set tree for multi-platform.  All Apple classes for file management are now in AppleMain.
- Since ZipFile uses the new Directory class for walking a directory tree, this fixes issues #13 and #15.
- Issue #12 fix - documentation example for creating a Zip64 file is updated
- ZipFile now takes a third constructor argument that allows optionally choosing Zip64 support at constructor time. default for the argument is false.
- A number of new unit tests have been built to better test File and ZipFile basic functions for Linux, Apple, Android, and JVM.
- An IOS/macOS stability issue was seen with using kotlinx.datetime library's defaultTimeZone lookup function multiple times. I never diagnosed the root cause. There is now a simple platform-specific TimeZones class in the library that uses native code to look up the default time zone.  The rest of kotlinx-datetime seems to work fine. Even the default time zone lookup worked, but sometimes after multiple usages unit tests would fail inside the lookup with an abort trap (signal 6).
- Issue #10 is fixed (new Path class support) for JVM on Windows.
- Stopped using the publish and signing plugins directly. Now using vanniktech plugin for publishing and signing. Avoids issues with both the new HTML doc jar task and signing, as well as migrating publishing to central.sonatype.org   (OSSRH is gone after 6/30/2025) 

*Note 6/21/2025:* Currently native unit tests run in Android Studio up to Narwhal Canary 6 fail with an error that is in the test runner infrastructure:

> No such property: getStartTimeMillis for class: org.gradle.api.internal.tasks.testing.results.DefaultTestResult

This is fixed in Idea Ultimate 2025.2 EAP, where unit tests run fine. See Jetbrains issue: https://youtrack.jetbrains.com/issue/KMT-978/AS-Common-tests-fail-for-ios
Don't know when Android Studio will incorporate this fix.

### 0.1.7

- Kotlin 1.9.23
- AGP 8.5.0-alpha02
- no functional changes

### 0.1.6

- Kotlin 1.9.22
- Gradle 8.6
- AGP 8.4.0-alpha09
- License.md containing Apache 2.0 license text
- no functional changes

### 0.1.5

- Kotlin 1.9.21
- Gradle 8.5
- Gradle version catalog used for build
- AGP 8.3.0-alpha1
- kotlinx.datetime 0.5.0
- Some sourceset names changed to conform to Default Kotlin Hierarchy Template. 
- New targets iosSimulatorArm64, macosArm64 added, but not tested
- fixed bug with bad ZipFile compress/uncompress support on Apple targets (payloads larger than 4k failed). New logic passes simple unit tests
- no other functional changes

### 0.1.4

- Kotlin 1.9.10
- atomicfu 0.22.0
- coroutines 1.7.3
- AGP 8.3.0-alpha03
- Gradle 8.3
- kotlinx.datetime 0.4.1
- no functional changes
- New native opt-in attributes added to some classes
- Change android sourceset names for test and instrumented test to new conventions
- javadoc name workaround for https://github.com/gradle/gradle/issues/26091

### 0.1.3

- Kotlin 1.7.10
- kotlinx coroutines 1.6.4
- Gradle 7.5.1
- Build changes for maven/sonatype publishing

### 0.1.2

- Added jvm platform support
- fix UByeBuffer slice bad endIndex
- Gradle 7.4.1, AGP 7.2.0-beta04
- Unit test for BufferReader
- Additional RawFile read methods, optional buffer reuse assistance on existing reads
- Added Buffer expand(size:Int) 
- Base64 repair, javadoc, Base64 unit tests

### 0.1.1 (2022-03)

- Fix CRC check when data descriptor is present
- add last: Boolean argument to various readEntry methods to indicate last call - all data for entry is read. 
- renamed ZipEntry property "entryDirectory" to "directories" 

### 0.1.0 (2022-02)

- Existing full Android support
- IOS and Mac using Kotlin Native and new memory model
- Kotlin 1.6.10
- Kotlin coroutines 1.6.0
- Usable as a cocoapods framework with Mac and IOS Xcode projects
- Zip and Zip64 support
  - Compression - DEFLATE (from zip specification) or None to start

This library has been in use for more than a year in Android, but IOS support is new. Once IOS support is passing unit tests, the repo will be tagged with the initial release.