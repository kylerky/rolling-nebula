# Initial Concept
A tool for rolling certificates for Nebula networks, including a certificate roller CLI and a configuration server.

# Product Guide

## Target Users
- **System Administrators:** Primary users responsible for managing Nebula network security and ensuring continuous operation.

## Goals
- **Automated Rotation:** Automate the periodic rotation of Nebula certificates to enhance network security and minimize manual overhead.
- **Centralized Management:** Provide a centralized server for managing and distributing Nebula node configurations securely.
- **Simplified Generation:** Simplify the process of generating new certificates from existing public keys, streamlining node enrollment.

## Key Features
- **Automated Certificate Generation:** Generate certificates automatically from a directory of public keys.
- **Configuration Server:** A server providing secure access to node-specific configuration templates.
- **Rolling Updates:** Support for rolling certificate updates with timestamped configuration versions for easy tracking and rollback.

## Target Environment
- **Containerized Environments:** Optimized for deployment in containerized environments like Podman or Docker to ensure easy deployment, isolation, and portability.
