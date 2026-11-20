# InkNote — competitor research (Nov 2026)

## Leading apps
| App | Strengths | Common complaints |
|---|---|---|
| Samsung Notes | Handwriting + text in one note, PDF annotate, voice recording, templates, page styles (normal/long/landscape), lock, folders with colours, Math Solver, customizable toolbar (One UI 8), search bar moved to bottom + card UI (One UI 8.5) | Only works fully on Galaxy devices; lags with long, photo-heavy notes; drawing tools limited vs art apps; sync tied to Samsung Cloud |
| Google Keep | Fast, simple, labels, colours, reminders, OCR in images | No real handwriting notebook, no PDF annotate |
| Microsoft OneNote | Free-form canvas, sections/pages, ink + text, audio | Heavy, slow on phones, cluttered UI |
| Squid | Vector ink, paper backgrounds, PDF markup, stylus-only mode | Text features weak, premium paywall |
| Notewise / Notein | Infinite canvas, PDF tools, lasso, shape recognition | Paywall, tablet-first; phone UI cramped |
| MyScript Notes (Nebo) | Best handwriting→text conversion, AI | Paid features, conversion needs specific workflow |
| Notability / Goodnotes | Audio synced to ink, templates, shapes | iPad-first; Android versions newer and less mature |

## Patterns to follow
- Home: grid of note thumbnails with title + date; FAB "+" bottom-right; drawer for folders on phone, permanent side panel on tablet.
- Editor: top bar with back, title, undo/redo, more; bottom (phone) / top (tablet) toolbar with pen tools, customizable order.
- Search reachable near the bottom (Samsung One UI 8.5).
- Recently deleted kept 30 days.

## Pitfalls to avoid
- Lag with long notes → tiled/incremental ink rendering, lazy-loaded images, paging.
- Lock-in → open backup format (zip with JSON + assets), export PDF/image.
- Cramped phone toolbars → collapsible tool groups, customizable toolbar.
- Paywalls → everything free.
