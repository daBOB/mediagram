export const HOME: "home";

/** A profile as stored: `household` absent (or null) is home. */
export interface HouseholdProfile {
  id: string;
  kids: boolean;
  owner?: boolean;
  parentId: string | null;
  household?: string | null;
}

export interface Household {
  id: string;
  adminId: string | null;
  removed?: boolean;
}

export interface HouseholdView {
  profiles: Record<string, { household: string; admin: boolean }>;
  admins: Record<string, string | null>;
}

/** A profile already placed by `householdView`. */
export interface HouseholdRoleView {
  id: string;
  kids: boolean;
  admin: boolean;
  owner?: boolean;
  parentId: string | null;
  household: string;
}

export type HouseholdAction =
  | "create-household"
  | "rename-household"
  | "set-household-admin"
  | "move-to-household"
  | "delete-household"
  | "join-household"
  | "claim-household";

export function householdView(profiles: HouseholdProfile[], households: Household[]): HouseholdView;
export function householdAllowed(
  profiles: HouseholdRoleView[],
  live: string[],
  actorId: string,
  action: HouseholdAction,
  householdId: string,
  targetId: string,
): boolean;
export function deviceHousehold(
  stored: string | null,
  chosen: string | null,
  live: string[],
  heard: boolean,
): string | null;
