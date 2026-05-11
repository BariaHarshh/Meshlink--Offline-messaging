# Contributing to MeshLink

Thank you for your interest in contributing! Here's how to get started.

## 🐛 Reporting Bugs

Please use the [Bug Report](.github/ISSUE_TEMPLATE/bug_report.md) template.  
Include device model, Android version, and steps to reproduce.

## 💡 Suggesting Features

Use the [Feature Request](.github/ISSUE_TEMPLATE/feature_request.md) template.

## 🔧 Making Changes

### Branching Convention

| Branch | Purpose |
|---|---|
| `main` | Stable, production-ready |
| `feature/<name>` | New features |
| `fix/<name>` | Bug fixes |
| `refactor/<name>` | Code refactoring |

### Commit Message Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add SOS broadcast with medical profile
fix: crash on discovery screen when BT off
refactor: extract routing logic to MeshRouter
docs: update README with setup instructions
```

### Pull Request Checklist

- [ ] Code compiles and runs without errors
- [ ] Existing tests pass (`./gradlew test`)
- [ ] New logic is covered by unit tests where possible
- [ ] No `build/` or `.gradle/` artifacts committed
- [ ] PR description clearly explains *what* and *why*

## 🧪 Running Tests

```bash
# Unit tests (all modules)
./gradlew test

# Instrumented tests (requires connected device)
./gradlew connectedAndroidTest
```

## 📐 Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Keep ViewModels free of Android framework references where possible
- Repository interfaces live in `:domain` — implementations in `:data` or `:mesh`
