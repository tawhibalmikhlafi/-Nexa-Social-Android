-- Nexa interaction notifications.
-- Notifications are written by SECURITY DEFINER triggers, so clients can never forge one.
-- Clients may only read, mark as read, and delete their own rows.

create table if not exists public.notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.profiles (id) on delete cascade,
    actor_id uuid,
    type text not null,
    post_id uuid references public.posts (id) on delete cascade,
    comment_id uuid references public.comments (id) on delete cascade,
    preview text not null default '',
    is_read boolean not null default false,
    created_at timestamptz not null default now(),
    constraint notifications_actor_id_fkey
        foreign key (actor_id) references public.profiles (id) on delete cascade,
    constraint notifications_type_valid check (
        type in (
            'post_like', 'post_comment', 'comment_reply', 'comment_like',
            'follow', 'mention', 'message', 'story_reply', 'system'
        )
    )
);

alter table public.notifications
    drop constraint if exists notifications_type_valid;
alter table public.notifications
    add constraint notifications_type_valid check (
        type in (
            'post_like', 'post_comment', 'comment_reply', 'comment_like',
            'follow', 'mention', 'message', 'story_reply', 'system'
        )
    );

create index if not exists notifications_user_created_idx
    on public.notifications (user_id, created_at desc);

-- ── Post like ──────────────────────────────────────────────────────────
create or replace function public.nexa_notify_post_like()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_author uuid;
begin
    select author_id into v_author from public.posts where id = new.post_id;
    if v_author is null or v_author = new.user_id then
        return new;
    end if;
    insert into public.notifications (user_id, actor_id, type, post_id)
    values (v_author, new.user_id, 'post_like', new.post_id);
    return new;
end;
$$;

drop trigger if exists nexa_notify_post_like on public.post_likes;
create trigger nexa_notify_post_like
after insert on public.post_likes
for each row execute function public.nexa_notify_post_like();

-- ── Comment / reply ────────────────────────────────────────────────────
create or replace function public.nexa_notify_comment()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_post_author uuid;
    v_parent_author uuid;
begin
    select author_id into v_post_author from public.posts where id = new.post_id;

    if new.parent_id is not null then
        select author_id into v_parent_author
        from public.comments where id = new.parent_id;

        if v_parent_author is not null and v_parent_author <> new.author_id then
            insert into public.notifications (user_id, actor_id, type, post_id, comment_id, preview)
            values (v_parent_author, new.author_id, 'comment_reply', new.post_id, new.id,
                    left(coalesce(new.text, ''), 80));
            return new;
        end if;
    end if;

    if v_post_author is not null and v_post_author <> new.author_id then
        insert into public.notifications (user_id, actor_id, type, post_id, comment_id, preview)
        values (v_post_author, new.author_id, 'post_comment', new.post_id, new.id,
                left(coalesce(new.text, ''), 80));
    end if;
    return new;
end;
$$;

drop trigger if exists nexa_notify_comment on public.comments;
create trigger nexa_notify_comment
after insert on public.comments
for each row execute function public.nexa_notify_comment();

-- ── Comment like ───────────────────────────────────────────────────────
create or replace function public.nexa_notify_comment_like()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_author uuid;
    v_post uuid;
begin
    select author_id, post_id into v_author, v_post
    from public.comments where id = new.comment_id;

    if v_author is null or v_author = new.user_id then
        return new;
    end if;
    insert into public.notifications (user_id, actor_id, type, post_id, comment_id)
    values (v_author, new.user_id, 'comment_like', v_post, new.comment_id);
    return new;
end;
$$;

drop trigger if exists nexa_notify_comment_like on public.comment_likes;
create trigger nexa_notify_comment_like
after insert on public.comment_likes
for each row execute function public.nexa_notify_comment_like();

-- ── Follow ─────────────────────────────────────────────────────────────
create or replace function public.nexa_notify_follow()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.notifications (user_id, actor_id, type)
    values (new.following_id, new.follower_id, 'follow');
    return new;
end;
$$;

drop trigger if exists nexa_notify_follow on public.follows;
create trigger nexa_notify_follow
after insert on public.follows
for each row execute function public.nexa_notify_follow();

-- ── Mentions ───────────────────────────────────────────────────────────
create or replace function public.nexa_notify_mentions()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    mentioned record;
begin
    if new.text is null or position('@' in new.text) = 0 then
        return new;
    end if;
    for mentioned in
        select distinct p.id
        from regexp_matches(new.text, '@([A-Za-z0-9_]{3,30})', 'g') as m(username)
        join public.profiles p on lower(p.username) = lower(m.username[1])
        where p.id <> new.author_id
          and p.account_status = 'ACTIVE'
          and public.nexa_can_mention(p.id)
    loop
        insert into public.notifications (user_id, actor_id, type, post_id, comment_id, preview)
        values (mentioned.id, new.author_id, 'mention', new.post_id, new.id, left(new.text, 140));
    end loop;
    return new;
end;
$$;

drop trigger if exists nexa_notify_mentions on public.comments;
create trigger nexa_notify_mentions
after insert on public.comments
for each row execute function public.nexa_notify_mentions();

-- ── Row Level Security ─────────────────────────────────────────────────
alter table public.notifications enable row level security;

drop policy if exists nexa_notifications_select on public.notifications;
create policy nexa_notifications_select on public.notifications
for select to authenticated using (user_id = auth.uid());

drop policy if exists nexa_notifications_update on public.notifications;
create policy nexa_notifications_update on public.notifications
for update to authenticated
using (user_id = auth.uid())
with check (user_id = auth.uid());

drop policy if exists nexa_notifications_delete on public.notifications;
create policy nexa_notifications_delete on public.notifications
for delete to authenticated using (user_id = auth.uid());

grant select, delete on public.notifications to authenticated;
grant update (is_read) on public.notifications to authenticated;

notify pgrst, 'reload schema';
