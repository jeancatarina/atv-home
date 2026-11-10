#!/bin/bash
# Starts the dev room (a real Social room and server, no TV) with 2 bots on port 18766.
# BOT_MS sets how often the bots vote (default 1500). Output: /tmp/e2e/out.log, commands: /tmp/e2e/in.fifo
cd "$(dirname "$0")/.."
mkdir -p /tmp/e2e
rm -f /tmp/e2e/in.fifo /tmp/e2e/out.log; mkfifo /tmp/e2e/in.fifo
( exec 3<>/tmp/e2e/in.fifo; exec ./gradlew :social-core:devRoom --args="--bots 2 --port 18766 --bot-ms ${BOT_MS:-1500}" -q --console=plain < /tmp/e2e/in.fifo > /tmp/e2e/out.log 2>&1 ) &
for i in $(seq 1 60); do grep -q 'Local :' /tmp/e2e/out.log 2>/dev/null && exit 0; sleep 1; done
echo "room did not start"; exit 1
