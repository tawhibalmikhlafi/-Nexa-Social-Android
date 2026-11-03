-- Nexa user directory, usernames and follow graph.
-- Supabase is the only store for accounts, profiles and the social graph.
--
-- Apply with: supabase db push
-- (or paste this file in the Supabase SQL editor before running the app).
-- This migration is additive: it never drops existing data.

-- ---------------------------------------------------------------------------
-- Profiles
-- ---------------------------------------------------------------------------
create table if not exists public.profiles (
    id uuid primary key references auth.users (id) on delete cascade,
    username text not null,
    display_name text not null,
    bio text not null default '',
    avatar_url text,
    cover_url text,
    city text,
    website text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    followers_count integer not null default 0 check (followers_count >= 0),
    following_count integer not null default 0 check (following_count >= 0),
    account_status text not null default 'ACTIVE',
    account_origin text not null default 'REMOTE_ACCOUNT',
    email_verification text not null default 'NOT_VERIFIED',
    constraint profiles_username_format check (username ~ '^[a-z][a-z0-9_]{2,19}$'),
    constraint profiles_username_not_reserved check (
        username not in (
            'admin', 'administrator', 'root', 'nexa', 'support', 'official',
            'system', 'help', 'helpdesk', 'moderator', 'mod', 'staff', 'team',
            'null', 'undefined', 'api', 'www', 'mail', 'security', 'about', 'guest'
        )
    ),
    constraint profiles_account_status_valid check (
        account_status in ('ACTIVE', 'PENDING_VERIFICATION', 'SUSPENDED', 'DELETED')
    ),
    constraint profiles_email_verification_valid check (
        email_verification in ('NOT_VERIFIED', 'LOCALLY_CONFIRMED', 'VERIFIED')
    )
);

create unique index if not exists profiles_username_lower_key
    on public.profiles (lower(username));
create index if not exists profiles_display_name_lower_idx
    on public.profiles (lower(display_name));

-- The unique username is enforced twice: the profiles index above and the reservation table
-- below. The reservation row is inserted before the profile exists, so sign-up can fail cleanly
-- on a race between two clients claiming the same name.
create table if not exists public.usernames (
    username text primary key,
    user_id uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now(),
    constraint usernames_format check (username ~ '^[a-z][a-z0-9_]{2,19}$')
);
create unique index if not exists usernames_user_id_key on public.usernames (user_id);

-- ---------------------------------------------------------------------------
-- Follow graph
-- ---------------------------------------------------------------------------
create table if not exists public.follows (
    follower_id uuid not null references public.profiles (id) on delete cascade,
    following_id uuid not null references public.profiles (id) on delete cascade,
    created_at timestamptz not null default now(),
    primary key (follower_id, following_id),
    constraint follows_no_self check (follower_id <> following_id)
);
create index if not exists follows_following_id_idx on public.follows (following_id);

-- ---------------------------------------------------------------------------
-- Server-side invariants
-- ---------------------------------------------------------------------------

-- Counters and verification state are never trusted from the client.
create or replace function public.nexa_profile_before_insert()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    new.followers_count := 0;
    new.following_count := 0;
    new.account_status := 'ACTIVE';
    new.account_origin := 'REMOTE_ACCOUNT';
    new.email_verification := case
        when exists (
            select 1 from auth.users u
            where u.id = new.id and u.email_confirmed_at is not null
        ) then 'VERIFIED'
        else 'NOT_VERIFIED'
    end;
    return new;
end;
$$;

drop trigger if exists nexa_profile_before_insert on public.profiles;
create trigger nexa_profile_before_insert
before insert on public.profiles
for each row execute function public.nexa_profile_before_insert();

create or replace function public.nexa_touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists nexa_profiles_touch_updated_at on public.profiles;
create trigger nexa_profiles_touch_updated_at
before update on public.profiles
for each row execute function public.nexa_touch_updated_at();

-- Keep the reservation table in sync with profile inserts. A conflict on username aborts the
-- profile insert, which is exactly the database-level guarantee the client relies on.
create or replace function public.nexa_reserve_profile_username()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.usernames (username, user_id)
    values (lower(new.username), new.id)
    on conflict (user_id) do update set username = excluded.username;
    return new;
end;
$$;

drop trigger if exists nexa_reserve_profile_username on public.profiles;
create trigger nexa_reserve_profile_username
after insert on public.profiles
for each row execute function public.nexa_reserve_profile_username();

create or replace function public.nexa_sync_follow_counts()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if tg_op = 'INSERT' then
        update public.profiles
        set followers_count = followers_count + 1
        where id = new.following_id;
        update public.profiles
        set following_count = following_count + 1
        where id = new.follower_id;
        return new;
    elsif tg_op = 'DELETE' then
        update public.profiles
        set followers_count = greatest(followers_count - 1, 0)
        where id = old.following_id;
        update public.profiles
        set following_count = greatest(following_count - 1, 0)
        where id = old.follower_id;
        return old;
    end if;
    return null;
end;
$$;

drop trigger if exists nexa_sync_follow_counts on public.follows;
create trigger nexa_sync_follow_counts
after insert or delete on public.follows
for each row execute function public.nexa_sync_follow_counts();

-- Keep the profile verification flag aligned with Supabase Auth (source of truth).
create or replace function public.nexa_sync_email_verification()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.profiles
    set email_verification = case
            when new.email_confirmed_at is not null then 'VERIFIED'
            else 'NOT_VERIFIED'
        end
    where id = new.id;
    return new;
end;
$$;

drop trigger if exists nexa_sync_email_verification on auth.users;
create trigger nexa_sync_email_verification
after insert or update of email_confirmed_at on auth.users
for each row execute function public.nexa_sync_email_verification();

-- Reconcile counters once so existing rows match the follow graph.
update public.profiles p
set followers_count = (
        select count(*) from public.follows f where f.following_id = p.id
    ),
    following_count = (
        select count(*) from public.follows f where f.follower_id = p.id
    );

-- ---------------------------------------------------------------------------
-- Row Level Security
-- ---------------------------------------------------------------------------
alter table public.profiles enable row level security;
alter table public.usernames enable row level security;
alter table public.follows enable row level security;

drop policy if exists nexa_profiles_select on public.profiles;
create policy nexa_profiles_select
on public.profiles for select
to authenticated
using (true);

drop policy if exists nexa_profiles_insert on public.profiles;
create policy nexa_profiles_insert
on public.profiles for insert
to authenticated
with check (id = auth.uid());

drop policy if exists nexa_profiles_update on public.profiles;
create policy nexa_profiles_update
on public.profiles for update
to authenticated
using (id = auth.uid())
with check (id = auth.uid());

drop policy if exists nexa_profiles_delete on public.profiles;
create policy nexa_profiles_delete
on public.profiles for delete
to authenticated
using (id = auth.uid());

-- Username availability must be checkable from the sign-up form before a session exists, so
-- anonymous clients may read the reservation keys. Only the owner can add or remove a key.
drop policy if exists nexa_usernames_select on public.usernames;
create policy nexa_usernames_select
on public.usernames for select
to anon, authenticated
using (true);

drop policy if exists nexa_usernames_insert on public.usernames;
create policy nexa_usernames_insert
on public.usernames for insert
to authenticated
with check (user_id = auth.uid());

drop policy if exists nexa_usernames_update on public.usernames;
create policy nexa_usernames_update
on public.usernames for update
to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists nexa_usernames_delete on public.usernames;
create policy nexa_usernames_delete
on public.usernames for delete
to authenticated
using (user_id = auth.uid());

drop policy if exists nexa_follows_select on public.follows;
create policy nexa_follows_select
on public.follows for select
to authenticated
using (true);

drop policy if exists nexa_follows_insert on public.follows;
create policy nexa_follows_insert
on public.follows for insert
to authenticated
with check (follower_id = auth.uid());

drop policy if exists nexa_follows_delete on public.follows;
create policy nexa_follows_delete
on public.follows for delete
to authenticated
using (follower_id = auth.uid());

-- ---------------------------------------------------------------------------
-- Column-level privileges: counters, ids and username cannot be patched by clients.
-- ---------------------------------------------------------------------------
revoke insert, update on public.profiles from anon, authenticated;
grant select on public.profiles to authenticated;
grant insert (
    id, username, display_name, bio, avatar_url, cover_url, city, website,
    created_at, updated_at
) on public.profiles to authenticated;
grant update (
    display_name, bio, avatar_url, cover_url, city, website, updated_at
) on public.profiles to authenticated;

revoke all on public.usernames from anon, authenticated;
grant select on public.usernames to anon, authenticated;
grant insert (username, user_id) on public.usernames to authenticated;
grant update (username) on public.usernames to authenticated;
grant delete on public.usernames to authenticated;

revoke all on public.follows from anon, authenticated;
grant select, insert (follower_id, following_id), delete on public.follows to authenticated;

