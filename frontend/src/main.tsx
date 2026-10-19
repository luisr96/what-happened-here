import "maplibre-gl/dist/maplibre-gl.css";
import "./app.css";
import { QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./App";
import { createQueryClient } from "./api/queryClient";

const root = document.getElementById("root");
if (!root) {
  throw new Error("index.html has no #root element");
}
const queryClient = createQueryClient();
createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
);
