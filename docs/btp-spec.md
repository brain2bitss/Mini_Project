# Bramble Protocol Suite (BTP) - Specification

## Overview
This document specifies the wire format and behavior of the Bramble Transport Protocol used in this peer-to-peer chat application. 
BTP provides delay-tolerant, chunked, serialized transport over multiple layers including Tor (onion services), Bluetooth RFCOMM, and Wi-Fi Direct.

## Cryptographic Primitives
- **Key Exchange:** X25519
- **Signatures:** Ed25519
- **Symmetric Encryption/Authentication:** AES-256-GCM
- **Hashing:** BLAKE2b

## Transport Layers
1. **Tor (Onion V3):** Connects to peer's `.onion` address directly. Streams are fully encrypted and authenticated end-to-end.
2. **Mesh (Bluetooth/Wi-Fi):** Direct socket connections to peers in physical proximity.
3. **Relay (Store-and-forward):** Packets encrypted for a destination are relayed through mutual contacts. Max hop limit is 3.

## Packet Format
All messages are serialized into chunks of a predefined maximum size (e.g., 32 KB) to prevent traffic analysis and fit within standard MTUs.

### Frame Header (Unencrypted but authenticated)
- `version` (1 byte)
- `type` (1 byte - e.g., HANDSHAKE, DATA, ACK, KEEPALIVE)
- `length` (2 bytes - length of payload)
- `stream_id` (4 bytes - for multiplexing)

### Handshake
- Peers exchange ephemeral X25519 public keys.
- Derive shared secret using HKDF(BLAKE2b).
- Establish AES-256-GCM keys for TX/RX.

### Encrypted Payload
- The payload is encrypted using AES-256-GCM with the derived keys.
- Includes a 16-byte authentication tag at the end.

## Routing and Relaying
- Packets are opaque to relays.
- A relay only sees: `[Next Hop ID (if known)] | [TTL] | [Encrypted Blob]`
- TTL is decremented at each hop. If TTL reaches 0, the packet is dropped.
