# Style Guidelines

- **Kotlin**: Project targets JVM 11 with `kotlin.code.style=official`. Files follow Jetpack Compose patterns—top-level composables, `@Composable` functions, and `MutableLiveData` bridging into Compose via `observeAsState`. Use descriptive camelCase identifiers and avoid one-letter names. Nullability and coroutines (`lifecycleScope`, `Dispatchers`) are standard.
- **Architecture Conventions**: Business logic lives in `modules`/`utils`, UI in `ui` using Compose Material3. Services and receivers reside at the root package. Prefer Kotlin coroutines for async work and companion objects for constants.
- **Front-End JS**: Source in `app/frontEnd/public/script` is readable ES2015 JavaScript using modules via script tags. Utilities rely on descriptive function names and multi-line formatting; keep code lint-friendly and avoid minifying in source.
- **Assets**: Static files (lang JSON, icons, styles) mirror the structure when copied into `app/src/main/assets`. Keep filenames consistent to satisfy build script filters.
- **Localization**: `public/lang/*.json` and JS helpers expect `data-i18n` attributes; align new strings with this pattern.