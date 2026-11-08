import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function keyFromJsonEnv(name: string): string | null {
  const raw = Deno.env.get(name);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    return typeof parsed?.default === "string" ? parsed.default : null;
  } catch {
    return null;
  }
}

function jsonResponse(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}

function adminHeaders(secretKey: string, contentType = false): Headers {
  const headers = new Headers({ apikey: secretKey });
  // Legacy service_role keys are JWTs. New sb_secret keys must be sent as apikey only.
  if (!secretKey.startsWith("sb_secret_")) {
    headers.set("Authorization", `Bearer ${secretKey}`);
  }
  if (contentType) headers.set("Content-Type", "application/json");
  return headers;
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }
  if (req.method !== "POST") {
    return jsonResponse(405, { error: "method_not_allowed" });
  }

  const supabaseUrl = Deno.env.get("SUPABASE_URL");
  const publicKey =
    Deno.env.get("SUPABASE_ANON_KEY") ??
    keyFromJsonEnv("SUPABASE_PUBLISHABLE_KEYS");
  const secretKey =
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ??
    keyFromJsonEnv("SUPABASE_SECRET_KEYS");

  if (!supabaseUrl || !publicKey || !secretKey) {
    return jsonResponse(500, { error: "server_not_configured" });
  }

  const authorization = req.headers.get("Authorization") ?? "";
  const accessToken = authorization.startsWith("Bearer ")
    ? authorization.slice(7).trim()
    : "";
  if (!accessToken) return jsonResponse(401, { error: "not_authenticated" });

  const baseUrl = supabaseUrl.replace(/\/$/, "");
  const userResponse = await fetch(`${baseUrl}/auth/v1/user`, {
    headers: {
      apikey: publicKey,
      Authorization: `Bearer ${accessToken}`,
    },
  });
  if (!userResponse.ok) return jsonResponse(401, { error: "invalid_session" });

  const user = await userResponse.json();
  const userId = typeof user?.id === "string" ? user.id : "";
  if (!userId) return jsonResponse(401, { error: "invalid_session" });

  // This RPC returns only Storage objects owned by auth.uid(); no user ID is accepted from clients.
  const objects: Array<{ bucket_id: string; name: string }> = [];
  for (let offset = 0; ; offset += 1000) {
    const page = await fetch(
      `${baseUrl}/rest/v1/rpc/get_my_storage_objects_for_deletion`,
      {
        method: "POST",
        headers: {
          apikey: publicKey,
          Authorization: `Bearer ${accessToken}`,
          "Content-Type": "application/json",
          "Range-Unit": "items",
          Range: `${offset}-${offset + 999}`,
        },
        body: "{}",
      },
    );
    if (!page.ok) return jsonResponse(500, { error: "could_not_list_account_files" });
    const rows = await page.json();
    if (!Array.isArray(rows)) return jsonResponse(500, { error: "invalid_storage_listing" });
    objects.push(...rows);
    if (rows.length < 1000) break;
  }

  // Storage objects are removed through the Storage API, never by deleting storage.objects rows.
  const byBucket = new Map<string, string[]>();
  for (const object of objects) {
    if (typeof object?.bucket_id !== "string" || typeof object?.name !== "string") {
      return jsonResponse(500, { error: "invalid_storage_object" });
    }
    const paths = byBucket.get(object.bucket_id) ?? [];
    paths.push(object.name);
    byBucket.set(object.bucket_id, paths);
  }

  for (const [bucket, paths] of byBucket) {
    for (let index = 0; index < paths.length; index += 1000) {
      const removeResponse = await fetch(
        `${baseUrl}/storage/v1/object/${encodeURIComponent(bucket)}`,
        {
          method: "DELETE",
          headers: adminHeaders(secretKey, true),
          body: JSON.stringify({ prefixes: paths.slice(index, index + 1000) }),
        },
      );
      if (!removeResponse.ok) return jsonResponse(500, { error: "could_not_delete_account_files" });
    }
  }

  // Keep audit records but detach their non-cascading references to this account.
  const cleanupResponse = await fetch(
    `${baseUrl}/rest/v1/rpc/prepare_my_account_deletion`,
    {
      method: "POST",
      headers: {
        apikey: publicKey,
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: "{}",
    },
  );
  if (!cleanupResponse.ok) {
    return jsonResponse(500, { error: "could_not_prepare_account_deletion" });
  }

  // Server-only permanent Auth deletion; relational rows cascade from auth.users/profiles.
  const deleteResponse = await fetch(
    `${baseUrl}/auth/v1/admin/users/${encodeURIComponent(userId)}`,
    { method: "DELETE", headers: adminHeaders(secretKey) },
  );
  if (!deleteResponse.ok) return jsonResponse(500, { error: "could_not_delete_account" });

  return jsonResponse(200, { success: true });
});
