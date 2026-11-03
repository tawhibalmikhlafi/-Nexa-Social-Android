-- Nexa story audience: real views and interactions (replies) from the database.
-- Story views are recorded once per viewer. Only the story author can read the audience.

create table if not exists public.story_views (
    story_id uuid not null references public.stories (id) on delete cascade,
    viewer_id uuid not null references public.profiles (id) on delete cascade,
    viewed_at timestamptz not null default now(),
    primary key (story_id, viewer_id)
);

create index if not exists story_views_story_viewed_idx
    on public.story_views (story_id, viewed_at desc);

alter table public.story_views enable row level security;

drop policy if exists nexa_story_views_select on public.story_views;
create policy nexa_story_views_select on public.story_views
for select to authenticated
using (
    exists (
        select 1 from public.stories s
        where s.id = story_views.story_id
          and s.author_id = auth.uid()
    )
);

-- Direct writes are not exposed to clients; the RPC below uses security definer.
revoke all on public.story_views from public;
revoke all on public.story_views from anon;
revoke all on public.story_views from authenticated;
grant select on public.story_views to authenticated;

-- Record the current user's view once. Idempotent and safe to call repeatedly.
create or replace function public.record_story_view(target_story_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    viewer uuid := auth.uid();
    story_author uuid;
    story_expires timestamptz;
begin
    if viewer is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;

    select s.author_id, s.expires_at
        into story_author, story_expires
    from public.stories s
    where s.id = target_story_id;

    if story_author is null or story_expires <= now() then
        return false;
    end if;
    if story_author = viewer then
        return false;
    end if;

    insert into public.story_views (story_id, viewer_id)
    values (target_story_id, viewer)
    on conflict (story_id, viewer_id) do nothing;

    return true;
end;
$$;

revoke all on function public.record_story_view(uuid) from public;
revoke all on function public.record_story_view(uuid) from anon;
grant execute on function public.record_story_view(uuid) to authenticated;

-- Owner-only audience list: everyone who viewed and/or replied to the story.
create or replace function public.get_story_audience(target_story_id uuid)
returns table (
    user_id uuid,
    username text,
    display_name text,
    avatar_url text,
    account_origin text,
    viewed_at timestamptz,
    interacted_at timestamptz,
    interaction_text text,
    interaction_count bigint
)
language plpgsql
security definer
set search_path = public
as $$
declare
    viewer uuid := auth.uid();
    story_author uuid;
begin
    if viewer is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;

    select s.author_id into story_author
    from public.stories s
    where s.id = target_story_id;

    if story_author is null then
        raise exception 'story not found' using errcode = 'P0002';
    end if;
    if story_author <> viewer then
        raise exception 'not story owner' using errcode = '42501';
    end if;

    return query
    with story_view_rows as (
        select sv.viewer_id as uid, min(sv.viewed_at) as first_viewed_at
        from public.story_views sv
        where sv.story_id = target_story_id
        group by sv.viewer_id
    ),
    story_reply_rows as (
        select
            m.sender_id as uid,
            count(*)::bigint as reply_count,
            max(m.created_at) as last_replied_at,
            (array_agg(nullif(m.text, '') order by m.created_at desc))[1] as last_reply
        from public.messages m
        where m.reply_to_story_id = target_story_id
          and not m.is_deleted
        group by m.sender_id
    ),
    audience as (
        select uid from story_view_rows
        union
        select uid from story_reply_rows
    )
    select
        a.uid,
        p.username,
        p.display_name,
        p.avatar_url,
        p.account_origin,
        v.first_viewed_at,
        r.last_replied_at,
        r.last_reply,
        coalesce(r.reply_count, 0)
    from audience a
    join public.profiles p on p.id = a.uid
    left join story_view_rows v on v.uid = a.uid
    left join story_reply_rows r on r.uid = a.uid
    order by coalesce(r.last_replied_at, v.first_viewed_at) desc nulls last,
             p.display_name asc;
end;
$$;

revoke all on function public.get_story_audience(uuid) from public;
revoke all on function public.get_story_audience(uuid) from anon;
grant execute on function public.get_story_audience(uuid) to authenticated;

notify pgrst, 'reload schema';
