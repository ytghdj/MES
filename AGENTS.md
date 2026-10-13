# Repository Guidelines

## Project Structure & Module Organization

- `ruoyi-admin`: Spring Boot entry point and environment configuration.
- `ruoyi-modules`: system, workflow, generator, job, AI, and demo modules.
- `ruoyi-common`: shared infrastructure; `ruoyi-api`: shared service contracts.
- `ruoyi-extend`: independently launched monitoring, scheduling, and AI services.
- Java sources reside in `src/main/java`, resources in `src/main/resources`, and tests in `src/test/java` within each module.
- `plus-ui`: Vue frontend with views, API clients, stores, components, and assets under `src/`. Frontend and backend are tracked together in this repository; run Git commands from the repository root.
- `script/sql` contains database initialization scripts; `script/docker` contains deployment examples.

## Build, Test, and Development Commands

Match the JDK to `pom.xml` (currently Java 25). Use Node >=20.19 and the frontend's declared pnpm 10.34.5. Local development requires MySQL and Redis.

From the repository root, use `./mvnw` (`.\mvnw.cmd` on Windows):

- `./mvnw -pl ruoyi-admin -am package`: build the application and dependencies, including tests.
- `./mvnw test`: run backend tests.
- `java -jar ruoyi-admin/target/ruoyi-admin.jar --spring.profiles.active=dev`: start the backend on port 8080.

From `plus-ui/`:

- `pnpm install --frozen-lockfile`: install locked dependencies.
- `pnpm dev`: start Vite on port 80; `/dev-api` proxies to backend 8080.
- `pnpm build:prod`: generate production assets.
- `pnpm lint`, `pnpm fmt`, `pnpm exec vue-tsc --noEmit`: lint, format, and check types. Review formatter changes before committing.

## Coding Style & Naming Conventions

Follow `.editorconfig`: UTF-8, LF, four-space backend indentation, two-space JSON/YAML and frontend indentation. Frontend formatting uses Oxfmt; linting uses Oxlint.

Keep Java packages under `org.dromara`. Follow `XxxController`, `IXxxService`, `XxxServiceImpl`, `XxxMapper`, `XxxBo`, and `XxxVo` conventions. Keep controllers thin; reuse existing utilities and generator templates. Frontend modules typically use `views/<module>/<feature>/index.vue` and matching `api/<module>/<feature>/{index,types}.ts`.

## Testing Guidelines

Backend tests use JUnit Jupiter and Mockito. Name classes `*Test`; add regression tests for changed behavior. Surefire excludes the `exclude` tag. No numeric coverage gate is configured. Frontend declares Vitest but currently has no test script or suite; validate with lint, type checks, build, and relevant browser flows.

## Commit & Pull Request Guidelines

History uses `fix`, `add`, and `update` prefixes, usually followed by Chinese descriptions. Keep commits focused. This MES project's remote is `git@github.com:ytghdj/MES.git` and its main branch is `main`. Pull updates with `git pull --ff-only` and push committed changes with `git push`. Project PRs target `main`; describe the problem, implementation rationale, and executed checks. Link relevant issues and include screenshots for UI changes.

## Security & Configuration

Never commit real credentials. Coordinate frontend encryption settings with backend configuration. Keep Docker's Java runtime aligned with the compilation target. New pages require backend menu entries and role permissions.
