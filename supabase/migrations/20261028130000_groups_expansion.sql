-- ═══════════════════════════════════════════════════════════════════════════
-- Nexa groups expansion: management, requests, roles, moderation, reactions,
-- comments, search, stats and notifications.
--
-- All user foreign keys reference public.profiles (not auth.users) so PostgREST
-- can embed profile rows directly. Membership counters remain owned by the
-- existing nexa_group_member_count trigger; the RPCs below never touch
-- member_count manually.
-- ═══════════════════════════════════════════════════════════════════════════

-- 1) Group columns ─────────────────────────────────────────────────────────
alter table public.groups add column if not exists rules text not null default '';
alter table public.groups add column if not exists category text;
alter table public.groups add column if not exists allow_member_posts boolean not null default true;
alter table public.groups add column if not exists allow_member_invites boolean not null default true;
alter table public.groups add column if not exists require_join_approval boolean not null default false;
alter table public.groups add column if not exists slug text;
alter table public.groups add column if not exists is_archived boolean not null default false;

create unique index if not exists idx_groups_slug on public.groups (slug) where slug is not null;
create index if not exists idx_groups_category on public.groups (category) where category is not null;

-- 2) Join requests ─────────────────────────────────────────────────────────
create table if not exists public.group_join_requests (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null references public.groups (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    message text,
    status text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
    reviewed_by uuid references public.profiles (id) on delete set null,
    reviewed_at timestamptz,
    created_at timestamptz not null default now(),
    unique (group_id, user_id)
);
create index if not exists idx_join_req_group on public.group_join_requests (group_id, status);
create index if not exists idx_join_req_user on public.group_join_requests (user_id, status);

-- 3) Post reactions ────────────────────────────────────────────────────────
create table if not exists public.group_post_reactions (
    post_id uuid not null references public.group_posts (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    kind text not null default 'like' check (kind in ('like', 'love', 'haha', 'sad', 'angry')),
    created_at timestamptz not null default now(),
    primary key (post_id, user_id)
);
create index if not exists idx_gpr_post on public.group_post_reactions (post_id);
create index if not exists idx_gpr_user on public.group_post_reactions (user_id);

-- 4) Post comments ─────────────────────────────────────────────────────────
create table if not exists public.group_post_comments (
    id uuid primary key default gen_random_uuid(),
    post_id uuid not null references public.group_posts (id) on delete cascade,
    author_id uuid not null references public.profiles (id) on delete cascade,
    content text not null check (char_length(content) between 1 and 2000),
    parent_id uuid references public.group_post_comments (id) on delete cascade,
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    is_deleted boolean not null default false
);
create index if not exists idx_gpc_post on public.group_post_comments (post_id, created_at);
create index if not exists idx_gpc_author on public.group_post_comments (author_id, created_at desc);

-- 5) Moderation log ────────────────────────────────────────────────────────
create table if not exists public.group_moderation_log (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null references public.groups (id) on delete cascade,
    actor_id uuid not null references public.profiles (id) on delete cascade,
    target_user_id uuid references public.profiles (id) on delete set null,
    action text not null,
    metadata jsonb,
    created_at timestamptz not null default now()
);
create index if not exists idx_gml_group on public.group_moderation_log (group_id, created_at desc);

-- 6) Denormalized post counters ────────────────────────────────────────────
alter table public.group_posts add column if not exists reaction_count int not null default 0;
alter table public.group_posts add column if not exists comment_count int not null default 0;

-- 7) Group notification support ────────────────────────────────────────────
alter table public.notifications add column if not exists group_id uuid
    references public.groups (id) on delete cascade;
alter table public.notifications drop constraint if exists notifications_type_valid;
alter table public.notifications add constraint notifications_type_valid check (
    type in (
        'post_like', 'post_comment', 'comment_reply', 'comment_like',
        'follow', 'mention', 'message', 'story_reply', 'system',
        'group_join_request', 'group_join_approved', 'group_join_rejected'
    )
);
create index if not exists notifications_group_idx
    on public.notifications (group_id, created_at desc) where group_id is not null;

-- 8) RLS ───────────────────────────────────────────────────────────────────
alter table public.group_join_requests enable row level security;
alter table public.group_post_reactions enable row level security;
alter table public.group_post_comments enable row level security;
alter table public.group_moderation_log enable row level security;

-- Admins must be able to see pending/banned members too.
drop policy if exists "members_select" on public.group_members;
create policy "members_select" on public.group_members for select using (
    user_id = auth.uid()
    or public.nexa_is_group_admin(group_id, auth.uid())
    or (status = 'active' and public.nexa_is_group_member(group_id, auth.uid()))
);

drop policy if exists "join_req_select_own_or_admin" on public.group_join_requests;
create policy "join_req_select_own_or_admin" on public.group_join_requests for select using (
    user_id = auth.uid()
    or public.nexa_is_group_admin(group_id, auth.uid())
);

drop policy if exists "join_req_insert_self" on public.group_join_requests;
create policy "join_req_insert_self" on public.group_join_requests for insert
    with check (user_id = auth.uid() and status = 'pending');

drop policy if exists "join_req_update_admin" on public.group_join_requests;
create policy "join_req_update_admin" on public.group_join_requests for update using (
    public.nexa_is_group_admin(group_id, auth.uid())
) with check (
    public.nexa_is_group_admin(group_id, auth.uid())
    and status in ('pending', 'approved', 'rejected')
);

drop policy if exists "gpr_select_members" on public.group_post_reactions;
create policy "gpr_select_members" on public.group_post_reactions for select using (
    exists (
        select 1 from public.group_posts gp
        where gp.id = group_post_reactions.post_id
          and not gp.is_deleted
          and public.nexa_is_group_member(gp.group_id, auth.uid())
    )
);

drop policy if exists "gpr_insert_self" on public.group_post_reactions;
create policy "gpr_insert_self" on public.group_post_reactions for insert with check (
    auth.uid() = user_id
    and exists (
        select 1 from public.group_posts gp
        where gp.id = group_post_reactions.post_id
          and not gp.is_deleted
          and public.nexa_is_group_member(gp.group_id, auth.uid())
    )
);

drop policy if exists "gpr_update_self" on public.group_post_reactions;
create policy "gpr_update_self" on public.group_post_reactions for update using (
    auth.uid() = user_id
) with check (
    auth.uid() = user_id
    and exists (
        select 1 from public.group_posts gp
        where gp.id = group_post_reactions.post_id
          and not gp.is_deleted
          and public.nexa_is_group_member(gp.group_id, auth.uid())
    )
);

drop policy if exists "gpr_delete_self" on public.group_post_reactions;
create policy "gpr_delete_self" on public.group_post_reactions for delete
    using (auth.uid() = user_id);

drop policy if exists "gpc_select_members" on public.group_post_comments;
create policy "gpc_select_members" on public.group_post_comments for select using (
    not is_deleted
    and exists (
        select 1 from public.group_posts gp
        where gp.id = group_post_comments.post_id
          and not gp.is_deleted
          and public.nexa_is_group_member(gp.group_id, auth.uid())
    )
);

drop policy if exists "gpc_insert_members" on public.group_post_comments;
create policy "gpc_insert_members" on public.group_post_comments for insert with check (
    auth.uid() = author_id
    and exists (
        select 1 from public.group_posts gp
        where gp.id = group_post_comments.post_id
          and not gp.is_deleted
          and public.nexa_is_group_member(gp.group_id, auth.uid())
    )
);

drop policy if exists "gpc_update_own" on public.group_post_comments;
create policy "gpc_update_own" on public.group_post_comments for update using (
    auth.uid() = author_id
) with check (
    auth.uid() = author_id
);

drop policy if exists "gml_select_admins" on public.group_moderation_log;
create policy "gml_select_admins" on public.group_moderation_log for select using (
    public.nexa_is_group_admin(group_id, auth.uid())
);

-- 9) RPCs ──────────────────────────────────────────────────────────────────
create or replace function public.update_group_details(
    p_group_id uuid,
    p_name text default null,
    p_description text default null,
    p_rules text default null,
    p_category text default null,
    p_privacy text default null,
    p_avatar_url text default null,
    p_cover_url text default null,
    p_allow_member_posts boolean default null,
    p_allow_member_invites boolean default null,
    p_require_join_approval boolean default null
) returns void
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_role text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    select role into v_role from public.group_members
        where group_id = p_group_id and user_id = v_uid and status = 'active';
    if v_role not in ('owner', 'admin') then raise exception 'NOT_AUTHORIZED'; end if;
    if p_name is not null and (char_length(trim(p_name)) < 2 or char_length(trim(p_name)) > 100) then
        raise exception 'INVALID_NAME';
    end if;
    if p_privacy is not null and p_privacy not in ('public', 'private') then
        raise exception 'INVALID_PRIVACY';
    end if;
    update public.groups set
        name = coalesce(nullif(trim(p_name), ''), name),
        description = coalesce(p_description, description),
        rules = coalesce(p_rules, rules),
        category = coalesce(p_category, category),
        privacy = coalesce(p_privacy, privacy),
        avatar_url = coalesce(p_avatar_url, avatar_url),
        cover_url = coalesce(p_cover_url, cover_url),
        allow_member_posts = coalesce(p_allow_member_posts, allow_member_posts),
        allow_member_invites = coalesce(p_allow_member_invites, allow_member_invites),
        require_join_approval = coalesce(p_require_join_approval, require_join_approval),
        updated_at = now()
    where id = p_group_id;
    insert into public.group_moderation_log (group_id, actor_id, action)
    values (p_group_id, v_uid, 'update_details');
end;
$$;
revoke all on function public.update_group_details(uuid,text,text,text,text,text,text,text,boolean,boolean,boolean) from public, anon;
grant execute on function public.update_group_details(uuid,text,text,text,text,text,text,text,boolean,boolean,boolean) to authenticated;

create or replace function public.delete_group(p_group_id uuid) returns void
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_owner uuid;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    select owner_id into v_owner from public.groups where id = p_group_id;
    if v_owner is null then raise exception 'GROUP_NOT_FOUND'; end if;
    if v_owner <> v_uid then raise exception 'NOT_AUTHORIZED'; end if;
    -- Delete members first so the member-count trigger does not update a row
    -- that is already being deleted by the cascade.
    delete from public.group_members where group_id = p_group_id;
    delete from public.groups where id = p_group_id;
end;
$$;
revoke all on function public.delete_group(uuid) from public, anon;
grant execute on function public.delete_group(uuid) to authenticated;

create or replace function public.request_join_group(p_group_id uuid, p_message text default null)
returns text
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_privacy text;
    v_requires_approval boolean;
    v_member_status text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

    select status into v_member_status from public.group_members
        where group_id = p_group_id and user_id = v_uid;
    if v_member_status = 'active' then return 'already_member'; end if;
    if v_member_status = 'banned' then raise exception 'BANNED'; end if;

    select privacy, require_join_approval into v_privacy, v_requires_approval
        from public.groups where id = p_group_id;
    if v_privacy is null then raise exception 'GROUP_NOT_FOUND'; end if;

    if v_privacy = 'public' and not v_requires_approval then
        insert into public.group_members (group_id, user_id, role, status)
        values (p_group_id, v_uid, 'member', 'active')
        on conflict (group_id, user_id) do update set status = 'active';
        return 'joined';
    end if;

    insert into public.group_join_requests (group_id, user_id, message, status)
    values (p_group_id, v_uid, nullif(trim(p_message), ''), 'pending')
    on conflict (group_id, user_id) do update
        set message = excluded.message, status = 'pending', created_at = now();
    return 'pending_approval';
end;
$$;
revoke all on function public.request_join_group(uuid, text) from public, anon;
grant execute on function public.request_join_group(uuid, text) to authenticated;

create or replace function public.approve_join_request(p_request_id uuid, p_approve boolean)
returns void
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_gid uuid;
    v_requester uuid;
    v_role text;
    v_member_status text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    select group_id, user_id into v_gid, v_requester
        from public.group_join_requests
        where id = p_request_id and status = 'pending';
    if v_gid is null then raise exception 'REQUEST_NOT_FOUND'; end if;

    select role into v_role from public.group_members
        where group_id = v_gid and user_id = v_uid and status = 'active';
    if v_role not in ('owner', 'admin') then raise exception 'NOT_AUTHORIZED'; end if;

    update public.group_join_requests
        set status = case when p_approve then 'approved' else 'rejected' end,
            reviewed_by = v_uid,
            reviewed_at = now()
        where id = p_request_id;

    if p_approve then
        select status into v_member_status from public.group_members
            where group_id = v_gid and user_id = v_requester;
        if v_member_status = 'banned' then raise exception 'BANNED'; end if;
        insert into public.group_members (group_id, user_id, role, status)
        values (v_gid, v_requester, 'member', 'active')
        on conflict (group_id, user_id) do update set status = 'active';
        insert into public.group_moderation_log (group_id, actor_id, target_user_id, action)
        values (v_gid, v_uid, v_requester, 'approve_join');
    else
        insert into public.group_moderation_log (group_id, actor_id, target_user_id, action)
        values (v_gid, v_uid, v_requester, 'reject_join');
    end if;
end;
$$;
revoke all on function public.approve_join_request(uuid, boolean) from public, anon;
grant execute on function public.approve_join_request(uuid, boolean) to authenticated;

create or replace function public.set_member_role(p_group_id uuid, p_user_id uuid, p_role text)
returns void
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_my_role text;
    v_target_role text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    if p_role not in ('admin', 'member') then raise exception 'INVALID_ROLE'; end if;

    select role into v_my_role from public.group_members
        where group_id = p_group_id and user_id = v_uid and status = 'active';
    select role into v_target_role from public.group_members
        where group_id = p_group_id and user_id = p_user_id and status = 'active';
    if v_target_role is null then raise exception 'MEMBER_NOT_FOUND'; end if;
    if v_target_role = 'owner' then raise exception 'CANNOT_CHANGE_OWNER'; end if;

    if v_my_role = 'owner' or (v_my_role = 'admin' and v_target_role = 'member') then
        update public.group_members set role = p_role
            where group_id = p_group_id and user_id = p_user_id;
        insert into public.group_moderation_log (group_id, actor_id, target_user_id, action, metadata)
        values (p_group_id, v_uid, p_user_id,
            case when p_role = 'admin' then 'promote' else 'demote' end,
            jsonb_build_object('new_role', p_role));
    else
        raise exception 'NOT_AUTHORIZED';
    end if;
end;
$$;
revoke all on function public.set_member_role(uuid, uuid, text) from public, anon;
grant execute on function public.set_member_role(uuid, uuid, text) to authenticated;

create or replace function public.remove_member(p_group_id uuid, p_user_id uuid, p_ban boolean default false)
returns void
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_my_role text;
    v_target_role text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    select role into v_my_role from public.group_members
        where group_id = p_group_id and user_id = v_uid and status = 'active';
    select role into v_target_role from public.group_members
        where group_id = p_group_id and user_id = p_user_id;
    if v_target_role = 'owner' then raise exception 'CANNOT_REMOVE_OWNER'; end if;
    if v_my_role not in ('owner', 'admin') then raise exception 'NOT_AUTHORIZED'; end if;
    if v_my_role = 'admin' and v_target_role = 'admin' then raise exception 'NOT_AUTHORIZED'; end if;
    if v_target_role is null then raise exception 'MEMBER_NOT_FOUND'; end if;

    if p_ban then
        update public.group_members set status = 'banned'
            where group_id = p_group_id and user_id = p_user_id;
    else
        delete from public.group_members
            where group_id = p_group_id and user_id = p_user_id;
    end if;
    insert into public.group_moderation_log (group_id, actor_id, target_user_id, action)
    values (p_group_id, v_uid, p_user_id, case when p_ban then 'ban' else 'remove' end);
end;
$$;
revoke all on function public.remove_member(uuid, uuid, boolean) from public, anon;
grant execute on function public.remove_member(uuid, uuid, boolean) to authenticated;

create or replace function public.unban_member(p_group_id uuid, p_user_id uuid)
returns void
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_my_role text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    select role into v_my_role from public.group_members
        where group_id = p_group_id and user_id = v_uid and status = 'active';
    if v_my_role not in ('owner', 'admin') then raise exception 'NOT_AUTHORIZED'; end if;
    delete from public.group_members
        where group_id = p_group_id and user_id = p_user_id and status = 'banned';
    insert into public.group_moderation_log (group_id, actor_id, target_user_id, action)
    values (p_group_id, v_uid, p_user_id, 'unban');
end;
$$;
revoke all on function public.unban_member(uuid, uuid) from public, anon;
grant execute on function public.unban_member(uuid, uuid) to authenticated;

create or replace function public.search_groups(
    p_query text,
    p_category text default null,
    p_limit int default 30
) returns table (
    id uuid,
    name text,
    description text,
    avatar_url text,
    cover_url text,
    privacy text,
    member_count int,
    category text,
    created_at timestamptz
)
language sql security definer set search_path = public as $$
    select g.id, g.name, g.description, g.avatar_url, g.cover_url,
           g.privacy, g.member_count, g.category, g.created_at
    from public.groups g
    where g.privacy = 'public'
      and not g.is_archived
      and (
          p_query is null or p_query = ''
          or g.name ilike '%' || p_query || '%'
          or g.description ilike '%' || p_query || '%'
      )
      and (p_category is null or g.category = p_category)
    order by g.member_count desc, g.created_at desc
    limit least(greatest(coalesce(p_limit, 30), 1), 50);
$$;
revoke all on function public.search_groups(text, text, int) from public, anon;
grant execute on function public.search_groups(text, text, int) to authenticated;

create or replace function public.group_stats(p_group_id uuid)
returns table (
    total_members int,
    active_members int,
    pending_members int,
    banned_members int,
    total_posts int,
    total_comments int,
    posts_last_7_days int
)
language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'NOT_AUTHENTICATED'; end if;
    if not public.nexa_is_group_admin(p_group_id, auth.uid()) then
        raise exception 'NOT_AUTHORIZED';
    end if;
    return query
    select
        (select count(*)::int from public.group_members where group_id = p_group_id),
        (select count(*)::int from public.group_members where group_id = p_group_id and status = 'active'),
        (select count(*)::int from public.group_join_requests where group_id = p_group_id and status = 'pending'),
        (select count(*)::int from public.group_members where group_id = p_group_id and status = 'banned'),
        (select count(*)::int from public.group_posts where group_id = p_group_id and not is_deleted),
        (select count(*)::int from public.group_post_comments c
            join public.group_posts p on p.id = c.post_id
            where p.group_id = p_group_id and not c.is_deleted),
        (select count(*)::int from public.group_posts
            where group_id = p_group_id and not is_deleted
              and created_at > now() - interval '7 days');
end;
$$;
revoke all on function public.group_stats(uuid) from public, anon;
grant execute on function public.group_stats(uuid) to authenticated;

-- 10) Counters ─────────────────────────────────────────────────────────────
create or replace function public.tg_group_post_reaction_count()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
    if tg_op = 'INSERT' then
        update public.group_posts
            set reaction_count = reaction_count + 1
            where id = new.post_id;
    elsif tg_op = 'DELETE' then
        update public.group_posts
            set reaction_count = greatest(0, reaction_count - 1)
            where id = old.post_id;
    end if;
    return coalesce(new, old);
end;
$$;

drop trigger if exists trg_gpr_count on public.group_post_reactions;
create trigger trg_gpr_count
after insert or delete on public.group_post_reactions
for each row execute function public.tg_group_post_reaction_count();

create or replace function public.tg_group_comment_count()
returns trigger
language plpgsql security definer set search_path = public as $$
begin
    if tg_op = 'INSERT' and not new.is_deleted then
        update public.group_posts
            set comment_count = comment_count + 1
            where id = new.post_id;
    elsif tg_op = 'UPDATE' then
        if new.is_deleted and not old.is_deleted then
            update public.group_posts
                set comment_count = greatest(0, comment_count - 1)
                where id = new.post_id;
        elsif not new.is_deleted and old.is_deleted then
            update public.group_posts
                set comment_count = comment_count + 1
                where id = new.post_id;
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists trg_gpc_count on public.group_post_comments;
create trigger trg_gpc_count
after insert or update on public.group_post_comments
for each row execute function public.tg_group_comment_count();

-- 11) Notifications ────────────────────────────────────────────────────────
create or replace function public.tg_notify_join_request()
returns trigger
language plpgsql security definer set search_path = public as $$
declare
    v_admin uuid;
begin
    if tg_op = 'INSERT' and new.status = 'pending' then
        for v_admin in
            select user_id from public.group_members
            where group_id = new.group_id
              and role in ('owner', 'admin')
              and status = 'active'
        loop
            insert into public.notifications (user_id, actor_id, type, group_id, preview)
            values (v_admin, new.user_id, 'group_join_request', new.group_id,
                    coalesce(new.message, ''));
        end loop;
    elsif tg_op = 'UPDATE'
        and old.status = 'pending'
        and new.status in ('approved', 'rejected') then
        insert into public.notifications (user_id, actor_id, type, group_id, preview)
        values (
            new.user_id,
            new.reviewed_by,
            case when new.status = 'approved'
                 then 'group_join_approved'
                 else 'group_join_rejected' end,
            new.group_id,
            ''
        );
    end if;
    return new;
end;
$$;

drop trigger if exists trg_notify_join_req on public.group_join_requests;
create trigger trg_notify_join_req
after insert or update on public.group_join_requests
for each row execute function public.tg_notify_join_request();

notify pgrst, 'reload schema';
