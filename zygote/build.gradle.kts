import com.v7878.zygisk.gradle.ZygoteLoader

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.com.github.aerathstuff.zygoteloader)
}

android {
    namespace = "com.example.zygisktemplate.zygote"

    compileSdk = 36

    defaultConfig {
        applicationId = namespace
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Compile trực tiếp 11 file com.v7878.vmtools.* + com.v7878.ti.* của
    // AndroidVMTools (submodule external/AndroidVMTools, pin 6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe),
    // giống HMA-OSS zygote/build.gradle.kts
    sourceSets {
        getByName("main") {
            java {
                srcDirs(rootDir.resolve("external/AndroidVMTools/src/main/java"))
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

zygisk {
    // Inject vào process Tammi (com.viettel.appviettel).
    // TODO verify on device: confirm packages() match đúng process target
    // (muốn inject system_server thì dùng ZygoteLoader.PACKAGE_SYSTEM_SERVER).
    packages("com.viettel.appviettel")

    // module properties
    id = "zygisk_hook_template"
    name = "Zygisk Hook Template"
    author = "example"
    description = "Template Zygisk module thuần Java (ART hook) theo kiến trúc HMA-OSS — vehicle cho Tammi stealth"
    entrypoint = "com.example.zygisktemplate.ZygoteEntry"
    archiveName = "zygisk-hook-template"
    // TODO verify on device: điền URL updateJson thật khi có release channel
    updateJson = ""
    isAddVariantToArchiveName = true
}

dependencies {
    // Annotation giữ entrypoint khỏi bị R8 strip (DoNotObfuscate/DoNotShrink)
    implementation(libs.io.github.vova7878.r8annotations)

    // ART hook toolkit: PanamaPort Core/Unsafe/LLVM, pin đúng c0d8fa4
    // như AndroidVMTools@6fa2d5c (đổi commit khác có thể vỡ compile vì
    // EmulatedStackFrame/Transformers chưa verify định nghĩa gốc)
    api(androidvmtools.panama.core)
    api(androidvmtools.panama.unsafe)
    api(androidvmtools.panama.llvm)

    implementation(androidvmtools.sun.cleaner)
}
