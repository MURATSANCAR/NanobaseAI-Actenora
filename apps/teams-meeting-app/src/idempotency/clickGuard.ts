/**
 * Prevents duplicate click submissions by reusing the same Idempotency-Key
 * for an in-flight or recently completed mutation.
 */
export class DuplicateClickGuard {
  private readonly keys = new Map<string, string>();

  begin(actionKey: string): string {
    const existing = this.keys.get(actionKey);
    if (existing) {
      return existing;
    }
    const idempotencyKey = crypto.randomUUID();
    this.keys.set(actionKey, idempotencyKey);
    return idempotencyKey;
  }

  release(actionKey: string): void {
    this.keys.delete(actionKey);
  }

  peek(actionKey: string): string | undefined {
    return this.keys.get(actionKey);
  }
}
