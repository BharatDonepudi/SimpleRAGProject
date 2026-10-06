#!/usr/bin/env bash
# Full-stack smoke test against a running stack (see README.md for the start order).
# Checks the HTTP shapes in docs/api-contract.md. Exits non-zero on the first failure.
#
#   scripts/smoke.sh
#   RAG_URL=http://localhost:8000 BACKEND_URL=http://localhost:8080 scripts/smoke.sh
#
# Needs curl and python3 (used only to read JSON). The chat request goes through the
# real model, so it can take up to a minute.

set -euo pipefail

RAG_URL="${RAG_URL:-http://localhost:8000}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
QUESTION="${QUESTION:-What are the main points that I should refer to first}"
CHAT_TIMEOUT="${CHAT_TIMEOUT:-180}"

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1" >&2; printf '      body: %s\n' "$(head -c 500 "$BODY_FILE")" >&2; exit 1; }

# request METHOD URL [JSON_BODY] [TIMEOUT] -> prints the HTTP status; the body goes to $BODY_FILE.
request() {
  local method="$1" url="$2" data="${3:-}" timeout="${4:-10}"
  : > "$BODY_FILE"  # so a failed request never shows the previous body
  local args=(-s -o "$BODY_FILE" -w '%{http_code}' -m "$timeout" -X "$method" "$url")
  if [[ -n "$data" ]]; then
    args+=(-H 'Content-Type: application/json' --data "$data")
  fi
  # On a connection failure curl still prints 000 for %{http_code}; `|| true` keeps set -e from exiting.
  curl "${args[@]}" || true
}

# check_json PYTHON_EXPR -> exits non-zero unless the expression is true for `b`, the parsed body.
check_json() {
  python3 -c '
import json, sys
b = json.load(open(sys.argv[1]))
sys.exit(0 if eval(sys.argv[2]) else 1)
' "$BODY_FILE" "$1" 2>/dev/null
}

json_field() {
  python3 -c 'import json, sys; print(json.load(open(sys.argv[1]))[sys.argv[2]])' "$BODY_FILE" "$1"
}

UUID_RE='^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'

# 1. rag-service is up and its index is built.
status="$(request GET "$RAG_URL/health")"
[[ "$status" == 200 ]] && check_json 'b["status"] == "ok" and b["chunks"] > 0' \
  || fail "rag-service /health is ok (got HTTP $status)"
pass "rag-service /health is ok ($(json_field chunks) chunks)"

# 2. The backend is up.
status="$(request GET "$BACKEND_URL/actuator/health")"
[[ "$status" == 200 ]] && check_json 'b["status"] == "UP"' \
  || fail "backend /actuator/health is UP (got HTTP $status)"
pass "backend /actuator/health is UP"

# 3. A question gets a non-empty answer and a new conversation id.
started=$SECONDS
status="$(request POST "$BACKEND_URL/api/chat" "$(python3 -c 'import json, sys; print(json.dumps({"conversationId": None, "message": sys.argv[1]}))' "$QUESTION")" "$CHAT_TIMEOUT")"
[[ "$status" == 200 ]] && check_json 'isinstance(b["answer"], str) and b["answer"].strip() and b["createdAt"]' \
  || fail "POST /api/chat returns an answer (got HTTP $status)"
conversation_id="$(json_field conversationId)"
[[ "$conversation_id" =~ $UUID_RE ]] || fail "POST /api/chat returns a UUID conversationId (got '$conversation_id')"
answer="$(json_field answer)"
pass "POST /api/chat answered in $((SECONDS - started))s, conversationId $conversation_id"
printf '      answer: %s\n' "$(printf '%s' "$answer" | tr '\n' ' ' | cut -c1-160)..."

# 4. The transcript has the user message and the answer, oldest first.
status="$(request GET "$BACKEND_URL/api/conversations/$conversation_id/messages")"
[[ "$status" == 200 ]] && check_json 'len(b) == 2 and [m["role"] for m in b] == ["user", "assistant"] and all(m["content"] and m["createdAt"] for m in b)' \
  || fail "GET /api/conversations/{id}/messages returns user then assistant (got HTTP $status)"
pass "GET /api/conversations/{id}/messages returns 2 messages (user, assistant)"

# 5. A blank message is rejected with 400 and the error shape, without a conversationId.
status="$(request POST "$BACKEND_URL/api/chat" '{"conversationId": null, "message": "   "}')"
[[ "$status" == 400 ]] && check_json 'isinstance(b.get("error"), str) and b["error"] and "conversationId" not in b' \
  || fail "blank message returns 400 with {\"error\": ...} (got HTTP $status)"
pass "blank message returns 400 with {\"error\": \"$(json_field error)\"}"

# 6. An unknown conversation returns 404 with the error shape.
status="$(request GET "$BACKEND_URL/api/conversations/00000000-0000-0000-0000-000000000000/messages")"
[[ "$status" == 404 ]] && check_json 'isinstance(b.get("error"), str) and b["error"]' \
  || fail "unknown conversation returns 404 with {\"error\": ...} (got HTTP $status)"
pass "unknown conversation returns 404 with the error shape"

echo "Smoke test passed."
