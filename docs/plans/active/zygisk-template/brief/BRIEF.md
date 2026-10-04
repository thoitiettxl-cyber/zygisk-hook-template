# BRIEF — Coordinator chiến dịch zygisk-hook-template

Bạn là COORDINATOR. Đọc PLAN.md cùng thư mục trước khi làm gì khác.

## Nhiệm vụ
Verify vật liệu rồi scaffold repo mẫu `~/workspace/zygisk-hook-template/` — Zygisk module
thuần Java theo kiến trúc HMA-OSS, làm vehicle cho stealth Tammi.

## Bối cảnh kỹ thuật (đã verify, không cần làm lại)
- Tammi (`com.viettel.appviettel`) tự sát khi có LSPosed module trong scope:
  `HookDetector.isHooked()` quét stack trace tìm "xposed"/"XposedBridge"/"substrate" + ZygoteInit×2.
  Native `checkSecurity()` chỉ gọi ngược 3 check Java qua JNI. Chi tiết:
  `~/workspace/research/tammi-antitamper/REPORT.md`.
- Mọi module LSPosed trong scope đều bị bắt → cần vehicle khác: Zygisk module hook
  native (không sinh Java frame "xposed").
- Boss đã chọn stack aerath-stuff (quyết định trong PLAN.md).

## Chi tiết kỹ thuật đã thu thập (dùng làm điểm xuất phát, VERIFY lại bằng grep source)
1. **ZygoteLoader** — plugin `com.github.aerath-stuff.ZygoteLoader` v`2a2160f` (JitPack).
   DSL trong `zygote/build.gradle.kts`:
   ```kotlin
   zygisk {
       packages("com.viettel.appviettel")   // hoặc ZygoteLoader.PACKAGE_SYSTEM_SERVER ("android"), ZygoteLoader.ALL_PACKAGES (".all")
       id = "..."; name = "..."; author = "..."; description = "..."
       entrypoint = "com.example.zygisktemplate.ZygoteEntry"
       archiveName = "..."; updateJson = "..."; isAddVariantToArchiveName = true
   }
   ```
   Build: `<module>:assembleRelease` → zip trong `build/outputs/magisk`.
2. **Entrypoint** — class có `public static void premain()` và `public static void main()`,
   annotate `@DoNotObfuscate`/`@DoNotShrink` (`com.v7878.r8.annotations`).
   (Mẫu: HMA-OSS `zygote/.../ZygoteEntry.java`.)
3. **AndroidVMTools** — submodule `external/AndroidVMTools` pin `6fa2d5cbbf51c96fb81a9b5d96fdd74beac4aafe`;
   compile trực tiếp `src/main/java` (11 file `com.v7878.vmtools.*` + `com.v7878.ti.*`).
   Deps từ `gradle/libs.versions.toml` của nó: R8Annotations, SunCleanerStub,
   PanamaPort (Core/Unsafe/LLVM) qua JitPack.
4. **Hook API** — `Hooks.hook(target, EntryPointType.DIRECT, transformer, EntryPointType.DIRECT)`;
   transformer `(original: MethodHandle, frame: EmulatedStackFrame) -> Unit`;
   giả kết quả bằng `frame.setReturnValue(...)`; gọi bản gốc bằng
   `Transformers.invokeExact(original, frame)`. (Mẫu: HMA-OSS `BulkHooker.kt`
   — `hookBefore`/`hookAfter`, fail-open try/catch, `hooksWasCrashed` circuit breaker,
   `disabledHooks` kill-switch, `ServiceUtils.clearStackTraces`.)
5. **Ví dụ Tammi** — trong process `com.viettel.appviettel`, hook
   `vn.com.viettel.vds.vanillalib.app.security.HookDetector.isHooked(Context): boolean`
   → luôn trả `false`, không gọi bản gốc. Lưu ý: class có thể chưa load khi `main()`
   chạy → xử lý bằng retry/`ClassLoaderHooks.hookFindClass()` (verify API trong source).

## Cách làm việc (luật đứng)
- 1 coordinator (bạn) fan-out workers; workers KHÔNG spawn con.
- Worker nộp kết quả ra file trong `docs/plans/active/zygisk-template/scratch/` TRƯỚC khi
  handoff (FILE là kênh chính). Im >10 phút thì bạn chủ động kiểm tra.
- Kỷ luật edit: worker không tự commit bừa — gom batch, reviewer fresh-eyes soi diff,
  rồi mới commit. Mọi commit local, KHÔNG push.
- Không chạy Gradle trong sandbox (daemon không chạy được) — verify bằng grep source
  + đọc code, ghi rõ cái nào chưa verify bằng build thật.
- Fable-check: claimed done phải có evidence (file tồn tại, grep hit); cùng lỗi lặp 2 lần
  thì đổi cách.

## Worker gợi ý
1. **verifier** — B1: đối chiếu từng API/claim (plugin id, DSL fields, `packages()`,
   `Hooks.hook` signature, `EmulatedStackFrame` API, submodule pin) với 3 source cache;
   xuất `scratch/verify-result.md` (bảng claim → evidence `file:line` / MISSING).
2. **builder** — B2: scaffold toàn bộ file theo PLAN (chỉ dùng claim đã VERIFIED;
   chỗ MISSING thì viết code defensively + comment `// TODO verify on device`).
   Xuất `scratch/build-result.md` (danh sách file).
3. **reviewer** (fresh eyes, khác builder) — B3: soi diff theo rubric:
   API có thật trong source? fail-open đúng? docs khớp code? Verdict
   `correct`/`needs attention` + fix queue. Xuất `scratch/review-result.md`.

Xong 3 batch → bạn tổng hợp, commit, báo root tóm tắt + điểm chưa verify.
