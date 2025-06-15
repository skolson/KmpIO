# Change Log

### 0.1.8 (in progress)

- Gradle 8.14.1
- Kotlin 2.1.21
- LinuxX64 native support
- Added the beginnings of IOS-specific unit tests
- Breaking change to Charsets and Charset support.  Now pure KMP code (no cinterop or jvm dependencies). Supported is limited to a few common charsets. New charsets are easy to add.
- 

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