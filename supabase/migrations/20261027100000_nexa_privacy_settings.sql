-- Nexa privacy & safety settings (server-side source of truth).
-- Mirrors com.nexa.social.domain.model.PrivacySettings / PrivacyJson.

create table if not exists public.user_privacy_settings (
    user_id uuid primary key references public.profiles (id) on delete cascade,

    is_private_account boolean not null default false,

    default_post_audience text not null default 'everyone',
    story_audience text not null default 'followers',
    story_replies_from text not null default 'followers',
    story_sharing_enabled boolean not null default true,

    show_followers_list boolean not null default true,
    show_following_list boolean not null default true,
    show_activity_status boolean not null default true,
    show_read_receipts boolean not null default true,

    messages_from text not null default 'everyone',
    group_invites_from text not null default 'everyone',
    hide_notification_content boolean not null default false,

    who_can_comment text not null default 'everyone',
    who_can_react text not null default 'everyone',
    who_can_mention text not null default 'everyone',
    mention_approval_required boolean not null default false,

    show_in_search boolean not null default true,
    allow_suggestions boolean not null default true,

    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),

    constraint user_privacy_settings_audience_valid check (
        default_post_audience in ('everyone', 'followers', 'mutuals', 'only_me')
        and story_audience in ('everyone', 'followers', 'mutuals', 'only_me')
        and story_replies_from in ('everyone', 'followers', 'mutuals', 'only_me')
        and who_can_comment in ('everyone', 'followers', 'mutuals', 'only_me')
        and who_can_react in ('everyone', 'followers', 'mutuals', 'only_me')
        and who_can_mention in ('everyone', 'followers', 'mutuals', 'only_me')
    ),
    constraint user_privacy_settings_messages_valid check (
        messages_from in ('everyone', 'followers', 'mutuals', 'nobody')
        and group_invites_from in ('everyone', 'followers', 'nobody')
    )
);

drop trigger if exists nexa_privacy_settings_touch_updated_at on public.user_privacy_settings;
create trigger nexa_privacy_settings_touch_updated_at
before update on public.user_privacy_settings
for each row execute function public.nexa_touch_updated_at();

-- Every account gets a row on sign-up so RLS helpers never have to guess.
create or replace function public.nexa_create_default_privacy_settings()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.user_privacy_settings (user_id)
    values (new.id)
    on conflict (user_id) do nothing;
    return new;
end;
$$;

drop trigger if exists nexa_profiles_default_privacy on public.profiles;
create trigger nexa_profiles_default_privacy
after insert on public.profiles
for each row execute function public.nexa_create_default_privacy_settings();

insert into public.user_privacy_settings (user_id)
select p.id from public.profiles p
on conflict (user_id) do nothing;

-- ── Privacy helpers ────────────────────────────────────────────────────
create or replace function public.nexa_audience_allows(rule text, owner uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select case coalesce(rule, 'everyone')
        when 'everyone' then true
        when 'only_me' then auth.uid() = owner
        when 'followers' then auth.uid() = owner or exists (
            select 1 from public.follows f
            where f.follower_id = auth.uid() and f.following_id = owner
        )
        when 'mutuals' then auth.uid() = owner or (
            exists (
                select 1 from public.follows f
                where f.follower_id = auth.uid() and f.following_id = owner
            )
            and exists (
                select 1 from public.follows f
                where f.follower_id = owner and f.following_id = auth.uid()
            )
        )
        when 'nobody' then false
        else true
    end;
$$;

create or replace function public.nexa_can_message(target uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.nexa_audience_allows(
        coalesce((select s.messages_from from public.user_privacy_settings s where s.user_id = target), 'everyone'),
        target
    );
$$;

create or replace function public.nexa_can_comment_on(target_post uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.nexa_audience_allows(
        coalesce((select s.who_can_comment from public.user_privacy_settings s where s.user_id = p.author_id), 'everyone'),
        p.author_id
    )
    from public.posts p
    where p.id = target_post;
$$;

create or replace function public.nexa_can_react_to(target_post uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.nexa_audience_allows(
        coalesce((select s.who_can_react from public.user_privacy_settings s where s.user_id = p.author_id), 'everyone'),
        p.author_id
    )
    from public.posts p
    where p.id = target_post;
$$;

create or replace function public.nexa_can_mention(target uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select public.nexa_audience_allows(
        coalesce((select s.who_can_mention from public.user_privacy_settings s where s.user_id = target), 'everyone'),
        target
    );
$$;

create or replace function public.nexa_is_searchable(target uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select coalesce(
        (select s.show_in_search from public.user_privacy_settings s where s.user_id = target),
        true
    );
$$;

grant execute on function public.nexa_audience_allows(text, uuid) to authenticated;
grant execute on function public.nexa_can_message(uuid) to authenticated;
grant execute on function public.nexa_can_comment_on(uuid) to authenticated;
grant execute on function public.nexa_can_react_to(uuid) to authenticated;
grant execute on function public.nexa_can_mention(uuid) to authenticated;
grant execute on function public.nexa_is_searchable(uuid) to authenticated;

-- ── Row Level Security ─────────────────────────────────────────────────
alter table public.user_privacy_settings enable row level security;

drop policy if exists nexa_user_privacy_settings_select on public.user_privacy_settings;
create policy nexa_user_privacy_settings_select on public.user_privacy_settings
for select to authenticated using (user_id = auth.uid());

drop policy if exists nexa_user_privacy_settings_insert on public.user_privacy_settings;
create policy nexa_user_privacy_settings_insert on public.user_privacy_settings
for insert to authenticated with check (user_id = auth.uid());

drop policy if exists nexa_user_privacy_settings_update on public.user_privacy_settings;
create policy nexa_user_privacy_settings_update on public.user_privacy_settings
for update to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists nexa_user_privacy_settings_delete on public.user_privacy_settings;
create policy nexa_user_privacy_settings_delete on public.user_privacy_settings
for delete to authenticated using (user_id = auth.uid());

grant select, insert, update, delete on public.user_privacy_settings to authenticated;

-- ── Enforcement on existing tables ─────────────────────────────────────
drop policy if exists nexa_comments_insert on public.comments;
create policy nexa_comments_insert on public.comments for insert to authenticated with check (
    author_id = auth.uid()
    and public.nexa_can_view_post(post_id)
    and public.nexa_can_comment_on(post_id)
);

drop policy if exists nexa_post_likes_insert on public.post_likes;
create policy nexa_post_likes_insert on public.post_likes for insert to authenticated with check (
    user_id = auth.uid()
    and public.nexa_can_view_post(post_id)
    and public.nexa_can_react_to(post_id)
);

drop policy if exists nexa_comment_likes_insert on public.comment_likes;
create policy nexa_comment_likes_insert on public.comment_likes for insert to authenticated with check (
    user_id = auth.uid()
    and exists (
        select 1 from public.comments c
        where c.id = comment_id
          and public.nexa_can_view_post(c.post_id)
          and public.nexa_can_react_to(c.post_id)
    )
);

drop policy if exists nexa_conversations_insert on public.conversations;
create policy nexa_conversations_insert on public.conversations for insert to authenticated with check (
    (user_a = auth.uid() or user_b = auth.uid())
    and public.nexa_can_message(
        case when user_a = auth.uid() then user_b else user_a end
    )
);

-- ── Account deletion ──────────────────────────────────────────────────
-- Deleting the auth user cascades to profiles and every owned row.
create or replace function public.delete_own_account()
returns void
language plpgsql
security definer
set search_path = public, auth
as $$
begin
    if auth.uid() is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    delete from auth.users where id = auth.uid();
end;
$$;

revoke all on function public.delete_own_account() from public;
grant execute on function public.delete_own_account() to authenticated;

notify pgrst, 'reload schema';
