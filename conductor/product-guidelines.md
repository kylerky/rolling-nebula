# Product Guidelines

## Tone and Communication
- **Technical and Precise:** Documentation and messages prioritize technical accuracy and clarity, tailored for system administrators managing security infrastructure.
- **Supportive and Explanatory:** Provide context and reasoning behind security practices and tool usage to help users understand the system's behavior.

## Error Handling and Feedback
- **Fail-Fast with Detailed Context:** The tool terminates immediately on critical errors (e.g., invalid keys, malformed configuration) providing clear, actionable feedback to prevent operating in an insecure or undefined state.
- **Structured Logging:** Use standardized log levels (INFO, WARN, ERROR) to maintain a clear audit trail of certificate rotations and server interactions.

## Security and Configuration
- **Filesystem-Based Secrets:** Assume that sensitive materials (keys, certificates) are managed as files on the host system and provided to the tool via secure volume mounts.

## Testing and Quality Assurance
- **Comprehensive Unit Testing:** Maintain high test coverage for all core logic, including certificate generation, configuration parsing, and template rendering, ensuring reliability in security-critical paths.
