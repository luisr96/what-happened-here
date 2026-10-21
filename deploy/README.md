# Production server

One VPS (Ubuntu 24.04, 8 GB RAM) runs Caddy (HTTPS), the API and Postgres + PostGIS from
`compose.yaml`. The frontend stays on GitHub Pages and calls the API at `https://$DOMAIN`.

## First-time setup (in this order)

1. **Server:** `ssh root@<ip> 'bash -s' < deploy/setup-server.sh`, then check `ssh deploy@<ip>`.
2. **Files:** copy `compose.yaml` and `Caddyfile` to `/opt/whh`, and create `/opt/whh/.env` from
   `.env.example` (`chmod 600`).
3. **API image:** push one image (run the Deploy API workflow, or wait for its first run), then
   make the `what-happened-here-api` package public on GitHub (Packages > settings >
   visibility), so the server can pull it without a login.
4. **Database before the API:** `docker compose up -d db`, then restore the dump with
   `pg_restore`. The API's Flyway would otherwise create an empty schema that clashes with it.
   After restoring, `CLUSTER` and `ANALYZE` are not needed if the dump came from a clustered
   database: `pg_restore` keeps the row order.
5. **Start everything:** `docker compose up -d`. Caddy gets the certificate on its own once DuckDNS points at the server.
6. **CI:** repository secrets `DEPLOY_SSH_KEY` (a key that logs in as deploy) and
   `DEPLOY_KNOWN_HOSTS` (`ssh-keyscan <ip>` output, checked against the server's own host keys),
   and the variable `DEPLOY_HOST` (the IP or domain). Then set `API_BASE_URL` to
   `https://$DOMAIN` and re-run the Pages workflow.

## Backups

The data comes from a Wikidata dump, so a full re-import is the recovery plan; Contabo's snapshot
covers the server itself. Take a snapshot before risky changes.
