# Radio Streaming API

Spring Boot REST API for the Flutter radio streaming app.

**Flutter app repo:** https://github.com/VikasbeerSingh1993/radio_streaming_app

## Tech Stack

- Java 21
- Spring Boot 3.4.2
- Spring Data MongoDB
- Spring Security + JWT (admin console)
- AngularJS 1.8 admin UI
- MongoDB Atlas (`divine_bliss_streaming` database)

## Admin console

Open **http://localhost:8080/admin/** after the API is running.

Default bootstrap login (change in production):

- Username: `admin` (or `ADMIN_USERNAME`)
- Password: `admin` (or `ADMIN_PASSWORD`)

Credentials are stored in MongoDB collection `admins` (password hashed). Set `ADMIN_RESET_PASSWORD=true` once if you need to reset the seeded password from env vars.

From the admin UI you can:

- Sign in as the seeded **super admin** (`admin` / `admin` unless you change env vars)
- Create **sub-admins** with independent Read / Add / Edit / Delete / Approve flags
- Limit a sub-admin to selected **categories** (stations and audio links follow those categories)
- Limit events to selected **organizations** (for example only `dodra`)
- Optionally restrict a user to **records they created**
- Search on every page against the in-memory admin cache (MongoDB indexes back the collections)
- Refresh from the ↻ icon when you want a live database snapshot; otherwise screens always use cache

MongoDB indexes are created automatically (`spring.data.mongodb.auto-index-creation=true`) on searchable fields such as event title/city/organization, station category, and admin username.

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Health check |
| GET | `/api/cache/status` | Cache age, TTL, and item counts |
| POST | `/api/cache/reload` | Reload stations, categories, events, and links from MongoDB |
| GET | `/api/stations` | All radio stations (from cache) |
| GET | `/api/categories` | All categories (from cache) |
| GET | `/api/events` | Approved/public events (from cache) |
| GET | `/api/events/nearby?lat=&lng=&radiusKm=50` | Approved events near a coordinate |
| POST | `/api/events/submit` | User event submission (stored as `pending`) |
| GET | `/api/audio-links/station/{stationId}` | Audio links for a station (from cache) |
| PUT | `/api/audio-links/{linkId}/played` | Update played flag (`{"played": true}`) |
| POST | `/api/audio-links/station/{stationId}/reset` | Reset played flags for station |
| POST | `/api/admin/login` | Admin login (`username`, `password`) |
| GET | `/api/admin/**` | JWT-protected catalog management |

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
   | `ADMIN_USERNAME` | Admin login username |
   | `ADMIN_PASSWORD` | Admin login password |
   | `JWT_SECRET` | Long random string used to sign admin JWTs |

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
