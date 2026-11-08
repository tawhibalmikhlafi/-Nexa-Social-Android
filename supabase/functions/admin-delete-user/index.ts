import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const reply = (status: number, value: Record<string, unknown>) =>
  new Response(JSON.stringify(value), { status, headers: { ...cors, "Content-Type": "application/json" } });

function secretHeaders(secret: string) {
  const h = new Headers({ apikey: secret });
  if (!secret.startsWith("sb_secret_")) h.set("Authorization", `Bearer ${secret}`);
  h.set("Content-Type", "application/json");
  return h;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return reply(405, { error: "method_not_allowed" });

  const url = Deno.env.get("SUPABASE_URL")?.replace(/\/$/, "");
  const anon = Deno.env.get("SUPABASE_ANON_KEY");
  const secret = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SUPABASE_SECRET_KEY");
  if (!url || !anon || !secret) return reply(500, { error: "server_not_configured" });

  const token = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!token) return reply(401, { error: "not_authenticated" });

  const who = await fetch(`${url}/auth/v1/user`, {
    headers: { apikey: anon, Authorization: `Bearer ${token}` },
  });
  if (!who.ok) return reply(401, { error: "invalid_session" });
  const actor = await who.json();
  const actorId = typeof actor?.id === "string" ? actor.id : "";
  if (!actorId) return reply(401, { error: "invalid_session" });

  const adminCheck = await fetch(
    `${url}/rest/v1/nexa_admin_roles?user_id=eq.${encodeURIComponent(actorId)}&role=eq.OWNER&select=user_id&limit=1`,
    { headers: secretHeaders(secret) },
  );
  if (!adminCheck.ok) return reply(500, { error: "authorization_check_failed" });
  const roles = await adminCheck.json();
  if (!Array.isArray(roles) || roles.length !== 1) return reply(403, { error: "owner_required" });

  let payload: { user_id?: unknown; confirmation?: unknown };
  try { payload = await req.json(); } catch { return reply(400, { error: "invalid_json" }); }
  const targetId = typeof payload.user_id === "string" ? payload.user_id : "";
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(targetId)) {
    return reply(400, { error: "invalid_user_id" });
  }
  if (targetId === actorId) return reply(400, { error: "cannot_delete_owner" });
  if (payload.confirmation !== "DELETE_ACCOUNT") return reply(400, { error: "confirmation_required" });

  // Collect and delete every object stored under the user's UUID folder before
  // deleting the Auth identity. All Nexa media upload policies use this folder layout.
  const buckets = [
    "post-media", "story-media", "profile-media", "message-media",
    "clips-media", "clip-covers", "nexa-pulse", "nexa-pulse-video",
  ];
  let removedObjects = 0;
  for (const bucket of buckets) {
    const listed = await fetch(
      `${url}/storage/v1/object/list/${encodeURIComponent(bucket)}`,
      {
        method: "POST",
        headers: secretHeaders(secret),
        body: JSON.stringify({ prefix: targetId, limit: 1000, offset: 0, sortBy: { column: "name", order: "asc" } }),
      },
    );
    if (!listed.ok) {
      return reply(502, { error: "storage_inventory_failed", bucket });
    }
    const rows = await listed.json();
    const names = Array.isArray(rows)
      ? rows.map((row: { name?: unknown; id?: unknown }) => row.name)
          .filter((name): name is string => typeof name === "string" && name.length > 0)
          .map((name) => targetId + "/" + name)
      : [];
    for (let offset = 0; offset < names.length; offset += 100) {
      const batch = names.slice(offset, offset + 100);
      if (batch.length === 0) continue;
      const removed = await fetch(`${url}/storage/v1/object/${encodeURIComponent(bucket)}`, {
        method: "DELETE",
        headers: secretHeaders(secret),
        body: JSON.stringify({ prefixes: batch }),
      });
      if (!removed.ok) {
        return reply(502, { error: "storage_cleanup_failed", bucket, remaining: names.length - offset });
      }
      removedObjects += batch.length;
    }
  }

  const deleted = await fetch(`${url}/auth/v1/admin/users/${encodeURIComponent(targetId)}`, {
    method: "DELETE",
    headers: secretHeaders(secret),
  });
  if (deleted.status === 404) return reply(404, { error: "user_not_found" });
  if (!deleted.ok) return reply(500, { error: "delete_failed" });
  return reply(200, { success: true, user_id: targetId, removed_storage_objects: removedObjects });
});
