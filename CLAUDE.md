# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ansflow is a CLI tool designed to simplify Ansible playbook execution. It provides both CLI and TUI (Terminal UI) interfaces for managing and executing Ansible playbooks across different environments with enhanced safety and usability features.

## Commands

### Build and Development
- **Build**: `./gradlew build`
- **Clean build**: `./gradlew clean build`
- **Run tests**: `./gradlew test`
- **Run single test**: `./gradlew test --tests "TestClassName" or ./gradlew test --tests "TestClassName.testMethodName"`

### Code Quality
- **Lint (ktlint)**: `./gradlew ktlintCheck`
- **Format code**: `./gradlew ktlintFormat`
- **Static analysis (Detekt)**: `./gradlew detekt`
- **All checks**: `./gradlew check` (runs tests, ktlint, and detekt)

### Running the Application
- **Run**: `./gradlew run`
- **Build executable JAR**: `./gradlew jar`

## Architecture

### Technology Stack
- **Language**: Kotlin 2.1.20 targeting JVM 21
- **UI Framework**: Mosaic 0.17.0 (Terminal UI library)
- **Testing**: Kotest 5.9.1 (test framework)
- **Serialization**: kotlinx-serialization with KAML for YAML support
- **Build Tool**: Gradle with Kotlin DSL

### Project Structure
The project follows standard Kotlin/Gradle conventions:
- `src/main/kotlin/` - Main source code
- `src/test/kotlin/` - Test code
- `src/main/resources/` - Resources
- `gradle/` - Gradle wrapper and dependency versions

### Key Design Decisions
1. **Single Binary Distribution**: The tool is designed to be distributed as a single executable file
2. **Dual Interface**: Supports both CLI (with tab completion) and TUI modes
3. **Environment Isolation**: Separate handling of development and production environments
4. **Safety First**: Built-in confirmation prompts and validation before executing playbooks

### Core Components (To Be Implemented)
1. **Inventory Parser**: Reads and analyzes Ansible inventory files to extract host groups
2. **Playbook Analyzer**: Parses playbooks to determine compatible host groups
3. **UI Layer**: Both CLI and TUI interfaces using Mosaic
4. **Execution Engine**: Manages Ansible process execution with proper error handling
5. **Logging System**: Stores execution logs in `~/.local/tool/logs/`
6. **Preset Manager**: Handles saved execution configurations

### Expected Directory Structure for Ansible Projects
```
ansible-project/
├── inventory/
│   ├── development/
│   └── production/
├── playbooks/
│   ├── development/
│   └── production/
└── roles/
```

## Development Guidelines

### Kotlin Style
- Follow official Kotlin coding conventions (enforced by ktlint)
- Use coroutines for asynchronous operations
- Prefer immutable data structures
- Use sealed classes for state management in TUI

### Testing
- Write tests using Kotest's BDD-style syntax
- Use property-based testing where appropriate
- Mock external dependencies (Ansible execution)
- Test both CLI and TUI interfaces

### Error Handling
- Gracefully handle malformed inventory/playbook files
- Provide clear error messages to users
- Continue execution where possible rather than failing completely