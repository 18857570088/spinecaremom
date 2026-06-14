const http = require("http");
const fs = require("fs");
const path = require("path");

const sourceRoot = path.resolve(__dirname, "..");
const dependencyRoot = path.resolve(
  process.env.SPINECARE_DEP_ROOT || "D:/2026/codexwork/SpinecareMom"
);
const port = Number(process.env.PORT || 4173);

const mimeTypes = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "application/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".png": "image/png",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".svg": "image/svg+xml; charset=utf-8",
  ".ico": "image/x-icon"
};

function send(res, status, body, headers = {}) {
  res.writeHead(status, headers);
  res.end(body);
}

function resolveInside(root, requestPath) {
  const cleanPath = decodeURIComponent(requestPath.split("?")[0]);
  const resolved = path.resolve(root, `.${cleanPath}`);
  if (!resolved.startsWith(root)) {
    return null;
  }
  return resolved;
}

function serveFile(res, filePath) {
  fs.readFile(filePath, (error, content) => {
    if (error) {
      send(res, error.code === "ENOENT" ? 404 : 500, "Not found", {
        "Content-Type": "text/plain; charset=utf-8"
      });
      return;
    }

    const ext = path.extname(filePath).toLowerCase();
    send(res, 200, content, {
      "Content-Type": mimeTypes[ext] || "application/octet-stream",
      "Cache-Control": "no-store"
    });
  });
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url, `http://${req.headers.host}`);

  if (url.pathname === "/deps/lucide.js") {
    serveFile(res, path.join(dependencyRoot, "vendor", "lucide.js"));
    return;
  }

  const candidate = resolveInside(sourceRoot, url.pathname === "/" ? "/index.html" : url.pathname);
  if (!candidate) {
    send(res, 403, "Forbidden", { "Content-Type": "text/plain; charset=utf-8" });
    return;
  }

  fs.stat(candidate, (error, stats) => {
    if (!error && stats.isFile()) {
      serveFile(res, candidate);
      return;
    }
    serveFile(res, path.join(sourceRoot, "index.html"));
  });
});

server.listen(port, "127.0.0.1", () => {
  console.log(`Spinecare Mom is running at http://127.0.0.1:${port}`);
  console.log(`Source root: ${sourceRoot}`);
  console.log(`Dependency root: ${dependencyRoot}`);
});
