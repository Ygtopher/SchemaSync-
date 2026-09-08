# Data Diff Memory & Performance

- **Streaming Chunks:** Large tables (> 100k rows) should be compared in paginated batches using primary key range filters.
- **Hash Pre-filtering:** Compare 32-bit row hashes first before deep cell comparisons.
