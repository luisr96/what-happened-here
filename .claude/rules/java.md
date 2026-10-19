---
paths:
  - "**/*.java"
---

# Java conventions

## Style
- Modern Java 21: records for data, sealed interfaces for "one of N shapes", pattern-matching
  `switch`, `var` only when the type is obvious from the right-hand side.
- Prefer immutability: final fields, records, unmodifiable collections (`List.copyOf`, `List.of`).
- Small, focused classes and methods. Nest small types that only make sense inside one type
  instead of giving each its own file.
- No Lombok; records cover what it would generate.
- Fail soft on bad input data (return null/empty and skip), fail fast on programmer errors
  (`Objects.requireNonNull` and `IllegalArgumentException` for invalid arguments).

## Javadoc
- Every public type and every public method that isn't self-evident gets Javadoc.
- Javadoc explains intent, contracts and non-obvious constraints (units, nullability, why a value
  can be absent), not what the code visibly does.
- Use `@param`/`@return` only when they add information beyond the name and type.
- Record components are documented with `@param` on the record's Javadoc, except API response
  records, which use `@Schema(description = ...)` instead so the text reaches the OpenAPI docs
  (one source, not both).

## Comments
- No comments that restate the code, narrate steps, or mark sections.
- Inline comments only for a non-obvious *why*: a Wikidata quirk, a deliberate trade-off,
  a workaround. Link the example entity when it explains the case (e.g. "Q243 has two P1619").
- No commented-out code, no TODOs without context.

## Tests
- JUnit 5. Test names describe behaviour (`yearPrecisionClearsMonthAndDay`), or use
  `@DisplayName` for readable reports.
- Extractor tests run against saved entity JSON in `src/test/resources/entities/`, never the network.
- Assert on the fields that matter for the case, not on whole objects, so adding a field
  doesn't break unrelated tests.
