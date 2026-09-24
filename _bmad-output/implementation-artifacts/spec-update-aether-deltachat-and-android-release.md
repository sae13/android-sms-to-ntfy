---
title: 'بهروزرسانی Aether و Delta Chat و انتشار قابل تشخیص اندروید'
type: 'feature'
created: '2026-09-24'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '5ce03990e38533b545e665dbc6ffa74029effca6'
context: []
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** گوشی نسخهٔ ۱۸ را دارد و انتشارهای جدید نیز با همان `versionCode` منتشر شدهاند؛ بنابراین بهروزرسان داخلی آنها را جدید تشخیص نمیدهد. بستهٔ فعلی Aether نسخهٔ ۱٫۹٫۰ و Delta Chat نسخهٔ ۲٫۵۹٫۰ را دارد، درحالیکه آخرین انتشارهای پایدار بالادست بهترتیب ۲٫۱٫۰ و ۲٫۵۹٫۱ هستند.

**Approach:** هر دو هسته و bindingهای متناظرشان از tag/commit ثابت بالادست بازتولید و در CI راستیآزمایی شوند، نسخهٔ برنامه به ۱٫۰٫۱۹/۱۹ افزایش یابد، و APK واقعی روی گوشی USB آزمایش شود. برای حفظ دادههای نصب فعلیِ debug، build دیباگِ همامضا بهصورت درجا نصب میشود؛ انتشار production جداگانه با کلید پایدار ساخته میشود.

## Boundaries & Constraints

**Always:** Aether از `v2.1.0` در commit دقیق `6398931aeaa551248530cc164aea6d5f2c5fe4a2` و Delta Chat از `v2.59.1` بازتولید شود؛ bindingهای Java و باینری native دلتاچت از یک tag باشند؛ APK شامل مجوز و اعلان منبع هر دو هسته باشد؛ `versionCode` نسبت به ۱۸ صعودی باشد؛ دادهٔ گوشی با نصب درجا حفظ شود؛ آزمون JVM، JNI روی دستگاه، اجرای Aether و ارسال واقعی مقصدهای قابلپیکربندی بدون افشای راز انجام شود.

**Never:** حذف برنامه یا دادهٔ گوشی، نصب APK production با امضای متفاوت روی نصب debug، کپی دستی باینری بدون فرایند بازتولیدپذیر، افشای ورودیهای خصوصی آزمایش، یا push/release بدون تأیید صریح کاربر.

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|---------------|---------------------------|----------------|
| ارتقای گوشی آزمایش | نصب debug نسخهٔ ۱۸ با دادهٔ موجود | نسخهٔ ۱۹ همامضا با `adb install -r` نصب و داده حفظ میشود | اختلاف امضا یا downgrade باعث توقف بدون uninstall میشود |
| ساخت native | دو ABI پشتیبانیشده | Aether ۲٫۱٫۰ و Delta Chat ۲٫۵۹٫۱ برای همان ABI ساخته و داخل APK قرار میگیرند | mismatch نسخه، hash، ABI یا JNI ساخت را شکست میدهد |
| بررسی بهروزرسان | نصب نسخهٔ ۱۸ و release با code ۱۹ | اعلان و لینک APK سازگار با ABI ارائه میشود | release ناقص/نامعتبر نادیده گرفته میشود |
| اجرای واقعی | گوشی ARM64 و تنظیمات خصوصی موجود | Aether اجرا و مسیر واقعی Telegram بررسی میشود؛ JNI دلتاچت account manager را میسازد | شکست هر هسته جداگانه گزارش میشود و برنامه crash نمیکند |

</frozen-after-approval>

## Code Map

- `android-native/app/build.gradle.kts` — منبع نسخهٔ پیشفرض و بستهبندی asset/native؛ نسخه را به ۱۹ ببرد و اعلانهای Delta Chat را در APK قرار دهد.
- `.github/workflows/android-native.yml` — اکنون نسخهٔ ۱۸ را hard-code کرده و Delta Chat را بازتولید/آزمایش نمیکند؛ ساخت هر دو هسته، آزمون JNI و metadata انتشار را همنسخه کند.
- `android-native/scripts/build-aether-android.sh` — pin و toolchain فعلی Aether؛ به commit ثابت ۲٫۱٫۰ و Rust موردنیاز ۱٫۹۸ ارتقا یابد.
- `android-native/scripts/sync-deltachat-android.sh` و `build-deltachat-android.sh` — از تعهد تاریخی `a643edb` فقط بهعنوان مبنا بازیابی و برای ۲٫۵۹٫۱ سختسازی شوند؛ binding و native را باهم تولید کنند.
- `android-native/app/src/main/java/chat/delta/rpc/` و `com/b44t/messenger/` — bindingهای تولیدشده؛ دستی ویرایش نشوند، جز patchهای صریح و آزمودهٔ سازگاری.
- `android-native/app/src/main/jniLibs/` و `app/src/main/assets/` — باینریها و اعلانهای مجوز؛ hash/version واقعی ثبت و حضورشان در APK کنترل شود.
- `android-native/app/src/main/java/com/saebm/smsntfy/aether/AetherProcess.kt` و آزمونهای Aether — سازگاری flags/profiles نسخهٔ ۲٫۱٫۰ را ثابت کنند.
- `android-native/app/src/androidTest/.../deltachat/NativeDeltaChatSmokeInstrumentedTest.kt` — آزمون JNI حذفشده را بازگرداند و ساخت account manager را روی گوشی ثابت کند.
- `android-native/app/src/main/java/com/saebm/smsntfy/update/` — منطق نسخه درست است؛ تست regression ثابت کند code ۱۹ برای نصب ۱۸ قابلتشخیص است.

## Tasks & Acceptance

**Execution:**
- [ ] اسکریپتهای native و pinهای منبع — Aether ۲٫۱٫۰ و Delta Chat ۲٫۵۹٫۱ را برای ARM64/ARMv7 بازتولید و منشأ/hash را ثبت کنند.
- [ ] کد و آزمونهای سازگاری — CLI نسخهٔ جدید Aether و binding/JNI نسخهٔ جدید Delta Chat را بدون تغییر رفتار مقصدها تطبیق دهند.
- [ ] نسخه و CI — ۱۹/۱٫۰٫۱۹ را یکپارچه کند و وجود دقیق باینریها، اعلانها، ABI و JNI را gate انتشار قرار دهد.
- [ ] گوشی USB — ابتدا تستها، سپس نصب درجا debug و بررسی package/version/signature/hash، JNI دلتاچت، launch و Aether data-plane را اجرا کند.
- [ ] انتشار — فقط پس از تأیید کاربر commit/push شود؛ سپس اجرای موفق CI و release production دانلود و امضا/hash آن بررسی شود.

**Acceptance Criteria:**
- Given کد تمیز و toolchainهای pinned، when ساخت اجرا میشود، then تستها پاس میشوند و APK فقط نسخههای native اعلامشده را برای ABIهای هدف دارد.
- Given گوشی دارای نسخهٔ debug ۱۸، when APK debug نسخهٔ ۱۹ نصب میشود، then نصب درجا موفق، داده حفظ، برنامه launch و هیچ خطای fatal/JNI دیده نمیشود.
- Given تنظیمات خصوصی موجود روی گوشی، when آزمونهای native اجرا میشوند، then account manager دلتاچت ساخته و Aether درخواست واقعی Telegram را عبور میدهد.
- Given انتشار production نسخهٔ ۱۹، when نسخهٔ ۱۸ بررسی update میکند، then release جدید و APK ARM64 را تشخیص میدهد.

### Review Findings

- [x] [Review][Patch] Preserve caller-supplied bindgen flags when adding required target-specific flags [android-native/scripts/build-aether-android.sh:104]
- [x] [Review][Patch] Document why bindgen requires target-specific Clang resource arguments [android-native/scripts/build-aether-android.sh:46]
- [x] [Review][Defer] Quote bindgen resource paths so NDK locations containing whitespace remain one argument [android-native/scripts/build-aether-android.sh:103] — deferred, pre-existing
- [x] [Review][Defer] Keep custom LIBCLANG_PATH aligned with the selected Clang resource headers [android-native/scripts/build-aether-android.sh:34] — deferred, pre-existing

#### Dismissed

- Add the Android API suffix to each bindgen target — dismissed because the pinned BoringSSL public headers expose no API-gated declarations, while both required ABI builds pass and native compilation already receives API 24.
- Canonicalize the ARMv7 bindgen target to armv7a-linux-androideabi24 — dismissed because the current triple selects the correct ARM architecture and NDK sysroot declarations; no binding-layout difference was substantiated for the pinned source.
- Restore an explicit bindgen sysroot — dismissed because boring-sys 4.22.0 already adds the selected NDK sysroot for Android, confirmed by source inspection and header tracing.
- Reject multiple Clang resource-directory candidates — dismissed because the pinned NDK contains exactly one candidate and no supported build path reaches the claimed ambiguity.
- Validate the discovered resource directory and stddef.h earlier — dismissed because the pinned directory is readable and complete; the claimed consequence requires a malformed NDK outside the supported pinned toolchain.
- Use -isystem instead of -I for Clang resource headers — dismissed because neither changed warning behavior nor incorrect output was observed; both required ABI builds complete successfully.
- Query clang -print-resource-dir instead of inferring the path — dismissed because the discovered path exactly matches the selected pinned Clang resource directory.
- Expand the missing-resource error message with the path and glob — dismissed because the existing message identifies the failed resource and ANDROID_NDK_HOME; no actionable failure-path defect was substantiated.
- Add architecture and Android API macro preflight assertions — dismissed because the exact pinned dependency builds end to end for both supported ABIs and no current binding defect from those macros was found.
- Add coverage for custom flags, whitespace paths, multiple resource directories, and custom libclang — dismissed as one combined claim because the two confirmed compatibility concerns are recorded separately as deferred pre-existing work, while the remaining scenarios do not occur in the supported pinned toolchain.
- Acceptance-criteria violation — dismissed because the acceptance audit found none in this focused CI repair.
- Verification gap — dismissed because the verification audit found none after the successful two-ABI builds and static checks.

## Implementation Notes

## Spec Change Log

## Review Triage Log

## Design Notes

نصب فعلی گوشی با certificate دیباگ امضا شده و APK عمومی با certificate پایدار release؛ Android ارتقای مستقیم میان این دو را رد میکند. بنابراین اثبات حفظ داده روی همین گوشی فقط با debug همامضا انجام میشود و صحت مسیر production از baseline/امضای release و artifact CI جداگانه اثبات خواهد شد.

## Verification

**Commands:**
- `JAVA_HOME=/usr/lib/jvm/java-17-amazon-corretto ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest --no-daemon --console=plain` — همهٔ تستها و APKها موفق.
- `adb -s N55PRO0000000019435 install -r app/build/outputs/apk/debug/app-debug.apk` — ارتقای درجا بدون حذف داده.
- `adb ... connectedDebugAndroidTest` با کلاسهای native — JNI دلتاچت و Aether روی ARM64 موفق.
- بررسی `apkanalyzer`/`unzip`، hash و اجرای `libaether.so --version` — نسخه، ABI، مجوز و محتویات دقیق.
- `actionlint` و اجرای GitHub Actions پس از push مجاز — build/release و دو APK امضاشده موفق.
