// Fails when src/api/schema.d.ts is stale against openapi.json, so the generated types can't
// drift from the committed API contract. Fix with: npm run gen:api
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const dir = mkdtempSync(join(tmpdir(), "whh-api-"));
const fresh = join(dir, "schema.d.ts");
execFileSync(process.execPath,
    ["node_modules/openapi-typescript/bin/cli.js", "openapi.json", "-o", fresh],
    { stdio: ["ignore", "ignore", "inherit"] });
const normalize = (text) => text.replace(/\r\n/g, "\n");
const stale = normalize(readFileSync(fresh, "utf8"))
    !== normalize(readFileSync("src/api/schema.d.ts", "utf8"));
rmSync(dir, { recursive: true });
if (stale) {
  console.error("src/api/schema.d.ts is out of date with openapi.json. Run: npm run gen:api");
  process.exit(1);
}
console.log("API types are up to date.");
