package com.example.zygisktemplate;

import android.util.Log;

import com.v7878.r8.annotations.DoNotObfuscate;
import com.v7878.r8.annotations.DoNotObfuscateType;
import com.v7878.r8.annotations.DoNotShrink;
import com.v7878.r8.annotations.DoNotShrinkType;
import com.v7878.zygisk.ZygoteLoader;

import com.example.zygisktemplate.hooks.TammiStealth;

/**
 * Entrypoint của Zygisk module, mẫu theo HMA-OSS ZygoteEntry.
 *
 * Contract của ZygoteLoader runtime (com.v7878.zygisk.EntryPoint):
 *  - premain(): gọi TRƯỚC specialize (trong zygote, chưa vào process target)
 *  - main():    gọi SAU specialize (đã ở trong process target, vd com.viettel.appviettel)
 *
 * Cả class + cả hai method BẮT BUỘC public và annotate DoNotObfuscate/DoNotShrink —
 * runtime tìm method bằng Class.getMethod() nên method private sẽ không thấy.
 */
@SuppressWarnings("all")
@DoNotObfuscateType
@DoNotShrinkType
public class ZygoteEntry {
    public static final String TAG = "ZygiskTemplate";

    @DoNotObfuscate
    @DoNotShrink
    public static void premain() throws Throwable {
        // TODO verify on device: quyết định có cần làm gì ở premain không.
        // Hiện tại giữ rỗng — toàn bộ hook chạy ở main() (post-specialize).
    }

    @DoNotObfuscate
    @DoNotShrink
    public static void main() throws Throwable {
        // Fail-open: mọi thứ dưới đây không được crash process target.
        try {
            Log.i(TAG, "Injected into " + ZygoteLoader.getPackageName());
        } catch (Throwable th) {
            Log.e(TAG, "Failed to log package name", th);
        }

        try {
            TammiStealth.INSTANCE.init();
            Log.i(TAG, "TammiStealth init done");
        } catch (Throwable th) {
            // BẮT BUỘC fail-open: hook init lỗi thì process target vẫn chạy bình thường.
            Log.e(TAG, "TammiStealth init failed (fail-open)", th);
        }
    }
}
