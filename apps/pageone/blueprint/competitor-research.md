# PageOne — competitor research (Nov 2026)

## Leading apps
| App | Key features | Layout | Praised | Complaints |
|---|---|---|---|---|
| Google PDF Viewer (2.19, 2020) | view, find, select/copy, forms, comments, reflow, print (Cloud Print, dead) | no launcher, single viewer screen | fast, clean | no file browser, no updates since 2020, no launcher icon |
| Adobe Acrobat Reader | annotate, fill & sign, Liquid Mode reflow, cloud | bottom nav: Home / Files / Shared; FAB | trusted, complete | heavy, account and subscription nags, ads for paid tools |
| Foxit PDF | strong annotate/edit, thumbnails, bookmarks, crop, rotate | Home/Files tabs | rated best overall by TechRadar | many features behind subscription |
| Xodo | tabs for multiple docs, annotation, forms | file browser + recent | free annotation | slow on big files, cluttered toolbars |
| ReadEra | auto-finds all docs, collections, reading progress, no ads | library list, filters | ad-free, offline, big books smooth | fewer edit tools |
| MJ PDF (FOSS) | tiny, fast, dark mode | minimal | speed | very basic |

## Patterns
- Bottom navigation on primary pages; toolbars (docked/floating) on the viewer page. M3 Expressive says not to pair a toolbar with the navigation bar on the same screen.
- "Continue reading" / recent first; quick folder chips (Downloads, WhatsApp, Telegram).
- Reading modes: night, sepia, reflow; thumbnails strip; bookmarks; go-to-page.
- Annotation toolbar opened from an edit FAB (same as androidx.pdf's EditablePdfViewerFragment).

## Engine
- Jetpack androidx.pdf 1.0.0-beta01 (Aug 26 2026): PdfViewerFragment / PdfViewer composable, text selection, search, fast scroll, forms, ink annotations via androidx.ink, highlights; parsing runs out of process (pdf-document-service). Apache 2.0.
- Fallback where the device's SDK extension lacks the PDF APIs: PDFium (BSD/Apache) bindings.
- Avoid MuPDF (AGPL).

## Pitfalls to avoid
- Ads, account walls, upsell nags. Slow first open of big files. Cluttered toolbars. Losing reading position. Saving over the original without asking.
