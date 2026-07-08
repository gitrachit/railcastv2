// Fails when docs/api-contracts.md changes without packages/shared being regenerated.
// Usage: node scripts/check-drift.mjs [--update]
import { createHash } from "node:crypto";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";

const repoRoot = new URL("../../../", import.meta.url);
const docPath = fileURLToPath(new URL("docs/api-contracts.md", repoRoot));
const contractsPath = fileURLToPath(new URL("packages/shared/src/contracts.ts", repoRoot));

const docHash = createHash("sha256").update(readFileSync(docPath)).digest("hex");
const contracts = readFileSync(contractsPath, "utf8");
const HASH_LINE = /contracts-sha256: [0-9a-f]{64}/;
const match = contracts.match(/contracts-sha256: ([0-9a-f]{64})/);

if (!match) {
  console.error(`drift check: no contracts-sha256 comment found in ${contractsPath}`);
  process.exit(1);
}

if (process.argv.includes("--update")) {
  writeFileSync(contractsPath, contracts.replace(HASH_LINE, `contracts-sha256: ${docHash}`));
  console.log(`drift check: hash updated to ${docHash}`);
  process.exit(0);
}

if (match[1] !== docHash) {
  console.error(
    "drift check FAILED: docs/api-contracts.md changed but packages/shared was not regenerated.\n" +
      "Update the types in packages/shared/src/contracts.ts to match the doc, then run:\n" +
      "  pnpm -F @railcast/shared check:drift --update",
  );
  process.exit(1);
}

console.log("drift check: contracts doc and shared types are in sync");
