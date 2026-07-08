// Pipeline entry: source/ → clean → dist/index.bin + dist/vN.delta (see CLAUDE.md).
// The ingest/clean/build stages land with backlog item 3.5.
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";

const sourceDir = fileURLToPath(new URL("../source/", import.meta.url));

if (!existsSync(sourceDir)) {
  console.log(
    "directory build: no source/ data yet — pipeline stages land with backlog item 3.5",
  );
  process.exit(0);
}

throw new Error("pipeline stages not implemented yet (backlog 3.5)");
