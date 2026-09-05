# Connection Troubleshooting Guide

## PostgreSQL "Connection Refused"
1. Verify PostgreSQL service is running (`pg_isready`).
2. Verify `listen_addresses = '*'` in `postgresql.conf`.
3. Check `pg_hba.conf` allows client IP connections.
