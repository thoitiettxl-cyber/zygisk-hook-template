pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        // ZygoteLoader + PanamaPort được publish qua JitPack
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }

    // Tái xuất version catalog của AndroidVMTools (submodule external/AndroidVMTools),
    // giống HMA-OSS: dùng được androidvmtools.panama.core / .unsafe / .llvm,
    // androidvmtools.sun.cleaner, androidvmtools.r8.annotations
    versionCatalogs {
        create("androidvmtools") {
            from(files(rootDir.resolve("external/AndroidVMTools/gradle/libs.versions.toml")))
        }
    }
}

rootProject.name = "zygisk-hook-template"

include(":zygote")
