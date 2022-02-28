Pod::Spec.new do |spec|
    spec.name                     = 'KmpIO'
    spec.version                  = '0.1.0'
    spec.homepage                 = 'https://github.com/skolson/KmpIO'
    spec.source                   = { :git => "Not Published", :tag => "Cocoapods/#{spec.name}/#{spec.version}" }
    spec.authors                  = 'Steven Olson'
    spec.license                  = 'Apache 2.0'
    spec.summary                  = 'Kotlin Multiplatform API for basic File I/O'

    spec.vendored_frameworks      = "build/cocoapods/framework/KmpIO.framework"
    spec.libraries                = "c++"
    spec.module_name              = "#{spec.name}_umbrella"

    spec.ios.deployment_target = '14'

                

    spec.pod_target_xcconfig = {
        'KOTLIN_PROJECT_PATH' => ':KmpIO',
        'PRODUCT_MODULE_NAME' => 'KmpIO',
    }

    spec.script_phases = [
        {
            :name => 'Build KmpIO',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                if [ "YES" = "$COCOAPODS_SKIP_KOTLIN_BUILD" ]; then
                  echo "Skipping Gradle build task invocation due to COCOAPODS_SKIP_KOTLIN_BUILD environment variable set to \"YES\""
                  exit 0
                fi
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/../gradlew" -p "$REPO_ROOT" $KOTLIN_PROJECT_PATH:syncFramework \
                    -Pkotlin.native.cocoapods.platform=$PLATFORM_NAME \
                    -Pkotlin.native.cocoapods.archs="$ARCHS" \
                    -Pkotlin.native.cocoapods.configuration=$CONFIGURATION
            SCRIPT
        }
    ]
end