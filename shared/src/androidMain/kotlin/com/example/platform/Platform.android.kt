mkdir -p shared/src/iosMain/kotlin/com/example/platform
cat > shared/src/iosMain/kotlin/com/example/platform/Platform.ios.kt <<'KT'
package com.example.platform

actual fun platformName(): String = "iPhone"
