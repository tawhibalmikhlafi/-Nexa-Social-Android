import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * Nexa · join-group Edge Function
 *
 * Alternative join entry point for clients that prefer HTTP over RPC. It mirrors
 * the rules enforced by the SQL RPC request_join_group: membership, bans,
 * privacy and require_join_approval are all decided server-side, never by the
 * client. The Android app currently calls the RPC directly.
 *
 * Contract:
 *   POST { groupId: string, message?: string }
 *   200 { status: "joined" | "pending_approval" | "already_member" }
 *   4xx { error: "NOT_AUTHENTICATED" | "GROUP_NOT_FOUND" | "BANNED" | ... }
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

  const admin = createClient(supabaseUrl, serviceKey, {
    auth: { persistSession: false, autoRefreshToken: false },
  });

  const { data: userData, error: userError } = await admin.auth.getUser(
    authHeader.replace(/^Bearer\s+/i, ""),
  );
  if (userError || !userData.user) {
    return json({ error: "NOT_AUTHENTICATED" }, 401);
  }
  const userId = userData.user.id;

  let payload: { groupId?: unknown; message?: unknown };
  try {
    payload = await req.json();
  } catch {
    return json({ error: "INVALID_BODY" }, 400);
  }

  const groupId = typeof payload.groupId === "string" ? payload.groupId.trim() : "";
  if (!UUID_PATTERN.test(groupId)) return json({ error: "INVALID_GROUP" }, 400);

  const message = typeof payload.message === "string"
    ? payload.message.trim().slice(0, 1000) || null
    : null;

  // Existing membership (active members and banned users are both stored here).
  const { data: member } = await admin
    .from("group_members")
    .select("status")
    .eq("group_id", groupId)
    .eq("user_id", userId)
    .maybeSingle();

  if (member?.status === "active") {
    return json({ status: "already_member" });
  }
  if (member?.status === "banned") {
    return json({ error: "BANNED" }, 403);
  }

  const { data: group, error: groupError } = await admin
    .from("groups")
    .select("privacy, require_join_approval, is_archived")
    .eq("id", groupId)
    .maybeSingle();

  if (groupError || !group) {
    return json({ error: "GROUP_NOT_FOUND" }, 404);
  }
  if (group.is_archived) {
    return json({ error: "GROUP_ARCHIVED" }, 409);
  }

  if (group.privacy === "public" && !group.require_join_approval) {
    const { error } = await admin
      .from("group_members")
      .upsert(
        { group_id: groupId, user_id: userId, role: "member", status: "active" },
        { onConflict: "group_id,user_id" },
      );
    if (error) return json({ error: "JOIN_FAILED" }, 500);
    return json({ status: "joined" });
  }

  const { error: requestError } = await admin
    .from("group_join_requests")
    .upsert(
      {
        group_id: groupId,
        user_id: userId,
        message,
        status: "pending",
        reviewed_by: null,
        reviewed_at: null,
        created_at: new Date().toISOString(),
      },
      { onConflict: "group_id,user_id" },
    );
  if (requestError) return json({ error: "REQUEST_FAILED" }, 500);

  return json({ status: "pending_approval" });
});
