# Project: rolling nebula

## General Instructions
- When you generate new Scala code, follow the existing coding style.
- Prefer functional programming paradigms where appropriate. Prefer streams where appropriate.
-  Please break down the implementation into pieces and follow the atomic commit instructions below. 

## Atomic Commit Instructions

### Commit Content

* **One Logical Change:** Each commit must encapsulate exactly one logical unit of change. This can be a single bug fix, a new feature, or a refactor, but it should not be multiple.
* **Do Not Mix Concerns:** Never bundle unrelated changes. For example, do not mix a feature implementation with code formatting (style) fixes, or a bug fix with a refactor.
* **Maintain a Working State:** The codebase must be in a complete, working state after the commit. The application should build successfully. While not all tests may pass, your changes should ensure that as many tests as possible are passing.

### Commit Message

* **Explain "Why":** The commit message must be descriptive and focus on *why* the change was made, not just *what* was changed (the "what" is visible in the code diff).
* **Use Conventional Commits:** Follow the Conventional Commits specification for a clear and structured message format.
    * **Format:** `type(scope?): subject`
    * **Common Types:**
        * **feat**: A new feature
        * **fix**: A bug fix
        * **refactor**: A code change that neither fixes a bug nor adds a feature
        * **docs**: Documentation-only changes
        * **style**: Changes that do not affect code meaning (white-space, formatting, etc.)
        * **test**: Adding new tests or correcting existing ones
        * **chore**: Maintenance changes (e.g., updating build scripts, tooling)
