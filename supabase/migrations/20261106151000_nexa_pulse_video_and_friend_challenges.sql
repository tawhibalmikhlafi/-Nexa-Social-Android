-- Pulse video entries and friend-to-friend challenges.
alter table public.pulse_entries
  add column if not exists video_path text;

alter table public.pulse_entries
  drop constraint if exists pulse_entry_body_length;
alter table public.pulse_entries
  drop constraint if exists pulse_entry_has_content;
alter table public.pulse_entries
  add constraint pulse_entry_has_content check (
    (char_length(trim(coalesce(body, ''))) between 1 and 1000)
    or (char_length(trim(coalesce(body, ''))) = 0 and (image_path is not null or video_path is not null))
  ),
  add constraint pulse_entry_single_media check (not (image_path is not null and video_path is not null));

update storage.buckets
set file_size_limit = 52428800,
    allowed_mime_types = array['image/jpeg','image/png','image/webp','video/mp4','video/quicktime','video/webm','video/3gpp']
where id = 'nexa-pulse';

create table if not exists public.pulse_friend_challenges (
  id uuid primary key default gen_random_uuid(),
  prompt_id uuid not null references public.pulse_prompts(id) on delete cascade,
  challenger_id uuid not null references auth.users(id) on delete cascade,
  challenged_user_id uuid not null references auth.users(id) on delete cascade,
  message text not null default '',
  status text not null default 'pending' check (status in ('pending','accepted','declined')),
  created_at timestamptz not null default now(),
  responded_at timestamptz,
  constraint pulse_friend_challenge_not_self check (challenger_id <> challenged_user_id),
  constraint pulse_friend_challenge_message_length check (char_length(message) <= 240)
);

create index if not exists pulse_friend_challenges_inbox_idx
  on public.pulse_friend_challenges (challenged_user_id, status, created_at desc);
create index if not exists pulse_friend_challenges_sender_idx
  on public.pulse_friend_challenges (challenger_id, created_at desc);
create unique index if not exists pulse_friend_challenge_pending_unique
  on public.pulse_friend_challenges (prompt_id, challenger_id, challenged_user_id)
  where status = 'pending';

alter table public.pulse_friend_challenges enable row level security;
revoke all on public.pulse_friend_challenges from anon, authenticated;
grant select, insert, update on public.pulse_friend_challenges to authenticated;

drop policy if exists "pulse friend challenges visible to participants" on public.pulse_friend_challenges;
create policy "pulse friend challenges visible to participants"
on public.pulse_friend_challenges for select to authenticated
using (challenger_id = (select auth.uid()) or challenged_user_id = (select auth.uid()));

drop policy if exists "pulse challenge mutual followers can invite" on public.pulse_friend_challenges;
create policy "pulse challenge mutual followers can invite"
on public.pulse_friend_challenges for insert to authenticated
with check (
  challenger_id = (select auth.uid())
  and status = 'pending'
  and exists (
    select 1 from public.pulse_prompts p
    where p.id = prompt_id and p.is_published = true
      and now() >= p.starts_at and now() < p.ends_at
  )
  and exists (
    select 1 from public.follows f
    where f.follower_id = challenger_id and f.following_id = challenged_user_id
  )
  and exists (
    select 1 from public.follows f
    where f.follower_id = challenged_user_id and f.following_id = challenger_id
  )
);

drop policy if exists "pulse invitee responds to challenge" on public.pulse_friend_challenges;
create policy "pulse invitee responds to challenge"
on public.pulse_friend_challenges for update to authenticated
using (challenged_user_id = (select auth.uid()) and status = 'pending')
with check (
  challenged_user_id = (select auth.uid())
  and status in ('accepted','declined')
);
