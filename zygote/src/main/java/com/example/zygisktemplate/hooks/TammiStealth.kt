package com.example.zygisktemplate.hooks

import android.content.Context
import android.util.Log
import com.example.zygisktemplate.HookPolicy
import com.example.zygisktemplate.ZygoteEntry
import com.v7878.vmtools.ClassLoaderHooks
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Ví dụ worked: stealth Tammi (com.viettel.appviettel).
 *
 * Bối cảnh (verify trong ~/workspace/research/tammi-antitamper/REPORT.md):
 *  - Bật SpoofX (LSPosed module) → Tammi tự sát sau ~1s, không dấu vết.
 *  - Root cause: HookDetector.isHooked() (vn.com.viettel.vds.vanillalib.app.security)
 *    quét stack trace tìm "xposed"/"XposedBridge"/"substrate" + ZygoteInit×2.
 *  - Mọi module LSPosed trong scope đều bị bắt → cần vehicle khác:
 *    Zygisk module hook native (không sinh Java frame "xposed").
 *
 * Mục tiêu: hook
 *   vn.com.viettel.vds.vanillalib.app.security.HookDetector.isHooked(Context): boolean
 *   descriptor (Landroid/content/Context;)Z
 * → LUÔN trả false, KHÔNG gọi bản gốc (không cho native checkSecurity() thấy "hooked").
 *
 * Xử lý class chưa load: HookDetector có thể chưa được load khi main() chạy,
 * nên thử Class.forName trước; nếu chưa có thì hook ClassLoader.findClass bằng
 * ClassLoaderHooks.hookFindClass() (API đã verify trong AVT source)
 * và cài hook ngay khi class được load lần đầu.
 *
 * TODO verify on device:
 *  - thời điểm main() (postSpecialize) so với lần đầu Tammi gọi checkSecurity();
 *  - packages("com.viettel.appviettel") match đúng process.
 */
object TammiStealth {

    private const val TARGET_CLASS = "vn.com.viettel.vds.vanillalib.app.security.HookDetector"
    private const val TARGET_METHOD = "isHooked"

    private val policy = HookPolicy()

    /**
     * Once-guard: hookFindClass bắn cho MỌI class qua loader; nếu findClass(TARGET_CLASS)
     * chạy lần 2 (loader khác / reload), không cài hook trùng lên method đã hook.
     */
    private val hooked = AtomicBoolean(false)

    fun init() {
        val loader = policy.appClassLoader()
            ?: throw IllegalStateException("No app ClassLoader available")

        // Case 1: class đã load → hook trực tiếp.
        val alreadyLoaded = runCatching {
            Class.forName(TARGET_CLASS, false, loader)
        }.getOrNull()

        if (alreadyLoaded != null) {
            hookIsHooked(loader)
            return
        }

        // Case 2: class chưa load → hook findClass, cài hook khi class load lần đầu.
        // Signature đã verify: ClassLoaderHooks.hookFindClass(ClassLoader, FindClassI),
        // FindClassI.findClass(ClassLoader loader, String name, FindClassBackup original).
        Log.i(ZygoteEntry.TAG, "$TARGET_CLASS not loaded yet, deferring via hookFindClass")
        try {
            ClassLoaderHooks.hookFindClass(loader) { _, name, original ->
                val clazz = original.findClass(loader, name)
                if (name == TARGET_CLASS) {
                    try {
                        hookIsHooked(loader)
                    } catch (th: Throwable) {
                        // Fail-open: findClass vẫn phải trả class về bình thường.
                        Log.e(ZygoteEntry.TAG, "Deferred hook of $TARGET_CLASS failed", th)
                    }
                }
                clazz
            }
        } catch (th: Throwable) {
            Log.e(ZygoteEntry.TAG, "hookFindClass failed (fail-open)", th)
        }
    }

    private fun hookIsHooked(loader: ClassLoader) {
        if (!hooked.compareAndSet(false, true)) {
            Log.d(ZygoteEntry.TAG, "$TARGET_CLASS.$TARGET_METHOD already hooked, skipping duplicate")
            return
        }
        // isHooked là static: (Context) -> boolean. Dùng hookBefore + replace để
        // short-circuit: trả false mà KHÔNG BAO GIỜ gọi bản gốc
        // (bản gốc quét stack trace — gọi nó là tự tố cáo).
        val method = resolveTargetMethod(loader)
        val ok = policy.hookBefore(
            clazz = TARGET_CLASS,
            methodName = TARGET_METHOD,
            argumentCount = 1,
            loader = loader,
            parameterTypes = arrayOf(Context::class.java),
        ) { _, frame, value ->
            // Không cần đọc argument — mọi invocation đều trả false.
            value.replace = true
            value.result = false
            Log.d(ZygoteEntry.TAG, "isHooked() spoofed -> false")
        }

        if (ok) {
            // Bằng chứng on-device: đúng overload nào đã bị hook (checklist TAMMI-EXAMPLE.md).
            val target = method?.let { describeTarget(it) } ?: "$TARGET_CLASS.$TARGET_METHOD"
            Log.i(ZygoteEntry.TAG, "Hooked $target -> always false")
        } else {
            Log.w(ZygoteEntry.TAG, "Failed to hook $TARGET_CLASS.$TARGET_METHOD (see logs above)")
        }
    }

    /**
     * Resolve method mục tiêu đi lên superclass hierarchy — mirror logic của
     * HookPolicy.applyHook để log đúng overload đã bị hook.
     */
    private fun resolveTargetMethod(loader: ClassLoader): Method? {
        var clazz: Class<*>? = runCatching {
            Class.forName(TARGET_CLASS, false, loader)
        }.getOrNull()
        while (clazz != null && clazz != Any::class.java) {
            clazz.declaredMethods.firstOrNull { it.name == TARGET_METHOD }?.let { return it }
            clazz = clazz.superclass
        }
        return null
    }

    private fun describeTarget(m: Method): String =
        "$m -> ${m.parameterTypes.contentToString()} : ${m.returnType}"
}
