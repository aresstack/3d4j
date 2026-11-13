# KeePassRPC-Anbindung (Kopie)

Unveränderte Kopie aus [aresstack/enterprise-ai-client](https://github.com/aresstack/enterprise-ai-client)
(`proasteion/platform/security-keepassrpc`, Commit `9fb91c320026616`), übernommen über die Kopie in
aresstack/space-harvester (`enterprise-ai-keepass`). Es ist der Code, mit dem Space Harvester und der
Enterprise AI Client koppeln. Lizenz: MIT, siehe `LICENSE.txt`.

- Nicht übernommen: `KeePassRpcSecretProvider`, `KeePassPairingKeyStore`, `InMemoryPairingKeyStore`
  (brauchen die Module `adyton`/`astu`).
- Einzige eigene Datei: `KeePassRpcLink` (öffentlicher Zugang zum paketprivaten Transport).
- Test-Fixture `FakeKeePassRpcServer` liegt unverändert unter `src/test`.

Änderungen gehören in den Upstream.
