# Plan: Cert Roller CLI Subcommands & Host Update

## Phase 1: CLI Refactoring (Subcommands) [checkpoint: 409d4a7]
- [x] Task: Refactor `CertRollerApp` to use `decline` subcommands. d5180f5
- [x] Task: Implement `roll` subcommand (identical behavior to current root command). d5180f5
- [x] Task: Write tests for subcommand parsing in a new `CertRollerAppSuite`. d5180f5
- [x] Task: Conductor - User Manual Verification 'CLI Refactoring' (Protocol in workflow.md) 409d4a7

## Phase 2: Implement `update` Subcommand
- [x] Task: Implement logic in `FileSystem` or `ConfigLoader` to locate the latest `config_*` directory and its CA files. 96b09c1
- [x] Task: Implement the `update` subcommand logic in `CertRollerApp`. 4b61c21
- [x] Task: Write unit tests for `update` logic (mocking `NebulaCert` and `FileSystem` where appropriate). 4b61c21
- [ ] Task: Write integration tests for `update` (verifying full flow using temporary directories).
- [ ] Task: Conductor - User Manual Verification 'Update Subcommand' (Protocol in workflow.md)
