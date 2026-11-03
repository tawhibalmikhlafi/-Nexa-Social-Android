import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * Nexa · revoke-session Edge Function
 *
 * Supabase Auth does not expose a public endpoint for revoking one specific session. This function
 * is the supported alternative: it verifies the caller's access token, then asks the
 * service-role-only `admin_revoke_session` RPC to delete a session only when it belongs to the
 * verified user. The service_role key never leaves the server.
 *
 * Contract:
 *   POST { sessionId: string }
 *   200 { status: "revoked" }
 *   4xx { error: "NOT_AUTHENTICATED" | "INVALID_SESSION" | "SESSION_NOT_FOUND" }
 */

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return json({ error: "METHOD_NOT_ALLOWED" }, 405);
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!supabaseUrl || !serviceKey) {
    return json({ error: "NOT_CONFIGURED" }, 500);
  }

  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) {
    return json({ error: "NOT_AUTHENTICATED" }, 401);
  }
  const accessToken = authHeader.replace(/^Bearer\s+/i, "");

  const admin = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: userData, error: userError } = await admin.auth.getUser(accessToken);
  if (userError || !userData.user) {
    return json({ error: "NOT_AUTHENTICATED" }, 401);
  }
  const userId = userData.user.id;

  let payload: { sessionId?: unknown };
  try {
    payload = await req.json();
  } catch {
    return json({ error: "INVALID_SESSION" }, 400);
  }

  const sessionId =
    typeof payload.sessionId === "string" ? payload.sessionId.trim() : "";
  if (!UUID_PATTERN.test(sessionId)) {
    return json({ error: "INVALID_SESSION" }, 400);
  }

  // Ownership is enforced by the RPC: the delete matches both the verified user id and session id.
  const { data: revoked, error: revokeError } = await admin.rpc(
    "admin_revoke_session",
    {
      target_user_id: userId,
      target_session_id: sessionId,
    },
  );

  if (revokeError) {
    return json({ error: "REVOKE_FAILED" }, 500);
  }
  if (!revoked) {
    return json({ error: "SESSION_NOT_FOUND" }, 404);
  }

  return json({ status: "revoked" });
});
