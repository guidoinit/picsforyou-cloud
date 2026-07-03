import express from "express";
import http from "http";
import path from "path";
import { createServer as createViteServer } from "vite";

async function startServer() {
  const app = express();
  const PORT = parseInt(process.env.PORT || "3000", 10);
  const API_TARGET = process.env.API_TARGET || "http://localhost:10980";

  // Proxy /api/* to Java backend (before body parsers to preserve raw stream)
  app.use("/api", (req, res) => {
    const targetUrl = new URL(req.originalUrl, API_TARGET);
    const proxyReq = http.request(
      targetUrl.toString(),
      {
        method: req.method,
        headers: { ...req.headers, host: targetUrl.host },
      },
      (proxyRes) => {
        res.statusCode = proxyRes.statusCode || 500;
        for (const [key, value] of Object.entries(proxyRes.headers)) {
          if (value) res.setHeader(key, Array.isArray(value) ? value.join(", ") : value);
        }
        proxyRes.pipe(res);
      }
    );
    proxyReq.on("error", () => res.status(502).json({ error: "Bad Gateway", message: "Backend unavailable" }));
    req.pipe(proxyReq);
  });

  app.use(express.json({ limit: "50mb" }));
  app.use(express.urlencoded({ limit: "50mb", extended: true }));

  // Vite middleware for React asset serving
  if (process.env.NODE_ENV !== "production") {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: "spa",
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(process.cwd(), "dist");
    app.use(express.static(distPath));
    app.get("*", (req, res) => {
      res.sendFile(path.join(distPath, "index.html"));
    });
  }

  app.listen(PORT, "0.0.0.0", () => {
    console.log(`Server running on http://localhost:${PORT}`);
  });
}

startServer();
