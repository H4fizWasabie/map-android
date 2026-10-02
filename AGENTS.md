# MAP agent guide

MAP is a public, personal-first Android app. Keep the product local-first and the implementation small enough to understand in one sitting.

## Before changing code

1. Create or identify one GitHub issue for the change.
2. Read the relevant product and domain context in [CONTEXT.md](CONTEXT.md).
3. Check [docs/decisions.md](docs/decisions.md) for settled boundaries.
4. Keep one task per branch and update [CHANGELOG.md](CHANGELOG.md).

## Engineering rules

- Prefer Android and Kotlin standard capabilities before adding dependencies.
- Keep SQLite/Room as the local source of truth; do not add a backend.
- Request permissions only at the feature boundary that needs them.
- Preserve user files and metadata when a referenced file is missing.
- Keep accessibility, explicit error states, and reduced motion in every UI flow.
- Never commit credentials, signing keys, personal files, or generated build output.

## Code navigation (Graft)

This repo is indexed by Graft: a local, gitignored graph (`graft/`) of every symbol, its `file:line` span and who calls it. It rebuilds against the working tree before each query, so results include uncommitted edits. Use it before grepping or reading files to find or understand code:

- `graft ask "<task>" --source` — locate and understand; ranked nodes with the code inlined. Keep the query short and built around identifiers.
- `graft grep "<regex>"` — every occurrence, grouped by enclosing symbol. It takes a regex, so escape parentheses (`Stream\(`).
- `graft skeleton <file>` — a file's full API with spans, far cheaper than reading it.
- `graft callers <symbol> [--depth all]` — who calls it, or the full blast radius. Run it before changing or renaming a symbol.
- `graft map` — orientation only.

If `graft` is not installed or `graft/` is missing, fall back to grep and read, or run `graft build` to create the graph. Never commit `graft/`.

## Verification

Run the checks in [docs/build.md](docs/build.md) before pushing. A release also needs a manual phone smoke test and a signed APK; release signing stays outside this repository.

## Scope

MVP scope and exclusions are recorded in [docs/decisions.md](docs/decisions.md). New cross-cutting decisions belong in a GitHub issue before implementation.
