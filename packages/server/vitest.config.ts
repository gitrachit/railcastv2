import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    // The *.int.test.ts files share one Postgres/Redis instance and TRUNCATE
    // the same tables in beforeEach. Running test files in parallel lets one
    // file's TRUNCATE wipe another's rows mid-test, so serialise file execution.
    // The suite is small; the cost is a second or two.
    fileParallelism: false,
  },
});
