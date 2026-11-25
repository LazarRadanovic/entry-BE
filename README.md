# Entry: backend

Backend for an app for the residents of one building entrance: feed, chat, polls, costs, craftsmen, notifications.
Built with Spring Boot 4.2 (Java 21), PostgreSQL, JWT, REST + STOMP over WebSocket, and Web Push.
The frontend (Angular PWA) lives in `../entry FE`.

See [`docs/PLAN.md`](docs/PLAN.md) for the plan and the list of pages.

## Requirements
- Java 21+ (no Maven install needed: use `./mvnw`)
- Docker with Compose. Your user must be able to run `docker ps` without sudo; Testcontainers needs this too.
  On Linux: `sudo usermod -aG docker $USER`, then log out and back in.

## Run

```bash
# Option A: everything in Docker
docker compose up --build              # API on http://localhost:8080

# Option B: DB in Docker, backend from source (fast dev loop)
docker compose up -d postgres
./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### Enabling push notifications (optional)
```bash
npx web-push generate-vapid-keys        # prints a public and a private key
cp .env.example .env                    # paste them into VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY
```
Without keys the app works normally; only Web Push is off. In-app and WebSocket notifications still work.
Browsers only allow push on `https://` or `http://localhost`. On iOS, push works only after the PWA is added to the home screen.

### Subscriptions (optional): free trial, then Stripe
Every new entrance gets a **free trial** (`BILLING_TRIAL_DAYS`, default 30). After that an entrance admin must
subscribe. Otherwise the entrance becomes **read-only**: everyone can still open and read everything (feed, chat,
costs, polls…) and gets live updates, but posting or changing anything answers `402 SUBSCRIPTION_REQUIRED` (own
profile, push settings and marking notifications read still work). Nothing is deleted. Admins get reminders 7, 3
and 1 days before the trial ends, plus a banner in the app.

Without Stripe keys, billing is **off**: nothing is enforced and every entrance is free. To turn it on with Stripe
**test mode**:
1. Stripe Dashboard (test mode) → *Product catalog* → add a product with a **recurring** price. Copy the `price_…` id.
2. *Developers → API keys*: copy the secret key `sk_test_…`.
3. *Settings → Billing → Customer portal*: activate it, so "Manage billing" works.
4. Webhooks locally, using the [Stripe CLI](https://docs.stripe.com/stripe-cli):
   `stripe listen --forward-to localhost:8080/api/billing/webhook`. It prints the `whsec_…` secret.
5. Put `STRIPE_SECRET_KEY`, `STRIPE_PRICE_ID`, `STRIPE_WEBHOOK_SECRET` (and optionally `BILLING_PRICE_LABEL`) in
   `.env`, then run `docker compose up -d`.
6. As an admin: **Subscription → Subscribe**, and pay with test card `4242 4242 4242 4242` (any future date, any CVC).

Any trial days left carry over to the Stripe subscription, so the first charge happens when the trial ends. After
checkout the app also re-reads the subscription from Stripe, so it unlocks even if webhooks aren't set up. Webhooks
keep it in sync afterwards (renewals, failed payments, cancellations).

## Configuration (environment variables)
| Variable | Default | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/entry` | |
| `DB_USERNAME` / `DB_PASSWORD` | `entry` / `entry` | |
| `JWT_SECRET` | dev-only value | Base64, at least 32 bytes. **Always set this outside local dev** (`openssl rand -base64 48`) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` | comma-separated; also used for the WebSocket handshake |
| `COOKIE_SECURE` | `false` | set to `true` behind HTTPS |
| `STORAGE_DIR` | `./uploads` | where images are stored |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | empty | Web Push keys; push is disabled when empty |
| `VAPID_SUBJECT` | `mailto:admin@example.com` | contact URI sent to push services |
| `BILLING_TRIAL_DAYS` | `30` | free trial length for new entrances |
| `STRIPE_SECRET_KEY` / `STRIPE_PRICE_ID` | empty | billing is enforced only when both are set |
| `STRIPE_WEBHOOK_SECRET` | empty | `whsec_…`, for `/api/billing/webhook` |
| `BILLING_PRICE_LABEL` | empty | text shown next to Subscribe, e.g. `€8.99 / mesečno` |
| `APP_URL` | `http://localhost:4200` | frontend URL that Stripe redirects back to |

## Tests
```bash
./mvnw test     # unit tests + integration tests (these start a throwaway PostgreSQL via Testcontainers)
```

## Design notes
- **Auth:** the access token is a JWT (HS256, 15 min), sent as `Authorization: Bearer …` and kept in memory by the
  frontend. The refresh token is an opaque value in an `httpOnly` cookie `refresh_token` (path `/api/auth`, 30 days),
  replaced on every refresh. Only its SHA-256 hash is stored, and reusing an old one revokes all of that user's sessions.
  The user's role and status are re-read from the DB on every request, so approvals and removals apply immediately.
- **Membership:** registering with `newEntrance` creates an entrance and makes you its `ENTRANCE_ADMIN`. Registering
  with an `inviteCode` makes you a `PENDING` resident until an admin approves you. Pending users can only use
  `/api/users/me/**` and `/api/push/**`; everything else returns 403 with `code: MEMBERSHIP_NOT_ACTIVE`.
- **Entrance isolation:** the entrance always comes from the logged-in user, never from the request. Another
  entrance's resources return 404.
- **Images:** upload first (`POST /api/files`), then reference the returned `id` in a post or chat message. Uploads are
  re-encoded (max 2048px, EXIF/GPS stripped), and a 480px thumbnail is generated. JPEG/PNG/GIF/WebP, up to 10 MB.
  Responses contain **signed URLs** (`/api/files/{id}?variant=…&exp=…&sig=…`) that work directly in `<img src>`
  without a token. Storage sits behind a `StorageService` interface, so local disk can be swapped for MinIO/S3.
- **Real time:** writes go through REST; changes are broadcast over STOMP after the DB commit (see below).
- **Notifications:** stored for new posts, polls, noise announcements (pushed regardless of category settings), lost & found posts, responses to your lost & found post, comments on your posts, likes on your posts (grouped per post),
  join requests (to admins) and approvals. Online users receive them over STOMP; offline users get a Web Push,
  filtered by their settings. Chat messages are push-only (not stored).
- **Errors:** RFC 9457 `application/problem+json` with an extra `code` field (and `errors` for validation failures).
- **Scaling note:** the STOMP broker and presence are in-memory, which is right for one instance. For several
  instances, use a broker relay (RabbitMQ) and Redis for presence.

## REST API
Lists that grow use cursor pages: `{ "items": [...], "nextCursor": "…" | null }`. To get the next page, call again
with `?cursor=…`. `size` is optional.

| Method | Path | Notes |
|---|---|---|
| **Auth** | | |
| POST | `/api/auth/register` | `{email, password, firstName, lastName, floor, apartment, inviteCode}` **or** `{…, newEntrance: {name, address}}` |
| POST | `/api/auth/login` | `{email, password}` → `{accessToken, expiresIn, user}`, plus a refresh cookie |
| POST | `/api/auth/refresh` | uses the refresh cookie |
| POST | `/api/auth/logout` | |
| **Me / residents** | | |
| GET / PUT | `/api/users/me` | PUT `{firstName, lastName, floor, apartment}` (pending users allowed) |
| PUT / DELETE | `/api/users/me/avatar` | multipart field `file` |
| GET | `/api/users`, `/api/users/{id}` | active residents of your entrance |
| GET | `/api/presence` | residents online right now |
| GET | `/api/entrances/current` | `inviteCode` only for admins |
| **Files** | | |
| POST | `/api/files` | multipart field `file` → `{id, url, thumbnailUrl, width, height}` |
| GET | `/api/files/{id}?variant=&exp=&sig=` | signed URL; no token needed |
| **Feed** | | |
| GET | `/api/posts?cursor=&size=&authorId=` | newest first; `authorId` for profile pages |
| POST | `/api/posts` | `{text?, imageIds?}` (at most 10 images; needs text or images) |
| GET / DELETE | `/api/posts/{id}` | delete: author or admin |
| PUT / DELETE | `/api/posts/{id}/like` | → `{postId, likeCount, likedByMe}` |
| GET / POST | `/api/posts/{id}/comments` | POST `{text}` |
| DELETE | `/api/posts/{id}/comments/{commentId}` | author or admin |
| **Chat** | | |
| GET | `/api/chat/messages?cursor=&size=` | newest first |
| POST | `/api/chat/messages` | `{text?, imageId?}` |
| **Polls** (always Yes/No) | | |
| GET | `/api/polls?status=ACTIVE\|EXPIRED` | |
| POST | `/api/polls` | `{question, expiresAt?}` (ISO-8601; must be in the future; at most 1 year) |
| GET / DELETE | `/api/polls/{id}` | delete: author or admin |
| PUT / DELETE | `/api/polls/{id}/vote` | PUT `{value: true\|false}`; changeable until expiry; 409 `POLL_EXPIRED` after |
| **Costs** (building cleaning, garage, repairs…) | | |
| GET | `/api/costs` | active costs: `{id, name, type: PER_APARTMENT\|BY_AREA, amount, currency, recurrence: MONTHLY\|ONE_TIME, appliesToAll, unitIds, dueDate, missingArea}` |
| GET | `/api/costs/{id}/table?year=` | apartments the cost applies to, each with `share` and `paid` (12 months, or 1 slot for one-time) |
| GET | `/api/costs/mine?year=` | costs of the caller's apartment |
| GET | `/api/units` | apartments (residents' apartments are added automatically), with `areaM2` |
| POST / PUT / DELETE | `/api/admin/costs`, `/api/admin/costs/{id}` | `{name, description?, type, amount, currency, recurrence, dueDate?, appliesToAll, unitIds}`; BY_AREA splits `amount` by m²; delete archives a cost that has payments |
| PUT | `/api/admin/costs/{id}/units` | `{appliesToAll, unitIds}` |
| PUT | `/api/admin/costs/payments` | `{costId, unitId, period: "YYYY-MM"\|"ONCE", paid}` |
| POST / PUT / DELETE | `/api/admin/units`, `/api/admin/units/{id}` | POST `{floor, apartment, areaM2?}`, PUT `{areaM2}`; only apartments without residents can be removed |
| **Craftsmen** | | |
| GET | `/api/craftsmen?trade=` | most recommended first |
| POST / PUT / DELETE | `/api/craftsmen`, `/api/craftsmen/{id}` | `{name, trade, phone, email?, website?, description?, photoFileId?}` (photo from `POST /api/files`); edit: author or admin |
| PUT / DELETE | `/api/craftsmen/{id}/recommend` | → `{craftsmanId, recommendations, recommendedByMe}` |
| **Noise announcements** ("I'm renovating / having a celebration") | | |
| GET | `/api/announcements?status=ACTIVE\|PAST` | ACTIVE = ongoing or upcoming (soonest first); each has `author` (with floor/apartment) and `ongoing` |
| POST | `/api/announcements` | `{kind: RENOVATION\|CELEBRATION\|MOVING\|OTHER, startDate, endDate, fromTime?, toTime?, note?}`; dates inclusive (`2026-12-01`), hours `08:00` (both or neither = all day; `toTime < fromTime` = past midnight); at most 31 days, 90 days ahead; 409 `TOO_MANY_ANNOUNCEMENTS` above 2 active per resident. Notifies every neighbour |
| GET / PUT / DELETE | `/api/announcements/{id}` | PUT/DELETE: author or admin; an announcement that already started can keep its start date |
| **Lost & found** | | |
| GET | `/api/lost-found?status=OPEN\|RESOLVED&kind=FOUND\|LOST` | newest first; each has `author`, `photo`, `responders`, `resolvedAt` |
| POST | `/api/lost-found` | `{kind: FOUND\|LOST, category: KEYS\|PACKAGE\|PET\|TOY\|DOCUMENTS\|CLOTHING\|OTHER, title, description?, location?, photoFileId?}`; notifies every neighbour |
| GET / PUT / DELETE | `/api/lost-found/{id}` | PUT/DELETE: author or admin |
| PUT / DELETE | `/api/lost-found/{id}/response` | "it's mine" (FOUND) / "I found it" (LOST); not on your own post (400 `OWN_ITEM`), not once resolved (409 `ITEM_RESOLVED`); tells the author once |
| PUT / DELETE | `/api/lost-found/{id}/resolved` | author or admin: mark returned/found, or reopen |
| **Notifications** | | |
| GET | `/api/notifications?cursor=&size=` | |
| GET | `/api/notifications/unread-count` | |
| POST | `/api/notifications/{id}/read`, `/api/notifications/read-all` | |
| GET / PUT | `/api/notifications/settings` | `{pushEnabled, newMessages, newPolls, newPosts}` |
| **Push** | | |
| GET | `/api/push/vapid-public-key` | `{enabled, publicKey}` |
| POST / DELETE | `/api/push/subscriptions` | body = `PushSubscription.toJSON()` / `{endpoint}` |
| **Billing** | | |
| GET | `/api/billing` | `{enabled, state: TRIAL\|ACTIVE\|PAST_DUE\|EXPIRED\|FREE, readOnly, trialDaysLeft, …}` |
| POST | `/api/admin/billing/checkout` | admin → `{url}` of Stripe Checkout |
| POST | `/api/admin/billing/portal` | admin → `{url}` of the Stripe Billing Portal |
| POST | `/api/admin/billing/sync` | admin: re-read the subscription from Stripe |
| POST | `/api/billing/webhook` | Stripe only (checked with the `Stripe-Signature` header) |
| **Admin** (entrance admins) | | |
| POST | `/api/admin/entrance/invite-code` | regenerate the invite code |
| GET | `/api/admin/members?status=PENDING\|ACTIVE` | |
| POST | `/api/admin/members/{id}/approve`, `/reject` | |
| PUT | `/api/admin/members/{id}/role` | `{role: RESIDENT\|ENTRANCE_ADMIN}` |
| DELETE | `/api/admin/members/{id}` | removes the member and ends their sessions |

## WebSocket (STOMP)
Connect to `ws://localhost:8080/ws` and send `Authorization: Bearer <accessToken>` as a STOMP **CONNECT header**.
Only active members can connect, and only to their own entrance's topics. Each message is `{type, payload}`:

| Destination | Types |
|---|---|
| `/topic/entrance/{entranceId}/feed` | `POST_CREATED` (PostDto), `POST_DELETED` `{postId}`, `POST_STATS` `{postId, likeCount, commentCount}`, `COMMENT_CREATED` (CommentDto) |
| `/topic/entrance/{entranceId}/chat` | `MESSAGE_CREATED` (ChatMessageDto) |
| `/topic/entrance/{entranceId}/polls` | `POLL_CREATED` (PollDto), `POLL_RESULTS` `{pollId, yesCount, noCount, totalVotes, yesPercent, noPercent}`, `POLL_DELETED` `{pollId}` |
| `/topic/entrance/{entranceId}/announcements` | `ANNOUNCEMENT_CREATED` / `ANNOUNCEMENT_UPDATED` (AnnouncementDto), `ANNOUNCEMENT_DELETED` `{announcementId}` |
| `/topic/entrance/{entranceId}/lost-found` | `LOST_FOUND_CREATED` / `LOST_FOUND_UPDATED` (LostFoundDto), `LOST_FOUND_DELETED` `{itemId}` |
| `/topic/entrance/{entranceId}/fees` | `FEES_UPDATED` `{}` (a cost, payment or apartment changed: reload) |
| `/topic/entrance/{entranceId}/presence` | `PRESENCE` `{userId, online, onlineUserIds}` |
| `/user/queue/notifications` | `NOTIFICATION` (NotificationDto) |

The access token expires after 15 minutes, but an open connection stays authenticated. Reconnect with a fresh token
after a refresh.

## Project layout
```
src/main/java/com/entry/
  auth/          register/login/refresh/logout, refresh tokens
  entrance/      entrance + invite codes
  user/          profile, avatar, residents, admin member management
  file/          image upload, processing, signed URLs, storage
  feed/          posts, images, likes, comments
  chat/          chat messages
  poll/          yes/no polls, votes, results
  notification/  in-app notifications, settings, dispatcher (event -> STOMP / push)
  push/          Web Push: subscriptions, RFC 8291 encryption, VAPID
  fee/           entrance costs: apartments (units), costs, assignments, area split, payments
  craftsman/     craftsmen directory + recommendations
  billing/       free trial + Stripe subscriptions, access policy, webhook, trial reminders
  realtime/      STOMP config, CONNECT auth, presence, event relay
  security/      JWT config, principal (AuthUser), HTTP security rules
  common/        config properties, OpenAPI, error handling, cursor paging
src/main/resources/db/migration/   Flyway migrations (V1..V7)
```
