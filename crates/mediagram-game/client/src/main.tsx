import "@fontsource/bricolage-grotesque/latin-600.css";
import "@fontsource/bricolage-grotesque/latin-800.css";
import "@fontsource/atkinson-hyperlegible/latin-400.css";
import "@fontsource/atkinson-hyperlegible/latin-700.css";
import "./styles.css";
import "./lobby.css";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { inApp } from "./phone/app-bridge";
import { PhoneApp } from "./phone/PhoneApp";
import { TvApp } from "./tv/TvApp";

const isTv = location.pathname.replace(/\/+$/, "") === "/tv";
document.documentElement.dataset.surface = isTv ? "tv" : "phone";
// Inside the controller apps the page is a drawing surface: no selection or callouts (styles.css).
if (inApp()) document.documentElement.classList.add("in-app");

createRoot(document.getElementById("root")!).render(
  <StrictMode>{isTv ? <TvApp /> : <PhoneApp />}</StrictMode>,
);
