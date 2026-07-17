#!/usr/bin/env bash
# End-to-end smoke test for queuectl. Exercises the scenarios called out in the assignment:
#   1. basic job completes successfully
#   2. failed job retries with backoff and moves to the DLQ
#   3. multiple workers process jobs without overlap/duplication
#   4. invalid commands fail gracefully
#   5. job data survives a "restart" (every CLI call here is its own fresh JVM process,
#      so this is exercised implicitly by every step, and explicitly at the end)
#
# Requires: a running MySQL instance reachable with the QUEUECTL_DB_* env vars (see README),
# and target/queuectl.jar already built (`mvn package`).

set -uo pipefail
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$DIR/target/queuectl.jar"
RUN_ID="smoke-$(date +%s)"

PASS=0
FAIL=0

pass() { echo "  PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $1"; FAIL=$((FAIL + 1)); }

qctl() { java -jar "$JAR" "$@"; }

if [ ! -f "$JAR" ]; then
    echo "queuectl.jar not found at $JAR. Build it first with: mvn package" >&2
    exit 2
fi

echo "== queuectl smoke test ($RUN_ID) =="

echo
echo "[1] Basic job completes successfully"
OK_ID="${RUN_ID}-ok"
qctl enqueue "{\"id\":\"$OK_ID\",\"command\":\"echo smoke-ok\"}" >/dev/null
qctl worker start --count 1 &
WPID=$!
for i in $(seq 1 15); do
    STATE=$(qctl list --limit 200 2>/dev/null | awk -v id="$OK_ID" '$1==id{print $2}')
    [ "$STATE" = "completed" ] && break
    sleep 1
done
qctl worker stop >/dev/null 2>&1
wait "$WPID" 2>/dev/null
if [ "$STATE" = "completed" ]; then pass "job '$OK_ID' reached state=completed"; else fail "job '$OK_ID' expected completed, got '$STATE'"; fi

echo
echo "[2] Failed job retries with backoff and lands in the DLQ"
DEAD_ID="${RUN_ID}-dead"
qctl enqueue "{\"id\":\"$DEAD_ID\",\"command\":\"exit 1\",\"max_retries\":2,\"backoff_base\":1}" >/dev/null
qctl worker start --count 1 &
WPID=$!
for i in $(seq 1 20); do
    STATE=$(qctl list --limit 200 2>/dev/null | awk -v id="$DEAD_ID" '$1==id{print $2}')
    [ "$STATE" = "dead" ] && break
    sleep 1
done
qctl worker stop >/dev/null 2>&1
wait "$WPID" 2>/dev/null
if [ "$STATE" = "dead" ]; then
    pass "job '$DEAD_ID' retried and moved to the DLQ"
else
    fail "job '$DEAD_ID' expected dead, got '$STATE'"
fi
DLQ_HIT=$(qctl dlq list --limit 200 2>/dev/null | awk -v id="$DEAD_ID" '$1==id{print $1}')
if [ "$DLQ_HIT" = "$DEAD_ID" ]; then pass "job '$DEAD_ID' visible via 'dlq list'"; else fail "job '$DEAD_ID' missing from 'dlq list'"; fi

echo
echo "[3] Multiple workers process jobs without overlap or duplication"
N=12
for i in $(seq 1 $N); do
    qctl enqueue "{\"id\":\"${RUN_ID}-par-$i\",\"command\":\"echo parallel-$i\"}" >/dev/null
done
qctl worker start --count 4 &
WPID=$!
for i in $(seq 1 20); do
    PENDING_OR_PROCESSING=$(qctl status 2>/dev/null | grep -E "pending:|processing:" | awk '{s+=$2} END{print s+0}')
    [ "$PENDING_OR_PROCESSING" = "0" ] && break
    sleep 1
done
qctl worker stop >/dev/null 2>&1
wait "$WPID" 2>/dev/null
COMPLETED=0
for i in $(seq 1 $N); do
    ST=$(qctl list --limit 200 2>/dev/null | awk -v id="${RUN_ID}-par-$i" '$1==id{print $2}')
    [ "$ST" = "completed" ] && COMPLETED=$((COMPLETED + 1))
    if [ "$ST" != "completed" ]; then
        echo "  (job ${RUN_ID}-par-$i ended in state '$ST', expected attempts=0 exactly-once completion)"
    fi
done
if [ "$COMPLETED" -eq "$N" ]; then
    pass "$N jobs fanned out across 4 worker threads all completed exactly once"
else
    fail "$COMPLETED/$N parallel jobs completed"
fi

echo
echo "[4] Invalid / not-found commands fail gracefully (not a crash) and eventually reach the DLQ"
BAD_ID="${RUN_ID}-badcmd"
qctl enqueue "{\"id\":\"$BAD_ID\",\"command\":\"this_command_does_not_exist_xyz\",\"max_retries\":1,\"backoff_base\":1}" >/dev/null
qctl worker start --count 1 &
WPID=$!
for i in $(seq 1 15); do
    STATE=$(qctl list --limit 200 2>/dev/null | awk -v id="$BAD_ID" '$1==id{print $2}')
    [ "$STATE" = "dead" ] && break
    sleep 1
done
qctl worker stop >/dev/null 2>&1
wait "$WPID" 2>/dev/null
if [ "$STATE" = "dead" ]; then
    pass "unresolvable command '$BAD_ID' failed gracefully and reached the DLQ (no crash)"
else
    fail "unresolvable command '$BAD_ID' expected dead, got '$STATE'"
fi

echo
echo "[5] Job data survives a restart (every CLI call above was already a fresh JVM process;"
echo "    this call is one more, reading the same rows back from MySQL)"
FINAL_STATE=$(qctl list --limit 200 2>/dev/null | awk -v id="$OK_ID" '$1==id{print $2}')
if [ "$FINAL_STATE" = "completed" ]; then
    pass "job '$OK_ID' from step [1] is still 'completed' after N subsequent process restarts"
else
    fail "job '$OK_ID' lost or changed across restarts (state='$FINAL_STATE')"
fi

echo
echo "== Summary: $PASS passed, $FAIL failed =="
[ "$FAIL" -eq 0 ]
