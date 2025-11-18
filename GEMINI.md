## 1. Core Directive

Your primary objective is to implement features, fix bugs, or refactor Scala code using a strict **Test-Driven Development (TDD)** and **Atomic Commit** methodology.

**You must not write any production code until a corresponding failing test exists.**

## 2. The TDD & Commit Workflow

You must follow this exact cycle for every logical change. This workflow *is* the atomic commit process.

### Step 1: RED (Write a Failing Test)
* Identify the **smallest** piece of required behavior.
* Write **one** minimal, isolated **unit test** for this behavior.
* Aggressively **mock or stub** all external dependencies (databases, APIs, etc.).
* Run tests and confirm the new test **fails** for the expected reason.

### Step 2: GREEN (Make the Test Pass)
* Write the **absolute minimum** amount of production code necessary to make the failing test pass.
* **Do not** add any logic not explicitly required by the test.
* Run all tests and confirm they **all pass**.

### Step 3: COMMIT (Feature/Fix)
* Commit *both* the new test code and the new production code *together*.
* This commit represents **one logical change** (the feature or fix).
* The commit message **must** follow the `feat:` or `fix:` format (see below).

### Step 4: REFACTOR (Clean the Code)
* **After committing**, look for ways to improve the code you just added (both production and test code).
* Refactor for clarity, design, or to remove duplication.
* Ensure all tests continue to pass.

### Step 5: COMMIT (Refactor)
* If you made refactoring changes in Step 4, commit them **separately**.
* This commit **must not** contain any new features or behavioral changes.
* Use the `refactor:` commit type.

### Step 6: REPEAT
* Return to Step 1 for the next piece of behavior.

## 3. Implementation Guidelines

### Commit Message Standard
You **must** follow the Conventional Commits specification.

* **Format:** `type(scope?): subject`
* **Focus:** The message must explain *why* the change was made, not just *what* changed.
* **Common Types:**
    * **`feat`**: (Step 3) A new feature.
    * **`fix`**: (Step 3) A bug fix.
    * **`refactor`**: (Step 5) Code changes that neither fix a bug nor add a feature.
    * **`test`**: Only for adding or correcting existing tests *without* production code changes.
    * **`style`**: Formatting changes only. **Do not** mix these with `feat`, `fix`, or `refactor` commits.
    * **`chore`**: Build script or tooling updates.

### Test Strategy
* **Unit Tests (Default):** Your primary tool. Test a single class/function in **complete isolation**. Mock all dependencies.
* **Integration Tests (When Necessary):** Use *only* to verify the **interaction** between two or more modules (e.g., service to database) when the connection itself is the risk. Do not re-test logic already covered by unit tests.

### Scala & Style
* Follow the existing coding style and conventions.
* Prefer functional paradigms and streams where appropriate.
