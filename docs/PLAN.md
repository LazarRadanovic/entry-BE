# Entry: an app for the residents of one building entrance

## Context
New project, starting from nothing. Both folders are empty:
- Backend goes in `/home/rootic/Documents/Projects/entry` (Spring Boot 3, PostgreSQL, JWT, STOMP WebSocket)
- Frontend goes in `/home/rootic/Documents/Projects/entry FE` (Angular PWA)

The spec is in `entry prompt.txt`. The user will supply an HTML template for each page; I'll turn those templates into Angular components and build the backend.

---

## 1. Pages the user needs to supply as HTML templates

### Public / auth (no app shell)
| # | Page | Contents |
|---|------|----------|
| 1 | **Login** | email, password, link to register |
| 2 | **Register** | first name, last name, email, password, floor, apartment, plus a choice: *join an entrance with an invite code* or *create a new entrance* (address and entrance number) |
| 3 | **Pending approval** | "Waiting for the entrance admin to approve you" screen, with a refresh button and logout |
| 4 | **Offline fallback** | shown by the service worker when there's no network and the page isn't cached |
| 5 | **404 / error** | generic not-found / something-went-wrong page |

### App shell (one layout around every logged-in page)
| # | Page | Contents |
|---|------|----------|
| 6 | **Layout shell** | top bar (entrance name, notification bell with badge, avatar) + **bottom nav on mobile** / **sidebar on desktop**: Feed, Chat, Polls, Residents, Profile |

### Logged-in pages
| # | Page | Contents |
|---|------|----------|
| 7 | **Feed** | list of post cards: author, time, text, image grid/carousel, like button with count, comment count. "New post" button (FAB on mobile) |
| 8 | **Post detail** | the post, all its comments, and an input for adding a comment (can be a full page or a bottom sheet) |
| 9 | **Create post** | text area, multi-image picker with thumbnail previews and remove buttons, submit |
| 10 | **Chat** | message list with text and image bubbles (own messages vs others), date separators, input with an attach-image button, **online users** panel (side column on desktop, drawer or avatar strip on mobile) |
| 11 | **Polls list** | Active / Expired tabs. Each poll card shows the question, Yes/No buttons, a results bar (% and count), expiry, and whether you already voted |
| 12 | **Create poll** | question and an optional expiration date/time |
| 13 | **Residents list** | searchable list: avatar, name, floor/apartment, online dot, admin badge |
| 14 | **Resident profile (view)** | someone else's profile: avatar, name, floor/apartment, their posts |
| 15 | **My profile / edit** | edit name, floor, apartment, and avatar upload. Settings section: enable push notifications, logout |
| 16 | **Notifications** | list of notifications (new message, poll, post, approval) with read/unread state |
| 17 | **Admin: members** | *Pending* tab (Approve / Reject) and *Members* tab (remove, promote to admin). Shows the entrance invite code with copy and regenerate buttons |

### Reusable pieces (small HTML snippets are enough)
- Loading skeleton (post card and list row), empty state, error state
- Toast / snackbar, confirm dialog
- Image lightbox (full-screen viewer)
- Avatar with online dot

**Total: 17 pages plus the shared snippets.** Pages 8 and 12 could be modals instead of full pages, if that's what the templates show.

---

## 2. Advice and recommended decisions

1. **Joining an entrance** isn't defined in the spec. Recommended: whoever creates an entrance becomes its `ENTRANCE_ADMIN`, and everyone else joins with an **invite code** and stays `PENDING` until an admin approves them. This keeps the flow simple and keeps strangers out.
2. **Tenant isolation:** always take `entranceId` from the authenticated user (JWT/DB), **never from the request**. Every repository query is scoped by entrance, and STOMP subscriptions to `/topic/entrance/{id}/**` are checked against the user's entrance.
3. **Tokens:** a short-lived access JWT (about 15 min) kept in memory, and a refresh token in an **httpOnly cookie**. Refresh tokens rotate and are stored hashed in the DB, so logout revokes them. An Angular interceptor retries a request once after a 401 by refreshing.
4. **WebSocket auth:** send the JWT in the STOMP `CONNECT` headers and validate it in a `ChannelInterceptor`. Topics:
   - `/topic/entrance/{id}/chat`, `/topic/entrance/{id}/polls`, `/topic/entrance/{id}/presence`, `/topic/entrance/{id}/feed`
   - `/user/queue/notifications` for each user's own notifications
5. **Online users:** keep presence in memory, updated on STOMP connect/disconnect events. That's fine for a single backend instance; note in the README that scaling out would need Redis.
6. **Push notifications:** Web Push with VAPID keys (the `nl.martijndwars:web-push` library), Angular `SwPush`, and a `push_subscription` table. Send a push only when the user has no active WebSocket session. **Caveat:** on iOS, push only works after the user installs the PWA to the home screen.
7. **Images:** local disk plus **Thumbnailator** for thumbnails. Validate MIME type and size (for example 10 MB at most). Serve images through `/api/files/{id}` rather than as a public folder. Put this behind a `StorageService` interface so MinIO can be added later. Skip MinIO for now.
8. **Polls:** a unique `(poll_id, user_id)` constraint allows one vote per user. Decide whether votes can be changed; I recommend allowing it. Results are broadcast after each vote, and votes on expired polls are rejected on the server.
9. **Chat pagination:** cursor-based (`before=<messageId>`) with infinite scroll upward.
10. **Backend structure:** Java 21, organized by feature (`auth`, `entrance`, `user`, `chat`, `post`, `poll`, `notification`, `file`, `common`). Flyway for migrations, MapStruct for DTOs, Bean Validation, a global `@RestControllerAdvice` that returns `ProblemDetail`, and springdoc OpenAPI (Swagger UI).
11. **Frontend structure:** Angular standalone components, signals, the new control flow, lazy-loaded feature routes (`core/`, `shared/`, `features/{auth,feed,chat,polls,residents,profile,admin,notifications}`), `@stomp/rx-stomp`, and `@angular/pwa`. Offline: cache the app shell, and cache the last feed and residents responses with ngsw `dataGroups` (freshness strategy).
12. **Version control:** neither folder is a git repo yet. I recommend running `git init` in both (or making one monorepo with `backend/` and `frontend/`). The spec allows either. Since you've already chosen two folders, I'll keep that and put docker-compose in the backend folder, referencing `../entry FE`.

## 3. Data model (summary)
`entrance(id, name, address, invite_code)` ·
`app_user(id, email, password_hash, first_name, last_name, floor, apartment, avatar_file_id, role, status[PENDING/ACTIVE/REJECTED], entrance_id)` ·
`refresh_token` · `chat_message(id, entrance_id, sender_id, text, image_file_id, created_at)` ·
`post` · `post_image` · `post_like(unique post+user)` · `comment` ·
`poll(id, entrance_id, author_id, question, expires_at)` · `poll_vote(unique poll+user, value bool)` ·
`notification(user_id, type, payload, read)` · `push_subscription` · `stored_file(id, path, thumb_path, mime, size)`

## 4. Build order
1. Backend skeleton, docker-compose (Postgres), Flyway, auth (register / login / refresh / logout), entrance creation and invite flow, admin approval
2. Angular PWA skeleton, auth pages, interceptor and guards (auth, active-member, admin), layout shell
3. Users / residents / profile, plus file upload and thumbnails
4. Feed: posts, images, likes, comments
5. Chat over STOMP: messages, images, presence
6. Polls with live results and expiry
7. Notifications: in-app over WebSocket, plus Web Push
8. Offline fallback, loading and error states, responsive polish, README

## 5. Verification
- `docker compose up` starts Postgres, the backend and the frontend; check the API in Swagger UI
- Two browsers with two users in the same entrance: chat and poll results update live, and online users show correctly
- A user from a different entrance can't read or subscribe to the first entrance's data (expect 403)
- `ng build` and serve the production build; Lighthouse PWA check passes; going offline shows the cached shell or the offline page
- Backend tests: service unit tests, plus Testcontainers integration tests for auth and tenant isolation

## Waiting on the user
- HTML templates for pages 1–17 and the shared snippets
- Which CSS approach the templates use (Tailwind, plain CSS, Bootstrap…). This decides how I integrate them.
