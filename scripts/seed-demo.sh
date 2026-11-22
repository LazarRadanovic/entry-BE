#!/usr/bin/env bash
# Creates demo accounts + sample content through the public API (idempotent-ish: skips if admin exists).
# Usage: scripts/seed-demo.sh [api-base]   (default http://localhost:8080)
set -euo pipefail
API=${1:-http://localhost:8080}
PW=password123
j() { python3 -c "import json,sys;print(json.load(sys.stdin)$1)"; }
login() { curl -sf -X POST "$API/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$1\",\"password\":\"$PW\"}" | j "['accessToken']"; }
post() { curl -sf -X POST "$API$1" -H "Authorization: Bearer $2" -H 'Content-Type: application/json' -d "$3"; }

if curl -sf -X POST "$API/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"ana@entry.local\",\"password\":\"$PW\"}" >/dev/null; then
  echo "Demo data already exists."; exit 0
fi

ADMIN=$(curl -sf -X POST "$API/api/auth/register" -H 'Content-Type: application/json' -d "{\"email\":\"ana@entry.local\",\"password\":\"$PW\",\"firstName\":\"Ana\",\"lastName\":\"Marković\",\"floor\":\"4\",\"apartment\":\"12\",\"newEntrance\":{\"name\":\"Entrance 3\",\"address\":\"Beogradska 12\"}}" | j "['accessToken']")
CODE=$(curl -sf "$API/api/entrances/current" -H "Authorization: Bearer $ADMIN" | j "['inviteCode']")

reg() { # email first last floor apt
  curl -sf -X POST "$API/api/auth/register" -H 'Content-Type: application/json' -d "{\"email\":\"$1\",\"password\":\"$PW\",\"firstName\":\"$2\",\"lastName\":\"$3\",\"floor\":\"$4\",\"apartment\":\"$5\",\"inviteCode\":\"$CODE\"}" | j "['user']['id']"
}
MARKO=$(reg marko@entry.local Marko Jovanović 2 5)
JELENA=$(reg jelena@entry.local Jelena Kovač 3 9)
reg petar@entry.local Petar Nikolić 1 2 >/dev/null   # stays PENDING to demo approvals
post "/api/admin/members/$MARKO/approve" "$ADMIN" '{}' >/dev/null
post "/api/admin/members/$JELENA/approve" "$ADMIN" '{}' >/dev/null

M=$(login marko@entry.local); J=$(login jelena@entry.local)
P1=$(post /api/posts "$ADMIN" '{"text":"Does anyone have a spare parking card? Mine stopped working this morning 😅"}' | j "['id']")
post /api/posts "$M" '{"text":"New plants in the hallway look great! Thanks to whoever arranged this 🌱"}' >/dev/null
post "/api/posts/$P1/comments" "$M" '{"text":"I have one extra. Come by apartment 5 after 6pm."}' >/dev/null
post "/api/posts/$P1/comments" "$J" '{"text":"Building management can issue a new one too."}' >/dev/null
curl -sf -X PUT "$API/api/posts/$P1/like" -H "Authorization: Bearer $M" >/dev/null
POLL=$(post /api/polls "$ADMIN" "{\"question\":\"Should we install better lighting in the parking area?\",\"expiresAt\":\"$(date -u -d '+3 days' +%Y-%m-%dT%H:%M:%SZ)\"}" | j "['id']")
curl -sf -X PUT "$API/api/polls/$POLL/vote" -H "Authorization: Bearer $M" -H 'Content-Type: application/json' -d '{"value":true}' >/dev/null
curl -sf -X PUT "$API/api/polls/$POLL/vote" -H "Authorization: Bearer $J" -H 'Content-Type: application/json' -d '{"value":false}' >/dev/null
post /api/chat/messages "$J" '{"text":"Does anyone know when the elevator will be fixed?"}' >/dev/null
post /api/chat/messages "$M" '{"text":"They said by Friday"}' >/dev/null

echo "Seeded. Invite code: $CODE"
echo "  ana@entry.local    / $PW  (entrance admin)"
echo "  marko@entry.local  / $PW  (resident)"
echo "  jelena@entry.local / $PW  (resident)"
echo "  petar@entry.local  / $PW  (pending approval)"
