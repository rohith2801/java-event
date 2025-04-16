```markdown
# Java Event Application

## Overview
The **Java Event Application** is a Spring Boot-based project designed to handle event-related operations. It uses Maven for dependency management and includes configurations for RESTful web services, CORS, and JSON serialization.

---

## Project Structure

### Key Files and Directories
- **`src/main/resources/application.properties`**: Contains application-level configurations such as server port, context path, and custom properties.
- **`src/main/java/com/demo/java_event/JavaEventApplication.java`**: The main entry point for the Spring Boot application.
- **`src/main/java/com/demo/java_event/config/AppConfig.java`**: Contains application-wide configurations such as CORS, `RestTemplate`, and `ObjectMapper` beans.
- **`src/main/java/com/demo/java_event/model/Reaction.java`**: A model class representing a reaction entity with fields like `id`, `eventId`, `emoji`, `userId`, and `ts`.

---

## Configuration

### `application.properties`
```ini
spring.application.name=java-event
server.port=9191
server.servlet.context-path=/${spring.application.name}
spring.mvc.async.request-timeout=0

sse.timeout=3600
sse.heartbeat.enabled=false
sse.heartbeat.interval=30
sse.resend.enabled=false
```

### Key Configurations
- **Server Port**: `9191`
- **Context Path**: `/java-event`
- **CORS**: Allows all origins and methods.
- **SSE (Server-Sent Events)**: Configurable timeout and heartbeat settings.

---

## Dependencies
The project uses the following key dependencies:
- **Spring Boot**: For building the application.
- **Jackson**: For JSON serialization/deserialization.
- **Spring Web**: For RESTful web services.
- **Maven Wrapper**: For consistent Maven builds.

---

## Key Components

### `JavaEventApplication.java`
The main class that bootstraps the Spring Boot application.

### `AppConfig.java`
- **CORS Configuration**: Allows all origins and HTTP methods.
- **Beans**:
    - `RestTemplate`: For making REST API calls.
    - `ObjectMapper`: Configured with `JavaTimeModule` for handling Java 8 date/time types.

### `Reaction.java`
A model class representing a reaction entity:
- **Fields**:
    - `id` (Long): Unique identifier.
    - `eventId` (String): Associated event ID.
    - `emoji` (String): Reaction emoji.
    - `userId` (String): User who reacted.
    - `ts` (LocalDateTime): Timestamp of the reaction.

---

## Build and Run

### Prerequisites
- **Java 17+**
- **Maven**

### Build
Run the following command to build the project:
```bash
./mvnw clean install
```

### Run
Start the application using:
```bash
./mvnw spring-boot:run
```

---

## Additional Resources

### Reference Documentation
- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Maven Documentation](https://maven.apache.org/guides/index.html)

### Guides
- [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
- [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)

---
```