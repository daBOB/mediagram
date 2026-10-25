# Phase 2: web gaps on series, title and department pages

All values the web's px at 1280 through `tvDp`, named in `TvPageSpacing.kt`.

## Todo

- [x] Series and title page: spread→tabs 64, tabs→panel 36, picker→rows 20 (`title-page.css:58,119,133,143-146`)
- [x] Episode/lesson rows: flush, ruled, 56 min height (`catalog.css:76`, `title-page.css:171`)
- [x] Department hero: words at the page's gutter once, not two or three times
- [x] Department Continue rows and Shows' "Every show": `.dept-row` 44/20, cards `.resume-strip` gap
- [x] One heading rule weight

## Notes

- Department hero bleeds out of the page padding (`Modifier.bleeds`, CSS negative margins) instead of each caller unwrapping it; Franchise's `NoInset` dropped.
- Not done: the web's 96px bottom inset (`TvKeptWall` pads outside its wall, so a bigger bottom would shrink its viewport).
