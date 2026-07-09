import pg from "pg";

export function createPool(): pg.Pool {
  return new pg.Pool({
    connectionString:
      process.env.DATABASE_URL ?? "postgres://railcast:railcast_dev@127.0.0.1:5432/railcast",
    max: 10,
  });
}
