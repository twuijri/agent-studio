## Problem and evidence — المشكلة والدليل

<!-- What is broken or missing, and how you reproduced or verified it. Do not just repeat the reporter's diagnosis. -->
<!-- اشرح ما ثبت، وكيف أعدت إنتاجه أو تحققت منه. لا تكتفِ بنقل تشخيص المبلّغ. -->

## Solution and scope — الحل والنطاق

<!-- What changed and why; what you deliberately left unchanged. -->
<!-- ماذا تغير ولماذا؟ وما الذي تعمدت عدم تغييره؟ -->

## Change record — سجل المهمة

<!-- Link to docs/changes/YYYY-MM-DD-owner-short-topic.md in this branch (required; CI checks it). -->
<!-- رابط docs/changes/YYYY-MM-DD-owner-short-topic.md الموجود في هذا الفرع. -->

## Actual checks — الفحوص الفعلية

<!-- Command, result, environment. Say what was not tested and why; never list planned commands as passed. -->
<!-- الأمر، النتيجة، البيئة. اذكر ما لم يُختبر وسببه، ولا تعرض أوامر مخططة كأنها نجحت. -->

## Compatibility, risks, rollback — التوافق والمخاطر والرجوع

<!-- Docker/desktop, data, permissions, API, Arabic/RTL. Write "not applicable" with the reason when needed. -->
<!-- Docker/desktop، البيانات، الصلاحيات، API، العربية/RTL. اكتب «لا ينطبق» مع السبب عند الحاجة. -->

## Approvals and review — الموافقات والمراجعة

- [ ] The requester agreed on the problem and plan before this pull request was opened.
      صاحب الطلب وافق على المشكلة والخطة وفتح هذا الطلب.
- [ ] No out-of-scope edits, secrets, real hostnames, or production data.
      لا توجد تعديلات خارج النطاق أو أسرار أو أسماء نطاقات حقيقية أو بيانات إنتاج.
- [ ] Change record and related docs are updated; user-facing strings exist in every locale.
      السجل والوثائق ذات العلاقة محدثة؛ النصوص موجودة في كل اللغات.
- [ ] License, attribution, and compatibility with existing stacks are preserved.
      الرخصة والنسب وتوافق الستاك القديم محفوظان.
- [ ] Verified against the latest `main`; commit ids and check results are listed above.
      تم التحقق من الدمج مع آخر `main`؛ معرّفات النسخ ونتائج الفحوص مذكورة.
- [ ] High-risk changes were reviewed by another person, or the reason this does not apply is stated.
      مراجع آخر راجع التغييرات عالية المخاطر، أو ذُكر سبب عدم انطباق ذلك.

<!-- Do not tick boxes that are not done. Approval to merge or publish is never implicit. -->
<!-- Only the owner (twuijri) decides and performs merges into main; do not enable auto-merge. -->
<!-- لا تحدد خانات لم تُنجز. صاحب المشروع twuijri وحده يقرر وينفذ الدمج؛ لا تفعّل auto-merge. -->
