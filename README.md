# Radio Streaming API

Spring Boot REST API for the Flutter radio streaming app.

**Flutter app repo:** https://github.com/VikasbeerSingh1993/radio_streaming_app

## Tech Stack

- Java 21
- Spring Boot 3.4.2
- Spring Data MongoDB
- MongoDB Atlas (`divine_bliss_streaming` database)

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Health check |
| GET | `/api/cache/status` | Cache age, TTL, and item counts |
| POST | `/api/cache/reload` | Reload stations, categories, events, and links from MongoDB |
| GET | `/api/stations` | All radio stations (from cache) |
| GET | `/api/categories` | All categories (from cache) |
| GET | `/api/events` | All events (from cache) |
| GET | `/api/audio-links/station/{stationId}` | Audio links for a station (from cache) |
| PUT | `/api/audio-links/{linkId}/played` | Update played flag (`{"played": true}`) |
| POST | `/api/audio-links/station/{stationId}/reset` | Reset played flags for station |

Normal GET requests are served from an in-memory second-level cache. MongoDB is queried:

- once at startup
- again after `app.cache.ttl` (default 24 hours)
- immediately when `POST /api/cache/reload` is called

A single shared `MongoClient` is used for all database access.

## Configuration

Set MongoDB URI via environment variable (required in production):

```powershell
$env:MONGODB_URI="mongodb+srv://USER:PASS@cluster0.example.mongodb.net/divine_bliss_streaming"
```

Or copy `src/main/resources/application.properties.example` to `application.properties` for local use.

## Run locally

Requires Java 21 and Maven.

```powershell
mvn spring-boot:run
```

Server starts on **http://localhost:8080**

## Test

```powershell
mvn test

curl http://localhost:8080/api/health
curl http://localhost:8080/api/cache/status
curl http://localhost:8080/api/stations
curl -X POST http://localhost:8080/api/cache/reload
```

## GitHub deployment

This repo is the deployable API. Set these environment variables on the host:

| Variable | Purpose |
|----------|---------|
| `MONGODB_URI` | MongoDB Atlas connection string |
| `SERVER_PORT` | Optional. Defaults to `8080` |

### Docker

```powershell
docker build -t radio-streaming-api .
docker run -p 8080:8080 -e MONGODB_URI="mongodb+srv://USER:PASS@cluster0.example.mongodb.net/divine_bliss_streaming" radio-streaming-api
```

Connect this GitHub repo to Railway, Render, Fly.io, or Cloud Run and deploy from `main`.
