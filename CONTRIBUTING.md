# Contributing to OffPay

Thank you for considering a contribution. OffPay is a small project with big ambitions (making offline UPI payments feel as smooth as online ones) and outside help on UX polish, device-quirk fixes, language coverage, and testing is genuinely valued.

This guide explains how to file an issue, set up the project, and get a pull request merged.

---

## Code of conduct

Be kind. We take all interactions on the issue tracker, in pull requests, and on any other project surface seriously, regardless of whether a formal CoC document is in place. Personal attacks, harassment, and discriminatory language are not welcome and will be removed.

If something feels off, please email the maintainers privately rather than escalating in public.

---

## Ways to contribute

You don't have to write code to help. All of these are equally welcome:

- **Bug reports** with a clear repro, your device + carrier, and what you expected vs what happened.
- **Compatibility reports.** Did OffPay work for you on a particular bank, carrier, or device? Did it not? Tell us.
- **UX feedback.** Screenshots and a short note are perfect.
- **Translations** of the UI strings into Hindi, Tamil, Telugu, Bengali, Marathi, or any Indian language.
- **Documentation fixes.** Typos, broken links, unclear sections in any of the markdown files.
- **Code.** Bug fixes, performance, new features that align with the roadmap.

---

## Reporting a bug

Before opening a new issue, please:

1. Search [existing issues](../../issues) to make sure it isn't already filed.
2. If it's a payment that went wrong, **double-check whether the bank actually debited you** (the `*99#` service does not debit on a failed session, but the in-app result might still confuse you). A screenshot of the carrier's exact reply (visible in OffPay's failure card) helps a lot.
3. Open a new issue using the bug report template if available.

A good bug report includes:

- A short, descriptive title.
- Device model, Android version, OS skin (Stock, OneUI, MIUI, ColorOS, …).
- Carrier (Airtel / Vi / BSNL / …) and the bank you tried to pay from.
- The OffPay version (visible at the bottom of Settings).
- The Operation Mode you were in (Auto / Advanced / Manual).
- Steps to reproduce, what you expected, and what actually happened.
- The carrier's verbatim reply if OffPay surfaced one.
- A short screen recording if the bug is visual or animation-related.

Please **redact the UPI ID, PIN, and any reference numbers** before posting.

---

## Suggesting a feature

Open a discussion or an issue tagged `enhancement`. Describe:

- The problem the feature solves.
- A rough idea of how the user would experience it.
- Any UX edge cases you've already thought about.

OffPay is opinionated about scope: it is an offline UPI client, not a general SMS/USSD framework, not a finance dashboard, not an account aggregator. Features that drift from that scope are likely to be politely declined.

---

## Setting up the project

See [ARCHITECTURE.md](ARCHITECTURE.md) for the full build instructions and project tour. The short version:

```bash
git clone https://github.com/<your-org>/OffPayApp.git
cd OffPayApp
# Create local.properties with sdk.dir=…
./gradlew :app:assembleDebug
```

Open the root in Android Studio Ladybug or newer. JDK 17 is required.

---

## Branching and pull request flow

1. **Fork** the repository and create your branch from `main`.
2. **Branch naming.** Short, hyphen-separated, prefixed with the type of change:
   - `fix/` for bug fixes (`fix/jio-detection-on-dual-sim`)
   - `feat/` for new features (`feat/marathi-locale`)
   - `docs/` for documentation (`docs/contributing-clarity`)
   - `refactor/`, `test/`, `chore/` for the rest
3. **Make focused commits.** One logical change per commit. Avoid mixing a refactor with a feature.
4. **Run the test suite** locally before opening the PR (see Testing below).
5. **Open the pull request** against `main`. Fill in the PR template if one is present, otherwise:
   - Describe what changed and why.
   - List the user-visible impact.
   - Mention any new permission, dependency, or runtime cost.
   - Link the issue you are fixing (`Fixes #123`).
6. **Be ready to iterate.** Reviewers might ask for renames, code restructure, or extra tests. That is normal: your contribution is welcome regardless.

Maintainers may rebase or squash your branch on merge. Please do not force-push to a PR after a reviewer has started looking at it; push fixup commits instead.

---

## Coding style

### Kotlin
- Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html). The project ships with `kotlin.code.style=official` in `gradle.properties`.
- Default visibilities: prefer `internal` over `public` for anything that is not a true module API. Prefer `private` for file-local helpers.
- Use data classes for value objects. Use `sealed class` / `sealed interface` for closed enumerations of states (we do this for `SessionState`, `ActionEvent`).
- Avoid `!!`. If you reach for it, an early `requireNotNull` with a message is almost always better.
- Prefer expression-bodied functions for one-liners.
- Coroutines: never block. Use the supplied `viewModelScope` / `lifecycleScope` rather than creating new scopes ad-hoc.

### Compose
- One screen Composable per file in `presentation/screens/`. Reusable widgets live in `presentation/ui/components/`.
- State hoisting: pass `state` down and `events` (lambdas) up. ViewModels expose `StateFlow`s and a flat surface of intent functions.
- Skip `Modifier.padding` chains where a single `padding` call suffices.
- Avoid `LaunchedEffect(Unit) { … }` for anything that depends on dynamic state: key it on the inputs that should re-trigger it.

### Comments
The existing codebase tends to comment **the why, not the what**. A good comment explains an unobvious decision, a known device quirk, or a non-trivial invariant. Please continue that style. Decorative dividers (`// ── header ──`) are welcome where they help skim a long file.

### Layered separation
Anything in `domain/` must remain pure Kotlin: no `android.*` imports, no Compose, no Room, and no Context. If your change introduces an Android dependency to `domain/`, refactor so the Android side lives in `platform/` or `data/`.

---

## Testing

### What to write tests for
Anything in `domain/` should have a corresponding test in `app/src/test/java/com/offpay/app/domain/`. We strongly prefer **property-based tests** (Kotest's `checkAll`) for anything that takes string input (such as regex matchers, validators, or parsers) because hand-written examples miss the long tail.

For ViewModel logic that does not require Android, drop a unit test under `app/src/test/java/com/offpay/app/presentation/`.

For anything that genuinely needs the platform (Room, DataStore, the QR decoder against a real image), write an instrumented test under `app/src/androidTest/`.

### Running tests

```bash
# JVM unit + property tests
./gradlew :app:test

# Instrumented tests (requires a connected device or running emulator)
./gradlew :app:connectedAndroidTest

# Both, plus lint
./gradlew check
```

Tests must pass before a PR can be merged.

### Manual testing checklist for changes that touch the USSD pipeline
If your PR changes anything in `domain/Action*`, `domain/FrameFilter`, or `platform/Ussd*`, please run through this checklist on a real Airtel / Vi / BSNL SIM:

- [ ] Successful payment in Auto mode shows the success card with the carrier reference number.
- [ ] Successful payment in Advanced mode shows progress in the floating chip and the success card at the end.
- [ ] Wrong PIN failure surfaces the carrier's exact "incorrect PIN" text.
- [ ] Cancelling mid-session via the overlay's Cancel button or the back button cleanly aborts.
- [ ] Two payments in a row work: no leftover dialog, no stale frames.
- [ ] Pressing Pay twice in quick succession does not start two sessions.

It is fine to skip this checklist on a PR that only touches UI styling or strings, but please call that out in the PR description.

---

## Translations

UI strings live in `app/src/main/res/values/strings.xml`. To add a translation:

1. Create `app/src/main/res/values-<locale>/strings.xml` where `<locale>` is the two-letter ISO code (`hi`, `ta`, `te`, `bn`, `mr`, …).
2. Copy every `<string name="…">…</string>` entry from the default file. Translate the text, leave the `name` attribute alone.
3. Test on a device or emulator with that locale set.
4. Open a PR. Multiple language PRs are encouraged.

If a string in the default file is missing or feels English-centric, that itself is a fine bug to file.

---

## Releasing (maintainers)

Release artefacts are built from a clean `main` branch with the version bumped in `app/build.gradle.kts` (`versionCode` and `versionName`). Sign with the production keystore (not committed) and publish via the GitHub Releases page. Tag the commit `vX.Y.Z`.

Public releases must:
- Pass `./gradlew check`.
- Run cleanly through the manual USSD checklist on at least one real device per supported carrier.
- Include a CHANGELOG entry summarising user-visible changes.

---

## Licence on contributions

By submitting a pull request you agree that your contribution is licensed under the same [MIT License](LICENSE) as the project, and that you have the right to license it under those terms. We do not require a CLA.

---

Thanks again: every issue, fix, and translation makes OffPay more useful to people who can't always rely on a data connection.
