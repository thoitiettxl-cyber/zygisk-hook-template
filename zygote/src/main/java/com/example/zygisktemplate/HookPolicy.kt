package com.example.zygisktemplate

import android.os.Build
import android.util.Log
import com.v7878.unsafe.invoke.EmulatedStackFrame
import com.v7878.unsafe.invoke.EmulatedStackFrame.RETURN_VALUE_IDX
import com.v7878.unsafe.invoke.Transformers
import com.v7878.vmtools.HookTransformer
import com.v7878.vmtools.Hooks
import java.lang.invoke.MethodHandle
import java.lang.reflect.Executable
import java.util.Collections

/**
 * Policy hook kiểu BulkHooker của HMA-OSS (đã giản lược cho template).
 * Nguồn: HMA-OSS zygote/.../service/BulkHooker.kt + util/ZLUtils.kt.
 *
 * Ba cơ chế an toàn:
 *  1. Fail-open: mọi callback (hook + gọi bản gốc) đều try/catch Throwable riêng —
 *     hook lỗi thì process target vẫn chạy bình thường.
 *  2. Circuit breaker [hooksWasCrashed]: một lần Hooks.hook() crash thì từ chối
 *     mọi hook tiếp theo (tránh cascade).
 *  3. Kill-switch [disabledHooks]: hook bị liệt kê "class->method(argCount)" sẽ bị skip.
 *
 * ⚠️ LƯU Ý verify (xem verify-result.md): EmulatedStackFrame/Transformers thuộc
 * PanamaPort:Unsafe (c0d8fa4, external — định nghĩa gốc chưa có trong source cache).
 * Code dưới chỉ dùng đúng API đã thấy qua usage của HMA/AVT:
 *   frame.type(), frame.accessor(), RETURN_VALUE_IDX, accessor().setValue(idx, value),
 *   Transformers.invokeExactNoChecks(original, frame).
 */
class HookPolicy {

    companion object {
        const val PARAMETER_COUNT_UNKNOWN = -1
    }

    /** Circuit breaker: true sau lần Hooks.hook() đầu tiên crash. */
    @Volatile
    var hooksWasCrashed = false
        private set

    /**
     * Kill-switch: tập "className->methodName(argumentCount)".
     * Hook nào có mặt ở đây sẽ bị skip ở addHook().
     */
    val disabledHooks = Collections.synchronizedSet(mutableSetOf<String>())

    /** Holder cho kết quả hook — port từ HMA ReturnValue. */
    class ReturnValue {
        var replace: Boolean = false   // true = không gọi bản gốc, dùng result
        var result: Any? = null
        var throwable: Throwable? = null
    }

    /**
     * Port nguyên văn HMA ZLUtils.setReturnValue:
     * KHÔNG phải method gốc của EmulatedStackFrame — là extension của HMA.
     * Skip khi returnType là void.
     */
    fun EmulatedStackFrame.setReturnValue(value: Any?) {
        if (type().returnType() != Void.TYPE) {
            accessor().setValue(RETURN_VALUE_IDX, value)
        }
    }

    /**
     * hookBefore: chạy callback TRƯỚC bản gốc.
     * Callback có thể set value.replace = true + value.result để short-circuit
     * (không gọi bản gốc) — dùng cho trường hợp như TammiStealth.
     */
    fun hookBefore(
        clazz: String,
        methodName: String,
        argumentCount: Int = PARAMETER_COUNT_UNKNOWN,
        loader: ClassLoader?,
        parameterTypes: Array<Class<*>>? = null,
        hook: (methodName: String, frame: EmulatedStackFrame, returnValue: ReturnValue) -> Unit,
    ) = applyHook(clazz, methodName, argumentCount, loader, parameterTypes) { original, frame ->
        val value = ReturnValue()

        try {
            hook(methodName, frame, value)
        } catch (it: Throwable) {
            // Fail-open: callback lỗi thì vẫn tiếp tục gọi bản gốc.
            Log.e(ZygoteEntry.TAG, "hookBefore callback crashed: $clazz->$methodName", it)
        }

        if (!value.replace) {
            try {
                invokeOriginal(original, frame, value)
            } catch (it: Throwable) {
                Log.d(ZygoteEntry.TAG, "hookBefore original crashed: $clazz->$methodName", it)
                value.throwable = it
            }
        }

        value.throwable?.let {
            // Stealth (port HMA BulkHooker): xóa frame của policy/framework
            // khỏi stack trace trước khi rethrow.
            clearStackTraces(it)
            throw it
        }

        if (value.replace) {
            frame.setReturnValue(value.result)
        }
    }

    /**
     * hookAfter: chạy bản gốc TRƯỚC, rồi callback có thể sửa kết quả
     * (value.result → ghi lại vào frame).
     */
    fun hookAfter(
        clazz: String,
        methodName: String,
        argumentCount: Int = PARAMETER_COUNT_UNKNOWN,
        loader: ClassLoader?,
        parameterTypes: Array<Class<*>>? = null,
        hook: (methodName: String, frame: EmulatedStackFrame, returnValue: ReturnValue) -> Unit,
    ) = applyHook(clazz, methodName, argumentCount, loader, parameterTypes) { original, frame ->
        val value = ReturnValue()

        try {
            invokeOriginal(original, frame, value)
        } catch (it: Throwable) {
            Log.d(ZygoteEntry.TAG, "hookAfter original crashed: $clazz->$methodName", it)
            value.throwable = it
        }

        if (value.throwable == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Port HMA; defensively skip khi method trả void (chưa verify hành vi getValue trên void).
            if (frame.type().returnType() != Void.TYPE) {
                value.result = frame.accessor().getValue(RETURN_VALUE_IDX)
            }
        }

        try {
            hook(methodName, frame, value)
        } catch (it: Throwable) {
            // Fail-open: callback lỗi thì giữ nguyên kết quả bản gốc.
            Log.e(ZygoteEntry.TAG, "hookAfter callback crashed: $clazz->$methodName", it)
        }

        value.throwable?.let {
            // Stealth (port HMA BulkHooker): xóa frame của policy/framework
            // khỏi stack trace trước khi rethrow.
            clearStackTraces(it)
            throw it
        }

        frame.setReturnValue(value.result)
    }

    /**
     * Gọi bản gốc qua Transformers — port nhánh TIRAMISU+ của
     * HMA BulkHooker.invokeExactCompat (dùng invokeExactNoChecks).
     * applyHook() đã từ chối cài hook trên SDK < 33 nên đến đây luôn là Tiramisu+.
     */
    private fun invokeOriginal(
        original: MethodHandle,
        frame: EmulatedStackFrame,
        value: ReturnValue,
    ) {
        Transformers.invokeExactNoChecks(original, frame)
        // Defensively: chỉ đọc kết quả khi method có return value
        // (hành vi getValue trên void chưa verify trong source cache).
        if (frame.type().returnType() != Void.TYPE) {
            value.result = frame.accessor().getValue(RETURN_VALUE_IDX)
        }
    }

    /**
     * Port HMA ServiceUtils.clearStackTraces (BulkHooker gọi trước mọi `throw it`):
     * lọc bỏ frame của policy/framework khỏi stack trace để không lộ trong
     * exception đi qua hook — biện pháp stealth của HMA.
     * Chỉ dùng stdlib: Throwable.stackTrace getter/setter, String.contains.
     */
    private fun clearStackTraces(throwableIn: Throwable?) {
        var throwable: Throwable? = throwableIn
        while (throwable != null) {
            val newTrace = throwable.stackTrace.filter { item ->
                !item.className.containsAny(
                    "HookPolicy",
                    "com.v7878",
                    "com.example.zygisktemplate",
                ) && item.fileName?.containsAny("r8-map-id-", "dex-id-") != true
            }
            if (newTrace.size != throwable.stackTrace.size) {
                throwable.stackTrace = newTrace.toTypedArray()
            }
            throwable = throwable.cause
        }
    }

    private fun String.containsAny(vararg targets: String): Boolean =
        targets.any { contains(it) }

    /**
     * Resolve Executable rồi gọi Hooks.hook() — port HMA BulkHooker.applyHook
     * (đi lên superclass hierarchy, circuit breaker khi crash).
     */
    private fun applyHook(
        clazz: String,
        methodName: String,
        argumentCount: Int,
        loader: ClassLoader?,
        parameterTypes: Array<Class<*>>?,
        impl: HookTransformer,
    ): Boolean {
        val key = "$clazz->$methodName($argumentCount)"
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Template chỉ target Tiramisu+: từ chối cài hook ngay (không bịa null như trước).
            Log.w(
                ZygoteEntry.TAG,
                "Refusing to install hook $key on SDK ${Build.VERSION.SDK_INT} (< 33), hooks require Tiramisu+"
            )
            return false
        }
        if (key in disabledHooks) {
            Log.i(ZygoteEntry.TAG, "Disabled hook: $key")
            return false
        }
        if (hooksWasCrashed) {
            // Circuit breaker: hook trước đã crash, không apply tiếp.
            return false
        }

        var curClazz: Class<*>? = try {
            Class.forName(clazz, false, loader)
        } catch (ex: ClassNotFoundException) {
            Log.e(ZygoteEntry.TAG, "Class $clazz not found", ex)
            return false
        }

        while (curClazz != null && curClazz != Any::class.java) {
            resolveExecutable(curClazz, methodName, parameterTypes)?.let { executable ->
                try {
                    val addresses = Hooks.hook(
                        executable, Hooks.EntryPointType.DIRECT,
                        impl, Hooks.EntryPointType.DIRECT
                    )
                    Log.d(ZygoteEntry.TAG, "Hooked $key @ $addresses")
                    return true
                } catch (e: Throwable) {
                    Log.e(ZygoteEntry.TAG, "Hook $key crashed!", e)
                    hooksWasCrashed = true
                    return false
                }
            }
            curClazz = curClazz.superclass
        }

        Log.w(ZygoteEntry.TAG, "Executable not found: $key")
        return false
    }

    private fun resolveExecutable(
        clazz: Class<*>,
        methodName: String,
        parameterTypes: Array<Class<*>>?,
    ): Executable? {
        if (methodName == "<init>") {
            return parameterTypes?.let { runCatching { clazz.getDeclaredConstructor(*it) }.getOrNull() }
        }
        return parameterTypes?.let { runCatching { clazz.getDeclaredMethod(methodName, *it) }.getOrNull() }
            ?: clazz.declaredMethods.firstOrNull { it.name == methodName }
    }

    /**
     * Lấy ClassLoader của app target: ưu tiên context classloader của thread hiện tại
     * (trong postSpecialize main(), thread này đã ở trong process target).
     * TODO verify on device: confirm loader này thấy được class của Tammi.
     */
    fun appClassLoader(): ClassLoader? =
        Thread.currentThread().contextClassLoader
            ?: ZygoteEntry::class.java.classLoader
}
