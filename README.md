# Spring Boot AI Model Routing with Jev

A Spring Boot + Spring AI proof of concept that uses **Jev through Spring AI Community TypeSafe** to classify an incoming prompt into a model tier, then routes the actual request to the selected Gemini model.

The project demonstrates a simple **AI model router**:

```text
Client
  |
  | POST /chat
  v
ChatController
  |
  | prompt
  v
ModelRouter
  |
  | Jev / TypeSafe classification
  v
GeminiModelTier
  |
  | selected model + confidence + probabilities
  v
Spring AI ChatClient
  |
  | request using selected Gemini model
  v
Gemini
```

## What this project demonstrates

The main idea is to separate **routing** from **answer generation**.

For every incoming prompt:

1. The prompt is sent to Jev through `TypeSafeClient`.
2. Jev chooses one of the configured model tiers.
3. The router receives:
   - selected tier
   - model ID
   - confidence
   - probabilities
4. Spring AI `ChatClient` sends the original prompt to the selected Gemini model.
5. The API returns both the routing decision and the generated answer.

This allows the application to use a cheaper/smaller model for simple requests while selecting a more capable model when the prompt requires it.

## Project structure

```text
src/main/java/in/codebuffdev/boot/routing/
│
├── SpringbootAiModelRoutingwithjevApplication.java
│
├── config/
│   └── AiConfig.java
│
├── controller/
│   └── ChatController.java
│
├── dto/
│   └── RoutingDecision.java
│
├── service/
│   └── ModelRouter.java
│
└── tier/
    └── GeminiModelTier.java
```

### Main components

#### `GeminiModelTier`

Defines the available model tiers.

```java
public enum GeminiModelTier {

    FLASH_LITE(
            "gemini-3.5-flash-lite",
            "Lightweight, latency-sensitive requests where speed and cost matter most."),

    FLASH_3_6(
            "gemini-3.6-flash",
            "High-efficiency workhorse: everyday coding, web tasks, and fast agentic tool use."),

    FLASH_3_8(
            "gemini-3.8-flash",
            "High-intensity Flash reasoning: long-horizon coding, agentic planning, and iterative tool calling.");
}
```

The descriptions are important because they provide the semantic information used by the router when choosing a tier.

#### `ModelRouter`

`ModelRouter` is responsible for making the model-selection decision.

It builds a TypeSafe `Choice` dynamically from the `GeminiModelTier` enum:

```java
Choice.Builder choiceInstructions =
        Choice.builder()
                .instructions(
                    "Which model tier is the cheapest one that can still answer this prompt well? " +
                    "Prefer the cheaper tier unless the prompt clearly needs more capability."
                );
```

Each enum value becomes an available choice.

The prompt is then evaluated through:

```java
typeSafeClient
        .systemOne(prompt, Map.of(QUESTION, tierChoice))
        .choice(QUESTION);
```

The result contains the selected value, confidence, and probabilities.

#### `RoutingDecision`

The routing result is represented as:

```java
public record RoutingDecision(
        GeminiModelTier tier,
        String model,
        double confidence,
        Map<String, Double> probabilities
) {
}
```

This keeps the routing decision explicit instead of hiding it inside the final model call.

#### `ChatController`

The controller performs the complete flow:

```java
RoutingDecision routingDecision = modelRouter.route(request.prompt());

String content = chatClient.prompt()
        .user(request.prompt())
        .options(ChatOptions.builder()
                .model(routingDecision.model()))
        .call()
        .content();
```

The selected model is therefore determined dynamically for every request.

## Dependency guide

The project uses Maven.

### Core dependencies

#### 1. Spring Boot Web MVC

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
```

Purpose:

- Creates the REST API.
- Provides Spring MVC.
- Provides embedded web-server support.
- Enables `@RestController` and `@PostMapping`.

This project exposes:

```text
POST /chat
```

---

#### 2. Spring AI OpenAI starter

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

Purpose:

Provides Spring AI's OpenAI-compatible chat-model integration.

Although the dependency is named `openai`, this project uses it against Google's OpenAI-compatible Gemini API endpoint:

```properties
spring.ai.openai.chat.base-url=https://generativelanguage.googleapis.com/v1beta/openai
```

That allows Spring AI's OpenAI model integration to communicate with Gemini through its compatible API.

---

#### 3. Spring AI Community TypeSafe

```xml
<dependency>
    <groupId>org.springaicommunity</groupId>
    <artifactId>spring-ai-starter-typesafe</artifactId>
    <version>0.1.0</version>
</dependency>
```

This is the dependency that provides the Jev/TypeSafe integration used by the model router.

The application uses:

```java
import org.springaicommunity.typesafe.TypeSafeClient;
```

and:

```java
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
```

The important concept is that the application does not ask Jev for free-form text. It defines a structured question with a fixed set of choices.

For this project:

```text
FLASH_LITE
FLASH_3_6
FLASH_3_8
```

are the possible routing decisions.

---

#### 4. Spring Boot test dependency

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
```

Used for Spring Boot test support.

The current test verifies that the application context can start:

```java
@SpringBootTest
class SpringbootAiModelRoutingwithjevApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

---

### Dependency management

Spring AI dependencies are managed through the Spring AI BOM:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

The project currently defines:

```xml
<spring-ai.version>2.0.1</spring-ai.version>
```

This means the Spring AI modules that participate in the BOM inherit their versions from the BOM rather than requiring a version on every dependency.

The TypeSafe dependency is explicitly versioned because it is a Spring AI Community artifact:

```xml
<version>0.1.0</version>
```

## Java and build requirements

The project currently uses:

```xml
<java.version>24</java.version>
```

So use a JDK compatible with Java 24.

The Maven Wrapper is included in the repository, so Maven does not need to be installed globally.

Maven Wrapper version:

```text
Apache Maven 3.9.16
```

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

### Linux/macOS/WSL

```bash
./mvnw spring-boot:run
```

## API configuration

The application uses environment variables for API keys.

`application.properties`:

```properties
spring.application.name=springboot-ai-model-routingwithjev

spring.ai.openai.chat.base-url=https://generativelanguage.googleapis.com/v1beta/openai
spring.ai.openai.api-key=${GEMINI_API_KEY}

spring.ai.openai.chat.model=models/gemini-3.6-flash
spring.ai.openai.chat.temperature=2

spring.ai.typesafe.api-key=${TYPE_SAFE_API_KEY}
```

### Required environment variables

You need:

```text
GEMINI_API_KEY
TYPE_SAFE_API_KEY
```

Do not commit these values to Git.

### Windows PowerShell

```powershell
$env:GEMINI_API_KEY="your-gemini-api-key"
$env:TYPE_SAFE_API_KEY="your-typesafe-api-key"
```

Then run:

```powershell
.\mvnw.cmd spring-boot:run
```

### Windows CMD

```cmd
set GEMINI_API_KEY=your-gemini-api-key
set TYPE_SAFE_API_KEY=your-typesafe-api-key
```

Then:

```cmd
mvnw.cmd spring-boot:run
```

### Linux/macOS/WSL

```bash
export GEMINI_API_KEY="your-gemini-api-key"
export TYPE_SAFE_API_KEY="your-typesafe-api-key"
```

Then:

```bash
./mvnw spring-boot:run
```

## Running the application

Clone the repository and enter the project directory:

```bash
cd springboot-ai-model-routingwithjev
```

Set the required environment variables and start Spring Boot:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

## Calling the API

Endpoint:

```text
POST /chat
```

Request body:

```json
{
  "prompt": "Explain Java virtual threads"
}
```

Example using `curl`:

```bash
curl -X POST http://localhost:8080/chat \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Explain Java virtual threads"}'
```

The response has this general structure:

```json
{
  "routingDecision": {
    "tier": "FLASH_3_6",
    "model": "gemini-3.6-flash",
    "confidence": 0.91,
    "probabilities": {
      "FLASH_LITE": 0.03,
      "FLASH_3_6": 0.91,
      "FLASH_3_8": 0.06
    }
  },
  "answer": "..."
}
```

The exact routing result depends on the router's classification.

## Request flow in detail

### Step 1 — Client sends a prompt

```http
POST /chat
Content-Type: application/json

{
  "prompt": "Explain Kubernetes service discovery"
}
```

### Step 2 — Controller delegates routing

```java
RoutingDecision routingDecision =
        modelRouter.route(request.prompt());
```

The controller does not decide which model should answer the prompt.

### Step 3 — `ModelRouter` asks Jev

The router creates a structured choice:

```text
Question:
Which model tier is the cheapest one that can still answer this prompt well?

Choices:
    FLASH_LITE
    FLASH_3_6
    FLASH_3_8
```

Jev evaluates the prompt against those choices.

### Step 4 — Router receives structured output

The returned `ChoiceAnswer` provides:

```text
value
confidence
probabilities
```

The selected value is converted back into the Java enum:

```java
GeminiModelTier tier =
        GeminiModelTier.valueOf(choiceAnswer.value());
```

### Step 5 — Spring AI calls the selected model

The selected model is passed into `ChatOptions`:

```java
ChatOptions.builder()
        .model(routingDecision.model())
```

The actual answer is then generated by the selected Gemini model.

## Architecture

```text
                         +------------------+
                         |      Client      |
                         +--------+---------+
                                  |
                                  | POST /chat
                                  v
                         +------------------+
                         | ChatController   |
                         +--------+---------+
                                  |
                                  | prompt
                                  v
                         +------------------+
                         |   ModelRouter    |
                         +--------+---------+
                                  |
                                  | structured choice
                                  v
                         +------------------+
                         |  Jev / TypeSafe  |
                         +--------+---------+
                                  |
                                  | tier + confidence
                                  v
                         +------------------+
                         | GeminiModelTier  |
                         +--------+---------+
                                  |
                                  | selected model
                                  v
                         +------------------+
                         | Spring AI        |
                         | ChatClient       |
                         +--------+---------+
                                  |
                                  v
                         +------------------+
                         | Gemini Model     |
                         +------------------+
```

## Why use a model router?

Different prompts do not necessarily require the same model capability.

For example:

```text
Simple:
"Convert 10 USD to EUR."

Moderate:
"Explain how Java class loading works."

Complex:
"Design a distributed event-driven architecture with failure recovery,
idempotency, retries, observability, and Kubernetes deployment."
```

Sending every request to the most capable model can increase cost and latency.

A routing layer can instead make a structured decision first:

```text
Prompt
  |
  v
Routing decision
  |
  +--> cheap model
  |
  +--> standard model
  |
  +--> high-capability model
```

The important architectural separation is:

```text
Routing decision != Answer generation
```

Jev decides **which tier should handle the request**.

Gemini generates **the actual answer**.

## Important design detail

The router's instruction explicitly asks for the cheapest tier that can still answer the prompt adequately:

```text
Which model tier is the cheapest one that can still answer this prompt well?
Prefer the cheaper tier unless the prompt clearly needs more capability.
```

This makes the routing policy explicit rather than relying only on model-name semantics.

The tier descriptions also encode the intended capability boundary:

```text
FLASH_LITE
    |
    +-- latency-sensitive / cost-sensitive

FLASH_3_6
    |
    +-- everyday coding / web / agentic work

FLASH_3_8
    |
    +-- intensive reasoning / long-horizon coding / complex tool use
```

## Configuration vs routing

There are two different model settings in the application.

### Default configured model

```properties
spring.ai.openai.chat.model=models/gemini-3.6-flash
```

This provides the default Spring AI model configuration.

### Runtime selected model

The router can override the model for an individual request:

```java
.options(
    ChatOptions.builder()
        .model(routingDecision.model())
)
```

Therefore the runtime routing decision is what determines the model used for the `/chat` request.

## Build and test

Compile the project:

```bash
./mvnw clean compile
```

Run tests:

```bash
./mvnw test
```

Package the application:

```bash
./mvnw clean package
```

Run the generated JAR:

```bash
java -jar target/springboot-ai-model-routingwithjev-0.0.1-SNAPSHOT.jar
```

## Learning goals

This project is intended as a focused POC for understanding:

- Spring Boot REST APIs
- Spring AI `ChatClient`
- `ChatOptions`
- OpenAI-compatible model providers
- Gemini through an OpenAI-compatible endpoint
- Jev structured classification
- Spring AI Community TypeSafe
- Structured choices instead of free-form LLM routing
- Model tier abstraction
- Runtime model selection
- Routing confidence and probability information
- AI model-cost/latency optimization patterns

## Possible next steps

This POC can be extended with:

1. Add more model tiers.
2. Add a fallback when routing confidence is below a threshold.
3. Add logging/metrics for routing decisions.
4. Track model usage and estimated cost.
5. Add request IDs and tracing.
6. Add retry handling for provider failures.
7. Add a circuit breaker around model calls.
8. Separate routing policy from the `ModelRouter` implementation.
9. Add integration tests for routing behavior.
10. Compare routed execution against always using one model.
11. Add token/cost telemetry.
12. Add configurable routing policies through application properties.
13. Add an evaluation dataset to measure routing quality.
14. Add a second provider and route across providers as well as models.

## Key takeaway

The core pattern demonstrated by this project is:

```text
                    ┌──────────────────┐
                    │      Prompt      │
                    └────────┬─────────┘
                             │
                             v
                    ┌──────────────────┐
                    │  Jev / TypeSafe  │
                    │  Classification  │
                    └────────┬─────────┘
                             │
                             v
                    ┌──────────────────┐
                    │   Model Tier     │
                    │    Selection     │
                    └────────┬─────────┘
                             │
                             v
                    ┌──────────────────┐
                    │ Spring AI        │
                    │ ChatClient       │
                    └────────┬─────────┘
                             │
                             v
                    ┌──────────────────┐
                    │ Selected Gemini  │
                    │      Model       │
                    └──────────────────┘
```

This is a small implementation of a broader **LLM routing architecture**: use one decision layer to determine which model should handle a request, then delegate the actual generation to the selected model.
