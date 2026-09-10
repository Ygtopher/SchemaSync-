# Data Export Performance

- **CSV:** Fastest (streams ~100k rows/sec).
- **Excel XLSX:** Memory heavy; auto-sizes columns up to 20 columns.
- **SQL:** Batches INSERT statements for fast database re-import.
