# Task Completion Checklist

- Run `./gradlew test` after Kotlin changes; add `./gradlew lint` or `./gradlew assembleDebug` if UI/build logic is touched.
- When front-end JavaScript or assets change, execute `cd app/frontEnd && npm run build` so the updated files land in `app/src/main/assets` before building APKs.
- Verify Git status stays clean aside from intentional edits; do not commit from the agent.
- Update README or API docs when altering user-facing functionality or endpoints.