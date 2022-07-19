# Change Log

### 0.1.3 (in progress)

- Kotlin 1.7.10
- kotlinx coroutines 1.6.4
- Gradle 7.5

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