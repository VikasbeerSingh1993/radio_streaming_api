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

## Run locally

Requires Java 21 and Maven.

```powershell
$env:MONGODB_URI="mongodb+srv://USER:PASS@cluster0.example.mongodb.net/divine_bliss_streaming"
mvn spring-boot:run
```

Server starts on **http://localhost:8080**

## Deploy on Railway (automatic from GitHub)

This repo is set up for Railway:

- `Dockerfile` — multi-stage Java 21 build
- `railway.toml` — Dockerfile builder + `/api/health` check
- `server.port=${PORT:8080}` — uses Railway's `PORT`

### First-time setup

1. Create a Railway project from this GitHub repo (`radio_streaming_api`).
2. Add a variable:

   | Variable | Value |
   |----------|--------|
   | `MONGODB_URI` | Your MongoDB Atlas connection string |

3. In the service **Settings → Networking**, click **Generate Domain**.
4. Enable GitHub auto-deploy on the `main` branch.

Later pushes to `main` rebuild and redeploy automatically.

### CLI alternative

```powershell
npm i -g @railway/cli
railway login
railway init
railway up
railway domain
railway variable set MONGODB_URI="mongodb+srv://USER:PASS@host/db"
```
