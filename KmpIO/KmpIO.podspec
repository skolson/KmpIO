Pod::Spec.new do |spec|
    spec.name                     = 'KmpIO'
    spec.version                  = '0.2.3'
    spec.homepage                 = 'https://github.com/skolson/KmpIO'
    spec.source                   = { :http=> ''}
    spec.authors                  = 'Steven Olson'
    spec.license                  = 'Apache 2.0'
    spec.summary                  = 'Kotlin Multiplatform API for basic File I/O'
    spec.vendored_frameworks      = 'build/cocoapods/framework/KmpIO.framework'
    spec.libraries                = 'c++'
    spec.ios.deployment_target    = '14'
                
                
    if !Dir.exist?('build/cocoapods/framework/KmpIO.framework') || Dir.empty?('build/cocoapods/framework/KmpIO.framework')
        raise "

        Kotlin framework 'KmpIO' doesn't exist yet, so a proper Xcode project can't be generated.
        'pod install' should be executed after running ':generateDummyFramework' Gradle task:

            ./gradlew :kmp-io:generateDummyFramework

        Alternatively, proper pod installation is performed during Gradle sync in the IDE (if Podfile location is set)"
    end
                
    spec.xcconfig = {
        'ENABLE_USER_SCRIPT_SANDBOXING' => 'NO',
    }
                
    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':kmp-io',
        'PRODUCT_MODULE_NAME' => 'KmpIO',
    }
                
    spec.script_phases = [
        {
            :name => 'Build KmpIO',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
                  echo "Skipping Gradle build task invocation due to OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration="$CONFIGURATION"
            SCRIPT
        }
    ]
                
end