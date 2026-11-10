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

  const targetOwnerCheck = await fetch(
    `${url}/rest/v1/nexa_admin_roles?user_id=eq.${encodeURIComponent(targetId)}&role=eq.OWNER&select=user_id&limit=1`,
    { headers: secretHeaders(secret) },
  );
  if (!targetOwnerCheck.ok) return reply(500, { error: "target_role_check_failed" });
  const targetOwnerRoles = await targetOwnerCheck.json();
  if (Array.isArray(targetOwnerRoles) && targetOwnerRoles.length > 0) {
    return reply(403, { error: "cannot_delete_owner_account" });
  }

  // Persist an owner audit record before destructive storage/auth operations.
  const auditInsert = await fetch(`${url}/rest/v1/nexa_owner_audit_events`, {
    method: "POST",
    headers: new Headers([...secretHeaders(secret).entries(), ["Prefer", "return=representation"]]),
    body: JSON.stringify({
      actor_id: actorId,
      action: "USER_DELETE_STARTED",
      entity_type: "USER",
      entity_id: targetId,
      details: { phase: "started", storage_cleanup_verified: false },
    }),
  });
  if (!auditInsert.ok) return reply(500, { error: "audit_record_failed_before_deletion" });
  const auditRows = await auditInsert.json();
  const auditId = Array.isArray(auditRows) && typeof auditRows[0]?.id === "string" ? auditRows[0].id : "";
  if (!auditId) return reply(500, { error: "audit_record_id_missing" });

  // Recursively inventory files in the user's folder. Storage list() returns
  // folders as entries too; deleting a folder name is not equivalent to deleting
  // the files nested inside it.
  const buckets = [
    "post-media", "story-media", "profile-media", "message-media",
    "clips-media", "clip-covers", "nexa-pulse", "nexa-pulse-video",
  ];
  let removedObjects = 0;

  async function listUserFiles(bucket: string, prefix: string, depth = 0): Promise<string[]> {
    if (depth > 20) throw new Error("storage_folder_depth_exceeded");
    const files: string[] = [];
    for (let offset = 0; ; offset += 1000) {
      const listed = await fetch(
        `${url}/storage/v1/object/list/${encodeURIComponent(bucket)}`,
        {
          method: "POST",
          headers: secretHeaders(secret),
          body: JSON.stringify({
            prefix,
            limit: 1000,
            offset,
            sortBy: { column: "name", order: "asc" },
          }),
        },
      );
      if (!listed.ok) throw new Error("storage_inventory_failed");
      const rows = await listed.json();
      if (!Array.isArray(rows) || rows.length === 0) break;
      for (const row of rows) {
        if (typeof row?.name !== "string" || row.name.length === 0) continue;
        const path = `${prefix}/${row.name}`;
        // Supabase Storage represents folders without an object id/metadata.
        if (row.id == null && row.metadata == null) {
          files.push(...await listUserFiles(bucket, path, depth + 1));
        } else {
          files.push(path);
        }
      }
      if (rows.length < 1000) break;
    }
    return files;
  }

  for (const bucket of buckets) {
    let names: string[];
    try {
      names = await listUserFiles(bucket, targetId);
    } catch (error) {
      return reply(502, {
        error: error instanceof Error ? error.message : "storage_inventory_failed",
        bucket,
      });
    }
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

    // Verify recursively; checking only the first directory level misses nested media.
    let remainingFiles: string[];
    try {
      remainingFiles = await listUserFiles(bucket, targetId);
    } catch (error) {
      return reply(502, {
        error: error instanceof Error ? error.message : "storage_verification_failed",
        bucket,
      });
    }
    if (remainingFiles.length > 0) {
      return reply(502, {
        error: "storage_objects_remaining",
        bucket,
        remaining_count: remainingFiles.length,
      });
    }
  }

  const deleted = await fetch(`${url}/auth/v1/admin/users/${encodeURIComponent(targetId)}`, {
    method: "DELETE",
    headers: secretHeaders(secret),
  });
  if (deleted.status === 404) {
    // A repeated tap/retry after a successful deletion must not be reported as
    // a failure. Only treat it as idempotent success when an earlier completed
    // deletion audit exists for this exact target.
    const priorDeletion = await fetch(
      `${url}/rest/v1/nexa_owner_audit_events?entity_id=eq.${encodeURIComponent(targetId)}&action=eq.USER_DELETED&select=id&order=created_at.desc&limit=1`,
      { headers: secretHeaders(secret) },
    );
    if (!priorDeletion.ok) return reply(500, { error: "deletion_state_check_failed" });
    const priorRows = await priorDeletion.json();
    if (!Array.isArray(priorRows) || priorRows.length === 0) {
      return reply(404, { error: "user_not_found" });
    }

    const retryAuditUpdate = await fetch(
      `${url}/rest/v1/nexa_owner_audit_events?id=eq.${encodeURIComponent(auditId)}`,
      {
        method: "PATCH",
        headers: secretHeaders(secret),
        body: JSON.stringify({
          action: "USER_DELETED",
          details: {
            phase: "already_completed",
            storage_cleanup_verified: true,
            removed_storage_objects: 0,
            already_deleted: true,
          },
        }),
      },
    );
    if (!retryAuditUpdate.ok) return reply(500, { error: "audit_record_update_failed" });
    return reply(200, {
      success: true,
      already_deleted: true,
      user_id: targetId,
      removed_storage_objects: 0,
      audit_recorded: true,
    });
  }
  if (!deleted.ok) return reply(500, { error: "delete_failed" });

  const auditUpdate = await fetch(
    `${url}/rest/v1/nexa_owner_audit_events?id=eq.${encodeURIComponent(auditId)}`,
    {
      method: "PATCH",
      headers: secretHeaders(secret),
      body: JSON.stringify({
        action: "USER_DELETED",
        details: { phase: "completed", storage_cleanup_verified: true, removed_storage_objects: removedObjects },
      }),
    },
  );
  return reply(200, {
    success: true,
    user_id: targetId,
    removed_storage_objects: removedObjects,
    audit_recorded: auditUpdate.ok,
  });
});
