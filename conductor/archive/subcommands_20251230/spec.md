# Specification: Cert Roller CLI Subcommands & Host Update

## 1. Overview
The `cert-roller` application currently performs a single task: batch generating certificates for all nodes. This track will refactor the CLI to use a subcommand structure, isolating the current behavior into a `roll` command and introducing a new `update` command. The `update` command allows regenerating a certificate for a specific host (e.g., after a key rotation) without regenerating the entire mesh, while ensuring it remains compatible with the existing CA.

## 2. Functional Requirements

### 2.1 CLI Restructuring
-   Refactor the main application to use the `decline` library's subcommand feature.
-   **Command:** `roll`
    -   **Description:** Preserves the existing behavior of batch processing all public keys in the input directory and generating configurations.
    -   **Arguments/Flags:** Inherits all existing flags and configuration options currently used by the root command.

### 2.2 New `update` Subcommand
-   **Command:** `update`
-   **Description:** Generates/refreshes the certificate for a single specific host.
-   **Usage:** `cert-roller update <hostname> [options]`
-   **Arguments:**
    -   `hostname` (Positional, Required): The name of the host to update.
-   **Flags:**
    -   `--pub-key <path>` (Optional): Path to the new public key file for the host.
-   **Behavior:**
    1.  **Context Loading:** Load the necessary configuration (CA certificate, CA key, global settings) just as the `roll` command does.
    2.  **Public Key Resolution:**
        -   **Case A (Flag provided):** If `--pub-key` is set, read the public key from the specified path.
        -   **Case B (Default):** If the flag is omitted, look for the host's public key in the configured input directory (matching the filename convention used by `roll`, e.g., `<hostname>.pub`).
    3.  **Certificate Generation:**
        -   Generate a new certificate for the specified `hostname`.
        -   Use the resolved public key.
        -   Sign the certificate using the loaded CA credentials.
        -   Preserve the host's identity details (IP, groups) as defined in the system's source of truth (likely the same mapping logic used by `roll`).
    4.  **Output:** Write the updated configuration/certificate to the configured output directory, overwriting the specific file for that host.

## 3. Non-Functional Requirements
-   **Library:** specific usage of `decline` for CLI parsing.
-   **Compatibility:** The `roll` command must produce identical output to the previous root command for the same inputs.
-   **Error Handling:** Provide clear error messages if the specified host is unknown, the public key is missing, or the CA cannot be loaded.

## 4. Acceptance Criteria
-   [ ] `cert-roller --help` lists `roll` and `update` as available subcommands.
-   [ ] `cert-roller roll` executes the original batch generation logic successfully.
-   [ ] `cert-roller update <host>` successfully regenerates the certificate using the public key from the input directory.
-   [ ] `cert-roller update <host> --pub-key <file>` successfully regenerates the certificate using the provided file.
-   [ ] The updated certificate is valid and signed by the same CA as the rest of the mesh.
