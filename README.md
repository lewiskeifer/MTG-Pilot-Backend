# MTG Pilot — Backend

REST API for tracking Magic: The Gathering decks and sealed collections. Pulls live market prices from the TCG Player API and records daily value snapshots for trend tracking.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 8 |
| Framework | Spring Boot 2.1.3 |
| Database | MySQL (via Spring Data JPA / Hibernate) |
| Auth | JWT (`jjwt`) |
| Build | Gradle |
| API Docs | Swagger / Springfox 2.9.2 |

## Prerequisites

- Java 8+
- MySQL database
- TCG Player API credentials (public/private key)
- PKCS12 SSL certificate (for HTTPS)

## Configuration

The app reads from two config files under `src/main/resources/`.

### `application.yml`

```yaml
publicKey: YOUR_TCG_PUBLIC_KEY
privateKey: YOUR_TCG_PRIVATE_KEY
secretKey: YOUR_JWT_SECRET_KEY

server:
  port: 8443
```

## Build & Run

```bash
# Build
./gradlew build

# Run
./gradlew bootRun

# Or run the jar directly
java -jar build/libs/finance-manager-0.1.0.jar
```

The server starts on `https://localhost:8443`.

Swagger UI is available at `https://localhost:8443/swagger-ui.html`.

## Authentication

JWT is required for all `/manager/*` endpoints. After logging in, include the returned token in the `Authorization` header:

```
Authorization: Bearer <token>
```

Sealed collection endpoints (`/sealed/*`) do not require authentication.

## API Reference

### Auth

```
POST /register                  body: User
POST /login                     body: { username, password }  →  User (with token)
POST /reset-password            body: { username, email }     →  User
```

---

### Users — `/manager/users`

```
GET    /manager/users                →  User[]
GET    /manager/users/{userId}       →  User
PUT    /manager/users                →  User
DELETE /manager/users/{userId}
```

---

### Decks — `/manager/users/{userId}/decks`

```
GET    /manager/users/{userId}/decks             →  Deck[]
GET    /manager/users/{userId}/decks/{deckId}    →  Deck
PUT    /manager/users/{userId}/decks             →  Deck
DELETE /manager/users/{userId}/decks/{deckId}
```

`GET /decks` returns a synthetic "Deck Overview" entry as the first item in the list — each card in the overview represents a single deck's aggregate value. Pass `deckId = 0` to retrieve just the overview on its own.

---

### Cards — `/manager/users/{userId}/decks/{deckId}/cards`

```
PUT    /manager/users/{userId}/decks/{deckId}/cards/{cardId}    →  Card
DELETE /manager/users/{userId}/decks/{deckId}/cards/{cardId}
```

Creating a card (`Card.id == null`) fetches the current market price and product image URL from TCG Player automatically.

---

### Snapshots & Ordering

```
PUT /manager/users/{userId}/decks/{deckId}/refresh      (triggers price refresh + snapshot)
PUT /manager/users/{userId}/decks/{deckId}/ordering     body: Integer  →  Deck
```

Pass `deckId = 0` to `/refresh` to refresh all decks for the user at once.

---

### Sets

```
GET /manager/users/sets                  →  String[] (all set names)
GET /manager/users/sets/{cardName}       →  String[] (sets containing that card)
GET /manager/users/set/{groupId}         →  String   (set name for groupId)
GET /manager/users/sets/sync             (re-syncs set list from TCG Player)
```

---

### Sealed Collections — `/sealed/{userId}`

```
GET    /sealed/{userId}                                        →  SealedCollection[]
GET    /sealed/{userId}/collection/{sealedId}                  →  SealedCollection
PUT    /sealed/{userId}                                        →  SealedCollection
DELETE /sealed/{userId}/collection/{sealedId}

PUT    /sealed/{userId}/collection/{sealedId}/sealed           →  Sealed
DELETE /sealed/{userId}/collection/{sealedId}/sealed/{cardId}

PUT    /sealed/{userId}/collection/{sealedId}/refresh          (triggers price refresh + snapshot)
PUT    /sealed/{userId}/collection/{sealedId}/ordering         body: Integer  →  SealedCollection
```

---

## Scheduled Jobs

| Time (ET) | Job | Description |
|---|---|---|
| 3:00 AM | Token refresh | Fetches a new OAuth token from TCG Player |
| 4:00 AM | Deck refresh | Updates market prices for all decks, writes daily snapshots |
| 8:00 AM | Set sync | Syncs MTG set/edition list from TCG Player |
| 8:00 AM | Sealed refresh | Updates market prices for all sealed collections, writes daily snapshots |

---

## Project Structure

```
src/main/java/keifer/
├── Application.java
├── api/model/          # DTOs (Card, Deck, Sealed, SealedCollection, User, ...)
├── configuration/      # CORS, JWT filter, Swagger config
├── controller/         # DeckController, SealedController, LoginController
├── converter/          # Entity <-> DTO converters
├── persistence/
│   ├── model/          # JPA entities
│   └── *Repository.java
└── service/            # Business logic + TCG Player API integration
```
