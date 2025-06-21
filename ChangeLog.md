# Change Log

### 0.1.8 (in progress)

*Note 6/21/2025:* a number of commits are pushed up for 0.1.8, but due to a gradle configuration issue with the Dokka V2.0.0 HTML jar task for each target, publishToMavenLocal is failing with a gradle cross-dependency error during the signing plugin.  Until this build/publish signing issue is fixed, 0.1.8 won't be tagged and published to Maven. It was pushed up as a re-creator repo for people assisting with researching this.

This release is after a long period of inactivity on the author's part (family health issues). So there is a lot of change, some of which is breaking changes.
- Gradle 8.14.2
- Kotlin 2.1.21
- LinuxX64 native support. This includes various basic posix functions and zlib support for zip file compression/decompression 
- Added the beginnings of IOS-specific unit tests
- Breaking change to Charsets and Charset support.  Now pure KMP code (no cinterop or jvm dependencies). Supported is limited to a few common charsets, including UTF8 (Kotlin), UTF16LE, UTF16BE, UTF-32, ISO-8859-1, and Windows1252. New charsets are easy to add.
- File class has had changes to make it almost entirely immutable (still one exception to this that is linux-specific). File will still need some enhancing to properly support file/directory permissions at creation time
- A pure Kotlin TextBuffer class has replaced prior logic underlying the various TextFile implementations.
- Made a new Directory class, also pure Kotlin, that uses the native File implementation to support directory contents lists, walking a directory tree, deleting populated directories, etc. Also moved path/name property parsing logic to a new pure Kotlin Path class. Actual FIle implementations now much more focused on the underlying native code required to provide the various File properties and functions.
- Moved temporary directory support to File class companion object. The previous File extension function is gone.
- Native zip file inflate/deflate logic for Linux and Apple refactored to improve memory management.
- inflate now attempts to detect zlib header bytes
- The zipDirectory function has been re-written to use the new Directory class walkTree function. This corrects a prior problem with path-relative names. It also now enforces use of forward slash as a path separator no matter what platform it is run on.
- The Apple native code base as been refactored to remove the base class usage tht was developed before Kotlin releases the default source set tree for multi-platform.  All Apple classes for file management are now in AppleMain.
- ZipFile uses the new Directory class for walking a directory tree. This fixes issues #13 and #15.
- Issue #12 fix - documentation example for creating a Zip64 file is updated
- ZipFile now takes a third constructor argument that allows optionally choosing Zip64 support at constructor time. default for the argument is false. 
- A number of new unit tests have been built to better test File and ZipFile basic functions.
- An IOS/macOS stability issue was seen with using kotlinx.datetime library's defaultTimeZone lookup function multiple times. I never diagnosed the root cause. There is now a simple platform-specific TimeZones class in the library that uses native code to look up the default time zone.  The rest of kotlinx-datetime seems to work fine. Even the default time zone lookup worked, but sometimes after multiple usages unit tests would fail inside the lookup with an abort trap (signal 6).

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