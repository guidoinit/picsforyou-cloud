/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from "react";
import { 
  Shield, 
  Upload, 
  Image as ImageIcon, 
  Trash2, 
  Copy, 
  Check, 
  Terminal as TerminalIcon, 
  FileText, 
  RefreshCw, 
  Key, 
  Database, 
  Cpu, 
  ChevronRight, 
  Sparkles, 
  AlertTriangle,
  Lock,
  Unlock,
  CloudLightning,
  ExternalLink
} from "lucide-react";



interface BucketFile {
  id: string;
  name: string;
  contentType: string;
  size: number;
  uploadedAt: string;
  owner: string;
  publicUrl: string;
}

interface LogEntry {
  id: string;
  timestamp: string;
  method: "GET" | "POST" | "DELETE";
  url: string;
  status: number;
  statusText: string;
  requestHeaders: Record<string, string>;
  requestBody?: any;
  responseBody?: any;
}

interface ClientCredential {
  clientId: string;
  clientSecret: string;
  scope: string;
  appName: string;
}

export default function App() {
  // Auth state
  const [isLoggedIn, setIsLoggedIn] = useState<boolean | null>(null);
  const [authUsername, setAuthUsername] = useState<string>("");
  const [showSignup, setShowSignup] = useState<boolean>(false);
  const [showVerify, setShowVerify] = useState<boolean>(false);
  const [authEmailInput, setAuthEmailInput] = useState<string>("");
  const [authUsernameInput, setAuthUsernameInput] = useState<string>("");
  const [authPasswordInput, setAuthPasswordInput] = useState<string>("");
  const [verifyTokenInput, setVerifyTokenInput] = useState<string>("");
  const [authError, setAuthError] = useState<string>("");
  const [authLoading, setAuthLoading] = useState<boolean>(false);

  // API credentials (from email verification / login)
  const [apiClientId, setApiClientId] = useState<string>("");
  const [apiClientSecret, setApiClientSecret] = useState<string>("");
  const [apiScope, setApiScope] = useState<string>("");
  const [storageUsedKb, setStorageUsedKb] = useState<number>(0);
  const [plan, setPlan] = useState<string>("");
  const [planLimitMb, setPlanLimitMb] = useState<number>(0);
  const [apiSelectedPlanId, setApiSelectedPlanId] = useState<string>("");
  const [apiAssignedPlan, setApiAssignedPlan] = useState<string>("");
  const [customPlanPending, setCustomPlanPending] = useState<boolean>(false);
  const [customPlanText, setCustomPlanText] = useState<string>("");
  const [showCustomPlanForm, setShowCustomPlanForm] = useState<boolean>(false);
  const [customPlanSending, setCustomPlanSending] = useState<boolean>(false);

  // Post-verify plan selection flow
  const [showPlanSelection, setShowPlanSelection] = useState<boolean>(false);
  const [showCardForm, setShowCardForm] = useState<boolean>(false);
  const [selectedPlanForPayment, setSelectedPlanForPayment] = useState<string>("");
  const [planSelectionLoading, setPlanSelectionLoading] = useState<boolean>(false);
  const [planSelectionError, setPlanSelectionError] = useState<string>("");
  const [showPlanUpgrade, setShowPlanUpgrade] = useState<boolean>(false);
  const [upgradeEmail, setUpgradeEmail] = useState<string>("");
  const [planDiffCents, setPlanDiffCents] = useState<number>(0);
  const [cardName, setCardName] = useState<string>("");
  const [cardNumber, setCardNumber] = useState<string>("");
  const [cardExpiry, setCardExpiry] = useState<string>("");
  const [cardCvv, setCardCvv] = useState<string>("");

  // Navigation & UI Tabs
  const [activeTab, setActiveTab] = useState<"explorer" | "publisher" | "plans">("explorer");

  const [activeMethodIndex, setActiveMethodIndex] = useState<number>(0);

  // Auth credentials & Token
  const [clientId, setClientId] = useState<string>("dev-client-id");
  const [clientSecret, setClientSecret] = useState<string>("dev-client-secret-xyz123");
  const [appScope, setAppScope] = useState<string>("FULL_ACCESS");
  const [appName, setAppName] = useState<string>("DevPortal-Gateway");
  const [accessToken, setAccessToken] = useState<string>("dev-bucket-access-token-9982"); // Seeded token
  const [isTokenValid, setIsTokenValid] = useState<boolean>(true);

  // Bucket explorer state
  const [files, setFiles] = useState<BucketFile[]>([]);
  const [loadingFiles, setLoadingFiles] = useState<boolean>(false);
  const [apiError, setApiError] = useState<{ status?: number; message?: string } | null>(null);

  // File Upload State
  const [selectedFile, setSelectedFile] = useState<{
    name: string;
    type: string;
    size: number;
    base64: string;
  } | null>(null);
  const [customFileName, setCustomFileName] = useState<string>("");
  const [uploading, setUploading] = useState<boolean>(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Request logs console
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [selectedLog, setSelectedLog] = useState<LogEntry | null>(null);
  const [systemUptime, setSystemUptime] = useState<string>("99.98%");
  
  // Clipboard copied indicator
  const [copiedCode, setCopiedCode] = useState<boolean>(false);
  const [copiedUrlId, setCopiedUrlId] = useState<string | null>(null);

  // Check session on mount
  useEffect(() => {
    fetch("/api/v1/auth/me")
      .then(res => res.ok ? res.json() : Promise.reject())
      .then(data => {
        setIsLoggedIn(true);
        setAuthUsername(data.username);
        if (data.email) setAuthEmailInput(data.email);
        if (data.clientId && data.clientSecret) {
          setApiClientId(data.clientId);
          setApiClientSecret(data.clientSecret);
          setApiScope(data.scope || "FULL_ACCESS");
        }
        setStorageUsedKb(data.storageUsedKb ?? 0);
        setPlan(data.plan ?? "");
        setPlanLimitMb(data.planLimitMb ?? 0);
        setCustomPlanPending(data.customPlanPending ?? false);
        fetchBucketFiles(undefined, data.clientId, data.clientSecret);
      })
      .catch(() => {
        setIsLoggedIn(false);
      });
  }, []);

  // Load files on mount
  useEffect(() => {
    fetchBucketFiles();
    updateSystemMetrics();
    
    // Seed an initial log entry to make the playground interactive on first load
    const welcomeLog: LogEntry = {
      id: "log_welcome_1",
      timestamp: new Date().toLocaleTimeString(),
      method: "GET",
      url: "/api/v1/storage/files",
      status: 200,
      statusText: "OK",
      requestHeaders: {
        "Authorization": "Bearer dev-bucket-access-token-9982",
        "Accept": "application/json"
      },
      responseBody: [
        { id: "cloud-storage-dashboard", name: "cloud-storage-dashboard.svg", contentType: "image/svg+xml", size: 4500 }
      ]
    };
    setLogs([welcomeLog]);
    setSelectedLog(welcomeLog);
  }, []);

  // Auth handlers
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError("");
    setAuthLoading(true);
    try {
      const body: Record<string, string> = { password: authPasswordInput };
      if (authUsernameInput.includes("@")) {
        body.email = authUsernameInput;
      } else {
        body.username = authUsernameInput;
      }
      const res = await fetch("/api/v1/auth/login", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body)
      });
      const data = await res.json();
      if (res.ok) {
        setIsLoggedIn(true);
        setAuthUsername(data.username);
        if (data.email) setAuthEmailInput(data.email);
        if (data.clientId && data.clientSecret) {
          setApiClientId(data.clientId);
          setApiClientSecret(data.clientSecret);
          setApiScope(data.scope || "FULL_ACCESS");
        }
        setStorageUsedKb(data.storageUsedKb ?? 0);
        setPlan(data.plan ?? "");
        setPlanLimitMb(data.planLimitMb ?? 0);
        setCustomPlanPending(data.customPlanPending ?? false);
        fetchBucketFiles();
      } else {
        setAuthError(data.message || "Login failed");
      }
    } catch {
      setAuthError("Network error");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleSignup = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError("");
    setAuthLoading(true);
    try {
      const res = await fetch("/api/v1/auth/signup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: authEmailInput, password: authPasswordInput })
      });
      const data = await res.json();
      if (res.ok) {
        setShowSignup(false);
        setShowVerify(true);
        setAuthError("Verification code sent to " + authEmailInput + ". Check server console for the token.");
      } else {
        setAuthError(data.message || "Registration failed");
      }
    } catch {
      setAuthError("Network error");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleVerifyEmail = async (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError("");
    setAuthLoading(true);
    try {
      const res = await fetch("/api/v1/auth/verify", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ token: verifyTokenInput })
      });
      const data = await res.json();
      if (res.ok) {
        setApiClientId(data.clientId);
        setApiClientSecret(data.clientSecret);
        setApiScope(data.scope || "FULL_ACCESS");
        setShowVerify(false);
        setShowPlanSelection(true);
        setAuthError("");
      } else {
        setAuthError(data.message || "Verification failed");
      }
    } catch {
      setAuthError("Network error");
    } finally {
      setAuthLoading(false);
    }
  };

  const handleLogout = async () => {
    await fetch("/api/v1/auth/logout", { method: "POST" });
    setIsLoggedIn(false);
    setAuthUsername("");
    setAuthEmailInput("");
    setAuthUsernameInput("");
    setAuthPasswordInput("");
    setVerifyTokenInput("");
    setApiClientId("");
    setApiClientSecret("");
    setApiScope("");
    setApiSelectedPlanId("");
    setApiAssignedPlan("");
    setAccessToken("");
    setIsTokenValid(false);
  };

  const updateSystemMetrics = async () => {
    try {
      const response = await fetch("/api/internal/debug-state");
      if (response.ok) {
        // Can read active clients or state if needed
      }
    } catch (e) {
      // Ignored
    }
  };

  // Helper to add terminal request/response logs
  const addLog = (
    method: "GET" | "POST" | "DELETE",
    url: string,
    status: number,
    statusText: string,
    reqHeaders: Record<string, string>,
    reqBody: any,
    resBody: any
  ) => {
    const newLog: LogEntry = {
      id: "log_" + Math.random().toString(36).substring(2, 9),
      timestamp: new Date().toLocaleTimeString(),
      method,
      url,
      status,
      statusText,
      requestHeaders: reqHeaders,
      requestBody: reqBody,
      responseBody: resBody
    };
    setLogs(prev => [newLog, ...prev]);
    setSelectedLog(newLog);
  };

  // REST CALLS

  // Generate OAuth2 Client Credentials token
  const requestOAuthToken = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    
    const requestHeaders = {
      "Content-Type": "application/json",
      "Accept": "application/json"
    };

    const requestBody = { clientId, clientSecret };

    try {
      // First, register the client to make sure server knows about it
      await fetch("/api/v1/auth/register", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ clientId, clientSecret, scope: appScope, appName })
      });

      const response = await fetch("/api/v1/auth/token", {
        method: "POST",
        headers: requestHeaders,
        body: JSON.stringify(requestBody)
      });

      const data = await response.json();
      
      addLog("POST", "/api/v1/auth/token", response.status, response.statusText, requestHeaders, requestBody, data);

      if (response.ok && data.access_token) {
        setAccessToken(data.access_token);
        setIsTokenValid(true);
        setApiError(null);
        // Refresh files list with new token
        setTimeout(() => fetchBucketFiles(data.access_token), 100);
      } else {
        setAccessToken("");
        setIsTokenValid(false);
        setApiError({ status: response.status, message: data.message || "Failed authentication" });
      }
    } catch (err: any) {
      addLog("POST", "/api/v1/auth/token", 500, "Internal Server Error", requestHeaders, requestBody, { error: err.message });
      setApiError({ status: 500, message: "Network connection refused" });
    }
  };

  // Build auth header: prefer Basic (API credentials) over Bearer
  const buildAuthHeaders = (tokenOverride?: string, clientIdOverride?: string, clientSecretOverride?: string): Record<string, string> => {
    const headers: Record<string, string> = { "Accept": "application/json" };
    const tok = tokenOverride !== undefined ? tokenOverride : accessToken;
    const cid = clientIdOverride ?? apiClientId;
    const csec = clientSecretOverride ?? apiClientSecret;
    if (cid && csec) {
      const basic = btoa(`${cid}:${csec}`);
      headers["Authorization"] = `Basic ${basic}`;
    } else if (tok) {
      headers["Authorization"] = `Bearer ${tok}`;
    }
    return headers;
  };

  // Fetch bucket files list
  const fetchBucketFiles = async (tokenOverride?: string, clientIdOverride?: string, clientSecretOverride?: string) => {
    setLoadingFiles(true);

    const requestHeaders = buildAuthHeaders(tokenOverride, clientIdOverride, clientSecretOverride);

    try {
      const response = await fetch("/api/v1/storage/files", {
        method: "GET",
        headers: requestHeaders
      });

      const data = await response.json();
      addLog("GET", "/api/v1/storage/files", response.status, response.statusText, requestHeaders, null, data);

      if (response.ok) {
        setFiles(data);
        setApiError(null);
        setIsTokenValid(true);
      } else {
        setFiles([]);
        setApiError({ status: response.status, message: data.message || "Unauthorized access to bucket" });
        if (response.status === 401) {
          setIsTokenValid(false);
        }
      }
    } catch (err: any) {
      setFiles([]);
      setApiError({ status: 500, message: "Error communicating with Java cloud storage API" });
    } finally {
      setLoadingFiles(false);
    }
  };

  // Upload an image via API
  const handlePublishImage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedFile) return;

    setUploading(true);

    const requestHeaders: Record<string, string> = {
      "Content-Type": "application/json",
      "Accept": "application/json"
    };
    Object.assign(requestHeaders, buildAuthHeaders());

    const payload = {
      name: customFileName || selectedFile.name,
      type: selectedFile.type,
      size: selectedFile.size,
      data: selectedFile.base64
    };

    try {
      const response = await fetch("/api/v1/storage/upload", {
        method: "POST",
        headers: requestHeaders,
        body: JSON.stringify(payload)
      });

      const data = await response.json();
      
      // Keep payload readable in logs (shorten huge base64 for cleaner render)
      const loggedPayload = { ...payload, data: payload.data.substring(0, 80) + "... [truncated base64 binary image stream]" };
      addLog("POST", "/api/v1/storage/upload", response.status, response.statusText, requestHeaders, loggedPayload, data);

      if (response.ok) {
        setCustomFileName("");
        setSelectedFile(null);
        setActiveTab("explorer");
        setStorageUsedKb(data.storageUsedKb ?? storageUsedKb);
        setPlan(data.plan ?? plan);
        setPlanLimitMb(data.planLimitMb ?? planLimitMb);
        fetchBucketFiles();
      } else {
        setApiError({ status: response.status, message: data.message || "Failed uploading file" });
      }
    } catch (err: any) {
      addLog("POST", "/api/v1/storage/upload", 500, "Error", requestHeaders, {}, { error: err.message });
      setApiError({ status: 500, message: "Server connection failed during upload" });
    } finally {
      setUploading(false);
    }
  };

  // Delete an image
  const handleDeleteFile = async (id: string) => {
    const requestHeaders = buildAuthHeaders();

    try {
      const response = await fetch(`/api/v1/storage/files/${id}`, {
        method: "DELETE",
        headers: requestHeaders
      });

      const data = await response.json();
      addLog("DELETE", `/api/v1/storage/files/${id}`, response.status, response.statusText, requestHeaders, null, data);

      if (response.ok) {
        setFiles(prev => prev.filter(f => f.id !== id));
        fetch("/api/v1/auth/me").then(r => r.ok && r.json()).then(d => d && setStorageUsedKb(d.storageUsedKb ?? 0)).catch(() => {});
      } else {
        setApiError({ status: response.status, message: data.message || "Could not delete resource" });
      }
    } catch (err: any) {
      addLog("DELETE", `/api/v1/storage/files/${id}`, 500, "Error", requestHeaders, null, { error: err.message });
    }
  };

  // Helper file processor
  const processSelectedFile = (file: File) => {
    if (!file.type.startsWith("image/")) {
      alert("Only image files (JPEG, PNG, SVG, WebP, GIF) are allowed for this Cloud Bucket storage setup.");
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      const base64String = (reader.result as string).split(",")[1];
      setSelectedFile({
        name: file.name,
        type: file.type,
        size: file.size,
        base64: base64String
      });
      setCustomFileName(file.name);
    };
    reader.readAsDataURL(file);
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      processSelectedFile(e.dataTransfer.files[0]);
    }
  };

  const copyToClipboard = (text: string, id?: string) => {
    navigator.clipboard.writeText(text);
    if (id) {
      setCopiedUrlId(id);
      setTimeout(() => setCopiedUrlId(null), 2000);
    } else {
      setCopiedCode(true);
      setTimeout(() => setCopiedCode(false), 2000);
    }
  };

  const clearLogs = () => {
    setLogs([]);
    setSelectedLog(null);
  };

  // Quick action: Seed token automatically
  const injectToken = (token: string, scope: string, name: string, cId?: string, cSecret?: string) => {
    setAccessToken(token);
    setAppScope(scope);
    setAppName(name);
    if (cId) setApiClientId(cId);
    if (cSecret) setApiClientSecret(cSecret);
    setApiScope(scope);
    setIsTokenValid(true);
    setApiError(null);
    fetchBucketFiles(token);
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return "0 Bytes";
    const k = 1024;
    const sizes = ["Bytes", "KB", "MB"];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
  };

  if (isLoggedIn === null) {
    return (
      <div className="min-h-screen bg-bg text-text font-sans flex items-center justify-center">
        <div className="text-center font-mono">
          <div className="w-6 h-6 border-2 border-accent border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
          <p className="text-sm text-subtext">Connecting to Cloud Storage API...</p>
        </div>
      </div>
    );
  }

  if (!isLoggedIn) {
    return (
      <div className="min-h-screen bg-bg text-text font-sans flex items-center justify-center p-4">
        <div className="w-full max-w-md">
          <div className="border border-line bg-panel p-8">
            <div className="text-center mb-8">
              <div className="inline-block px-3 py-1 bg-accent/15 border border-accent text-accent rounded-full text-[10px] font-mono font-bold tracking-wider mb-4">
                JAVA CORE v2.4
              </div>
              <h1 className="text-3xl font-black tracking-tighter uppercase">picsforyou.cloud</h1>
              <p className="text-subtext text-xs font-mono mt-2">Cloud Storage API Playground</p>
            </div>

            {showVerify ? (
              <form onSubmit={handleVerifyEmail} className="space-y-4">
                <h2 className="text-sm font-bold font-mono uppercase tracking-wider text-center mb-4">Verify Email</h2>
                {authError && (
                  <div className="p-3 bg-amber-950/20 border border-amber-800/40 rounded text-xs text-amber-200 font-mono">
                    {authError}
                  </div>
                )}
                <div>
                  <label className="block text-[10px] text-subtext font-mono mb-1 uppercase tracking-widest">Verification Token</label>
                  <input
                    type="text"
                    value={verifyTokenInput}
                    onChange={e => setVerifyTokenInput(e.target.value)}
                    className="w-full bg-bg border border-line rounded px-3 py-2 text-xs font-mono text-text focus:outline-none focus:border-accent"
                    placeholder="Paste verification token from server log"
                    required
                  />
                </div>
                <button
                  type="submit"
                  disabled={authLoading}
                  className="w-full bg-accent hover:bg-accent-hover text-white font-mono text-xs font-bold py-2.5 rounded transition-all tracking-wider cursor-pointer disabled:opacity-50"
                >
                  {authLoading ? "Verifying..." : "VERIFY EMAIL"}
                </button>
                <p className="text-center text-[11px] text-subtext font-mono">
                  <button type="button" onClick={() => { setShowVerify(false); setShowSignup(false); setAuthError(""); }} className="text-accent hover:underline cursor-pointer">
                    Back to login
                  </button>
                </p>
              </form>
            ) : showSignup ? (
              <form onSubmit={handleSignup} className="space-y-4">
                <h2 className="text-sm font-bold font-mono uppercase tracking-wider text-center mb-4">Create Account</h2>
                {authError && (
                  <div className="p-3 bg-amber-950/20 border border-amber-800/40 rounded text-xs text-amber-200 font-mono">
                    {authError}
                  </div>
                )}
                <div>
                  <label className="block text-[10px] text-subtext font-mono mb-1 uppercase tracking-widest">Email</label>
                  <input
                    type="email"
                    value={authEmailInput}
                    onChange={e => setAuthEmailInput(e.target.value)}
                    className="w-full bg-bg border border-line rounded px-3 py-2 text-xs font-mono text-text focus:outline-none focus:border-accent"
                    placeholder="your@email.com"
                    required
                  />
                </div>
                <div>
                  <label className="block text-[10px] text-subtext font-mono mb-1 uppercase tracking-widest">Password</label>
                  <input
                    type="password"
                    value={authPasswordInput}
                    onChange={e => setAuthPasswordInput(e.target.value)}
                    className="w-full bg-bg border border-line rounded px-3 py-2 text-xs font-mono text-text focus:outline-none focus:border-accent"
                    placeholder="Minimum 4 characters"
                    required
                    minLength={4}
                  />
                </div>
                <button
                  type="submit"
                  disabled={authLoading}
                  className="w-full bg-accent hover:bg-accent-hover text-white font-mono text-xs font-bold py-2.5 rounded transition-all tracking-wider cursor-pointer disabled:opacity-50"
                >
                  {authLoading ? "Creating Account..." : "SIGN UP"}
                </button>
                <p className="text-center text-[11px] text-subtext font-mono">
                  Already have an account?{" "}
                  <button type="button" onClick={() => { setShowSignup(false); setAuthError(""); }} className="text-accent hover:underline cursor-pointer">
                    Log in
                  </button>
                </p>
              </form>
            ) : (
              <form onSubmit={handleLogin} className="space-y-4">
                <h2 className="text-sm font-bold font-mono uppercase tracking-wider text-center mb-4">Sign In</h2>
                {authError && (
                  <div className="p-3 bg-amber-950/20 border border-amber-800/40 rounded text-xs text-amber-200 font-mono">
                    {authError}
                  </div>
                )}
                <div>
                  <label className="block text-[10px] text-subtext font-mono mb-1 uppercase tracking-widest">Email or Username</label>
                  <input
                    type="text"
                    value={authUsernameInput}
                    onChange={e => setAuthUsernameInput(e.target.value)}
                    className="w-full bg-bg border border-line rounded px-3 py-2 text-xs font-mono text-text focus:outline-none focus:border-accent"
                    placeholder="admin@example.com or admin"
                    required
                  />
                </div>
                <div>
                  <label className="block text-[10px] text-subtext font-mono mb-1 uppercase tracking-widest">Password</label>
                  <input
                    type="password"
                    value={authPasswordInput}
                    onChange={e => setAuthPasswordInput(e.target.value)}
                    className="w-full bg-bg border border-line rounded px-3 py-2 text-xs font-mono text-text focus:outline-none focus:border-accent"
                    placeholder="Enter your password"
                    required
                  />
                </div>
                <button
                  type="submit"
                  disabled={authLoading}
                  className="w-full bg-accent hover:bg-accent-hover text-white font-mono text-xs font-bold py-2.5 rounded transition-all tracking-wider cursor-pointer disabled:opacity-50"
                >
                  {authLoading ? "Signing In..." : "LOG IN"}
                </button>
                <p className="text-center text-[11px] text-subtext font-mono">
                  No account?{" "}
                  <button type="button" onClick={() => { setShowSignup(true); setAuthError(""); }} className="text-accent hover:underline cursor-pointer">
                    Sign up
                  </button>
                </p>
              </form>
            )}

            {/* Forced plan selection after email verification */}
            {showPlanSelection && (
              <div className="mt-4">
                {showCustomPlanForm ? (
                  <div className="text-center">
                    <div className="inline-block px-3 py-1 bg-amber-400/15 border border-amber-400 text-amber-400 rounded-full text-[10px] font-mono font-bold tracking-wider mb-3">
                      CUSTOM PLAN
                    </div>
                    <h2 className="text-sm font-bold font-mono uppercase tracking-wider text-text mb-2">
                      Describe your needs
                    </h2>
                    <p className="text-[11px] text-subtext font-mono mb-3">
                      Tell us what you need and we will contact you.
                    </p>
                    {planSelectionError && (
                      <div className="p-3 bg-red-950/20 border border-red-800/40 rounded text-xs text-red-200 font-mono mb-3">
                        {planSelectionError}
                      </div>
                    )}
                    <textarea
                      value={customPlanText}
                      onChange={e => setCustomPlanText(e.target.value.slice(0, 512))}
                      maxLength={512}
                      rows={4}
                      placeholder="Describe your storage needs, expected volume, and any special requirements..."
                      className="w-full bg-bg border border-line rounded px-3 py-2 text-xs font-mono text-text focus:outline-none focus:border-amber-400 resize-none"
                    />
                    <div className="text-right text-[10px] text-subtext font-mono mt-1 mb-3">
                      {customPlanText.length}/512
                    </div>
                    <div className="flex gap-2">
                      <button type="button" onClick={() => { setShowCustomPlanForm(false); setCustomPlanText(""); setPlanSelectionError(""); }}
                        className="flex-1 bg-bg border border-line text-subtext font-mono text-xs font-bold py-2.5 rounded hover:border-text transition-all cursor-pointer">
                        BACK
                      </button>
                      <button type="button" disabled={customPlanSending || customPlanText.trim().length === 0}
                        onClick={async () => {
                          setCustomPlanSending(true);
                          setPlanSelectionError("");
                          try {
                            const res = await fetch("/api/v1/plans/custom-request", {
                              method: "POST",
                              headers: { "Content-Type": "application/json" },
                              body: JSON.stringify({ email: authEmailInput, text: customPlanText })
                            });
                            const data = await res.json();
                            if (res.ok) {
                              setPlanSelectionError("");
                              setShowPlanSelection(false);
                            } else {
                              setPlanSelectionError(data.message || "Failed to send request");
                            }
                          } catch (e: any) {
                            setPlanSelectionError(e.message || "Network error");
                          }
                          setCustomPlanSending(false);
                        }}
                        className="flex-1 bg-amber-400 hover:bg-amber-500 text-black font-mono text-xs font-bold py-2.5 rounded transition-all cursor-pointer disabled:opacity-50">
                        {customPlanSending ? "SENDING..." : "SEND REQUEST"}
                      </button>
                    </div>
                  </div>
                ) : !showCardForm ? (
                  <>
                    <div className="text-center mb-4">
                      <div className="inline-block px-3 py-1 bg-accent/15 border border-accent text-accent rounded-full text-[10px] font-mono font-bold tracking-wider mb-3">
                        STEP 2 OF 2
                      </div>
                      <h2 className="text-sm font-bold font-mono uppercase tracking-wider text-text flex items-center justify-center gap-2">
                        <Sparkles className="w-4 h-4 text-accent" /> Choose your plan
                      </h2>
                    </div>
                    {planSelectionError && (
                      <div className="p-3 bg-red-950/20 border border-red-800/40 rounded text-xs text-red-200 font-mono mb-3">
                        {planSelectionError}
                      </div>
                    )}
                    <div className="grid grid-cols-2 gap-2">
                      {[
                        { id: "free", name: "Free", price: "€0", limit: "30 MB", desc: "30 MB storage", color: "text-zinc-400", border: "border-zinc-800", bg: "bg-zinc-950/30" },
                        { id: "base", name: "Base", price: "€5.99", limit: "5 GB", desc: "5 GB / month", color: "text-blue-400", border: "border-blue-800", bg: "bg-blue-950/20" },
                        { id: "professional", name: "Pro", price: "€20", limit: "20 GB", desc: "20 GB / month", color: "text-purple-400", border: "border-purple-800", bg: "bg-purple-950/20" },
                        { id: "custom", name: "Custom", price: "—", limit: "∞", desc: "Contact us", color: "text-amber-400", border: "border-amber-800", bg: "bg-amber-950/20" },
                      ].map(p => (
                        <button
                          key={p.id}
                          onClick={async () => {
                            setPlanSelectionError("");
                            if (p.id === "custom") {
                              setShowCustomPlanForm(true);
                              return;
                            }
                            if (p.id === "free") {
                              setPlanSelectionLoading(true);
                              setApiSelectedPlanId(p.id);
                              const res = await fetch("/api/v1/plans/assign", {
                                method: "POST",
                                headers: { "Content-Type": "application/json" },
                                body: JSON.stringify({ email: authEmailInput, planId: p.id })
                              });
                              if (res.ok) {
                                const data = await res.json();
                                setApiAssignedPlan(data.plan);
                                setShowPlanSelection(false);
                              } else {
                                const err = await res.json();
                                setPlanSelectionError(err.message || "Failed to assign plan");
                              }
                              setPlanSelectionLoading(false);
                            } else {
                              setSelectedPlanForPayment(p.id);
                              setShowCardForm(true);
                            }
                          }}
                          disabled={planSelectionLoading}
                          className={`text-left p-3 rounded border font-mono transition-all cursor-pointer disabled:opacity-50 ${p.bg} ${p.border} hover:brightness-125`}
                        >
                          <div className={`text-[10px] uppercase tracking-widest font-bold ${p.color}`}>{p.name}</div>
                          <div className="text-lg font-black text-text mt-1">{p.price}<span className="text-xs text-subtext font-normal">/mo</span></div>
                          <div className="text-[11px] text-subtext mt-1">{p.limit}</div>
                          <div className="text-[10px] text-subtext/70 mt-1">{p.desc}</div>
                        </button>
                      ))}
                    </div>
                  </>
                ) : (
                  <div className="text-center">
                    <div className="inline-block px-3 py-1 bg-accent/15 border border-accent text-accent rounded-full text-[10px] font-mono font-bold tracking-wider mb-3">
                      REDIRECT TO STRIPE
                    </div>
                    <h2 className="text-sm font-bold font-mono uppercase tracking-wider text-text mb-4">
                      {selectedPlanForPayment === "base" ? "Base Plan — €5.99/mo" : "Professional Plan — €20.00/mo"}
                    </h2>
                    {planSelectionError && (
                      <div className="p-3 bg-red-950/20 border border-red-800/40 rounded text-xs text-red-200 font-mono mb-3">
                        {planSelectionError}
                      </div>
                    )}
                    <p className="text-[11px] text-subtext font-mono mb-4">
                      You will be redirected to Stripe's secure payment page to complete your purchase.
                    </p>
                    <div className="flex gap-2">
                      <button type="button" onClick={() => { setShowCardForm(false); setPlanSelectionError(""); }}
                        className="flex-1 bg-bg border border-line text-subtext font-mono text-xs font-bold py-2.5 rounded hover:border-text transition-all cursor-pointer">
                        BACK
                      </button>
                      <button type="button" disabled={planSelectionLoading}
                        onClick={async () => {
                          setPlanSelectionLoading(true);
                          setPlanSelectionError("");
                          try {
                            const res = await fetch("/api/v1/plans/create-checkout", {
                              method: "POST",
                              headers: { "Content-Type": "application/json" },
                              body: JSON.stringify({ email: authEmailInput, planId: selectedPlanForPayment })
                            });
                            const data = await res.json();
                            if (res.ok && data.url) {
                              window.location.href = data.url;
                            } else {
                              setPlanSelectionError(data.message || "Failed to create checkout session");
                            }
                          } catch (e: any) {
                            setPlanSelectionError(e.message || "Network error");
                          }
                          setPlanSelectionLoading(false);
                        }}
                        className="flex-1 bg-accent hover:bg-accent-hover text-white font-mono text-xs font-bold py-2.5 rounded transition-all cursor-pointer disabled:opacity-50">
                        {planSelectionLoading ? "REDIRECTING..." : "PAY WITH STRIPE"}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* API Credentials (shown after plan selection is complete) */}
            {apiClientId && apiClientSecret && !showPlanSelection && (
              <div className="mt-4 p-3 bg-panel border border-accent/40 rounded text-xs font-mono">
                <p className="text-accent font-bold mb-1 uppercase tracking-wider">API Credentials Generated</p>
                <p className="text-subtext">clientId: <span className="text-text">{apiClientId}</span></p>
                <p className="text-subtext">clientSecret: <span className="text-text">{apiClientSecret}</span></p>
                <p className="text-subtext">scope: <span className="text-text">{apiScope}</span></p>
                {apiAssignedPlan && (
                  <p className="text-[10px] text-accent mt-2">Plan: <span className="font-bold uppercase">{apiAssignedPlan}</span>. Login to start using the API.</p>
                )}
                {!apiAssignedPlan && (
                  <p className="text-[10px] text-accent mt-2">Login to start using the API.</p>
                )}
              </div>
            )}

            <div className="mt-6 pt-4 border-t border-line text-center">
              <p className="text-[10px] text-subtext/60 font-mono">
                Default: <span className="text-text">admin@example.com / admin</span>
              </p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div id="java-storage-root" className="min-h-screen bg-bg text-text font-sans flex flex-col md:grid md:grid-cols-[320px_1fr_40px] border border-line overflow-x-hidden selection:bg-accent selection:text-white">
      
      {/* 1. LEFT SIDEBAR */}
      <aside className="border-b md:border-b-0 md:border-r border-line p-6 flex flex-col justify-between bg-bg z-10">
        <div>
          {/* Logo Badge & Tagline */}
          <div className="flex items-center gap-3 mb-6">
            <span className="inline-block px-3 py-1 bg-accent/15 border border-accent text-accent rounded-full text-[10px] font-mono font-bold tracking-wider">
              JAVA CORE v2.4
            </span>
            <span className="text-xs text-subtext font-mono flex items-center gap-1">
              <CloudLightning className="w-3.5 h-3.5 text-accent animate-pulse" />
              GCS CONNECTED
            </span>
          </div>

          <h2 className="text-xl font-black tracking-tighter mb-6 uppercase text-text">
            API AUTHORIZATION
          </h2>

          {/* Credentials Display & Configuration Panel */}
          <div className="bg-panel border border-line p-4 mb-6 rounded-sm">
            <div className="flex items-center justify-between mb-3">
              <span className="text-[11px] uppercase tracking-widest text-subtext font-mono font-bold">OAuth2 Config</span>
              {accessToken ? (
                <span className="text-[10px] text-green-400 bg-green-950/40 border border-green-800 px-2 py-0.5 rounded flex items-center gap-1 font-mono font-bold">
                  <Lock className="w-2.5 h-2.5" /> AUTHORIZED
                </span>
              ) : (
                <span className="text-[10px] text-accent bg-accent/10 border border-accent/30 px-2 py-0.5 rounded flex items-center gap-1 font-mono font-bold">
                  <Unlock className="w-2.5 h-2.5" /> PUBLIC READ
                </span>
              )}
            </div>

            <form onSubmit={requestOAuthToken} className="space-y-3">
              <div>
                <label className="block text-[10px] text-subtext font-mono mb-1">CLIENT_ID</label>
                <div className="relative">
                  <Key className="absolute left-2.5 top-2.5 w-3 h-3 text-subtext" />
                  <input 
                    id="client-id-input"
                    type="text" 
                    value={clientId}
                    onChange={(e) => setClientId(e.target.value)}
                    className="w-full bg-bg border border-line rounded px-2 py-1.5 pl-8 text-xs font-mono text-text focus:outline-none focus:border-accent"
                    placeholder="e.g. client-id"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[10px] text-subtext font-mono mb-1">CLIENT_SECRET</label>
                <input 
                  id="client-secret-input"
                  type="password" 
                  value={clientSecret}
                  onChange={(e) => setClientSecret(e.target.value)}
                  className="w-full bg-bg border border-line rounded px-2 py-1.5 text-xs font-mono text-text focus:outline-none focus:border-accent"
                  placeholder="•••••••••••••••••••••"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block text-[10px] text-subtext font-mono mb-1">SCOPE</label>
                  <select 
                    id="client-scope-select"
                    value={appScope}
                    onChange={(e) => setAppScope(e.target.value)}
                    className="w-full bg-bg border border-line rounded px-2 py-1.5 text-[11px] font-mono text-text focus:outline-none focus:border-accent"
                  >
                    <option value="FULL_ACCESS">FULL_ACCESS</option>
                    <option value="READ_WRITE">READ_WRITE</option>
                    <option value="READ">READ</option>
                  </select>
                </div>
                <div>
                  <label className="block text-[10px] text-subtext font-mono mb-1">CLIENT APP</label>
                  <input 
                    id="client-appname-input"
                    type="text" 
                    value={appName}
                    onChange={(e) => setAppName(e.target.value)}
                    className="w-full bg-bg border border-line rounded px-2 py-1.5 text-[11px] font-mono text-text focus:outline-none focus:border-accent"
                    placeholder="Gateway"
                  />
                </div>
              </div>

              <button 
                id="btn-generate-token"
                type="submit" 
                className="w-full mt-2 bg-accent hover:bg-accent-hover text-white font-mono text-[11px] font-bold py-2 rounded transition-all tracking-wider flex items-center justify-center gap-1.5 cursor-pointer shadow-lg shadow-accent/10"
              >
                <Shield className="w-3.5 h-3.5" /> GENERATE BEARER TOKEN
              </button>
            </form>
          </div>

          {/* API Credentials Display */}
        {apiClientId && apiClientSecret && (
          <div className="mb-4 p-3 bg-panel border border-accent/30 rounded-sm">
            <div className="text-[10px] uppercase tracking-widest text-accent font-mono mb-2 font-bold">API Credentials</div>
            <div className="text-[10px] font-mono space-y-1">
              <div><span className="text-subtext">Client ID: </span><span className="text-text break-all">{apiClientId}</span></div>
              <div><span className="text-subtext">Secret: </span><span className="text-text break-all">{apiClientSecret.substring(0, 16)}...</span></div>
              <div><span className="text-subtext">Scope: </span><span className="text-accent font-bold">{apiScope}</span></div>
              <div><span className="text-subtext">Plan: </span><span className="text-accent font-bold uppercase">{plan}</span></div>
              <div><span className="text-subtext">Storage: </span><span className="text-text">{storageUsedKb} KB / {planLimitMb > 0 ? planLimitMb + " MB" : "∞"}</span></div>
            </div>
          </div>
        )}

        {/* Quick Authorization Helper Tokens */}
          <div className="mb-8">
            <div className="text-[10px] uppercase tracking-widest text-subtext font-mono mb-2 font-bold">Preset Authorization Keys</div>
            <div className="space-y-2">
              <button 
                id="btn-preset-full-access"
                onClick={() => injectToken("dev-bucket-access-token-9982", "FULL_ACCESS", "DevPortal-Gateway", "dev-client-id", "dev-client-secret-xyz123")}
                className={`w-full text-left font-mono text-xs p-2 rounded border border-line flex items-center justify-between transition-all ${accessToken === "dev-bucket-access-token-9982" ? "bg-accent/10 border-accent/40 text-accent" : "bg-panel hover:bg-panel-hover text-subtext"}`}
              >
                <span>🔑 dev-client-id</span>
                <span className="text-[9px] px-1 bg-accent/20 text-accent rounded font-bold">FULL_ACCESS</span>
              </button>
              
              <button 
                id="btn-preset-admin"
                onClick={() => injectToken("dev-bucket-access-token-9982", "FULL_ACCESS", "Admin", "admin-client-id", "admin-client-secret")}
                className={`w-full text-left font-mono text-xs p-2 rounded border border-line flex items-center justify-between transition-all ${apiClientId === "admin-client-id" ? "bg-accent/10 border-accent/40 text-accent" : "bg-panel hover:bg-panel-hover text-subtext"}`}
              >
                <span>🔑 admin-client-id</span>
                <span className="text-[9px] px-1 bg-accent/20 text-accent rounded font-bold">FULL_ACCESS</span>
              </button>
            </div>
          </div>

          {/* Active Methods Tracker */}
          <div className="hidden md:block">
            <div className="text-[10px] uppercase tracking-widest text-subtext font-mono mb-3 font-bold">Interactive Endpoint Router</div>
            <div className="space-y-2 font-mono">
              {[
                { label: "PUBLISH", path: "POST /api/v1/storage/upload", idx: 1 },
                { label: "READ BINARY", path: "GET /api/v1/storage/files/:id", idx: 2 },
                { label: "LIST BUCKET", path: "GET /api/v1/storage/files", idx: 3 },
                { label: "DELETE OBJECT", path: "DELETE /api/v1/storage/files/:id", idx: 4 },
              ].map((m, i) => (
                <div 
                  key={m.label}
                  onClick={() => {
                    setActiveMethodIndex(i);
                    if (i === 0) setActiveTab("publisher");
                    else setActiveTab("explorer");
                  }}
                  className={`group cursor-pointer p-2 border-l-2 transition-all flex items-center justify-between ${activeMethodIndex === i ? "border-accent bg-accent/5 text-text" : "border-line text-subtext hover:border-text"}`}
                >
                  <span className="text-xs font-bold">{m.label} <span className="text-[10px] opacity-40 font-normal">0{m.idx}</span></span>
                  <span className="text-[9px] opacity-50 group-hover:opacity-100 font-normal">{m.path}</span>
                </div>
              ))}
            </div>
          </div>
        </div>

        {/* Sidebar Footer Metrics */}
        <div className="mt-6 pt-6 border-t border-line font-mono text-xs space-y-2">
          <div className="flex justify-between">
            <span className="text-subtext">BUCKET NAME:</span>
            <span className="font-bold text-text">java-service-bucket</span>
          </div>
          <div className="flex justify-between">
            <span className="text-subtext">UPTIME STATUS:</span>
            <span className="text-green-400 font-bold flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse"></span>
              {systemUptime}
            </span>
          </div>
          {apiClientId && (
            <div className="flex justify-between">
              <span className="text-subtext text-[10px]">API CRED:</span>
              <span className="text-accent font-bold text-[10px] truncate max-w-32">{apiClientId}</span>
            </div>
          )}
          <div className="flex justify-between">
            <span className="text-subtext text-[10px]">PLAN:</span>
            <span className="text-accent font-bold text-[10px] uppercase">{plan || "FREE"}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-subtext text-[10px]">STORAGE:</span>
            <span className="text-text font-bold text-[10px]">{storageUsedKb} KB / {planLimitMb > 0 ? planLimitMb + " MB" : "∞"}</span>
          </div>
          <div className="flex justify-between items-center pt-2 border-t border-line/50">
            <span className="text-subtext text-[10px]">USER: <span className="text-accent font-bold">{authUsername}</span></span>
            <button
              onClick={handleLogout}
              className="text-[10px] text-subtext hover:text-accent transition-colors cursor-pointer uppercase tracking-wider font-bold"
            >
              Logout
            </button>
          </div>
        </div>
      </aside>

      {/* 2. MAIN WORKSPACE CONTENT */}
      <main className="flex-1 flex flex-col min-w-0 bg-bg">
        
        {/* Main Hero Header */}
        <header className="border-b border-line p-8 relative flex flex-col lg:flex-row lg:items-center lg:justify-between gap-6 overflow-hidden">
          {/* Subtle decoration */}
          <div className="absolute top-0 right-0 w-64 h-64 bg-accent/5 rounded-full filter blur-3xl pointer-events-none"></div>
          
          <div>
            <div className="text-xs text-accent font-mono tracking-widest uppercase mb-1 flex items-center gap-1.5">
              <Cpu className="w-4.5 h-4.5" /> SPRING SECURITY & GOOGLE CLOUD STORAGE INTEGRATION
            </div>
            <h1 className="text-5xl lg:text-7xl font-black tracking-tighter uppercase leading-[0.85] select-none">
              picsforyou<span className="text-accent">.cloud</span>
            </h1>
            <div className="mt-4 font-mono text-xs text-subtext flex flex-wrap items-center gap-x-4 gap-y-2">
              <span className="flex items-center gap-1 bg-panel border border-line px-2 py-1 rounded">
                <span className="w-1.5 h-1.5 bg-accent rounded-full"></span>
                API GATEWAY: ACTIVE
              </span>
              <span>HOST: <span className="text-text">picsforyou.cloud</span></span>
              <span>SIMULATION: <span className="text-text">SPRING BOOT 3.2</span></span>
              <span>PLAN: <span className="text-accent font-bold uppercase">{plan || "FREE"}</span></span>
              <span>STORAGE: <span className="text-text font-bold">{storageUsedKb} KB / {planLimitMb > 0 ? planLimitMb + " MB" : "∞"}</span></span>
            </div>
          </div>

          {/* Running endpoint dynamic console */}
          <div className="bg-[#0c0c0c] border border-line p-4 rounded-md font-mono text-xs w-full lg:w-96 shadow-2xl relative">
            <div className="absolute top-2 right-3 flex gap-1.5">
              <div className="w-2 h-2 rounded-full bg-red-500/30"></div>
              <div className="w-2 h-2 rounded-full bg-yellow-500/30"></div>
              <div className="w-2 h-2 rounded-full bg-green-500/30"></div>
            </div>
            <div className="text-subtext mb-1 text-[10px]">ACTIVE_API_CHANNEL_STREAM</div>
            <div className="flex items-center gap-2 text-text">
              <span className="text-accent font-bold">&gt;</span>
              <span className="text-green-400 font-semibold">
                {activeTab === "publisher" ? "POST" : activeTab === "plans" ? "GET" : "GET"}
              </span>
              <span>
                {activeTab === "publisher" 
                  ? "/api/v1/storage/upload" 
                  : activeTab === "plans"
                  ? "/api/v1/plans"
                  : "/api/v1/storage/files"
                }
              </span>
              <span className="w-2 h-4 bg-accent inline-block animate-pulse shrink-0"></span>
            </div>
          </div>
        </header>

        {/* Tab Navigation Menu */}
        <div className="border-b border-line px-6 bg-[#0c0c0c]/80 flex items-center justify-between">
          <div className="flex">
            <button 
              id="tab-explorer"
              onClick={() => { setActiveTab("explorer"); setActiveMethodIndex(2); }}
              className={`px-6 py-4 font-mono text-xs font-bold uppercase tracking-wider border-b-2 transition-all flex items-center gap-2 ${activeTab === "explorer" ? "border-accent text-accent bg-bg" : "border-transparent text-subtext hover:text-text"}`}
            >
              <Database className="w-4 h-4" /> BUCKET EXPLORER ({files.length})
            </button>
            <button 
              id="tab-publisher"
              onClick={() => { setActiveTab("publisher"); setActiveMethodIndex(0); }}
              className={`px-6 py-4 font-mono text-xs font-bold uppercase tracking-wider border-b-2 transition-all flex items-center gap-2 ${activeTab === "publisher" ? "border-accent text-accent bg-bg" : "border-transparent text-subtext hover:text-text"}`}
            >
              <Upload className="w-4 h-4" /> PUBLISH IMAGE
            </button>
            <button 
              id="tab-plans"
              onClick={() => setActiveTab("plans")}
              className={`px-6 py-4 font-mono text-xs font-bold uppercase tracking-wider border-b-2 transition-all flex items-center gap-2 ${activeTab === "plans" ? "border-accent text-accent bg-bg" : "border-transparent text-subtext hover:text-text"}`}
            >
              <Sparkles className="w-4 h-4" /> PLANS
            </button>

          </div>

          <div className="hidden lg:flex items-center gap-2 text-xs font-mono text-subtext">
            <span>BEARER CREDENTIAL:</span>
            <span className="font-semibold text-text max-w-40 truncate bg-panel px-2 py-0.5 rounded border border-line">
              {accessToken ? `${accessToken.substring(0, 14)}...` : "[None - Public]"}
            </span>
          </div>
        </div>

        {/* 3. CENTER ACTIVE VIEWPORT */}
        <div className="p-8 flex-1 overflow-y-auto">
          
          {/* API Error Alert Block */}
          {apiError && (
            <div className="mb-6 p-4 bg-amber-950/20 border border-amber-800/40 rounded flex items-start gap-3 text-sm text-amber-200 font-mono">
              <AlertTriangle className="w-5 h-5 text-accent shrink-0 mt-0.5" />
              <div className="flex-1">
                <div className="font-bold text-accent uppercase tracking-wider text-xs">API Error Response Stream</div>
                <div className="text-xs mt-1">
                  <strong className="text-text">Status: {apiError.status || "Connection Error"}</strong> — {apiError.message}
                </div>
                {apiError.status === 401 && (
                  <button 
                    id="btn-alert-quick-auth"
                    onClick={() => injectToken("dev-bucket-access-token-9982", "FULL_ACCESS", "DevPortal-Gateway")}
                    className="mt-2 inline-flex items-center gap-1 bg-accent/20 hover:bg-accent/40 text-accent font-bold px-2.5 py-1 rounded text-[11px] transition-all cursor-pointer border border-accent/40"
                  >
                    Quick Authorize (Inject Master Token)
                  </button>
                )}
              </div>
            </div>
          )}

          {/* VIEW A: BUCKET EXPLORER */}
          {activeTab === "explorer" && (
            <div>
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
                <div>
                  <h3 className="text-lg font-bold font-mono tracking-tight uppercase flex items-center gap-2">
                    <Database className="w-5 h-5 text-accent" /> Clouddrive Objects Store
                  </h3>
                  <p className="text-xs text-subtext mt-1">
                    Live reflection of the simulated Java Spring GCS service bucket. Public image links are serving binary streams.
                  </p>
                </div>
                
                <div className="flex items-center gap-2">
                  <button 
                    id="btn-refresh-bucket"
                    onClick={() => fetchBucketFiles()}
                    disabled={loadingFiles}
                    className="font-mono text-xs bg-panel hover:bg-panel-hover border border-line px-3 py-1.5 rounded text-text flex items-center gap-2 cursor-pointer transition-all disabled:opacity-50"
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${loadingFiles ? "animate-spin text-accent" : ""}`} /> Refresh Bucket
                  </button>
                </div>
              </div>

              {loadingFiles && files.length === 0 ? (
                <div className="border border-line border-dashed p-16 text-center font-mono text-xs text-subtext">
                  <RefreshCw className="w-8 h-8 text-accent animate-spin mx-auto mb-3" />
                  CONNECTING TO GCS FILE SYSTEM SERVICE...
                </div>
              ) : files.length === 0 ? (
                <div className="border border-line border-dashed p-16 text-center font-mono rounded bg-panel/30">
                  <ImageIcon className="w-12 h-12 text-subtext/40 mx-auto mb-4" />
                  <p className="text-sm font-bold text-text uppercase">No Cloud Assets Stored</p>
                  <p className="text-xs text-subtext mt-1 max-w-md mx-auto">
                    The cloud bucket is currently empty or you haven't authorized. Generate a Bearer token and upload your first image in the "Publish Image" tab.
                  </p>
                  <div className="mt-4 flex justify-center gap-3">
                    <button 
                      id="btn-empty-go-publish"
                      onClick={() => setActiveTab("publisher")}
                      className="bg-accent hover:bg-accent-hover text-white text-xs font-mono font-bold px-4 py-2 rounded transition-all cursor-pointer"
                    >
                      Go to Publish Tab
                    </button>
                  </div>
                </div>
              ) : (
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                  {files.map((file) => {
                    const isSvg = file.contentType === "image/svg+xml";
                    return (
                      <div 
                        key={file.id} 
                        className="bg-panel border border-line rounded-lg overflow-hidden group hover:border-accent/40 transition-all flex flex-col justify-between"
                      >
                        {/* Image Preview Container */}
                        <div className="h-48 bg-[#0c0c0c] border-b border-line relative overflow-hidden flex items-center justify-center p-4">
                          <img 
                            src={`/api/v1/storage/files/${file.id}`}
                            alt={file.name} 
                            className="max-h-full max-w-full object-contain group-hover:scale-105 transition-transform duration-300"
                            onError={(e) => {
                              (e.target as HTMLImageElement).src = `data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect width="100" height="100" fill="%23222"/><text x="50" y="55" font-family="sans-serif" font-size="10" fill="%23666" text-anchor="middle">Broken Image</text></svg>`;
                            }}
                          />
                          <div className="absolute top-2 left-2 bg-[#080808]/80 border border-line px-2 py-0.5 rounded text-[9px] font-mono font-semibold text-accent uppercase">
                            {file.contentType.split("/")[1]}
                          </div>
                        </div>

                        {/* File Details */}
                        <div className="p-4 flex-1 flex flex-col justify-between">
                          <div>
                            <div className="font-mono text-sm font-bold text-text truncate" title={file.name}>
                              {file.name}
                            </div>
                            
                            <div className="mt-2 space-y-1 text-[11px] font-mono text-subtext">
                              <div className="flex justify-between">
                                <span>SIZE:</span>
                                <span className="text-text">{formatBytes(file.size)}</span>
                              </div>
                              <div className="flex justify-between">
                                <span>OWNER APP:</span>
                                <span className="text-accent font-semibold">{file.owner}</span>
                              </div>
                              <div className="flex justify-between">
                                <span>UPLOADED:</span>
                                <span className="text-text">{new Date(file.uploadedAt).toLocaleString()}</span>
                              </div>
                            </div>
                          </div>

                          {/* Action Bar */}
                          <div className="mt-4 pt-3 border-t border-line/50 grid grid-cols-2 gap-2">
                            <button 
                              id={`btn-copy-url-${file.id}`}
                              onClick={() => copyToClipboard(window.location.origin + file.publicUrl, file.id)}
                              className="font-mono text-[10px] font-bold py-1.5 px-2 bg-[#121212] border border-line rounded hover:bg-zinc-800 text-text transition-all flex items-center justify-center gap-1 cursor-pointer"
                            >
                              {copiedUrlId === file.id ? (
                                <>
                                  <Check className="w-3.5 h-3.5 text-green-400" />
                                  COPIED!
                                </>
                              ) : (
                                <>
                                  <Copy className="w-3.5 h-3.5" />
                                  COPY API URL
                                </>
                              )}
                            </button>

                            <button 
                              id={`btn-delete-${file.id}`}
                              onClick={() => handleDeleteFile(file.id)}
                              className="font-mono text-[10px] font-bold py-1.5 px-2 bg-red-950/20 hover:bg-red-950/40 border border-red-900/40 rounded text-red-300 transition-all flex items-center justify-center gap-1 cursor-pointer"
                            >
                              <Trash2 className="w-3.5 h-3.5 text-red-400" />
                              DELETE OBJECT
                            </button>
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* VIEW B: PUBLISH IMAGE (uploader) */}
          {activeTab === "publisher" && (
            <div className="max-w-3xl mx-auto">
              <div className="mb-6">
                <h3 className="text-lg font-bold font-mono tracking-tight uppercase flex items-center gap-2">
                  <Upload className="w-5 h-5 text-accent" /> Publish Image Object
                </h3>
                <p className="text-xs text-subtext mt-1">
                  Enforce Client authorization checks while writing base64 image binary streams into the Google Cloud Storage bucket simulator.
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-5 gap-6">
                
                {/* Drag-and-drop / Select Widget */}
                <div className="md:col-span-3 space-y-4">
                  <div 
                    onDragOver={(e) => e.preventDefault()}
                    onDrop={handleDrop}
                    onClick={() => fileInputRef.current?.click()}
                    className="border-2 border-dashed border-line hover:border-accent rounded-lg p-8 text-center cursor-pointer bg-panel hover:bg-panel-hover transition-all group min-h-60 flex flex-col justify-center items-center"
                  >
                    <input 
                      id="file-selector-input"
                      type="file" 
                      ref={fileInputRef}
                      onChange={(e) => e.target.files && processSelectedFile(e.target.files[0])}
                      className="hidden" 
                      accept="image/*"
                    />
                    
                    {selectedFile ? (
                      <div className="w-full">
                        <ImageIcon className="w-10 h-10 text-accent mx-auto mb-2" />
                        <p className="font-mono text-sm font-bold text-text truncate max-w-xs mx-auto">
                          {selectedFile.name}
                        </p>
                        <p className="font-mono text-[11px] text-subtext mt-1">
                          {selectedFile.type} — {formatBytes(selectedFile.size)}
                        </p>
                        <span className="mt-3 inline-block font-mono text-[10px] bg-accent/20 border border-accent/40 text-accent px-2 py-1 rounded">
                          BINARY PRE-PROCESSED
                        </span>
                      </div>
                    ) : (
                      <div className="space-y-2">
                        <Upload className="w-10 h-10 text-subtext group-hover:text-accent mx-auto transition-colors" />
                        <p className="font-mono text-xs font-bold uppercase tracking-wider text-text">
                          DRAG & DROP IMAGE
                        </p>
                        <p className="font-mono text-[10px] text-subtext">
                          or click to browse local folders
                        </p>
                        <p className="text-[10px] text-subtext/60">
                          Supports PNG, JPEG, SVG, WebP up to 10MB
                        </p>
                      </div>
                    )}
                  </div>

                  {selectedFile && (
                    <form onSubmit={handlePublishImage} className="bg-panel border border-line p-4 rounded-md space-y-3">
                      <div>
                        <label className="block text-[11px] font-mono text-subtext uppercase tracking-widest mb-1.5 font-bold">
                          Cloud Object Target Filename
                        </label>
                        <input 
                          id="object-filename-input"
                          type="text" 
                          value={customFileName}
                          onChange={(e) => setCustomFileName(e.target.value)}
                          className="w-full bg-bg border border-line rounded px-3 py-2 text-xs font-mono text-text focus:outline-none focus:border-accent"
                          placeholder="Filename to write in GCS"
                          required
                        />
                      </div>

                      <div className="flex gap-2">
                        <button 
                          id="btn-publish-submit"
                          type="submit"
                          disabled={uploading}
                          className="flex-1 bg-accent hover:bg-accent-hover text-white font-mono text-xs font-bold py-2 px-4 rounded transition-all flex items-center justify-center gap-1.5 cursor-pointer disabled:opacity-50"
                        >
                          <CloudLightning className="w-4 h-4 animate-pulse" />
                          {uploading ? "PUBLISHING TO GCS..." : "PUBLISH TO CLOUD"}
                        </button>
                        
                        <button 
                          id="btn-publish-cancel"
                          type="button"
                          onClick={() => setSelectedFile(null)}
                          className="font-mono text-xs bg-bg hover:bg-zinc-900 border border-line px-4 py-2 rounded text-text cursor-pointer transition-all"
                        >
                          Cancel
                        </button>
                      </div>
                    </form>
                  )}
                </div>

                {/* Java API Endpoint Context panel */}
                <div className="md:col-span-2 space-y-4">
                  <div className="bg-panel border border-line p-5 rounded-lg space-y-4">
                    <h4 className="text-xs font-mono font-bold text-accent uppercase tracking-widest">
                      Spring API Controller Mapping
                    </h4>
                    
                    <div className="space-y-3 text-[11px] font-mono">
                      <div>
                        <div className="text-subtext">ANNOTATION MAPPING:</div>
                        <div className="text-text mt-0.5 font-semibold bg-bg p-1.5 rounded border border-line text-xs">
                          @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
                        </div>
                      </div>

                      <div>
                        <div className="text-subtext">AUTHORITY ROLE REQUIREMENT:</div>
                        <div className="text-text mt-0.5 font-semibold bg-bg p-1.5 rounded border border-line text-xs text-orange-400">
                          @PreAuthorize("hasAuthority('SCOPE_WRITE') or hasAuthority('SCOPE_FULL_ACCESS')")
                        </div>
                      </div>

                      <div className="border-t border-line/60 pt-3">
                        <div className="text-subtext">Active Authorization Token:</div>
                        <div className="flex items-center gap-2 mt-1">
                          <span className={`w-2 h-2 rounded-full ${accessToken ? "bg-green-500" : "bg-red-500"}`}></span>
                          <span className="text-text font-bold text-[10px]">
                            {accessToken ? "Bearer active-session-token" : "No token (401 Unauthorized expected)"}
                          </span>
                        </div>
                        {accessToken && (
                          <div className="text-[10px] text-subtext/80 mt-1 leading-relaxed">
                            Authorized with App: <strong className="text-text">{appName}</strong>. Granted scopes: <strong className="text-accent">{appScope}</strong>.
                          </div>
                        )}
                      </div>
                    </div>

                    {/* Quick tester helper */}
                    <div className="bg-[#0c0c0c] p-3 rounded text-[11px] font-mono border border-line leading-relaxed text-subtext">
                      <strong className="text-text block mb-1">💡 Sandbox Info</strong>
                      When clicking <span className="text-text font-bold">"Publish to Cloud"</span>, we transmit an HTTP POST with standard multi-part payload emulation. The underlying system stores the asset persistently in-memory inside our high-performance Node environment.
                    </div>
                  </div>
                </div>

              </div>
            </div>
          )}

          {/* VIEW C: SUBSCRIPTION PLANS */}
          {activeTab === "plans" && (
            <div className="animate-in fade-in slide-in-from-bottom-2 duration-300">
              <h2 className="text-lg font-black font-mono uppercase tracking-tight flex items-center gap-2 text-text mb-6">
                <Sparkles className="w-5 h-5 text-accent" /> Subscription Plans
              </h2>
              {planSelectionError && (
                <div className="mb-4 p-3 bg-red-950/20 border border-red-800/40 rounded text-xs text-red-200 font-mono">
                  {planSelectionError}
                </div>
              )}

              {showCustomPlanForm ? (
                <div className="max-w-lg mx-auto bg-panel border border-amber-800 rounded-lg p-6 text-center">
                  <div className="inline-block px-3 py-1 bg-amber-400/15 border border-amber-400 text-amber-400 rounded-full text-[10px] font-mono font-bold tracking-wider mb-3">
                    CUSTOM PLAN
                  </div>
                  <h3 className="text-sm font-bold font-mono text-text mb-2">
                    Describe your needs
                  </h3>
                  <p className="text-[11px] text-subtext font-mono mb-3">
                    Tell us what you need and we will contact you.
                  </p>
                  <textarea
                    value={customPlanText}
                    onChange={e => setCustomPlanText(e.target.value.slice(0, 512))}
                    maxLength={512}
                    rows={4}
                    placeholder="Describe your storage needs, expected volume, and any special requirements..."
                    className="w-full bg-bg border border-line rounded px-3 py-2 text-xs font-mono text-text focus:outline-none focus:border-amber-400 resize-none"
                  />
                  <div className="text-right text-[10px] text-subtext font-mono mt-1 mb-3">
                    {customPlanText.length}/512
                  </div>
                  <div className="flex gap-2">
                    <button type="button" onClick={() => { setShowCustomPlanForm(false); setCustomPlanText(""); setPlanSelectionError(""); }}
                      className="flex-1 bg-bg border border-line text-subtext font-mono text-xs font-bold py-2.5 rounded hover:border-text transition-all cursor-pointer">
                      BACK
                    </button>
                    <button type="button" disabled={customPlanSending || customPlanText.trim().length === 0}
                      onClick={async () => {
                        setCustomPlanSending(true);
                        setPlanSelectionError("");
                        try {
                          const res = await fetch("/api/v1/plans/custom-request", {
                            method: "POST",
                            headers: { "Content-Type": "application/json" },
                            body: JSON.stringify({ text: customPlanText })
                          });
                          const data = await res.json();
                          if (res.ok) {
                            setCustomPlanPending(true);
                            setShowCustomPlanForm(false);
                            setCustomPlanText("");
                            setPlanSelectionError("");
                          } else {
                            setPlanSelectionError(data.message || "Failed to send request");
                          }
                        } catch (e: any) {
                          setPlanSelectionError(e.message || "Network error");
                        }
                        setCustomPlanSending(false);
                      }}
                      className="flex-1 bg-amber-400 hover:bg-amber-500 text-black font-mono text-xs font-bold py-2.5 rounded transition-all cursor-pointer disabled:opacity-50">
                      {customPlanSending ? "SENDING..." : "SEND REQUEST"}
                    </button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                    {[
                      { id: "free", name: "Free", price: "€0", limit: "30 MB", desc: "Free plan with 30 MB storage limit. Delete files to free up space.", color: "text-zinc-400", border: "border-zinc-800", bg: "bg-zinc-950/30" },
                      { id: "base", name: "Base", price: "€5.99", limit: "5 GB", desc: "5 GB storage for €5.99/month. Perfect for personal use.", color: "text-blue-400", border: "border-blue-800", bg: "bg-blue-950/20" },
                      { id: "professional", name: "Professional", price: "€20", limit: "20 GB", desc: "20 GB storage for €20.00/month. For professionals.", color: "text-purple-400", border: "border-purple-800", bg: "bg-purple-950/20" },
                      { id: "custom", name: "Custom", price: "—", limit: "∞", desc: "Custom plan. Contact us for personalized solutions.", color: "text-amber-400", border: "border-amber-800", bg: "bg-amber-950/20" },
                    ].map(p => {
                      const isPendingCustom = customPlanPending && p.id !== "custom";
                      return (
                        <div key={p.id} className={`bg-panel border ${p.border} rounded-lg p-6 flex flex-col justify-between ${plan === p.id ? "ring-2 ring-accent" : ""}`}>
                          <div>
                            <div className={`text-[10px] uppercase tracking-widest font-mono font-bold ${p.color} mb-1`}>{p.name}</div>
                            <div className="text-3xl font-black font-mono text-text mt-2">{p.price}<span className="text-xs text-subtext font-normal">/mo</span></div>
                            <div className="text-xs font-mono text-subtext mt-1">Up to <span className="text-text font-bold">{p.limit}</span></div>
                            <div className="text-[11px] text-subtext mt-4 leading-relaxed">{p.desc}</div>
                          </div>
                          {isPendingCustom ? (
                            <div className="mt-6 w-full font-mono text-xs font-bold py-2.5 rounded text-center bg-panel border border-amber-800/40 text-amber-400 cursor-default">
                              Pending response
                            </div>
                          ) : (
                            <button
                              onClick={async () => {
                                if (p.id === "custom") {
                                  setShowCustomPlanForm(true);
                                  return;
                                }
                                const res = await fetch("/api/v1/plans/select", {
                                  method: "POST",
                                  headers: { "Content-Type": "application/json" },
                                  body: JSON.stringify({ planId: p.id })
                                });
                                if (res.ok) {
                                  const data = await res.json();
                                  if (data.status === "UPGRADE_REQUIRED") {
                                    setPlanSelectionError("");
                                    setSelectedPlanForPayment(p.id);
                                    setPlanDiffCents(data.diffCents);
                                    setUpgradeEmail(data.email || authEmailInput || "");
                                    setShowPlanUpgrade(true);
                                  } else if (data.status === "PENDING_RESPONSE") {
                                    setPlanSelectionError("Pending response – You have a pending custom plan request. Wait for our team to contact you.");
                                  } else {
                                    setPlan(data.plan);
                                    setPlanLimitMb(data.planLimitMb);
                                  }
                                } else {
                                  const err = await res.json();
                                  setPlanSelectionError(err.message || "Failed to select plan");
                                }
                              }}
                              disabled={plan === p.id}
                              className={`mt-6 w-full font-mono text-xs font-bold py-2.5 rounded transition-all cursor-pointer ${plan === p.id ? "bg-accent/20 text-accent border border-accent/40 cursor-default" : "bg-accent hover:bg-accent-hover text-white"}`}
                            >
                              {plan === p.id ? "✓ CURRENT PLAN" : "SELECT PLAN"}
                            </button>
                          )}
                        </div>
                      );
                    })}
                  </div>

                  {/* Upgrade modal for plan changes requiring payment */}
                  {showPlanUpgrade && (
                    <div className="mt-6 max-w-lg mx-auto bg-panel border border-line rounded-lg p-6 text-center">
                      <div className="inline-block px-3 py-1 bg-accent/15 border border-accent text-accent rounded-full text-[10px] font-mono font-bold tracking-wider mb-3">
                        UPGRADE
                      </div>
                      <h3 className="text-sm font-bold font-mono text-text mb-2">
                        {selectedPlanForPayment === "base" ? "Base Plan — €5.99/mo" : "Professional Plan — €20.00/mo"}
                      </h3>
                      <p className="text-xs text-subtext mb-4">
                        Pay the difference of <strong className="text-text">€{(planDiffCents / 100).toFixed(2)}</strong> to upgrade.
                      </p>
                      {planSelectionError && (
                        <div className="p-3 bg-red-950/20 border border-red-800/40 rounded text-xs text-red-200 font-mono mb-3">
                          {planSelectionError}
                        </div>
                      )}
                      <div className="flex gap-2">
                        <button type="button" onClick={() => { setShowPlanUpgrade(false); setPlanSelectionError(""); }}
                          className="flex-1 bg-bg border border-line text-subtext font-mono text-xs font-bold py-2.5 rounded hover:border-text transition-all cursor-pointer">
                          CANCEL
                        </button>
                        <button type="button" disabled={planSelectionLoading}
                          onClick={async () => {
                            setPlanSelectionLoading(true);
                            setPlanSelectionError("");
                            try {
                              const email = upgradeEmail || authEmailInput || "";
                              const res = await fetch("/api/v1/plans/create-checkout", {
                                method: "POST",
                                headers: { "Content-Type": "application/json" },
                                body: JSON.stringify({ email, planId: selectedPlanForPayment, diffCents: String(planDiffCents) })
                              });
                              const data = await res.json();
                              if (res.ok && data.url) {
                                window.location.href = data.url;
                              } else {
                                setPlanSelectionError(data.message || "Failed to create checkout session");
                              }
                            } catch (e: any) {
                              setPlanSelectionError(e.message || "Network error");
                            }
                            setPlanSelectionLoading(false);
                          }}
                          className="flex-1 bg-accent hover:bg-accent-hover text-white font-mono text-xs font-bold py-2.5 rounded transition-all cursor-pointer disabled:opacity-50">
                          {planSelectionLoading ? "REDIRECTING..." : "PAY €" + (planDiffCents / 100).toFixed(2)}
                        </button>
                      </div>
                    </div>
                  )}
                </>
              )}
            </div>
          )}

        </div>

        {/* 4. FOOTER DYNAMIC HTTP REQUEST CONSOLE LOGS */}
        <footer className="border-t border-line bg-[#040404] p-6 z-10 font-mono">
          <div className="flex items-center justify-between mb-4">
            <h4 className="text-xs font-bold tracking-widest uppercase flex items-center gap-2 text-text">
              <TerminalIcon className="w-4 h-4 text-accent" /> Playback Web console logs
            </h4>
            
            <div className="flex items-center gap-2 text-xs">
              <button 
                id="btn-clear-logs"
                onClick={clearLogs}
                className="text-subtext hover:text-text px-2.5 py-1 rounded bg-panel border border-line hover:border-subtext transition-all cursor-pointer"
              >
                Clear Console
              </button>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
            
            {/* Logs List Pane */}
            <div className="lg:col-span-5 bg-bg border border-line rounded p-2 max-h-56 overflow-y-auto space-y-1">
              {logs.length === 0 ? (
                <div className="text-[11px] text-subtext/50 p-8 text-center">
                  Await trigger actions... (Tokens, file upload/list endpoints are tracked here)
                </div>
              ) : (
                logs.map((log) => {
                  const isSuccess = log.status >= 200 && log.status < 300;
                  const isAuthError = log.status === 401 || log.status === 403;
                  return (
                    <div 
                      key={log.id}
                      onClick={() => setSelectedLog(log)}
                      className={`p-2 rounded cursor-pointer text-xs transition-all flex items-center justify-between ${selectedLog?.id === log.id ? "bg-accent/10 border border-accent/40 text-text" : "border border-transparent hover:bg-panel text-subtext"}`}
                    >
                      <div className="flex items-center gap-2 truncate">
                        <span className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${
                          log.method === "GET" ? "bg-cyan-950/50 text-cyan-400 border border-cyan-900" :
                          log.method === "POST" ? "bg-emerald-950/50 text-emerald-400 border border-emerald-900" :
                          "bg-rose-950/50 text-rose-400 border border-rose-900"
                        }`}>
                          {log.method}
                        </span>
                        <span className="font-semibold truncate text-text">{log.url}</span>
                      </div>
                      
                      <div className="flex items-center gap-2">
                        <span className="text-[10px] text-subtext/60">{log.timestamp}</span>
                        <span className={`font-bold text-[11px] ${
                          isSuccess ? "text-green-400" : isAuthError ? "text-amber-400 animate-pulse" : "text-rose-400"
                        }`}>
                          {log.status}
                        </span>
                      </div>
                    </div>
                  );
                })
              )}
            </div>

            {/* Selected Log Detail Inspector Pane */}
            <div className="lg:col-span-7 bg-bg border border-line rounded p-4 max-h-56 overflow-y-auto">
              {selectedLog ? (
                <div className="space-y-3 text-[11px]">
                  <div className="flex items-center justify-between border-b border-line/50 pb-2">
                    <div>
                      <span className="text-subtext uppercase">ENDPOINT STREAM:</span>
                      <strong className="text-text ml-2">{selectedLog.method} {selectedLog.url}</strong>
                    </div>
                    <div>
                      <span className="text-subtext">STATUS:</span>
                      <span className={`ml-1 font-bold ${selectedLog.status >= 200 && selectedLog.status < 300 ? "text-green-400" : "text-accent"}`}>
                        {selectedLog.status} {selectedLog.statusText}
                      </span>
                    </div>
                  </div>

                  <div>
                    <span className="text-subtext block mb-1">REQUEST HEADERS:</span>
                    <pre className="p-2 bg-panel rounded text-[10px] text-text whitespace-pre-wrap overflow-x-auto border border-line/40">
                      {JSON.stringify(selectedLog.requestHeaders, null, 2)}
                    </pre>
                  </div>

                  {selectedLog.requestBody && (
                    <div>
                      <span className="text-subtext block mb-1">REQUEST PAYLOAD BODY:</span>
                      <pre className="p-2 bg-panel rounded text-[10px] text-text whitespace-pre-wrap overflow-x-auto border border-line/40 max-h-24">
                        {JSON.stringify(selectedLog.requestBody, null, 2)}
                      </pre>
                    </div>
                  )}

                  <div>
                    <span className="text-subtext block mb-1">RESPONSE BODY JSON:</span>
                    <pre className="p-2 bg-panel rounded text-[10px] text-green-400 whitespace-pre-wrap overflow-x-auto border border-line/40 max-h-24">
                      {JSON.stringify(selectedLog.responseBody, null, 2)}
                    </pre>
                  </div>
                </div>
              ) : (
                <div className="text-center text-subtext/40 py-12 text-xs">
                  Select a live log sequence to inspect headers, request schemas and GCS response stream outputs.
                </div>
              )}
            </div>

          </div>
        </footer>

      </main>

      {/* 3. RIGHT VERTICAL RAIL */}
      <aside className="hidden md:flex border-l border-line p-4 py-8 bg-bg flex-col items-center justify-between text-[10px] text-subtext tracking-[4px] uppercase select-none font-mono">
        <div className="rotate-90 origin-left translate-x-2 whitespace-nowrap">
          SYSTEM_STATUS_INTEGRITY_VERIFIED //
        </div>
        <div className="rotate-90 origin-left translate-x-2 whitespace-nowrap text-accent font-bold">
          CLOUD_BUCKET_ACTIVE //
        </div>
        <div className="rotate-90 origin-left translate-x-2 whitespace-nowrap">
          API_VERSION_2.4.0
        </div>
      </aside>

    </div>
  );
}
