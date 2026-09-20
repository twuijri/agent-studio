# Core Hub Mobile — Design & Navigation Spec (from the web client, 2026-09-19)

Source of truth: `packages/client/src/styles/variables.scss`, `theme.ts`,
`components/layout/PageSidebarNav.vue`, `AppSidebar.vue`, `SessionListItem.vue`,
`ChatPanel.vue`, `MessageItem.vue`, `ChatInput.vue`. Mobile must implement these
values once per platform as central tokens (never scattered constants).

## Palette ("Pure Ink", greyscale; no hue in the base palette)
| Token | Light | Dark |
|---|---|---|
| bg.primary (page) | #fafafa | #1a1a1a |
| bg.secondary | #f0f0f0 | #252525 |
| bg.sidebar | #f5f5f5 | #202020 |
| bg.card | #ffffff | #2a2a2a |
| bg.cardHover | #fafafa | #333333 |
| bg.input | #ffffff | #2a2a2a (composer card dark: #333333) |
| border | #e0e0e0 | #3a3a3a |
| border.light | #ebebeb | #333333 |
| accent | #333333 | #e0e0e0 |
| accent.hover | #1a1a1a | #f5f5f5 |
| accent.muted | #888888 | #888888 |
| text.primary | #1a1a1a | #e0e0e0 |
| text.secondary | #666666 | #a0a0a0 |
| text.muted | #999999 | #888888 |
| success / error / warning | #2e7d32 / #c62828 / #f57f17 | #66bb6a / #ef5350 / #ffb74d |
| info | #4a90d9 | #6ba3d6 |
| msg.user / msg.assistant bg | #f5f5f5 | #292929 |
| code bg | #f4f4f4 | #1e1e1e |
| text.onAccent | #ffffff | #1a1a1a |
| splash/theme color | #f7f7f4 | #1a1a1a |
State formulas: hover = accent @ 6% alpha; active/selected = accent @ 12% (+ text.primary, weight 500);
input border idle accent@18%, hover accent@32%, focus solid accent; text selection accent@30%.

## Typography (Inter/system sans; JetBrains Mono/monospace for code)
base 14 (user-adjustable 12–20), body line-height 1.6; message body 14/1.65; page & chat title 16/600;
nav item 14; page-sidebar tab 13; session title 13; author name 12; meta/time/tool line 11;
group header 10/600 uppercase +0.5 letter-spacing; category tag 10; code 13/1.5 mono; thinking 13 italic 85%.
Inputs never below 16px on phones (no zoom).

## Radii & shadows
6 (buttons, nav items, tool rows, code block, session row), 8 (naive default, tool summary header),
10 (message bubble), 14 (cards: sidebar, session list, chat main), 18 (composer card), 999 (pills),
4 (category tag, workspace chip), 5 (segmented tab). Card shadow 0 8 24 rgba(0,0,0,.10);
composer 0 8 28 rgba(0,0,0,.08) (dark .32); focused 0 10 32 .11. Transitions 150ms / 250ms ease.
Sidebar width 240, collapsed 64, header 60, breakpoint 768, safe-area insets respected.
On phones the chat surface has NO card border/radius/shadow (full-bleed).

## Navigation (mirror the web's mobile behaviour: off-canvas drawer + hamburger, 250ms slide, 40% scrim)
Primary rail (top of the session-list drawer), icons 24-viewBox stroke 1.8 round:
1. New Chat / محادثة جديدة  (+)   2. Search / بحث (⌘K)   3. Device connections / ترابط الأجهزة
4. Computer apps / برامج الجهاز (desktop only — hide on mobile)   5. Agent Manager / إدارة الوكلاء (super-admin only)
6. Models / النماذج
Conversation switch (4 segments): Chat / محادثة · Group Chat / محادثة جماعية · Workflow / مسار العمل · History / السجل.
Track accent@5% at radius 7 (segment radius + its 2px inset) with 2px of padding; segments 2px apart, each
the base 30px plus 12px for a 16px icon sitting 2px above its label; label at the group-header size (10),
semibold when selected, regular otherwise, text.primary vs text.secondary. The selected segment is a bg.card
thumb at radius 5 that slides to the selection in 150ms, in the reading direction. Outer gutter 12 × 8.
The switch picks what the drawer's list shows — sessions, rooms, workflows — in place; the drawer stays open
and only a row navigates. History is a page of its own and closes the drawer.
Drawer shell: width min(300, 84% of the screen), square with a 1px border rule down its outer edge, 18px
edge-swipe strip to open. Header 60 tall inside a 14 gutter: 26 app mark, 10 gap, 16/600 title, 20 close
glyph in a 34 target. Rail rows 36 tall, 2 apart, 20 icon + 10 gap, inside an 8 gutter with a 4 lead-in;
an unselected row is text.secondary.
Drawer list per segment: sessions (RECENT + groups), rooms (GROUP CHAT n, with “New room” and “Join by code”
at 14 in 24 targets; row = 18 avatars, 10 gap, title 13 … time 11, then “n agents  n members” 11 muted),
workflows (WORKFLOW n; row = 16 icon, name 13, node count 11).
Drawer footer (12 × 10, rows 6 apart): profile and model chips side by side (30 tall, radius 6, bg.card on a
1px border, 18 avatar / 14 icon, label 13, 9 up-down chevron); Sign Out as a 34 pill (bg.card, capsule,
accent@18% border, 12 glyph, 13/500) with the username chip beside it (11, 22 tall, capsule, accent@6%) and
the settings gear (18 in 32) at the end; connection dot 7 + label 11, then the language and theme toggles
(28 × 24, radius 4, accent@6%; language shows “A” / “EN” / “ع”); version 11 muted forced LTR + GitHub 14.
Settings drawer (AppSidebar) order: Logs/السجلات, Usage/الاستخدام, Performance/الأداء (super-admin), Skills Usage/استخدام المهارات,
Theme/المظهر, Pets/الحيوانات الأليفة, Profiles/البروفايلات (super-admin), Settings/الإعدادات; then ProfileSelector, ModelSelector;
footer: Sign Out/تسجيل الخروج (+username chip), status dot Connected/متصل · Disconnected/غير متصل + language switch,
"Core Hub v{version}" + GitHub link + theme switch; "Back/رجوع" to chat.
Settings page tabs: Current Account/الحساب الحالي, Account Management/إدارة الحسابات (sa), Webhooks/خطافات الويب (sa),
Display/العرض, Proxy/البروكسي, Compression/الضغط, Privacy/الخصوصية, Models/النماذج.
Icon paths (24 viewBox): New chat `M12 5v14 M5 12h14`; Search `circle 11,11 r7 + m20 20-3.5-3.5`;
Device connections `circles (18,5)(6,12)(18,19) r2.5 + m8.2 10.7 7.6-4.4M8.2 13.3l7.6 4.4`;
Agent Manager `M12 8V4H8 + rect 4,8 16x12 rx3 + M2 14h2M20 14h2M9 13v2M15 13v2`;
Models `circle 12,12 r3 + M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9 7 7M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1`;
Chat `M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z`;
Group `M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 + circle 9,7 r4 + M22 21v-2a4 4 0 0 0-3-3.87 + M16 3.13a4 4 0 0 1 0 7.75`;
Workflow `circles (5,12)(19,6)(19,18) r3 + M8 12h3a4 4 0 0 0 4-4V6 + M8 12h3a4 4 0 0 1 4 4v2`; History `circle 12,12 r9 + M12 7v5l3 2`;
Settings gear `circle 12,12 r3 + M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z`.

## Session list
Row: padding 8×10, radius 6, two lines. Line 1: [pin 11px accent] [unread dot 6px accent + 12% halo] title 13 (dir=auto, ellipsis) … time 11 muted
(same day → HH:mm, else "Sep 18"). Line 2: agent avatar 18px circle (1px white border; streaming = animated rainbow ring),
profile chip (16px avatar + name 11 muted), category tag (10, padding 1×6, radius 4, bg #7f7f7f@12%, max 45%).
Long-press 500ms → context menu (rename, category, archive, delete). Delete ✕ visible at 50% on touch.
Groups: RECENT (10 default, 1–100, gear opens count) → Pinned → categories (⋯ menu) → Uncategorized/غير مصنّف.
Group header: chevron 10px (rotated 90° when expanded), label 10/600 uppercase, count 10 muted.

## Agent Manager, the agent screen and settings rows (both phones; Android `AgentManagerScreen.kt` is the reference)
List: 16 gutter, 8 top / 28 bottom, items 10 apart. Intro line 12 text.secondary; "runs on the server" note 12 on info@10% at radius 12 with 12 padding;
section headers `Hermes runtime` · `Built in` · `Coding agents` 13/700 text.secondary, 8 above. Card: bg.card, radius 18, 1px border, 16 padding, lines 8 apart:
34 avatar (1px white ring) + 12 gap → name 14/700 over vendor 12 text.secondary; at the trailing edge the state pill (11/600 on success/warning@16%, 10×5)
and, 6 under it, the `Update` pill (11/600 info on info@16%, 12×5; 8% while it runs; the verb alone, never wraps); meta line `Local CLI · v0.154.0 · v0.155.1 available`
12 text.muted; package and path 11 mono text.muted, one line; error 12 error; `Update automatically` 13 + switch; loading row = 22 spinner in 16;
actions row 6 apart, wrapping from the start edge: `Agent settings` outlined pill 40 tall (1px border, 24 padding, 16 gear + 6 gap, 13/500),
text buttons 40 tall / 12 padding (`CLI details`, `Reinstall`/`Install`, `Check for an update`, `Delete` in error). The 40/24/12 are Material's `ButtonDefaults`.
Agent screen: items 12 apart; 40 avatar + 12 gap → name 14/700 over `v0.21.3 · Local CLI` 12 text.muted; note 12 text.secondary; CLI details card and the
capability card = bg.card radius 14, 1px border.light, no padding; capability row = 38 tile (accent@16%, radius 10) holding a 20 glyph, 12 gap, title 14/500,
chevron 24 text.secondary (mirrors in RTL), 12×9 padding; dividers border.light indented 66 (12 in the details card). Settings is the last row.
Settings rows (Hermes › Settings, app Settings on Android): Material tab row 48 tall, tabs equal width, 13/500, text.primary selected / text.secondary,
2px accent indicator, no divider; row = bg.card radius 18 inside a 16×4 margin, 16×14 padding, 24 glyph text.secondary + 14 gap → label 14 over value 11
text.secondary (2 lines), trailing switch or chevron; section label 12/600 text.secondary inside 22/18/22/4; hint 11 inside 20×4. Every change is its own
`PUT` (one key) with the loading row above while it saves and "Saved" after.

## Chat header
Title 16/600 dir=auto (hidden on phones in web; on mobile show it in the app bar instead), workspace chip (folder icon 12, 11/16 muted,
bg white@5%, padding 2×8, radius 4, last path segment), actions ⋯ menu; tool-panel toggle super-admin only.

## Message row (M3 will implement details; tokens apply now)
user: right-aligned, max 75%, bubble msg.user, radius 10, padding 10×14; assistant: avatar 22px + label, max 80%, bubble msg.assistant.
system: 3px inline-start warning border; error: error text on error@6%. Tool summary card "N tools/{count} أداة": 520px max, header 30px, radius 8,
chevron rotates 90°, wrench icon, up to 3 tool names joined " · " (+N), trailing check/•••. Thinking block: 💭 Thinking/التفكير · {duration} · {count} chars, 13 italic 85%.
Action row (always visible on phones): play speech, copy, reference/reply, fork, time (11 muted); buttons 24px radius 6.

## Composer
Card radius 18, min-height 150, padding 22 12 9, shadow; textarea dir=auto 16px on phones, placeholder "Type a message…"/"اكتب رسالة…";
toolbar: [+ attach] [🧠 reasoning pill] [⚙ Settings pill: Voice mode, Show tool calls, Push] [model pill, max 190] … [mic 30px] [send 30px circle accent; stop square while streaming].
On phones pill labels collapse to icons. Context indicator top-right: "45.0k / 256.0k · remaining 211.0k" (ar: متبقٍ), 11 muted, >80% #e8a735, bar 60×4 (42 on phones).

## Branding
Name "Core Hub"; logo `packages/client/public/logo.png`; vector mark `core-hub-mark.svg` (1024 viewBox, #101010: outer C path + rounded 278px core square);
agent avatars `packages/client/public/coding-agents/{hermes.png, ekko-agent.png, claude-code.svg, codex-openai.png, pi.svg, grok.svg, opencode.png, deepseek.svg}`
(copy these assets into the mobile apps; runtime id → file mapping as in `packages/client/src/utils/chat-agent-avatar.ts`).

## Arabic / RTL rules (docs/CONTENT-DIRECTION.md)
Layout direction from the app locale (ar → RTL). Content direction per string: first-strong (dir=auto) with isolation, text-align start;
code/paths/model ids forced LTR; markdown resolved per block (an RTL paragraph can be followed by an LTR code block);
never rewrite stored text, never inject bidi control characters, never force RTL globally, never use "dominant language" heuristics.
All directional spacing must be logical (start/end), icons with direction must auto-mirror.
