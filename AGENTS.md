## Android UI Design

Before implementing or modifying any Jetpack Compose UI:

1. Load the `ui-android` skill.
2. Read `DESIGN_RULES.md` if it exists.
3. Follow the established design tokens and component conventions.
4. Never introduce new colors, typography, spacing or shapes
   without updating the design system.
5. Validate visual changes using the Android Emulator.

## Version bump, commit, and push

`version.properties` is the single source of truth for the app version. Gradle reads it directly.

At the end of every iteration that changes app sources, including tests, resources, the manifest, or build configuration, even for a one-line change:

1. Run `./scripts/bump-version.sh` once. It increments the SemVer patch and Android `versionCode` together and synchronizes the generated version line in `README.md`. Do not bump for documentation-only iterations or rerun the bump to retry a failing check.
2. Run `./scripts/bump-version.sh --check` and the project checks against the bumped version. Check `git status --short`, then stage only the files changed for this iteration, including `version.properties` and `README.md`.
3. Commit the changes and the bump together. Use a Conventional Commit subject in American English that describes the actual change, not just the bump.
4. Run `git push`. If it fails, report the error and stop; do not force-push.

The bump script only updates the version file and the README version line. It does not stage, commit, push, or create a tag.
