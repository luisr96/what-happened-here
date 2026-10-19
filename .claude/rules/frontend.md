---
paths:
  - "frontend/**"
---

# Frontend conventions

## TypeScript
- `strict: true`, plus `noUncheckedIndexedAccess` and `exactOptionalPropertyTypes`. No `any`; use
  `unknown` and narrow it. No non-null assertions (`!`) to silence the compiler.
- Types for API data come from the OpenAPI spec (`frontend/openapi.json`, generated from the API's
  `/v3/api-docs`), not hand-written copies, so a changed field breaks the build.
- Prefer `type` aliases and discriminated unions over classes and enums; model "one of N shapes"
  as a union with a `kind`/`status` tag and handle it with an exhaustive `switch`.
- Immutable data: `readonly` properties and arrays for props and API types; never mutate state.
- Named exports only, so renames are checked.

## React
- Function components and hooks only. One component per file, named like the file.
- Small, focused components: a component either fetches/holds state or renders props, not both.
  Map layers, the time slider, and the details panel are separate components.
- Data fetching lives in hooks (`useEvents`, `useTimeline`, ...) that return a union state
  (`loading | error | ready`), never in render code. Cancel stale requests (`AbortController`)
  when the viewport or time range changes.
- Derive values instead of storing them in state; `useMemo`/`useCallback` only for a measured
  cost or a stable identity a dependency needs (e.g. deck.gl layer props).
- Historic dates are never JavaScript `Date`s: format from `{year, month, day, precision,
  calendar}` (e.g. "c. 1200s", "44 BC"), the same rule as the backend's `HistoricDate`.
- Accessible by default: semantic elements, labels on controls, keyboard-reachable interactions.

## Comments and documentation
- No comments that restate the code, narrate steps, or mark sections. No commented-out code, no
  TODOs without context.
- TSDoc only where intent isn't obvious from names and types: a contract, a unit, a quirk of the
  API or a library (e.g. why a bbox is split at the antimeridian). Link the API field or the
  example that explains the case.

## Errors
- API errors are RFC 9457 problem details; show their `detail` to the user, never a stack trace.
- Fail soft on bad data from the API (skip the item), fail loudly on programmer errors.

## Tests
- Vitest + Testing Library for units and components; Playwright for end-to-end flows.
- Test behaviour the user sees (roles, labels, text), not implementation details: no snapshot
  tests of whole trees, no asserting on internal state or CSS classes.
- The API is mocked at the network boundary (MSW in Vitest, `page.route` in Playwright) with
  responses that match the OpenAPI spec, including 400 problem details and empty results.
- Test names describe behaviour (`shows "c. 1200s" for century precision`).
- Assert on what matters for the case, so adding a field doesn't break unrelated tests.
