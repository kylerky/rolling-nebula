# Project: rolling nebula

# General Instructions
- When you generate new Scala code, follow the existing coding style.
- Prefer functional programming paradigms where appropriate. Prefer streams
  where appropriate.
- Please break down the implementation into pieces and follow the atomic commit
  instructions below. Commit the code as you go.

# Atomic Commit Instructions

## Commit Content

* **One Logical Change:** Each commit must encapsulate exactly one logical unit
  of change. This can be a single bug fix, a new feature, or a refactor, but it
  should not be multiple.
* **Do Not Mix Concerns:** Never bundle unrelated changes. For example, do not
  mix a feature implementation with code formatting (style) fixes, or a bug fix
  with a refactor.
* **Maintain a Working State:** The codebase must be in a complete, working
  state after the commit. The application should build successfully. While not
  all tests may pass, your changes should ensure that as many tests as possible
  are passing.

## Commit Message

* **Explain "Why":** The commit message must be descriptive and focus on *why*
  the change was made, not just *what* was changed (the "what" is visible in the
  code diff).
* **Use Conventional Commits:** Follow the Conventional Commits specification
  for a clear and structured message format.
    * **Format:** `type(scope?): subject`
    * **Common Types:**
        * **feat**: A new feature
        * **fix**: A bug fix
        * **refactor**: A code change that neither fixes a bug nor adds a
          feature
        * **docs**: Documentation-only changes
        * **style**: Changes that do not affect code meaning (white-space,
          formatting, etc.)
        * **test**: Adding new tests or correcting existing ones
        * **chore**: Maintenance changes (e.g., updating build scripts, tooling)

# Core Directive: TDD Protocol

Your primary objective is to implement new features, refactor code, or fix bugs
using a strict **Test-Driven Development (TDD)** methodology.

**Do not** write any production code until a corresponding failing test has been
written.

## TDD Workflow

Follow this cycle for *every* piece of new functionality:

1.  **Analyze Requirement:** Identify the smallest, discrete behavior required.
2.  **Write Failing Test (Red):**
    * Author a single, minimal test (unit or integration) that specifies this
      behavior.
    * Use descriptive test names (e.g.,
      `test_user_service_should_reject_invalid_email`).
    * Assert the *exact* expected outcome.
    * Run the test and confirm it **fails** (or does not compile) for the
      correct reason.
3.  **Write Passing Code (Green):**
    * Write the *absolute minimum* amount of production code necessary to make
      the test pass.
    * **Do not** add extra features or logic not required by the test.
    * Run all tests and confirm they **pass**.
4.  **Refactor:**
    * Improve the design, clarity, and efficiency of the production code (and
      test code if needed).
    * Ensure no external behavior is changed.
    * Re-run all tests to confirm they still pass.
5.  **Repeat:** Select the next small behavior and return to Step 1.

---

## Test Strategy: Unit vs. Integration

Your testing strategy must be deliberate.

### Unit Tests (Default)
* **Purpose:** To test a single function, method, or class in **complete
  isolation**.
* **Rule:** This is your **default** choice.
* **Action:**
    * Aggressively **mock or stub** all external dependencies.
    * This includes: Databases, file systems, network APIs, and other
      services/modules.
    * Focus on logic, edge cases, and return values.

### Integration Tests (When Necessary)
* **Purpose:** To verify the **interaction and data flow** between two or more
  critical modules (e.g., `ApiService` -> `DatabaseRepository`).
* **Rule:** Use *only* when the primary risk is the *connection* between
  components, not the logic within them.
* **Action:**
    * **Do not** test logic already covered by unit tests.
    * Focus on data contracts, request/response flow, and side effects (e.g.,
      "Was the record actually created in the test database?").
    * Clearly identify the modules being integrated in the test.

---

## Guiding Principles

* **Test First:** Always. No exceptions.
* **Isolate Failures:** A single bug should ideally cause only one test to fail.
* **Test Coverage:** Ensure all new code paths (happy path, error conditions,
  edge cases) are covered by a test.
* **Analyze Context:** Before starting, scan the existing codebase to understand
  module boundaries, dependency injection patterns, and existing test
  conventions (e.g., file naming, test utilities).
* **Clarity:** Tests must be clear, readable, and serve as documentation for the
  production code.
