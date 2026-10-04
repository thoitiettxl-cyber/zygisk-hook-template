// Root build.gradle.kts — chỉ khai báo plugin cho các module con.
// Toàn bộ cấu hình nằm ở zygote/build.gradle.kts.
// (HMA-OSS root phức tạp hơn vì có signing/local.properties; template này giữ tối thiểu.)

plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.com.github.aerathstuff.zygoteloader) apply false
}
