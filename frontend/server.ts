import express from "express";
import path from "path";
import { createServer as createViteServer } from "vite";
import crypto from "crypto";

interface BucketFile {
  id: string;
  name: string;
  contentType: string;
  size: number; // bytes
  data: string; // base64 string
  uploadedAt: string;
  owner: string;
}

interface ClientCredential {
  clientId: string;
  clientSecret: string;
  scope: "READ" | "READ_WRITE" | "FULL_ACCESS";
  appName: string;
}

interface AppUser {
  id: string;
  username: string;
  passwordHash: string;
  createdAt: Date;
}

interface UserSession {
  userId: string;
  username: string;
  createdAt: Date;
  expiresAt: Date;
}

// In-memory data store
const activeTokens = new Map<string, { scope: string; appName: string; expiresAt: Date }>();
const registeredClients = new Map<string, ClientCredential>();
const filesStore = new Map<string, BucketFile>();
const users = new Map<string, AppUser>();
const userSessions = new Map<string, UserSession>();

function hashPassword(password: string): string {
  return crypto.createHash("sha256").update(password).digest("hex");
}

function generateSessionId(): string {
  return "sess_" + crypto.randomBytes(24).toString("hex");
}

// Register default client
registeredClients.set("dev-client-id", {
  clientId: "dev-client-id",
  clientSecret: "dev-client-secret-xyz123",
  scope: "FULL_ACCESS",
  appName: "DevPortal-Gateway"
});

// Seed default user
const defaultUserId = "user_" + crypto.randomBytes(8).toString("hex");
users.set("admin", {
  id: defaultUserId,
  username: "admin",
  passwordHash: hashPassword("admin"),
  createdAt: new Date()
});

// Seed a pre-authorized master token for quick testing
activeTokens.set("dev-bucket-access-token-9982", {
  scope: "FULL_ACCESS",
  appName: "DevPortal-Gateway",
  expiresAt: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000) // 1 year
});

// Seed starter images
const defaultDashboardSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 600" width="100%" height="100%">
  <defs>
    <linearGradient id="bg" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0f172a"/>
      <stop offset="100%" stop-color="#1e1b4b"/>
    </linearGradient>
    <linearGradient id="grid-grad" x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stop-color="#4f46e5" stop-opacity="0.2"/>
      <stop offset="100%" stop-color="#06b6d4" stop-opacity="0"/>
    </linearGradient>
  </defs>
  <rect width="800" height="600" fill="url(#bg)"/>
  <path d="M 0,100 L 800,100 M 0,200 L 800,200 M 0,300 L 800,300 M 0,400 L 800,400 M 0,500 L 800,500 M 100,0 L 100,600 M 200,0 L 200,600 M 300,0 L 300,600 M 400,0 L 400,600 M 500,0 L 500,600 M 600,0 L 600,600 M 700,0 L 700,600" stroke="url(#grid-grad)" stroke-width="1"/>
  <circle cx="400" cy="300" r="120" fill="none" stroke="#6366f1" stroke-width="2" stroke-dasharray="10 5"/>
  <circle cx="400" cy="300" r="80" fill="none" stroke="#22d3ee" stroke-width="4"/>
  <circle cx="400" cy="300" r="10" fill="#38bdf8"/>
  <text x="400" y="470" text-anchor="middle" fill="#38bdf8" font-family="monospace" font-size="22" font-weight="bold">JAVA GCS BACKEND BUCKET</text>
  <text x="400" y="505" text-anchor="middle" fill="#64748b" font-family="sans-serif" font-size="14">Default Seeded Asset: cloud-storage-dashboard.svg</text>
</svg>`;

const defaultLogoSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 400 400" width="100%" height="100%">
  <defs>
    <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#f97316"/>
      <stop offset="100%" stop-color="#ec4899"/>
    </linearGradient>
  </defs>
  <rect width="400" height="400" rx="40" fill="url(#grad)"/>
  <circle cx="200" cy="200" r="90" fill="#ffffff" fill-opacity="0.15"/>
  <path d="M 150,150 L 250,150 L 200,250 Z" fill="#ffffff"/>
  <text x="200" y="320" text-anchor="middle" fill="#ffffff" font-family="sans-serif" font-size="18" font-weight="bold">CLOUD BUCKET EXPLORER</text>
</svg>`;

const defaultSuccessSvg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 600 400" width="100%" height="100%">
  <rect width="600" height="400" fill="#020617"/>
  <rect x="50" y="50" width="500" height="300" rx="8" fill="#0f172a" stroke="#1e293b" stroke-width="2"/>
  <circle cx="300" cy="160" r="40" fill="#22c55e" fill-opacity="0.2"/>
  <path d="M 285,160 L 297,172 L 320,145" fill="none" stroke="#22c55e" stroke-width="4" stroke-linecap="round" stroke-linejoin="round"/>
  <text x="300" y="240" text-anchor="middle" fill="#ffffff" font-family="sans-serif" font-size="18" font-weight="bold">Connection Authorized</text>
  <text x="300" y="270" text-anchor="middle" fill="#64748b" font-family="sans-serif" font-size="13">Java Spring Boot Integration: Successful</text>
</svg>`;

// Helper to base64 encode SVG
const base64Encode = (str: string) => Buffer.from(str).toString("base64");

filesStore.set("cloud-storage-dashboard", {
  id: "cloud-storage-dashboard",
  name: "cloud-storage-dashboard.svg",
  contentType: "image/svg+xml",
  size: Buffer.byteLength(defaultDashboardSvg),
  data: base64Encode(defaultDashboardSvg),
  uploadedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
  owner: "DevPortal-Gateway"
});

filesStore.set("java-cloud-logo", {
  id: "java-cloud-logo",
  name: "java-cloud-logo.svg",
  contentType: "image/svg+xml",
  size: Buffer.byteLength(defaultLogoSvg),
  data: base64Encode(defaultLogoSvg),
  uploadedAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
  owner: "DevPortal-Gateway"
});

filesStore.set("auth-success-badge", {
  id: "auth-success-badge",
  name: "auth-success-badge.svg",
  contentType: "image/svg+xml",
  size: Buffer.byteLength(defaultSuccessSvg),
  data: base64Encode(defaultSuccessSvg),
  uploadedAt: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
  owner: "DevPortal-Gateway"
});


async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json({ limit: "50mb" }));
  app.use(express.urlencoded({ limit: "50mb", extended: true }));

  // Middleware to log API requests for our interactive Console
  app.use((req, res, next) => {
    // We can intercept calls to /api/v1 and store logs or let React poll them
    // For simplicity, we just pass through
    next();
  });

  // Security Verification Helper
  const verifyAuth = (req: express.Request, res: express.Response, requiredScope?: string) => {
    const authHeader = req.headers.authorization;
    if (!authHeader) {
      res.status(401).json({
        timestamp: new Date().toISOString(),
        status: 401,
        error: "Unauthorized",
        message: "Full authentication is required to access this resource",
        path: req.originalUrl
      });
      return null;
    }

    if (!authHeader.startsWith("Bearer ")) {
      res.status(401).json({
        timestamp: new Date().toISOString(),
        status: 401,
        error: "Unauthorized",
        message: "Bearer credentials structure is required (Bearer <token>)",
        path: req.originalUrl
      });
      return null;
    }

    const token = authHeader.substring(7);
    const tokenInfo = activeTokens.get(token);

    if (!tokenInfo) {
      res.status(401).json({
        timestamp: new Date().toISOString(),
        status: 401,
        error: "Unauthorized",
        message: "Invalid or expired access token",
        path: req.originalUrl
      });
      return null;
    }

    if (tokenInfo.expiresAt < new Date()) {
      activeTokens.delete(token);
      res.status(401).json({
        timestamp: new Date().toISOString(),
        status: 401,
        error: "Unauthorized",
        message: "Access token has expired",
        path: req.originalUrl
      });
      return null;
    }

    // Verify Scope
    if (requiredScope) {
      const scope = tokenInfo.scope;
      const isAuthorized = 
        scope === "FULL_ACCESS" || 
        (requiredScope === "READ" && (scope === "READ_WRITE" || scope === "READ")) ||
        (requiredScope === "WRITE" && scope === "READ_WRITE");

      if (!isAuthorized) {
        res.status(403).json({
          timestamp: new Date().toISOString(),
          status: 403,
          error: "Forbidden",
          message: `Access Denied: Insufficient scope. Required: ${requiredScope}, Actual: ${scope}`,
          path: req.originalUrl
        });
        return null;
      }
    }

    return tokenInfo;
  };

  // User Auth Middleware
  const requireUser = (req: express.Request, res: express.Response) => {
    const cookie = req.headers.cookie?.split("; ").find(c => c.startsWith("session="));
    if (!cookie) {
      res.status(401).json({ error: "Unauthorized", message: "Not logged in" });
      return null;
    }
    const sessionId = cookie.split("=")[1];
    const session = userSessions.get(sessionId);
    if (!session || session.expiresAt < new Date()) {
      if (session) userSessions.delete(sessionId);
      res.status(401).json({ error: "Unauthorized", message: "Session expired or invalid" });
      return null;
    }
    return session;
  };

  // -------------------------------------------------------------
  // API ENDPOINTS (Simulating Spring Boot Controller endpoints)
  // -------------------------------------------------------------

  // 0. User Signup
  app.post("/api/v1/auth/signup", (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) {
      res.status(400).json({ error: "Bad Request", message: "Username and password are required" });
      return;
    }
    if (username.length < 3) {
      res.status(400).json({ error: "Bad Request", message: "Username must be at least 3 characters" });
      return;
    }
    if (password.length < 4) {
      res.status(400).json({ error: "Bad Request", message: "Password must be at least 4 characters" });
      return;
    }
    if (users.has(username)) {
      res.status(409).json({ error: "Conflict", message: "Username already exists" });
      return;
    }
    const id = "user_" + crypto.randomBytes(8).toString("hex");
    users.set(username, {
      id,
      username,
      passwordHash: hashPassword(password),
      createdAt: new Date()
    });
    const sessionId = generateSessionId();
    userSessions.set(sessionId, {
      userId: id,
      username,
      createdAt: new Date(),
      expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000)
    });
    res.cookie("session", sessionId, {
      httpOnly: true,
      sameSite: "lax",
      maxAge: 24 * 60 * 60 * 1000
    });
    res.status(201).json({
      status: "SUCCESS",
      message: "User registered successfully",
      username
    });
  });

  // 0b. User Login
  app.post("/api/v1/auth/login", (req, res) => {
    const { username, password } = req.body;
    if (!username || !password) {
      res.status(400).json({ error: "Bad Request", message: "Username and password are required" });
      return;
    }
    const user = users.get(username);
    if (!user || user.passwordHash !== hashPassword(password)) {
      res.status(401).json({ error: "Unauthorized", message: "Invalid username or password" });
      return;
    }
    const sessionId = generateSessionId();
    userSessions.set(sessionId, {
      userId: user.id,
      username: user.username,
      createdAt: new Date(),
      expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000)
    });
    res.cookie("session", sessionId, {
      httpOnly: true,
      sameSite: "lax",
      maxAge: 24 * 60 * 60 * 1000
    });
    res.json({
      status: "SUCCESS",
      message: "Login successful",
      username: user.username
    });
  });

  // 0c. Check current session
  app.get("/api/v1/auth/me", (req, res) => {
    const session = requireUser(req, res);
    if (!session) return;
    res.json({
      username: session.username,
      userId: session.userId
    });
  });

  // 0d. Logout
  app.post("/api/v1/auth/logout", (req, res) => {
    const cookie = req.headers.cookie?.split("; ").find(c => c.startsWith("session="));
    if (cookie) {
      const sessionId = cookie.split("=")[1];
      userSessions.delete(sessionId);
    }
    res.clearCookie("session");
    res.json({ status: "SUCCESS", message: "Logged out" });
  });

  // 1. Client Registration (UI utility)
  app.post("/api/v1/auth/register", (req, res) => {
    const { clientId, clientSecret, scope, appName } = req.body;
    if (!clientId || !clientSecret || !scope) {
      res.status(400).json({ error: "Bad Request", message: "Missing required Client Credentials parameters" });
      return;
    }
    registeredClients.set(clientId, { clientId, clientSecret, scope, appName: appName || "ExternalApp" });
    res.status(201).json({
      status: "SUCCESS",
      message: `Client credentials for '${clientId}' registered successfully.`,
      clientId,
      scope
    });
  });

  // 2. Client Token Endpoint (Spring Security OAuth2 Client Credentials grant flow)
  app.post("/api/v1/auth/token", (req, res) => {
    const { clientId, clientSecret } = req.body;
    if (!clientId || !clientSecret) {
      res.status(400).json({
        timestamp: new Date().toISOString(),
        status: 400,
        error: "Bad Request",
        message: "Missing clientId or clientSecret in request body",
        path: "/api/v1/auth/token"
      });
      return;
    }

    const client = registeredClients.get(clientId);
    if (!client || client.clientSecret !== clientSecret) {
      res.status(401).json({
        timestamp: new Date().toISOString(),
        status: 401,
        error: "Unauthorized",
        message: "Bad credentials. Invalid Client ID or Client Secret.",
        path: "/api/v1/auth/token"
      });
      return;
    }

    // Generate token
    const token = "token_" + Math.random().toString(36).substring(2, 15) + Math.random().toString(36).substring(2, 15);
    const expiresAt = new Date(Date.now() + 60 * 60 * 1000); // 1 hour expiry

    activeTokens.set(token, {
      scope: client.scope,
      appName: client.appName,
      expiresAt
    });

    res.json({
      access_token: token,
      token_type: "Bearer",
      expires_in: 3600,
      scope: client.scope,
      jti: "jti_" + Math.random().toString(36).substring(2, 8)
    });
  });

  // 3. List Storage Files (GET)
  app.get("/api/v1/storage/files", (req, res) => {
    const authorized = verifyAuth(req, res, "READ");
    if (!authorized) return;

    const filesList = Array.from(filesStore.values()).map(file => ({
      id: file.id,
      name: file.name,
      contentType: file.contentType,
      size: file.size,
      uploadedAt: file.uploadedAt,
      owner: file.owner,
      publicUrl: `/api/v1/storage/files/${file.id}`
    }));

    res.json(filesList);
  });

  // 4. Upload File (POST)
  app.post("/api/v1/storage/upload", (req, res) => {
    const authorized = verifyAuth(req, res, "WRITE");
    if (!authorized) return;

    const { name, type, size, data } = req.body;
    if (!name || !type || !data) {
      res.status(400).json({
        timestamp: new Date().toISOString(),
        status: 400,
        error: "Bad Request",
        message: "Payload must contain name, type, and base64 data attributes",
        path: "/api/v1/storage/upload"
      });
      return;
    }

    // Verify type is an image
    if (!type.startsWith("image/")) {
      res.status(415).json({
        timestamp: new Date().toISOString(),
        status: 415,
        error: "Unsupported Media Type",
        message: "Only image uploads are allowed in this cloud bucket configuration",
        path: "/api/v1/storage/upload"
      });
      return;
    }

    const fileId = name.toLowerCase().replace(/[^a-z0-9]/g, "-") + "-" + Math.random().toString(36).substring(2, 6);
    
    const newFile: BucketFile = {
      id: fileId,
      name,
      contentType: type,
      size: size || Math.round(data.length * 0.75), // approximate from base64 if not provided
      data,
      uploadedAt: new Date().toISOString(),
      owner: authorized.appName
    };

    filesStore.set(fileId, newFile);

    res.status(201).json({
      status: "CREATED",
      message: "File successfully uploaded to Google Cloud Storage bucket 'my-java-service-bucket'",
      id: fileId,
      name,
      contentType: type,
      size: newFile.size,
      owner: authorized.appName,
      uploadedAt: newFile.uploadedAt,
      publicUrl: `/api/v1/storage/files/${fileId}`
    });
  });

  // 5. Read raw image file content (GET)
  app.get("/api/v1/storage/files/:id", (req, res) => {
    const { id } = req.params;
    const file = filesStore.get(id);

    if (!file) {
      res.status(404).json({
        timestamp: new Date().toISOString(),
        status: 404,
        error: "Not Found",
        message: `Object with ID '${id}' does not exist in bucket 'my-java-service-bucket'`,
        path: req.originalUrl
      });
      return;
    }

    try {
      const buffer = Buffer.from(file.data, "base64");
      res.set("Content-Type", file.contentType);
      res.set("Content-Length", buffer.length.toString());
      res.set("Cache-Control", "public, max-age=86400");
      res.send(buffer);
    } catch (err) {
      res.status(500).json({
        error: "Internal Server Error",
        message: "Failed to decode binary data stream from storage backend"
      });
    }
  });

  // 6. Delete File (DELETE)
  app.delete("/api/v1/storage/files/:id", (req, res) => {
    const authorized = verifyAuth(req, res, "WRITE"); // deletion requires WRITE or greater
    if (!authorized) return;

    const { id } = req.params;
    if (!filesStore.has(id)) {
      res.status(404).json({
        timestamp: new Date().toISOString(),
        status: 404,
        error: "Not Found",
        message: `Object with ID '${id}' does not exist in bucket 'my-java-service-bucket'`,
        path: req.originalUrl
      });
      return;
    }

    filesStore.delete(id);
    res.status(200).json({
      status: "SUCCESS",
      message: `Object '${id}' has been permanently deleted from bucket 'my-java-service-bucket'.`
    });
  });

  // 7. Get Credentials List & Active Tokens (Private Dev API for the frontend playground)
  app.get("/api/internal/debug-state", (req, res) => {
    res.json({
      clients: Array.from(registeredClients.values()),
      activeTokens: Array.from(activeTokens.entries()).map(([token, val]) => ({
        token,
        scope: val.scope,
        appName: val.appName,
        expiresAt: val.expiresAt
      }))
    });
  });

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
