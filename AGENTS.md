# Supreme maintenance contract

Modernize for Minecraft 1.21.11 and newer, prioritizing Paper/Purpur and appropriate Leaf/Folia validation. Preserve old item/research IDs, typed persistent values, inventories, machine state, recipes, energy costs, output rates, transport order and staged ingredients. The minimum server version does not permit dropping old saved-item support.

Do not alter chance boundaries, generator/robotic-bee formulas, production caps or buffering semantics as incidental optimization. Performance changes require deterministic comparisons with original behavior, and measured TPS claims require actual server measurements. Keep data-sensitive legacy APIs until replacements have durable storage and rollback evidence.

Run both `scripts/verify-supreme-invariants.sh` and `scripts/verify-doctor-migration.sh`, then Maven `clean verify` against an exact locally published Legacy API. Keep the canonical plugin name/output, existing provider/fingerprint checks and supported optional integrations. Test-only dependencies must not be packaged. Current development output is `target/SF_Supreme1.0.20.jar`; coordinate version changes with validated release/bundle work.

Keep batches scoped, record original and tested core/addon commits, preserve concurrent changes and keep new diagnostics/docs in English. Source matches are not compiler diagnostics. Never silently discard unreadable records, regenerate item identities or replace player items with guide templates. Do not merge or publish a stable release from a temporary validation branch.
