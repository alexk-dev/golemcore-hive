# Coding Guide

Code style, conventions, and best practices for the Golemcore Hive codebase.

---

## Commit Messages

This project follows [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/).

### Format

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | When to use |
|------|-------------|
| `feat` | New feature or capability |
| `fix` | Bug fix |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `test` | Adding or updating tests |
| `docs` | Documentation only |
| `chore` | Build config, CI, dependencies, tooling |
| `perf` | Performance improvement |
| `style` | Formatting, whitespace (no logic change) |

### Scope (optional)

Use the module or area name: `auth`, `golems`, `roles`, `boards`, `flow`, `cards`, `chat`, `runs`, `audit`, `storage`, `security`, `ui`, `ci`.

### Examples

```
feat(boards): add board team filters by role

fix(auth): refresh expired operator access token

refactor(storage): extract JsonFileStore from board persistence

test(flow): add signal mapping resolution tests

chore(ci): add PR title conventional-commit guardrail

feat(flow)!: rename in_progress column key to active

BREAKING CHANGE: board flow definitions must use active instead of in_progress.
```

### Rules

- Use imperative mood: "add feature", not "added feature" or "adds feature"
- First line under 72 characters
- No period at the end of the subject line
- Breaking changes: append `!` after type/scope **and** add a `BREAKING CHANGE:` footer

---

## Licensing

This repository is distributed under the Apache License 2.0.

Rules:

- keep `LICENSE` and `NOTICE` files in the repository root,
- preserve attribution notices when adapting files from other Apache 2.0 sources,
- add the standard Apache 2.0 header to new Java source files and tests,
- document material file changes clearly in commit history when copying or adapting code.

---

## Java Style

### Explicit Type Declarations

Always declare variable types explicitly. Do not use `var`.

```java
// Correct
List<Board> boards = boardService.getBoards();
String cardId = createCardId(boardId, sequence);
Map<String, GolemRole> registry = new ConcurrentHashMap<>();
Optional<Card> existing = load(cardId);
CompletableFuture<CommandDispatchResult> future = commandDispatcher.dispatch(command);

// Incorrect
var boards = boardService.getBoards();
var cardId = createCardId(boardId, sequence);
var registry = new ConcurrentHashMap<String, GolemRole>();
```

This applies to all code: production, tests, and configuration classes.

### Constructor Injection

All Spring-managed beans use constructor injection via Lombok's `@RequiredArgsConstructor`. Field injection (`@Autowired`) is prohibited.

```java
// Correct
@Service
@RequiredArgsConstructor
@Slf4j
public class BoardService {

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    // Lombok generates the constructor
}

// Incorrect — never use field injection
@Service
public class BoardService {

    @Autowired
    private StoragePort storagePort;  // DO NOT
}
```

Dependencies must be `private final` fields. This guarantees immutability and makes dependencies explicit.

**`@Lazy` is prohibited.** It masks circular dependency problems. Break cycles by:
1. Extracting a shared interface/service that both sides depend on
2. Using `ApplicationEventPublisher` for one-way notifications
3. Moving the dependency into a method parameter instead of a constructor field

### Class Organization

Order members within a class consistently:

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ExampleService {

    // 1. Static constants
    private static final String DIR_NAME = "examples";
    private static final int MAX_ITEMS = 100;

    // 2. Injected dependencies (private final)
    private final StoragePort storagePort;
    private final HiveProperties properties;

    // 3. Mutable state (caches, registries)
    private final Map<String, Item> cache = new ConcurrentHashMap<>();

    // 4. Initialization (@PostConstruct)
    @PostConstruct
    public void init() {
        reload();
    }

    // 5. Public interface methods
    @Override
    public List<Item> getAll() { ... }

    // 6. Public methods
    public void reload() { ... }

    // 7. Private methods
    private Optional<Item> load(String id) { ... }
}
```

### Access Modifiers

- Fields: always `private`. Injected dependencies: `private final`.
- Constants: `private static final` (or `public` if shared across packages).
- Methods: `public` for API surface, `private` for internals. Avoid `protected` unless designing for inheritance.
- Classes: `public` for Spring-managed beans. Package-private only for internal implementation details.

### Naming Conventions

**Classes:**

| Suffix | Layer | Example |
|--------|-------|---------|
| `*Service` | Domain services | `BoardService`, `AssignmentService` |
| `*Adapter` | Adapter implementations | `LocalJsonStorageAdapter`, `GolemControlChannelAdapter` |
| `*Port` | Port interfaces | `StoragePort`, `NotificationPort`, `CommandDispatchPort` |
| `*Controller` | Inbound web layer | `BoardsController`, `AuthController` |
| `*Properties` | Configuration POJOs | `HiveProperties`, `SecurityProperties` |

**Methods:**

| Pattern | Purpose | Example |
|---------|---------|---------|
| `get*` | Retrieve, throw if missing | `getBoard()` |
| `find*` | Lookup, return Optional | `findById()` |
| `is*`, `has*`, `can*` | Boolean query | `isEnabled()`, `hasRole()` |
| `create*`, `build*` | Factory | `createCard()`, `buildSignal()` |
| `resolve*` | Policy resolution | `resolveTransition()` |
| `dispatch*` | Send a command/event | `dispatchCommand()` |

**Constants:** `UPPER_SNAKE_CASE`. Use `_` as thousand separator in numeric literals:

```java
private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
private static final long INITIAL_BACKOFF_MS = 5_000;
```

### Imports

Prefer explicit imports over wildcards:

```java
// Correct
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Avoid
import java.util.*;
```

Static imports are allowed in tests:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
```

---

## Lombok

### Standard Annotations

| Annotation | Where | Purpose |
|------------|-------|---------|
| `@RequiredArgsConstructor` | Services, adapters, controllers, utilities | Constructor injection |
| `@Slf4j` | Any class that logs | Generates `log` field |
| `@Data` | Domain model POJOs | Getters, setters, equals, hashCode, toString |
| `@Builder` | Domain models, request/response objects | Builder pattern |
| `@NoArgsConstructor` | Models deserialized by Jackson | Required for JSON/YAML parsing |
| `@AllArgsConstructor` | Models with `@NoArgsConstructor` | Complete constructor |
| `@Builder.Default` | Fields with defaults in `@Builder` classes | Default values in builder |
| `@Getter` | When `@Data` is too much | Read-only model |

### Gotchas

Computed getters in `@Data` classes get serialized by Jackson. Mark them `@JsonIgnore`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    private String id;

    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @JsonIgnore
    public boolean isDoneColumn() {
        return "done".equals(columnId);
    }
}
```

---

## Spring Patterns

### Bean Design

All beans always exist at runtime. Use `isEnabled()` for runtime enable/disable — never `@ConditionalOnProperty` or `@ConditionalOnBean`.

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationsService {

    private final HiveProperties properties;

    public boolean isEnabled() {
        return properties.getNotifications().isEnabled()
                && properties.getNotifications().getWebhookUrl() != null;
    }
}
```

This avoids `NoSuchBeanDefinitionException` issues and makes the dependency graph predictable.

### Configuration

Use `@ConfigurationProperties` with nested classes. Access via getter chain:

```java
HiveProperties.SecurityProperties config = properties.getSecurity();
boolean enabled = config.isJwtEnabled();
String issuer = config.getJwtIssuer();
```

### Stereotypes

- `@Service` — domain services (business logic)
- `@Component` — adapters, infrastructure, utilities
- `@Configuration` — Spring configuration classes

`@Bean` methods in `@Configuration` classes that have injected fields must be `static` to avoid circular dependencies:

```java
@Configuration
@RequiredArgsConstructor
public class AutoConfiguration {

    private final List<NotificationPort> notifiers;

    @Bean
    public static Clock clock() {  // static — no dependency on instance fields
        return Clock.systemUTC();
    }
}
```

---

## Logging

Use Lombok `@Slf4j`. Parametrized messages only — no string concatenation.

```java
// Correct
log.info("Card created: {}", cardId);
log.debug("Processing {} lifecycle signals", signals.size());
log.error("Failed to save card: {}", card.getId(), exception);

// Incorrect
log.info("Card created: " + cardId);
```

### Levels

| Level | Use for |
|-------|---------|
| `error` | Failures that need attention (with exception) |
| `warn` | Recoverable issues (rate limit, fallback triggered) |
| `info` | Milestones (board created, card assigned, command dispatched) |
| `debug` | Internal flow (system timing, cache hits, config details) |
| `trace` | Very detailed (input content, raw responses) |

### Contextual Prefixes

Use `[Area]` prefix for feature-specific logs:

```java
log.info("[Boards] Created board '{}'", boardName);
log.debug("[Flow] Resolved transition for card {}", cardId);
log.warn("[Security] Injection pattern detected in input");
```

---

## Error Handling

### Strategy

- No custom exception hierarchy. Use standard exceptions (`IllegalStateException`, `IllegalArgumentException`).
- Catch broadly (`Exception`) in persistence and I/O layers where graceful degradation is required.
- Mark intentional broad catches with `// NOSONAR`:

```java
private Optional<Card> load(String cardId) {
    try {
        String json = storagePort.getText(CARDS_DIR, cardId + ".json").join();
        Card card = objectMapper.readValue(json, Card.class);
        return Optional.of(card);
    } catch (Exception e) { // NOSONAR — graceful fallback for missing/corrupt cards
        log.debug("Card not found: {} - {}", cardId, e.getMessage());
    }
    return Optional.empty();
}
```

- Log at the appropriate level: `debug` for expected failures, `warn` for recoverable issues, `error` for unexpected failures (include the exception object as the last argument).

### Null Handling

- Use `Optional` for lookup operations that may not find a result.
- Use defensive null checks at method entry for public APIs.
- Never return `null` from public methods — return `Optional`, empty collection, or empty string.

```java
// Lookup — return Optional
public Optional<Card> findById(String id) {
    return Optional.ofNullable(cardRegistry.get(id));
}

// Defensive check
public String sanitize(String input) {
    if (input == null) {
        return "";
    }
    return normalizeUnicode(input);
}
```

---

## Testing

### Structure

Tests mirror the main source structure. One test class per production class.

### Naming

- Test class: `*Test` suffix (`BoardServiceTest`, `AssignmentPolicyResolverTest`)
- Test method: descriptive name without `test` prefix

```java
@Test
void shouldCreateCardWhenBoardExists() { }

@Test
void shouldRejectPathTraversalAttempt() { }

@Test
void shouldReturnEmptyWhenCardNotFound() { }
```

### Pattern

Use Arrange-Act-Assert (Given-When-Then):

```java
@Test
void shouldResolveCompletedSignalToReviewColumn() {
    // Arrange
    BoardFlowDefinition flow = createEngineeringFlow();
    Card card = createCardInColumn("in_progress");
    CardLifecycleSignal signal = createCompletedSignal(card.getId());

    // Act
    CardTransitionResult result = transitionService.resolve(flow, card, signal);

    // Assert
    assertEquals("review", result.getTargetColumnId());
    assertEquals(TransitionDecision.AUTO_APPLY, result.getDecision());
}
```

### Mocking

Use Mockito. Create mocks in `@BeforeEach`, not as class fields with `@Mock`:

```java
class BoardServiceTest {

    private StoragePort storagePort;
    private BoardService boardService;

    @BeforeEach
    void setUp() {
        storagePort = mock(StoragePort.class);
        boardService = new BoardService(storagePort, new ObjectMapper(), Clock.systemUTC());
    }
}
```

For varargs mocks, use custom `Answer` on mock creation:

```java
CommandDispatchPort commandDispatchPort = mock(CommandDispatchPort.class, invocation -> {
    if (invocation.getMethod().getName().equals("dispatch")) {
        return CompletableFuture.completedFuture(CommandDispatchResult.accepted("cmd-1"));
    }
    return null;
});
```

### Parametrized Tests

Use `@ParameterizedTest` for input validation and normalization rules:

```java
@ParameterizedTest
@ValueSource(strings = {
    "OPS TEAM",
    "../admin",
    "role:admin"
})
void shouldRejectInvalidRoleSlug(String input) {
    assertFalse(roleSlugValidator.isValid(input));
}
```

---

## Frontend UI

### Tailwind-First UI in New Work

For new or substantially reworked UI, prefer shared local UI primitives and Tailwind CSS 3 utility classes.

This keeps interaction patterns and spacing consistent and avoids introducing a second component stack such as `react-bootstrap`.

### Inputs With Leading Icons

Inputs with a leading search or status icon must reserve a dedicated icon slot. Do not place an absolutely positioned icon on top of a standard input and assume the default input padding will be enough.

This bug repeats easily:
- the icon visually overlaps the placeholder
- typed text starts underneath the icon
- the issue can appear only on certain font sizes or responsive breakpoints

Use these rules:
- wrap the input in a `relative` container
- position the icon with an explicit slot, for example `absolute left-3 top-1/2 -translate-y-1/2`
- increase left padding on the input to clear the icon, typically `pl-11`
- keep the icon `pointer-events-none`
- verify both placeholder text and typed text, not just the empty field

Recommended pattern:

```tsx
<label className="relative block">
  <span className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground">
    <FiSearch size={14} />
  </span>
  <Input
    className="h-10 pl-11 pr-3"
    placeholder="Search golems"
    aria-label="Search golems"
  />
</label>
```

Avoid patterns where the icon container stretches across the full height using only `inset-y-0 left-0` plus small input padding like `pl-8` or `pl-9`. That spacing is too fragile and tends to regress.

---

## Architecture Rules

### Port/Adapter Boundaries

Domain code (`domain/`) depends only on port interfaces (`port/`). Never import adapter classes in domain code.

```
domain/ → port/         (allowed)
domain/ → adapter/      (PROHIBITED)
adapter/ → port/        (allowed)
adapter/ → domain/      (allowed for models and services)
```

### JSON Persistence

Hive uses local JSON persistence. Domain services must not write files directly. File I/O belongs in storage adapters.

Rules:

- write through a storage port or dedicated persistence adapter,
- use temp-file + atomic replace semantics,
- never partially update a JSON file in place,
- record schema version fields on persisted top-level documents when evolution is expected.

### Card State Ownership

Board state belongs to Hive. Bot/runtime integrations may emit structured lifecycle signals, but they do not directly own card columns.

Rules:

- card column changes go through one transition service,
- signal ingestion records raw signals before applying policy,
- free-form text parsing must not be the primary transition source,
- manual operator moves remain explicit transition events.

### Models

Domain models live in `domain/model/`. Use `@Builder` for construction and keep API-facing DTOs separate from persistence payloads when semantics differ:

```java
CardLifecycleSignal signal = CardLifecycleSignal.builder()
        .cardId(cardId)
        .golemId(golemId)
        .signalType(SignalType.BLOCKER_RAISED)
        .summary("Missing staging credentials")
        .createdAt(Instant.now())
        .build();
```
