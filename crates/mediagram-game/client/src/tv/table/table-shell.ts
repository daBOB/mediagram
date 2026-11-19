// The life of a 3D board on the TV, without React or three.js: the stage loads (and a stage that arrives after the board
// was left or the ranking took over is freed at once), OK on the remote opens the settings over the board, the winner is
// cheered for a few seconds before the ranking replaces the board, and a new game at the same table builds it again.

/** How long the finished board stays up, with the winner in the banner, before the final ranking. */
export const CHEER_MS = 4000;

export interface Stage {
  dispose(): void;
}
/** Starts the game's stage in `container`: its chunk, its assets, its WebGL context. */
export type StageStart = (container: HTMLElement) => Promise<Stage>;
export interface Clock {
  set(run: () => void, ms: number): unknown;
  clear(timer: unknown): void;
}
export interface TableView {
  state: "loading" | "ready" | "failed";
  /** The settings panel is open over the board. */
  settings: boolean;
  /** The winner has been cheered: the ranking shows instead of the board. */
  cheered: boolean;
  /** Counts the games at this table: a new one needs its board built again. */
  round: number;
}

const timers: Clock = { set: (run, ms) => setTimeout(run, ms), clear: (t) => clearTimeout(t as ReturnType<typeof setTimeout>) };

export class TableShell {
  private stage: Stage | null = null;
  private current: TableView = { state: "loading", settings: false, cheered: false, round: 0 };
  private readonly listeners = new Set<() => void>();

  /** `name` labels a failed load in the console. */
  constructor(private readonly start: StageStart, private readonly name: string, private readonly clock: Clock = timers) {}

  readonly view = () => this.current;
  readonly subscribe = (listener: () => void) => {
    this.listeners.add(listener);
    return () => void this.listeners.delete(listener);
  };

  /** Builds the board into `container`; returns what leaves it (the board freed, a load still on its way dropped). */
  load(container: HTMLElement): () => void {
    let gone = false;
    this.set({ state: "loading" });
    this.start(container)
      .then((stage) => {
        // the ranking may have taken over while the box was still building the board
        if (gone || this.current.cheered) stage.dispose();
        else {
          this.stage = stage;
          this.set({ state: "ready" });
        }
      })
      .catch((e) => {
        console.error(this.name, e);
        if (!gone) this.set({ state: "failed" });
      });
    return () => {
      gone = true;
      this.stop();
    };
  }

  /** The scene says whether the game is over; returns what calls off a cheer not yet run. */
  watch(over: boolean): () => void {
    if (over && !this.current.cheered) {
      const timer = this.clock.set(() => {
        this.stop(); // the ranking replaces the board: no more frames drawn behind it
        this.set({ settings: false, cheered: true }); // else the panel would reopen over the next game's board
      }, CHEER_MS);
      return () => this.clock.clear(timer);
    }
    // the same table started again without this TV seeing the lobby
    if (!over && this.current.cheered) this.set({ cheered: false, round: this.current.round + 1 });
    return () => {};
  }

  /** A key on the remote: OK opens the settings over the board while it is up; the game runs on underneath. */
  press(key: string, repeat: boolean) {
    // not on a held OK: the one that started the game would open the panel over the new board
    if (key === "Enter" && !repeat && !this.current.cheered) this.set({ settings: true });
  }

  readonly closeSettings = () => this.set({ settings: false });

  private stop() {
    this.stage?.dispose();
    this.stage = null;
  }

  private set(change: Partial<TableView>) {
    this.current = { ...this.current, ...change };
    this.listeners.forEach((l) => l());
  }
}
