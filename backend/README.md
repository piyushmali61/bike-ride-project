# Smart Intercom — Backend Signaling Server

Minimal WebSocket signaling server + TURN credential endpoint (Section 9.4).

## Architecture

- **WebSocket** `/v1/signal?room=<roomId>` — room-based message relay
- **REST** `POST /v1/turn-credentials` — ephemeral TURN credentials
- No audio processing, no message persistence, no PII in logs

## Messages (Section 20.3)

```
join{deviceId, sig}
presence
offer|answer|candidate{payload, sig}
peer_status{hasInternet}
revoke
bye
```

## Setup

```bash
cd backend
npm install
npm run dev        # Development with hot reload
npm run build      # Production build
npm start          # Production server
```

## Environment Variables

```
PORT=8080
TURN_SECRET=<shared-secret-for-coturn>
TURN_URLS=turn:your.server:3478,turns:your.server:5349
WS_PING_INTERVAL=15000
RATE_LIMIT_PER_DEVICE=100
```

## coturn Configuration

See `coturn.conf` for a reference configuration with:
- `use-auth-secret` for ephemeral REST credentials
- UDP + TCP + TLS on 443
- Rate limiting per-user

## Load Testing Note

Each WebSocket room holds 2 connections (1:1 v1). A single Node process can handle ~10,000+ concurrent rooms. TURN bandwidth scales with concurrent relay sessions at ~40 kbps per direction.
