// One poll pipeline per ENTITY, not per watch (FR-7.1): 500 watchers of one
// train cost one upstream call. Implemented as self-rescheduling delayed
// BullMQ jobs (jobId = entity key) rather than repeatable jobs, because the
// cadence adapts per poll (FR-7.5) and the chain simply stops when the last
// watch expires.
import { Queue, Worker, type Job, type ConnectionOptions } from "bullmq";

export const WATCH_QUEUE = "watch-poll";

export interface PollJobData {
  entityKey: string;
}

/** Result of one poll: how long until the next one, or null to stop the chain. */
export type PollFn = (entityKey: string) => Promise<{ nextDelayS: number } | null>;

function connectionFor(redisUrl: string): ConnectionOptions {
  const url = new URL(redisUrl);
  return {
    host: url.hostname,
    port: Number(url.port || 6379),
    ...(url.password ? { password: url.password } : {}),
    maxRetriesPerRequest: null, // required by BullMQ workers
  };
}

export class WatchScheduler {
  private readonly queue: Queue<PollJobData>;
  private readonly connection: ConnectionOptions;
  private worker: Worker<PollJobData> | null = null;

  constructor(redisUrl: string) {
    this.connection = connectionFor(redisUrl);
    this.queue = new Queue<PollJobData>(WATCH_QUEUE, { connection: this.connection });
  }

  /** Idempotently start the poll chain for an entity (called on watch creation). */
  async ensureScheduled(entityKey: string, firstDelayS = 1): Promise<void> {
    // jobId = entityKey dedups: while a job for this entity is pending/delayed,
    // add() with the same id is a no-op.
    await this.queue.add(
      "poll",
      { entityKey },
      {
        jobId: entityKey,
        delay: firstDelayS * 1000,
        removeOnComplete: true, // frees the jobId so the worker can re-add
        removeOnFail: true,
      },
    );
  }

  /** Start processing polls. pollFn returns the next delay or null to stop. */
  start(pollFn: PollFn, concurrency = 4): void {
    this.worker = new Worker<PollJobData>(
      WATCH_QUEUE,
      // The processor only polls; chaining happens on "completed" below —
      // re-adding the same jobId while this job is still active would be
      // silently deduped and the chain would die after one poll.
      async (job: Job<PollJobData>) => pollFn(job.data.entityKey),
      { connection: this.connection, concurrency },
    );

    this.worker.on("completed", (job: Job<PollJobData>) => {
      const outcome = job.returnvalue as Awaited<ReturnType<PollFn>>;
      if (!outcome) return; // chain ends
      // removeOnComplete has freed the jobId by now.
      void this.ensureScheduled(job.data.entityKey, outcome.nextDelayS).catch(() => {
        // resume() on boot / watch creation re-arms a dropped chain.
      });
    });
  }

  /** Re-arm chains after a restart (delayed jobs survive in Redis, but be safe). */
  async resume(entityKeys: string[]): Promise<void> {
    await Promise.all(entityKeys.map((k) => this.ensureScheduled(k, 5)));
  }

  async close(): Promise<void> {
    await this.worker?.close();
    await this.queue.close();
  }
}
