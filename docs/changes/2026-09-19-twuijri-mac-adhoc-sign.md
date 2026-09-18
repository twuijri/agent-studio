# توقيع ad-hoc لبناء ماك غير الموقّع حتى يعرض «Open Anyway» بدل «تالف»

المسؤول: twuijri
الفرع: fix/mac-adhoc-sign
الحالة: review

## المشكلة والهدف
عند تنزيل التطبيق على ماك يظهر «"Core Hub" is damaged and can’t be opened» ولا
يوجد زر «Open Anyway» في إعدادات الخصوصية، فيضطر كل مستخدم لأمر `xattr` في
الطرفية. أما أغلب تطبيقات GitHub غير الموقّعة فتعرض رسالة «Apple could not
verify…» ويكفي «Open Anyway». السبب: بلا شهادة مطوّر يتخطى electron-builder
التوقيع كلياً، فيبقى توقيع Electron الأصلي (ad-hoc) على الحزمة بعد تغيير
اسمها وإضافة asar، فيصير التوقيع مكسوراً، وGatekeeper يسمي التوقيع المكسور
«تالفاً».

## القرار والموافقات
- الموافقة: المالك (2026-09-19: «لزم نشوف الحل الموقت هذا… اكيد فيه طريقة»).
- **الحل:** خطاف `afterSign` جديد `scripts/adhoc-sign-macos.mjs` يوقّع الحزمة
  النهائية توقيعاً ad-hoc (`codesign --force --deep --sign -`) ثم يتحقق منها،
  فقط عندما لا توجد هوية توقيع (`CSC_LINK`/`MAC_CSC_LINK`/`CSC_NAME`) ولا يحمل
  التطبيق توقيع Developer ID. مع شهادة حقيقية لا يفعل شيئاً. متغير
  `CORE_HUB_SKIP_ADHOC_SIGN=1` يعطّله.
- **النتيجة المتوقعة:** توقيع صالح ⇒ رسالة Gatekeeper العادية مع «Open Anyway»
  في System Settings → Privacy & Security، بدون طرفية. الحل الكامل يبقى
  التوقيع والتوثيق بحساب مطوّر آبل (الأسرار مدعومة في سير العمل أصلاً).
- خارج النطاق: ويندوز (SmartScreen يعرض «Run anyway» أصلاً).

## الملفات والتأثير
`packages/desktop/scripts/adhoc-sign-macos.mjs` (جديد)،
`packages/desktop/electron-builder.yml` (`afterSign`)، `packages/desktop/README.md`
(قسم فتح بناء ماك غير الموقّع)، `tests/desktop/adhoc-sign-macos.test.ts` (جديد).

## الفحوص
- `tests/desktop/adhoc-sign-macos.test.ts`: قرار التوقيع لكل حالة، والربط في
  إعداد electron-builder، وأوامر codesign المستخدمة.
- `tests/desktop` كاملة، `harness:check`، `git diff --check`.
- لا يمكن تشغيل `codesign` هنا (لينكس)؛ التحقق الفعلي عند أول بناء ماك في CI
  (الخطوة تفشل البناء لو فشل التحقق) وبفتح التطبيق على ماك المالك.

## المخاطر والرجوع
- لو رفض `codesign` التوقيع يفشل بناء ماك بدل إنتاج حزمة مكسورة؛ الرجوع: إزالة
  سطر `afterSign` أو ضبط `CORE_HUB_SKIP_ADHOC_SIGN=1` في سير العمل.

## التسليم والخطوة التالية
طلب دمج إلى `main` ثم دمج في `test`. بعد الدمج: إصدار `v1.0.1` وتجربة الفتح على
ماك بدون طرفية.
