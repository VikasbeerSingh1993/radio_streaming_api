# Radio Streaming API

Spring Boot REST API and admin console for the Flutter radio streaming app.

**Flutter app:** https://github.com/VikasbeerSingh1993/radio_streaming_app

**Live API:** https://api-production-31af.up.railway.app  
**Live admin console:** https://api-production-31af.up.railway.app/admin/

## What this service does

- Serves stations, categories, events, and audio links to the Flutter app from an in-memory cache in front of MongoDB.
- Accepts public event submissions and keeps them `pending` until an admin approves them.
- Finds approved events near a latitude/longitude.
- Hosts an AngularJS admin console for catalog CRUD, event approval, and sub-admin access control.
- Uses JWT for admin login. Public `/api/**` routes do not require a token.

## Tech stack

| Piece | Choice |
|-------|--------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.4.2 |
| Database | MongoDB Atlas, database `divine_bliss_streaming` |
| Security | Spring Security, BCrypt passwords, JWT (8 hour expiry) |
| Admin UI | AngularJS 1.8 static SPA under `/admin/` |
| Deploy | Railway, Docker, auto-deploy from `main` |

## Architecture

```
Flutter app  ──GET /api/stations, /events, /audio-links/...──►  Spring Boot
Public users ──POST /api/events/submit────────────────────────►  MongoDB
Admin browser ──/admin/ + /api/admin/** (JWT)─────────────────►  In-memory cache
                                                                  ▲
                                                                  │ 24h TTL, or ↻ refresh
                                                                  └── MongoDB collections
```

**Cache**

- On startup the API loads stations, categories, events, and audio links into memory (`app.cache.ttl=24h`).
- Public and admin **reads** come from that snapshot. They do not hit Mongo on every request.
- Create / update / delete **patches the cache** in place. They do not reload the whole database.
- The ↻ button in the admin UI (and `POST /api/admin/cache/reload`) is the only path that reloads everything from Mongo.

**Admin lists**

- Admin list endpoints return a page, not the full collection:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "total": 134,
  "totalPages": 7
}
```

- Query params: `q` (search), `page` (0-based), `size` (1–100, default 20).
- Events also accept `status=pending|approved|rejected|all`.
- Audio links also accept `stationId`.

## MongoDB collections

| Collection | Purpose |
|------------|---------|
| `stations` | Radio stations (`category`, `live`, `play_mode`, `translations`) |
| `categories` | Station groups (`category` key, `order`, `icon`, `translations`) |
| `audio_links` | Playlist URLs per station (`station_id`, `url`, `sequence`, `played`) |
| `events` | Sangat / community events (`approvalStatus`, `organization`, geo) |
| `admins` | Super-admins and sub-admins (BCrypt password hash, permissions) |

Indexes are created automatically (`spring.data.mongodb.auto-index-creation=true`) on searchable fields such as event title/city/organization, station category, and admin username.

## Event approval flow

1. The app collects event details plus **username** and **email**.
2. `POST /api/events/submit` stores a draft and emails a 6-digit OTP. The event is **not** created yet.
3. The user enters the code. `POST /api/events/submit/verify` checks it, then creates the event as `approvalStatus: pending`.
4. Public `GET /api/events` and nearby search return only **approved** events (legacy rows with a blank status are treated as approved).
5. An admin with Approve permission reviews the event and **Approve** or **Reject**.
6. Admins can also create events directly as approved.

OTP codes expire in 10 minutes, drafts expire in 30 minutes, resend waits 60 seconds, and verification is limited to 5 attempts. The code is hashed; it is never returned in the API response.

Submit body (ISO-8601 dates):

```json
{
  "title": "Sunday Sangat",
  "date": "2026-08-30T10:00:00Z",
  "end_date": "2026-08-30T13:00:00Z",
  "city": "Amritsar",
  "address": "Gurdwara road",
  "latitude": 31.634,
  "longitude": 74.872,
  "organizedBy": "Dodra",
  "username": "aman",
  "submitterName": "Aman Singh",
  "submitterEmail": "user@example.com",
  "submitterPhone": "+91..."
}
```

## Admin console

Open **http://localhost:8080/admin/** locally, or the live URL above.

Bootstrap login (change in production):

| | Default | Environment variable |
|---|---|---|
| Username | `admin` | `ADMIN_USERNAME` |
| Password | `admin` | `ADMIN_PASSWORD` |

Credentials live in MongoDB `admins`. Passwords are hashed. Set `ADMIN_RESET_PASSWORD=true` for one deploy if you need to reset the seeded password from env vars, then turn it off.

### Screens

| Page | What you can do |
|------|-----------------|
| Dashboard | Counts for pending/approved events, stations, links; cache age |
| Events | Filter pending / approved / rejected; approve, reject, add, edit, delete |
| Stations | Add / edit / delete stations shown in the app |
| Categories | Station grouping keys and display names |
| Audio links | Playlist URLs attached to a station |
| Sub-admins | Create users and grant module rights |

Every list page has search, pagination (10 / 20 / 50 rows), and a ↻ control. Popups close with **Cancel**, the **X**, the dimmed background, or Escape. Save / Edit / Delete show a loader until the request finishes.

### Roles and permissions

- **Super admin** — full access to every module.
- **Sub-admin** — independent flags per module: Read, Add, Edit, Delete, and Approve (events only).

You can also:

- Limit a user to selected **category keys** (stations and audio links follow those categories).
- Limit events to selected **organizations** (for example only `dodra`).
- Restrict a user to **records they created**.
- New sub-admin passwords must be at least 8 characters.

## Public API

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/health` | Health check |
| GET | `/api/cache/status` | Cache age, TTL, and item counts |
| POST | `/api/cache/reload` | Force reload from MongoDB |
| GET | `/api/stations` | All stations (cache) |
| GET | `/api/categories` | All categories (cache) |
| GET | `/api/events` | Approved / public events (cache) |
| GET | `/api/events/nearby?lat=&lng=&radiusKm=50` | Approved events within radius (max 500 km) |
| POST | `/api/events/submit` | Start submit: save draft and email OTP |
| POST | `/api/events/submit/verify` | Verify OTP, then create pending event |
| POST | `/api/events/submit/resend` | Resend OTP for a draft |
| GET | `/api/audio-links/station/{stationId}` | Audio links for a station (cache) |
| PUT | `/api/audio-links/{linkId}/played` | `{"played": true}` |
| POST | `/api/audio-links/station/{stationId}/reset` | Mark all links unplayed |

## Admin API

All routes below require `Authorization: Bearer <jwt>` except login.

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/admin/login` | `{ "username", "password" }` → `{ token, profile }` |
| GET | `/api/admin/me` | Current admin profile |
| GET | `/api/admin/stats` | Dashboard counts |
| POST | `/api/admin/cache/reload` | Reload Mongo into cache |
| GET | `/api/admin/events` | Page of events (`q`, `status`, `page`, `size`) |
| POST | `/api/admin/events` | Create event |
| PUT | `/api/admin/events/{id}` | Update event |
| DELETE | `/api/admin/events/{id}` | Delete event |
| POST | `/api/admin/events/{id}/approve` | Approve (`reviewNote` optional) |
| POST | `/api/admin/events/{id}/reject` | Reject (`reviewNote` optional) |
| GET/POST/PUT/DELETE | `/api/admin/stations` | Stations |
| GET/POST/PUT/DELETE | `/api/admin/categories` | Categories |
| GET/POST/PUT/DELETE | `/api/admin/audio-links` | Audio links (`stationId` filter on GET) |
| GET/POST/PUT/DELETE | `/api/admin/users` | Sub-admins |

Login example:

```powershell
$body = '{"username":"admin","password":"admin"}'
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/admin/login -ContentType application/json -Body $body
```

## Environment variables

| Variable | Required | Default | Purpose |
|----------|----------|---------|---------|
| `MONGODB_URI` | Yes (prod) | placeholder URI | MongoDB Atlas connection string |
| `PORT` | Railway sets this | `8080` | HTTP port |
| `ADMIN_USERNAME` | No | `admin` | Seeded super-admin username |
| `ADMIN_PASSWORD` | No | `admin` | Seeded super-admin password |
| `ADMIN_RESET_PASSWORD` | No | `false` | Set `true` once to reset the seed password |
| `JWT_SECRET` | Yes (prod) | dev placeholder | Must be a long random string in production |
| `MAIL_HOST` | Yes (prod) | unset | SMTP host, e.g. `smtp.gmail.com` |
| `MAIL_PORT` | No | `587` | SMTP port |
| `MAIL_USERNAME` | Yes (prod) | unset | SMTP username |
| `MAIL_PASSWORD` | Yes (prod) | unset | SMTP password or app password |
| `MAIL_FROM` | No | `MAIL_USERNAME` | From address on OTP emails |
| `MAIL_LOG_OTP` | No | `false` | Set `true` only in local dev to log OTPs when mail is unset |
| `app.cache.ttl` | No | `24h` | How long the in-memory snapshot is considered fresh |

Do not commit real Mongo URIs or production passwords.

## Run locally

Requires **Java 21** and **Maven**.

```powershell
$env:MONGODB_URI="mongodb+srv://USER:PASS@cluster0.example.mongodb.net/divine_bliss_streaming"
$env:JWT_SECRET="change-me-to-a-long-random-string"
mvn spring-boot:run
```

Then:

- API: http://localhost:8080/api/health
- Admin: http://localhost:8080/admin/

```powershell
mvn test
```

## Deploy on Railway

This repo is wired for Railway:

- `Dockerfile` — multi-stage Java 21 build
- `railway.toml` — Dockerfile builder + `/api/health` check
- `server.port=${PORT:8080}` — uses Railway’s `PORT`

### First-time setup

1. Create a Railway project from this GitHub repo (`radio_streaming_api`).
2. Set variables:

   | Variable | Value |
   |----------|--------|
   | `MONGODB_URI` | MongoDB Atlas connection string |
   | `ADMIN_USERNAME` | Super-admin username |
   | `ADMIN_PASSWORD` | Super-admin password |
   | `JWT_SECRET` | Long random string |
   | `MAIL_HOST` | SMTP host (`smtp.gmail.com`) |
   | `MAIL_PORT` | `587` |
   | `MAIL_USERNAME` | SMTP username |
   | `MAIL_PASSWORD` | SMTP password or Gmail app password |
   | `MAIL_FROM` | From address for OTP emails |
   | `ADMIN_RESET_PASSWORD` | `true` only when you need to reset the seed user |

3. In **Settings → Networking**, generate a public domain.
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

## Project layout

```
radio_streaming_api/
├── Dockerfile
├── railway.toml
├── pom.xml
└── src/main/
    ├── java/com/radiostreaming/api/
    │   ├── controller/     # Public and admin HTTP APIs
    │   ├── security/       # JWT filter, RBAC enums
    │   ├── service/        # Cache, catalog, admin users
    │   ├── model/          # Mongo documents
    │   ├── repository/     # Spring Data Mongo
    │   └── dto/            # Request/response bodies
    └── resources/
        ├── application.properties
        └── static/admin/   # AngularJS console (index.html, css, js)
```

## Related app

Point the Flutter app at this API in `lib/config/api_config.dart`:

```dart
static const String baseUrl = 'https://api-production-31af.up.railway.app/api';
```

Local emulator: `http://10.0.2.2:8080/api`
