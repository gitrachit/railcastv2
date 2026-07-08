// CLI entry: pnpm -F server migrate
import { createPool } from "./pool.js";
import { migrate } from "./migrate.js";

const pool = createPool();
const ran = await migrate(pool);
console.log(ran.length ? `applied: ${ran.join(", ")}` : "migrations up to date");
await pool.end();
