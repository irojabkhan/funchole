-- The exported function name within the entrypoint file that should be
-- invoked as the handler (e.g. "GrowUp" for `export async function GrowUp(...)`).
-- Defaults to "handler" for both the column (existing rows) and new
-- submissions that omit it, matching the two hand-placed dev-seed artifacts.
ALTER TABLE function_version_sources
ADD COLUMN handler VARCHAR(255) NOT NULL DEFAULT 'handler';
