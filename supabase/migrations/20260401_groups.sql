-- Nexa groups: groups, membership, posts, invites.
-- Real server-side data only. Membership changes go through the RPCs below so the
-- member_count trigger stays authoritative.

create table if not exists public.groups (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references public.profiles (id) on delete cascade,
    name text not null check (char_length(name) between 2 and 100),
    description text not null default '',
    avatar_url text,
    cover_url text,
    privacy text not null default 'public' check (privacy in ('public', 'private')),
    member_count int not null default 0 check (member_count >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);
create index if not exists idx_groups_owner on public.groups (owner_id);
create index if not exists idx_groups_privacy on public.groups (privacy);
create index if not exists idx_groups_created on public.groups (created_at desc);

create table if not exists public.group_members (
    group_id uuid not null references public.groups (id) on delete cascade,
    user_id uuid not null references public.profiles (id) on delete cascade,
    role text not null default 'member' check (role in ('owner', 'admin', 'member')),
    status text not null default 'active' check (status in ('pending', 'active', 'banned')),
    joined_at timestamptz not null default now(),
    primary key (group_id, user_id)
);
create index if not exists idx_group_members_user on public.group_members (user_id, status);
create index if not exists idx_group_members_group on public.group_members (group_id, status);

create table if not exists public.group_posts (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null references public.groups (id) on delete cascade,
    author_id uuid not null references public.profiles (id) on delete cascade,
    content text not null default '',
    media_url text,
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    is_deleted boolean not null default false
);
create index if not exists idx_group_posts_group on public.group_posts (group_id, created_at desc);
create index if not exists idx_group_posts_author on public.group_posts (author_id, created_at desc);

create table if not exists public.group_invites (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null references public.groups (id) on delete cascade,
    inviter_id uuid not null references public.profiles (id) on delete cascade,
    invitee_id uuid not null references public.profiles (id) on delete cascade,
    status text not null default 'pending' check (status in ('pending', 'accepted', 'rejected')),
    created_at timestamptz not null default now(),
    unique (group_id, invitee_id)
);
create index if not exists idx_group_invites_invitee on public.group_invites (invitee_id, status);
create index if not exists idx_group_invites_group on public.group_invites (group_id, invitee_id, status);

drop trigger if exists nexa_groups_touch_updated_at on public.groups;
create trigger nexa_groups_touch_updated_at
before update on public.groups
for each row execute function public.nexa_touch_updated_at();

-- ── Membership helpers (security definer avoids recursive RLS) ───────────
create or replace function public.nexa_is_group_member(p_group_id uuid, p_uid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select p_uid is not null and exists (
        select 1 from public.group_members m
        where m.group_id = p_group_id and m.user_id = p_uid and m.status = 'active'
    );
$$;

create or replace function public.nexa_is_group_admin(p_group_id uuid, p_uid uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select p_uid is not null and exists (
        select 1 from public.group_members m
        where m.group_id = p_group_id and m.user_id = p_uid
          and m.status = 'active' and m.role in ('owner', 'admin')
    );
$$;

revoke all on function public.nexa_is_group_member(uuid, uuid) from public;
grant execute on function public.nexa_is_group_member(uuid, uuid) to authenticated, anon;
revoke all on function public.nexa_is_group_admin(uuid, uuid) from public;
grant execute on function public.nexa_is_group_admin(uuid, uuid) to authenticated, anon;

-- ── Counters / guards ────────────────────────────────────────────────────
create or replace function public.nexa_group_member_count()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        if new.status = 'active' then
            update public.groups set member_count = member_count + 1 where id = new.group_id;
        end if;
    elsif tg_op = 'DELETE' then
        if old.status = 'active' then
            update public.groups set member_count = greatest(0, member_count - 1) where id = old.group_id;
        end if;
    elsif tg_op = 'UPDATE' then
        if old.status = 'active' and new.status <> 'active' then
            update public.groups set member_count = greatest(0, member_count - 1) where id = new.group_id;
        elsif old.status <> 'active' and new.status = 'active' then
            update public.groups set member_count = member_count + 1 where id = new.group_id;
        end if;
    end if;
    return null;
end;
$$;

drop trigger if exists nexa_group_member_count on public.group_members;
create trigger nexa_group_member_count
after insert or update of status or delete on public.group_members
for each row execute function public.nexa_group_member_count();

create or replace function public.nexa_groups_guard_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.owner_id <> old.owner_id and auth.uid() <> old.owner_id then
        raise exception 'NOT_AUTHORIZED';
    end if;
    return new;
end;
$$;

drop trigger if exists nexa_groups_guard_update on public.groups;
create trigger nexa_groups_guard_update
before update on public.groups
for each row execute function public.nexa_groups_guard_update();

create or replace function public.nexa_group_members_guard_update()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.group_id <> old.group_id or new.user_id <> old.user_id then
        raise exception 'NOT_AUTHORIZED';
    end if;
    if new.role = 'owner' and old.role <> 'owner' then
        raise exception 'NOT_AUTHORIZED';
    end if;
    return new;
end;
$$;

drop trigger if exists nexa_group_members_guard_update on public.group_members;
create trigger nexa_group_members_guard_update
before update on public.group_members
for each row execute function public.nexa_group_members_guard_update();

-- ── RLS ──────────────────────────────────────────────────────────────────
alter table public.groups enable row level security;
alter table public.group_members enable row level security;
alter table public.group_posts enable row level security;
alter table public.group_invites enable row level security;

drop policy if exists "groups_insert" on public.groups;
drop policy if exists "groups_select" on public.groups;
create policy "groups_select" on public.groups for select using (
    privacy = 'public'
    or public.nexa_is_group_member(id, auth.uid())
    or exists (
        select 1 from public.group_invites i
        where i.group_id = groups.id and i.invitee_id = auth.uid() and i.status = 'pending'
    )
);

drop policy if exists "groups_update" on public.groups;
create policy "groups_update" on public.groups for update using (
    auth.uid() = owner_id or public.nexa_is_group_admin(id, auth.uid())
) with check (
    auth.uid() = owner_id or public.nexa_is_group_admin(id, auth.uid())
);

drop policy if exists "groups_delete" on public.groups;
create policy "groups_delete" on public.groups for delete using (auth.uid() = owner_id);

drop policy if exists "members_insert_self" on public.group_members;
drop policy if exists "members_delete_self_or_admin" on public.group_members;
drop policy if exists "members_select" on public.group_members;
create policy "members_select" on public.group_members for select using (
    user_id = auth.uid()
    or (status = 'active' and public.nexa_is_group_member(group_id, auth.uid()))
);

drop policy if exists "members_update_admins" on public.group_members;
create policy "members_update_admins" on public.group_members for update using (
    role <> 'owner' and public.nexa_is_group_admin(group_id, auth.uid())
) with check (
    role in ('admin', 'member') and status in ('active', 'pending', 'banned')
);

drop policy if exists "group_posts_select" on public.group_posts;
create policy "group_posts_select" on public.group_posts for select using (
    not is_deleted and public.nexa_is_group_member(group_id, auth.uid())
);

drop policy if exists "group_posts_insert" on public.group_posts;
create policy "group_posts_insert" on public.group_posts for insert with check (
    auth.uid() = author_id and public.nexa_is_group_member(group_id, auth.uid())
);

drop policy if exists "group_posts_update_own" on public.group_posts;
create policy "group_posts_update_own" on public.group_posts for update using (
    auth.uid() = author_id and not is_deleted
) with check (
    auth.uid() = author_id
);

drop policy if exists "group_posts_delete_own" on public.group_posts;
create policy "group_posts_delete_own" on public.group_posts for delete using (
    auth.uid() = author_id or public.nexa_is_group_admin(group_id, auth.uid())
);

drop policy if exists "invites_select" on public.group_invites;
create policy "invites_select" on public.group_invites for select using (
    inviter_id = auth.uid() or invitee_id = auth.uid()
);

-- Invites are created and answered through the RPCs below, so clients cannot
-- bypass the invitee's privacy settings or forge an accepted invite.
drop policy if exists "invites_insert" on public.group_invites;
drop policy if exists "invites_update_invitee" on public.group_invites;

-- ── RPCs ─────────────────────────────────────────────────────────────────
create or replace function public.create_group(p_name text, p_description text, p_privacy text)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_gid uuid;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    if char_length(trim(p_name)) < 2 or char_length(trim(p_name)) > 100 then
        raise exception 'INVALID_NAME';
    end if;
    if p_privacy not in ('public', 'private') then raise exception 'INVALID_PRIVACY'; end if;

    insert into public.groups (owner_id, name, description, privacy)
    values (v_uid, trim(p_name), coalesce(trim(p_description), ''), p_privacy)
    returning id into v_gid;

    insert into public.group_members (group_id, user_id, role, status)
    values (v_gid, v_uid, 'owner', 'active');

    return v_gid;
end;
$$;

create or replace function public.join_group(p_group_id uuid)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_privacy text;
    v_status text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

    select privacy into v_privacy from public.groups where id = p_group_id;
    if v_privacy is null then raise exception 'GROUP_NOT_FOUND'; end if;

    select status into v_status from public.group_members
    where group_id = p_group_id and user_id = v_uid;

    if v_status = 'active' then return 'already_member'; end if;
    if v_status = 'banned' then raise exception 'BANNED'; end if;

    if v_privacy = 'public' then
        insert into public.group_members (group_id, user_id, role, status)
        values (p_group_id, v_uid, 'member', 'active')
        on conflict (group_id, user_id) do update set status = 'active';
        return 'joined';
    end if;

    if v_status = 'pending' then return 'pending_approval'; end if;

    insert into public.group_members (group_id, user_id, role, status)
    values (p_group_id, v_uid, 'member', 'pending')
    on conflict (group_id, user_id) do update set status = 'pending';
    return 'pending_approval';
end;
$$;

create or replace function public.leave_group(p_group_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    delete from public.group_members
    where group_id = p_group_id and user_id = v_uid and role <> 'owner';
end;
$$;

create or replace function public.respond_to_invite(p_invite_id uuid, p_accept boolean)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_gid uuid;
    v_invitee uuid;
    v_status text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;

    select group_id, invitee_id into v_gid, v_invitee
    from public.group_invites
    where id = p_invite_id and status = 'pending';

    if v_gid is null then raise exception 'INVITE_NOT_FOUND'; end if;
    if v_invitee <> v_uid then raise exception 'NOT_AUTHORIZED'; end if;

    select status into v_status from public.group_members
    where group_id = v_gid and user_id = v_uid;
    if v_status = 'banned' then raise exception 'BANNED'; end if;

    update public.group_invites
    set status = case when p_accept then 'accepted' else 'rejected' end
    where id = p_invite_id;

    if p_accept then
        insert into public.group_members (group_id, user_id, role, status)
        values (v_gid, v_uid, 'member', 'active')
        on conflict (group_id, user_id) do update set status = 'active'
        where group_members.status <> 'active';
    end if;
end;
$$;

create or replace function public.invite_to_group(p_group_id uuid, p_invitee_id uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_allowed boolean;
    v_invitee_status text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    if not exists (select 1 from public.groups where id = p_group_id) then
        raise exception 'GROUP_NOT_FOUND';
    end if;
    if not public.nexa_is_group_member(p_group_id, v_uid) then
        raise exception 'NOT_AUTHORIZED';
    end if;
    if not exists (select 1 from public.profiles where id = p_invitee_id) then
        raise exception 'USER_NOT_FOUND';
    end if;

    select status into v_invitee_status from public.group_members
    where group_id = p_group_id and user_id = p_invitee_id;
    if v_invitee_status = 'active' then raise exception 'ALREADY_MEMBER'; end if;
    if v_invitee_status = 'banned' then raise exception 'BANNED'; end if;

    select public.nexa_audience_allows(
        coalesce((select s.group_invites_from from public.user_privacy_settings s where s.user_id = p_invitee_id), 'everyone'),
        p_invitee_id
    ) into v_allowed;
    if v_allowed is null then v_allowed := true; end if;
    if not v_allowed then raise exception 'INVITES_DISABLED'; end if;

    insert into public.group_invites (group_id, inviter_id, invitee_id, status)
    values (p_group_id, v_uid, p_invitee_id, 'pending')
    on conflict (group_id, invitee_id) do update
    set inviter_id = excluded.inviter_id, status = 'pending', created_at = now();
end;
$$;

revoke all on function public.create_group(text, text, text) from public, anon;
grant execute on function public.create_group(text, text, text) to authenticated;
revoke all on function public.join_group(uuid) from public, anon;
grant execute on function public.join_group(uuid) to authenticated;
revoke all on function public.leave_group(uuid) from public, anon;
grant execute on function public.leave_group(uuid) to authenticated;
revoke all on function public.respond_to_invite(uuid, boolean) from public, anon;
grant execute on function public.respond_to_invite(uuid, boolean) to authenticated;
revoke all on function public.invite_to_group(uuid, uuid) from public, anon;
grant execute on function public.invite_to_group(uuid, uuid) to authenticated;

notify pgrst, 'reload schema';
