-- Nexa remote stories. Stories are visible to every authenticated account for 24 hours.

create table if not exists public.stories (
    id uuid primary key default gen_random_uuid(),
    author_id uuid not null references public.profiles (id) on delete cascade,
    image_uri text,
    text text not null default '',
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '24 hours'),
    constraint stories_content_present check (
        image_uri is not null or length(btrim(text)) > 0
    )
);

create index if not exists stories_expires_at_idx on public.stories (expires_at);
create index if not exists stories_author_created_idx
    on public.stories (author_id, created_at desc);

alter table public.stories enable row level security;

drop policy if exists nexa_stories_select on public.stories;
create policy nexa_stories_select on public.stories
for select to authenticated
using (expires_at > now());

drop policy if exists nexa_stories_insert on public.stories;
create policy nexa_stories_insert on public.stories
for insert to authenticated
with check (author_id = auth.uid());

drop policy if exists nexa_stories_delete on public.stories;
create policy nexa_stories_delete on public.stories
for delete to authenticated
using (author_id = auth.uid());

grant select, insert, delete on public.stories to authenticated;

notify pgrst, 'reload schema';
