import "@fontsource/bricolage-grotesque/latin-600.css";
import "@fontsource/bricolage-grotesque/latin-800.css";
import "@fontsource/atkinson-hyperlegible/latin-400.css";
import "@fontsource/atkinson-hyperlegible/latin-700.css";
import "./styles.css";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { PhoneApp } from "./phone/PhoneApp";
import { TvApp } from "./tv/TvApp";

const isTv = location.pathname.replace(/\/+$/, "") === "/tv";
document.documentElement.dataset.surface = isTv ? "tv" : "phone";

createRoot(document.getElementById("root")!).render(
  <StrictMode>{isTv ? <TvApp /> : <PhoneApp />}</StrictMode>,
);
