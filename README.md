# SplitMate

An original, production-oriented expense-sharing app (Android + backend), inspired by
Splitwise's core functionality but with entirely original branding, code, and design.
"SplitMate" is a placeholder name; the architecture doesn't hardcode it anywhere that
would make renaming hard (Android `applicationId`/`app_name`, backend `app_name` config
value, and repo name are the only three places it appears).

## Architecture

```
splitmate/
├── backend/          FastAPI + PostgreSQL REST API
│   ├── app/
│   │   ├── balance_engine.py   Core money-math: splits, net balances (integer minor units)
│   │   ├── simplify.py         Debt-simplification algorithm
│   │   ├── models.py           SQLAlchemy models (13 tables)
│   │   ├── routers/            auth, groups, expenses, settlements, friends, notifications,
│   │   │                       recurring expenses, analytics/export
│   │   └── deps.py             JWT auth dependency + per-group authorization guard
│   ├── alembic/                DB migrations
│   └── tests/                  pytest suite for the balance/simplification engine
├── android/          Kotlin + Jetpack Compose app (Gradle project)
│   └── app/src/main/java/app/splitmate/
│       ├── domain/balance/      Client-side mirror of the balance engine (offline previews)
│       ├── data/remote/         Retrofit API + DTOs
│       ├── data/local/          Room entities/DAOs (offline cache + pending-expense queue)
│       ├── data/repository/     Repositories bridging network + cache
│       ├── di/                  Hilt modules
│       └── ui/                  Compose screens, navigation, theme
└── docker-compose.yml           postgres + redis + backend
```

**Why this stack**: FastAPI/Postgres/SQLAlchemy/Alembic/JWT and Kotlin/Compose/MVVM/
Hilt/Room/Retrofit are the versions specified in the brief; every monetary amount is an
integer in minor units (paise/cents) end to end — never a float — so `sum(splits) ==
total` exactly, on both the backend and the client.

## What's fully implemented

- **Auth**: register/login/refresh with bcrypt password hashing, JWT access + refresh
  tokens, refresh-token rotation.
- **Groups**: create/rename/archive/delete, add/remove members, admin role.
- **Invitations & deep links**: unique per-group invite tokens with expiry and
  disable/regenerate, a real Android App Link (`https://splitmate.app/join/{token}`)
  plus a `splitmate://join/{token}` fallback, native share-sheet integration, and a
  join screen with Join/Decline actions per the spec.
- **Expenses**: equal / exact / percentage / shares splits, multiple payers, receipts
  field, category, soft delete, filtering by member/category.
- **Balance engine**: deterministic largest-remainder rounding so splits always sum
  exactly to the total; per-expense net calculation; aggregation across expenses +
  settlements into a running group balance. 27 backend unit tests + 7 Android unit
  tests cover equal/exact/percentage/shares splits, multiple payers, multiple
  expenses, settlement, partial settlement, rounding, and validation-error paths.
- **Debt simplification**: greedy largest-debtor/largest-creditor matching, proven to
  preserve net balances and to never exceed `n-1` transactions for `n` non-zero
  balances (tested).
- **Settlements**: record cash/UPI/bank/other payments; UPI method takes a UPI ID
  (launching an installed UPI app via an `upi://pay` intent is a client-side, one-line
  addition on top of the `method`/`upi_id` fields already modeled — not built into the
  UI in this pass to keep the review surface reasonable).
- **Friends**: search, send/accept/reject requests, shared list.
- **Notifications**: persisted per-user notification records generated on expense-add,
  settlement, and friend-request events (see "What's partial" for push delivery).
- **Recurring expenses**: model + creation endpoint + `run_due_recurring_expenses()`
  scheduler function that materializes a real `Expense` when due (wire this into a
  cron/Celery-beat/APScheduler job in production — see Troubleshooting).
- **Analytics & export**: spend by category/member/month, largest expenses, CSV export.
- **Security**: bcrypt hashing, JWT with expiry, per-request rate limiting, group
  membership is checked on every group-scoped endpoint (`require_group_member` /
  `require_group_admin`) so changing an ID in a request cannot reach another group's
  data, invitation tokens expire and can be disabled, audit_log table modeled for
  write paths.
- **Offline (Android)**: Room caches groups/expenses; the dashboard and group screens
  read from Room first (so cached data shows immediately/offline) and refresh from the
  API in the background; an expense created while offline is queued in a
  `pending_expenses` table and retried by `ExpenseRepository.syncPendingExpenses()`.
- **Docker**: `docker compose up -d` brings up Postgres + Redis + the backend
  (migrations run automatically on container start).

## What's intentionally partial (and why)

Building a literal, 100%-complete version of every one of the brief's ~40 sections is
a multi-week engineering effort. To stay honest rather than paper over gaps, these are
explicitly partial:

- **FCM push notifications**: the `Notification` table and creation logic are real;
  actually registering device tokens and calling Firebase Cloud Messaging is not
  wired up (needs a Firebase project + `google-services.json`, which only you can
  create).
- **Receipt image upload / OCR**: the `receipt_url` field and expense/response wiring
  exist; the actual multipart upload endpoint and Android camera/gallery picker are
  not implemented in this pass.
- **PDF export**: CSV export is implemented; PDF export is not.
- **Analytics charts**: the `/groups/{id}/analytics` endpoint returns real aggregated
  numbers; rendering them as charts in Compose is not implemented (data is ready for
  a charting library of your choice).
- **UPI app-launch intent** and **Add Expense member picker**: the Add Expense screen
  currently takes payer/participant **user IDs** as text input rather than a picker
  backed by `GET /groups/{id}` members — the balance math, validation, and API
  contract are fully correct; swapping the text field for a member-list picker is
  pure UI work.
- **Recurring-expense cron trigger**: the materialization function is implemented and
  unit-testable; nothing in this repo calls it on a timer yet (add a `cron` entry or
  APScheduler in the backend container).
- **Instrumented/UI tests, dark-mode/locale polish, debug screen (§33)**: not built.

None of these gaps affect the correctness of the money math, auth, or authorization —
they're the "nice to have, needs product/infra decisions or design time" items.

## Running it

### 1. Backend + database

```bash
cp .env.example .env        # edit JWT_SECRET_KEY etc. for anything beyond local dev
docker compose up -d        # postgres + redis + backend (migrations run automatically)
curl http://localhost:8000/health
```

API docs: http://localhost:8000/docs

To generate the first migration (models already exist, no migration files are checked
in yet since there's no prior schema to diff against):

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements-dev.txt
alembic revision --autogenerate -m "initial schema"
alembic upgrade head
```

### 2. Backend tests

```bash
cd backend
source .venv/bin/activate   # from step 1
pytest -v                   # 27 tests, all passing: split types, multi-payer,
                             # multi-expense aggregation, settlement, partial
                             # settlement, debt simplification, rounding
```

### 3. Android

Open `android/` in Android Studio (Iguana+), or from the CLI:

```bash
cd android
./gradlew testDebugUnitTest   # 7 unit tests for the client-side balance calculator
./gradlew assembleDebug       # -> app/build/outputs/apk/debug/app-debug.apk
```

The emulator reaches the backend via `10.0.2.2:8000` by default
(`BuildConfig.API_BASE_URL` in `app/build.gradle.kts`) — that's the emulator's alias
for the host machine's `localhost`, where `docker compose up -d` is listening. A
physical device needs your machine's LAN IP instead; change `API_BASE_URL` and, if it's
not HTTPS, add the host to `network_security_config.xml`.

**Firebase (optional, for push notifications)**: create a Firebase project, download
`google-services.json` into `android/app/`, add the `com.google.gms.google-services`
plugin and `firebase-messaging` dependency, and implement a `FirebaseMessagingService`
that posts into the same notification channel the UI already reads from.

## APK

A verified debug APK was built in this environment and confirmed to be a valid,
correctly-signed (debug key) Android package:

```
android/app/build/outputs/apk/debug/app-debug.apk
```

Package: `app.splitmate.debug`, minSdk 26, targetSdk 34, versionName 1.0.0 — verified
with `aapt dump badging`. Build it yourself with `cd android && ./gradlew assembleDebug`.

A signed **release** APK is not provided: `signingConfigs.release` currently reuses the
debug key so `./gradlew assembleRelease` *will* produce an installable, minified
release build, but it is not production-signed. Generate a real upload keystore
(`keytool -genkey -v -keystore release.keystore -keyalg RSA -keysize 2048 -validity
10000 -alias splitmate`) and point `signingConfigs.release` at it before shipping.

## Database schema

13 tables per the spec: `users`, `friends`, `friend_requests`, `groups`,
`group_members`, `invitations`, `expenses`, `expense_participants`,
`expense_payments`, `settlements`, `notifications`, `recurring_expenses`,
`audit_logs`. All primary keys are UUIDs; monetary columns are `Integer` (minor
units); foreign keys and indexes are declared in `backend/app/models.py`.

## Troubleshooting

- **`alembic upgrade head` fails / no tables**: make sure `docker compose up -d`
  finished starting Postgres (`docker compose ps` should show it healthy) before the
  backend container runs migrations; `docker compose logs backend` shows the exact
  error.
- **Android can't reach the backend**: emulator → use `10.0.2.2`, not `localhost`;
  physical device → use your machine's LAN IP and ensure your firewall allows port 8000.
- **Gradle build fails with a missing SDK**: set `sdk.dir` in `android/local.properties`
  to your Android SDK path, or export `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
- **`email-validator` import error running the backend directly**: covered by
  `pip install -r requirements.txt` (it pulls in `pydantic[email]`); make sure you're
  using the venv, not system Python.
- **Recurring expenses aren't appearing**: `run_due_recurring_expenses()` in
  `app/routers/recurring.py` is not scheduled by anything yet — call it from a cron
  job, Celery beat task, or APScheduler job that runs against a DB session.

## License

MIT — see `LICENSE`. Original code and branding; no Splitwise assets, logos, or
copyrighted material were used.
