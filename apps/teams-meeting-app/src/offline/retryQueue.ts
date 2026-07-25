export type OfflineOp =
  | {
      id: string;
      kind: "create-marker";
      meetingId: string;
      type: string;
      body: string;
      idempotencyKey: string;
      attempts: number;
    }
  | {
      id: string;
      kind: "update-agenda";
      meetingId: string;
      items: string[];
      expectedVersion: number | null;
      idempotencyKey: string;
      attempts: number;
    };

export type OfflineDispatcher = (op: OfflineOp) => Promise<void>;

/**
 * Queues collaboration mutations while offline and flushes when connectivity returns.
 */
export class OfflineRetryQueue {
  private readonly queue: OfflineOp[] = [];
  private online = true;

  constructor(private readonly dispatch: OfflineDispatcher) {}

  setOnline(online: boolean): void {
    this.online = online;
  }

  isOnline(): boolean {
    return this.online;
  }

  pending(): readonly OfflineOp[] {
    return this.queue;
  }

  async enqueue(op: Omit<OfflineOp, "id" | "attempts">): Promise<"sent" | "queued"> {
    const full = { ...op, id: crypto.randomUUID(), attempts: 0 } as OfflineOp;
    if (!this.online) {
      this.queue.push(full);
      return "queued";
    }
    try {
      await this.dispatch(full);
      return "sent";
    } catch {
      full.attempts += 1;
      this.queue.push(full);
      return "queued";
    }
  }

  async flush(): Promise<number> {
    if (!this.online) {
      return 0;
    }
    let sent = 0;
    while (this.queue.length > 0) {
      const next = this.queue[0];
      try {
        await this.dispatch(next);
        this.queue.shift();
        sent += 1;
      } catch {
        next.attempts += 1;
        break;
      }
    }
    return sent;
  }
}
