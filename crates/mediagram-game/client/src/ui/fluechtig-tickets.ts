import type { FluechtigTicket } from "../protocol/FluechtigTicket";
import type { FluechtigTickets } from "../protocol/FluechtigTickets";

/** A ticket, or the double move, as phones and the TV count them. */
export type TicketKey = keyof FluechtigTickets;

/** The plan's line colours: the TV draws its links in them, the phones their tickets. */
export const TICKET_COLOR: Record<TicketKey, string> = {
  taxi: "#8b95a1",
  bus: "#0f766e",
  ubahn: "#6d28d9",
  schatten: "#1f2937",
  doppel: "#b95f17",
};

export const TICKET_NAME: Record<FluechtigTicket, string> = { taxi: "Taxi", bus: "Bus", ubahn: "U-Bahn", schatten: "Schattenfahrt" };
export const TICKET_SHORT: Record<TicketKey, string> = { taxi: "Taxi", bus: "Bus", ubahn: "U-Bahn", schatten: "Schatten", doppel: "Doppel" };
