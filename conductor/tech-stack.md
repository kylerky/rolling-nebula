# Tech Stack

## Language and Runtime
- **Scala 3:** The primary programming language, leveraging its strong typing and functional programming capabilities.
- **Java Runtime Environment (JRE):** Required for running the compiled Scala applications.

## Core Frameworks
- **http4s & tapir:** Used for building the Config Server's HTTP API, providing typesafe and functional endpoint definitions.
- **cats-effect:** The foundation for managing asynchronous operations and side effects in a purely functional manner.
- **fs2:** Used for efficient, streaming I/O operations, particularly for filesystem interactions.
- **circe & circe-yaml:** Used for robust JSON and YAML parsing and serialization of configurations.
- **decline:** Used for building a user-friendly and type-safe command-line interface for the Cert Roller.

## Testing
- **munit-cats-effect-3:** The primary testing framework, integrated with cats-effect for testing functional and effectful code.

## Build and Deployment
- **sbt:** The standard build tool for Scala projects, managing dependencies and compilation.
- **Containerization (Podman/Buildah):** Applications are packaged as container images for consistent deployment and isolation, managed with Podman and built using Buildah.
