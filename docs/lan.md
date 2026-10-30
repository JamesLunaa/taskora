# Taskora LAN protocol (version 1)

Taskora devices on the same local network can pair, browse each other's lists and tasks live, tick tasks off on the other device, and copy tasks in either direction. Each device keeps its own data; nothing is merged or synced. There is no server and nothing leaves the local network.

Every Taskora app (Android, Linux, Windows) speaks the same protocol, through the shared library in `lan/`.

## Overview

1. **Discovery:** each device announces itself with mDNS/DNS-SD while Taskora is open (the desktop app while it runs, including in the tray; the phone only while the app is on screen).
2. **Connection:** devices connect directly over TCP with TLS. Each device has its own key; there are no certificate authorities.
3. **Pairing:** done once. Afterwards each device only talks to devices whose keys it has pinned.
4. **Requests and events:** length-prefixed JSON messages over the TLS connection. The device being browsed pushes a `changed` event whenever its data changes, and the browsing device fetches what it shows again.

## Device identity

- A device ID: a random UUID, created on first use.
- A key pair: EC P-256, created on first use and kept in the app's private storage.
- A self-signed X.509 certificate for that key (subject `CN=Taskora <device ID>`, valid for 100 years), only so TLS can carry the key.
- The **fingerprint** identifies a device: the SHA-256 of the DER-encoded SubjectPublicKeyInfo (the public key, not the certificate), written as 64 lowercase hex digits.
- A display name, chosen by the user. Defaults are the computer's host name, or the phone's model name.

Resetting the identity (a new key) means pairing again with every device.

## Discovery (mDNS / DNS-SD)

- Service type: `_taskora._tcp`, in the `local.` domain.
- Instance name: the device's display name.
- Port: the port the device listens on. It prefers 47390 and falls back to any free port.
- TXT records:

  | Key | Value |
  |---|---|
  | `v` | protocol version, `1` |
  | `id` | device ID |
  | `fp` | the first 16 hex digits of the fingerprint (a hint only: the full fingerprint is checked in TLS) |
  | `os` | `android`, `linux` or `windows` |

A device that isn't announced (a phone with Taskora closed) shows as unavailable. Paired devices are remembered by ID; their current address comes from discovery.

## Connection

- TCP, then TLS 1.2 or 1.3 (Android 8 and 9 only have 1.2), with mutual authentication: both sides present their certificate.
- TLS accepts any certificate. Trust is decided right after the handshake, by the peer's fingerprint: a known (paired) fingerprint gets the full protocol; an unknown one may only use the pairing messages. The TLS session is what binds the fingerprint to the connection.
- One connection carries any number of requests. Either side may close it at any time; the browsing device reconnects when it needs to.

### Framing

Each message is a 4-byte big-endian length followed by that many bytes of UTF-8 JSON. Messages over 4 MiB are a protocol error and close the connection.

### Messages

Every message is a JSON object with a `type`. Requests carry an `id` (an integer chosen by the sender) and get exactly one reply with the same `id`, either the matching response or `error`. Events have no `id`.

Unknown fields are ignored, so later versions can add fields. Everything else is checked strictly:
- Text that isn't valid JSON, or a message without a string `type`, closes the connection.
- A known message with a missing required field, a wrong type, an unknown enum value or an out-of-range value gets `error` code `bad_request`, and nothing is changed.
- Unknown message types get `error` code `unsupported`.

Size limits, beyond the 4 MiB message limit:

| Field | Limit |
|---|---|
| Task title, subtask title, list name, device name | 500 characters |
| Task notes | 20,000 characters |
| Subtasks per task | 200 |
| Recurrence `interval` | 1–99 |

#### Hello (both directions, first message)

The client sends `hello` right after the TLS handshake. The server answers with its own `hello` (same `id`).

```json
{ "type": "hello", "id": 1, "protocol": 1, "deviceId": "6f1c…", "name": "Jose's laptop", "os": "linux", "paired": true }
```

`paired` tells the other side whether the sender trusts it. If one side has the other stored as paired but the other says `paired: false` (a pairing that broke off at the very end, or an unpair the device didn't hear about), the side that still trusts it removes its entry and shows the device as "not paired any more". So a device is never left half-paired for long.

A server that doesn't support the client's `protocol` replies with `error` code `unsupported_version` and closes the connection.

## Pairing

The two ways to pair confirm, in different ways, that each side sees the other's real key. Both end with each device storing the other's device ID, name and fingerprint. Either side can cancel at any step with `pair_cancel`.

### By comparing a code (any two devices)

This is the numeric comparison used by Bluetooth. A commitment stops an attacker in the middle from choosing values that make the two codes match.

1. The user picks a device in "Add device". The initiator (client) connects and sends `pair_start`.
2. The responder (server) shows a pairing dialog naming the initiator. It picks a random 32-byte nonce `Nb` and sends `pair_commit` with `commitment = SHA-256(Nb)`, or `error` code `busy` if it's already pairing with someone else.
3. The initiator picks a random 32-byte nonce `Na` and sends `pair_nonce` with `Na`.
4. The responder sends `pair_reveal` with `Nb`. The initiator checks that `SHA-256(Nb)` equals the commitment, otherwise it cancels.
5. Both compute the code, as a 6-digit number with leading zeros:
   `SHA-256(fp_initiator ‖ fp_responder ‖ Na ‖ Nb)`, taking the first 4 bytes as an unsigned big-endian integer, modulo 1,000,000. Each fingerprint is its 32 raw bytes.
6. Both screens show the code and the other device's name. On each device, the user checks that the codes match and taps "Pair". Each side sends `pair_confirm` once its own user has accepted; one user's approval never stands in for the other's.
7. A side stores the other device only when it has both sent and received `pair_confirm`. If either user rejects or cancels, or the connection drops, both sides send or get `pair_cancel` and store nothing.
8. Pairing that hasn't finished within 2 minutes of `pair_start` is cancelled on both sides.

Nonces are sent as lowercase hex.

Pairing messages go in a fixed order and have no `id`, apart from `pair_start` and `pair_token`, whose failures come back as `error` with their `id`:

| Message | Sent by | Fields |
|---|---|---|
| `pair_start` | initiator | `id` |
| `pair_commit` | responder | `commitment` (hex) |
| `pair_nonce` | initiator | `nonce` (hex) |
| `pair_reveal` | responder | `nonce` (hex) |
| `pair_confirm` | both | – |
| `pair_cancel` | either | `reason` (optional text) |
| `pair_token` | phone | `id`, `token` (hex) |
| `pair_done` | computer | `id` |

### By scanning a QR code (a phone and a computer)

1. The computer shows a QR code with a URI:
   `taskora://pair?v=1&id=<deviceId>&fp=<full fingerprint>&host=<IP address>&port=<port>&token=<secret>`
   - `token` is 16 random bytes in hex. It's valid for 5 minutes or one use. The QR code changes when it expires or is used.
   - `host` is the computer's IP address on the network it believes the phone is on. If the computer has several, it uses the one on its default route.
2. The phone scans it, connects, and checks that the server's fingerprint equals `fp` (otherwise it stops).
3. The phone sends `pair_token` with `token` and waits. The server checks the token; a wrong token gets `error` code `rejected`, and the QR code is replaced by a new one.
4. The computer asks its user "Pair with <phone name>?", using the name from the phone's `hello`. This also stops someone who photographed the QR code from pairing a different phone.
5. If the user accepts, the server stores the phone (its fingerprint comes from TLS) and replies with `pair_done`, and the phone stores the computer. If the user declines, or doesn't answer within 2 minutes, the server replies with `error` code `rejected`.

No codes need comparing, because the QR code came straight off the computer's screen.

### Unpairing

Either side can remove the other. If the other device is reachable, the remover sends `unpair` so the other side forgets it too. Otherwise the other side keeps its entry, but is refused with `not_paired` the next time it connects, and shows the device as "not paired any more".

## Requests (paired devices only)

The examples show the request, then the response.

### Lists

```json
{ "type": "get_lists", "id": 2 }
{ "type": "lists", "id": 2, "lists": [ { "id": 1, "name": "Inbox", "color": -10777105, "activeCount": 3 } ] }
```

`color` is an ARGB integer, as stored.

### Tasks in a list

```json
{ "type": "get_tasks", "id": 3, "listId": 1, "includeCompleted": false }
{ "type": "tasks", "id": 3, "tasks": [ <Task>, … ] }
```

`listId` −1 means all lists. Tasks come in the list's own sort order.

### Tick a task off, or untick it

```json
{ "type": "set_completed", "id": 4, "taskId": 42, "completed": true }
{ "type": "ok", "id": 4 }
```

This behaves as on the device itself: completing a repeating task creates its next occurrence there. Setting the state a task already has does nothing, so it's safe to retry.

### Add a task (send or copy)

```json
{ "type": "add_task", "id": 5, "key": "0b6e…", "listId": null, "task": <Task> }
{ "type": "task_added", "id": 5, "taskId": 77 }
```

`key` is a random UUID per copy. If the connection drops before the reply arrives, the sender retries with the same `key` after reconnecting. A receiver that has already added a task under that key (it remembers keys for an hour) replies with the same `taskId`, so a retry never creates a duplicate.

`listId` null means the receiver's Inbox; a list that no longer exists also falls back to the Inbox. The receiver ignores `task.id`, `task.listId` and the subtask IDs, and creates a new task. Its reminder is scheduled there, unless it's already in the past.

Copying a task from a browsed device to this one needs no request: the browsing device already has the task from `tasks` and adds it locally.

### Watching for changes

```json
{ "type": "subscribe", "id": 6 }
{ "type": "ok", "id": 6 }
… later, at most once a second:
{ "type": "changed" }
```

After `subscribe`, the server sends `changed` whenever its lists or tasks change, including changes made by the subscriber. The subscription lasts as long as the connection.

### Other requests

- `unpair`, answered with `ok`: the sender has removed the receiver; the receiver removes the sender.
- `ping`, answered with `pong`: keeps a connection alive. Either side closes a connection that has been silent for 60 seconds.

### Errors

```json
{ "type": "error", "id": 4, "code": "not_found", "message": "No task 42" }
```

| Code | Meaning |
|---|---|
| `not_paired` | The sender isn't trusted; pair first |
| `not_found` | The list or task doesn't exist |
| `bad_request` | A missing or invalid field |
| `unsupported` | Unknown message type |
| `unsupported_version` | Incompatible protocol version |
| `rejected` | The user declined (pairing) |
| `busy` | Pairing is already in progress with another device |

## The Task object

Times use ISO 8601. Optional fields may be missing or null.

```json
{
  "id": 42,
  "listId": 1,
  "title": "Water plants",
  "notes": "",
  "dueDate": "2026-11-02",
  "dueTime": "09:00",
  "reminderAt": "2026-11-02T08:50:00Z",
  "priority": "HIGH",
  "recurrence": { "frequency": "WEEKLY", "interval": 1, "daysOfWeek": ["MONDAY", "THURSDAY"] },
  "completed": false,
  "completedAt": null,
  "createdAt": "2026-10-30T12:00:00Z",
  "subtasks": [ { "id": 7, "title": "Balcony", "completed": true } ]
}
```

- `priority`: `NONE`, `LOW`, `MEDIUM` or `HIGH`.
- `frequency`: `DAILY`, `WEEKLY`, `MONTHLY` or `YEARLY`.
- `daysOfWeek`: only for `WEEKLY`.

## Limits and timeouts

| What | Limit |
|---|---|
| TLS handshake | 10 seconds, then the connection is closed |
| `hello` after the handshake | 10 seconds |
| Open connections per device | 8 in total; 2 from devices that aren't paired |
| An unpaired connection that hasn't started pairing | closed after 30 seconds |
| Pairing in progress | one at a time; others get `busy` |
| Failed or cancelled code pairings | after 3 within 10 minutes, new pairings are refused with `busy` for 5 minutes |
| A request without a reply | 15 seconds, then the request fails as "unavailable" |
| A connection with no traffic | `ping` every 20 seconds; closed after 60 seconds without anything received |

## Connection loss

The app shows each paired device's state: **online**, **connecting**, **unavailable** (not on the network, or Taskora isn't open there), or **not paired any more**.

- **Wi-Fi drops, or the other device sleeps or closes:** the connection fails or goes silent and is closed after 60 seconds at most. The device shows as connecting, then unavailable. A phone stops announcing itself and listening as soon as Taskora leaves the screen, so it shows as unavailable straight away.
- **Reconnecting:** while you're browsing a device, the app retries with exponential backoff: 1, 2, 4, 8, 16, then every 30 seconds. It also retries at once when discovery sees the device again or this device's network comes back.
- **After reconnecting:** the app sends `subscribe` and fetches everything it shows again. Changes it missed while offline therefore appear without any other catching up.
- **The app closes unexpectedly:** the other side sees the connection drop. A pairing in progress is cancelled and nothing is stored (see Pairing). A half-finished request either happened completely on the receiving device or not at all, because each request is a single database transaction there.
- **The connection drops while a task is being copied:** the sender doesn't know whether the copy arrived, so it retries with the same `key` once it reconnects (see `add_task`). If it can't reconnect within 2 minutes, it tells the user the copy may not have arrived.

## Security notes

- Everything after the TCP connection is encrypted and authenticated by TLS.
- Only paired devices can read or change anything. Pairing needs a person on each device to confirm a code, or the phone to scan the computer's QR code.
- Keys are pinned, so a device on the network can't impersonate a paired one without its private key.
- An attacker in the middle during code pairing would need to guess a random 6-digit code (1 in a million) and still get both users to confirm it. Rate limits stop anyone from trying over and over.
- QR pairing also needs the user at the computer to confirm the phone's name.
- A device that isn't paired can only use the pairing messages, holds at most 2 connections, and is dropped after 30 seconds unless it starts pairing.
- A paired device can read all lists and tasks, tick tasks off, and add tasks. It can't edit or delete anything else.
- Firewalls may need to allow Taskora: Windows asks the first time the app listens, and on Linux, `ufw` or `firewalld` may block the port and mDNS (UDP 5353).
