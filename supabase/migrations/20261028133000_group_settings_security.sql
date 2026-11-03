-- ═══════════════════════════════════════════════════════════════════════════
-- Group settings security follow-up.
--
-- Hardens the administrative RPCs added by the groups expansion:
--   * set_group_archived          — validates the caller and the group.
--   * group_pending_requests_count — owner/admin only.
--   * ensure_group_slug            — owner/admin only, atomic with retry.
--   * group_notification_prefs     — table + guarded upsert RPC.
--
-- Every function is SECURITY DEFINER with an empty search_path and fully
-- qualified object names, so a caller cannot shadow a table or function.
-- This migration only defines objects; it does not run any data change.
-- ═══════════════════════════════════════════════════════════════════════════

-- ── 1) Archive guard ──────────────────────────────────────────────────────
-- The previous definition updated the row even when the group did not exist and
-- did not take a row lock, so two concurrent calls could interleave their log
-- entries. The role policy stays the same: owners and admins may archive.
create or replace function public.set_group_archived(
    p_group_id uuid,
    p_archived boolean
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_uid uuid := auth.uid();
    v_role text;
    v_group_id uuid;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    select g.id into v_group_id
      from public.groups as g
     where g.id = p_group_id
     for update;

    if v_group_id is null then
        raise exception 'GROUP_NOT_FOUND';
    end if;

    select gm.role::text into v_role
      from public.group_members as gm
     where gm.group_id = p_group_id
       and gm.user_id = v_uid
       and gm.status = 'active';

    if v_role is null or v_role not in ('owner', 'admin') then
        raise exception 'NOT_AUTHORIZED';
    end if;

    update public.groups
       set is_archived = coalesce(p_archived, false),
           updated_at = now()
     where id = p_group_id;

    insert into public.group_moderation_log (group_id, actor_id, action, metadata)
    values (
        p_group_id,
        v_uid,
        case when coalesce(p_archived, false) then 'archive' else 'unarchive' end,
        jsonb_build_object('is_archived', coalesce(p_archived, false))
    );
end;
$function$;

revoke all on function public.set_group_archived(uuid, boolean) from public, anon;
grant execute on function public.set_group_archived(uuid, boolean) to authenticated;

-- ── 2) Pending join-request counter ───────────────────────────────────────
-- Only owners and admins may learn how many requests are waiting. This mirrors
-- the visibility granted by the join_req_select_own_or_admin policy.
create or replace function public.group_pending_requests_count(
    p_group_id uuid
)
returns integer
language plpgsql
stable
security definer
set search_path = ''
as $function$
declare
    v_uid uuid := auth.uid();
    v_role text;
    v_count integer;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    if not exists (
        select 1 from public.groups as g where g.id = p_group_id
    ) then
        raise exception 'GROUP_NOT_FOUND';
    end if;

    select gm.role::text into v_role
      from public.group_members as gm
     where gm.group_id = p_group_id
       and gm.user_id = v_uid
       and gm.status = 'active';

    if v_role is null or v_role not in ('owner', 'admin') then
        raise exception 'NOT_AUTHORIZED';
    end if;

    select count(*)::integer into v_count
      from public.group_join_requests as jr
     where jr.group_id = p_group_id
       and jr.status = 'pending';

    return coalesce(v_count, 0);
end;
$function$;

revoke all on function public.group_pending_requests_count(uuid) from public, anon;
grant execute on function public.group_pending_requests_count(uuid) to authenticated;

-- ── 3) Slug generation ────────────────────────────────────────────────────
-- idx_groups_slug is a partial unique index on non-null slugs, so retrying on
-- unique_violation is safe. The row lock serialises concurrent first calls for
-- the same group; the unique index protects across groups.
create or replace function public.ensure_group_slug(
    p_group_id uuid
)
returns text
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_uid uuid := auth.uid();
    v_role text;
    v_slug text;
    v_attempt integer := 0;
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    select g.slug into v_slug
      from public.groups as g
     where g.id = p_group_id
     for update;

    if not found then
        raise exception 'GROUP_NOT_FOUND';
    end if;

    select gm.role::text into v_role
      from public.group_members as gm
     where gm.group_id = p_group_id
       and gm.user_id = v_uid
       and gm.status = 'active';

    if v_role is null or v_role not in ('owner', 'admin') then
        raise exception 'NOT_AUTHORIZED';
    end if;

    if nullif(trim(v_slug), '') is not null then
        return v_slug;
    end if;

    loop
        v_attempt := v_attempt + 1;

        if v_attempt > 5 then
            raise exception 'SLUG_GENERATION_FAILED';
        end if;

        begin
            update public.groups as g
               set slug = 'g_' || replace(gen_random_uuid()::text, '-', '')
             where g.id = p_group_id
               and (g.slug is null or trim(g.slug) = '')
            returning g.slug into v_slug;

            if nullif(trim(v_slug), '') is not null then
                return v_slug;
            end if;

            select g.slug into v_slug
              from public.groups as g
             where g.id = p_group_id;

            if nullif(trim(v_slug), '') is not null then
                return v_slug;
            end if;
        exception when unique_violation then
            v_slug := null;
        end;
    end loop;
end;
$function$;

revoke all on function public.ensure_group_slug(uuid) from public, anon;
grant execute on function public.ensure_group_slug(uuid) to authenticated;

-- ── 4) Per-group notification preferences ─────────────────────────────────
create table if not exists public.group_notification_prefs (
    user_id uuid not null references public.profiles (id) on delete cascade,
    group_id uuid not null references public.groups (id) on delete cascade,
    posts_enabled boolean not null default true,
    comments_enabled boolean not null default true,
    replies_enabled boolean not null default true,
    join_requests_enabled boolean not null default true,
    admin_alerts_enabled boolean not null default true,
    updated_at timestamptz not null default now(),
    primary key (user_id, group_id)
);

alter table public.group_notification_prefs enable row level security;

drop policy if exists "gnp_select_own" on public.group_notification_prefs;
create policy "gnp_select_own" on public.group_notification_prefs for select using (
    user_id = auth.uid()
);

drop policy if exists "gnp_insert_own" on public.group_notification_prefs;
create policy "gnp_insert_own" on public.group_notification_prefs for insert with check (
    user_id = auth.uid()
    and public.nexa_is_group_member(group_id, auth.uid())
);

drop policy if exists "gnp_update_own" on public.group_notification_prefs;
create policy "gnp_update_own" on public.group_notification_prefs for update using (
    user_id = auth.uid()
    and public.nexa_is_group_member(group_id, auth.uid())
) with check (
    user_id = auth.uid()
    and public.nexa_is_group_member(group_id, auth.uid())
);

-- Preferences are only meaningful for active members. The RPC writes with the
-- caller's identity, so direct table writes are restricted to the owner row.
create or replace function public.upsert_group_notification_prefs(
    p_group_id uuid,
    p_posts boolean default null,
    p_comments boolean default null,
    p_replies boolean default null,
    p_join_requests boolean default null,
    p_admin_alerts boolean default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then
        raise exception 'NOT_AUTHENTICATED';
    end if;

    if not exists (
        select 1 from public.groups as g where g.id = p_group_id
    ) then
        raise exception 'GROUP_NOT_FOUND';
    end if;

    if not exists (
        select 1
          from public.group_members as gm
         where gm.group_id = p_group_id
           and gm.user_id = v_uid
           and gm.status = 'active'
    ) then
        raise exception 'NOT_MEMBER';
    end if;

    insert into public.group_notification_prefs (user_id, group_id)
    values (v_uid, p_group_id)
    on conflict (user_id, group_id) do nothing;

    update public.group_notification_prefs as prefs
       set posts_enabled = coalesce(p_posts, prefs.posts_enabled),
           comments_enabled = coalesce(p_comments, prefs.comments_enabled),
           replies_enabled = coalesce(p_replies, prefs.replies_enabled),
           join_requests_enabled = coalesce(p_join_requests, prefs.join_requests_enabled),
           admin_alerts_enabled = coalesce(p_admin_alerts, prefs.admin_alerts_enabled),
           updated_at = now()
     where prefs.user_id = v_uid
       and prefs.group_id = p_group_id;
end;
$function$;

revoke all on function public.upsert_group_notification_prefs(
    uuid, boolean, boolean, boolean, boolean, boolean
) from public, anon;
grant execute on function public.upsert_group_notification_prefs(
    uuid, boolean, boolean, boolean, boolean, boolean
) to authenticated;

notify pgrst, 'reload schema';
