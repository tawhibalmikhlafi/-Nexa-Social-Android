-- Real comments and server-awarded achievements for Nexa Pulse.
create table if not exists public.pulse_comments (
  id uuid primary key default gen_random_uuid(),
  entry_id uuid not null references public.pulse_entries(id) on delete cascade,
  user_id uuid not null references auth.users(id) on delete cascade,
  body text not null,
  created_at timestamptz not null default now(),
  constraint pulse_comment_body_length check (char_length(trim(body)) between 1 and 500)
);
create index if not exists pulse_comments_entry_created_idx
  on public.pulse_comments (entry_id, created_at asc);
alter table public.pulse_comments enable row level security;
revoke all on public.pulse_comments from anon, authenticated;
grant select, insert, delete on public.pulse_comments to authenticated;

drop policy if exists "pulse comments readable" on public.pulse_comments;
create policy "pulse comments readable" on public.pulse_comments
for select to authenticated using (
  exists (select 1 from public.pulse_entries e
    join public.pulse_prompts p on p.id = e.prompt_id
    where e.id = entry_id and p.is_published = true)
);
drop policy if exists "pulse users comment as themselves" on public.pulse_comments;
create policy "pulse users comment as themselves" on public.pulse_comments
for insert to authenticated with check (
  user_id = (select auth.uid())
  and char_length(trim(body)) between 1 and 500
  and exists (select 1 from public.pulse_entries e
    join public.pulse_prompts p on p.id = e.prompt_id
    where e.id = entry_id and p.is_published = true
      and now() >= p.starts_at and now() < p.ends_at)
);
drop policy if exists "pulse users delete own comments" on public.pulse_comments;
create policy "pulse users delete own comments" on public.pulse_comments
for delete to authenticated using (user_id = (select auth.uid()));

create table if not exists public.pulse_user_badges (
  user_id uuid not null references auth.users(id) on delete cascade,
  badge_id text not null check (badge_id in (
    'pulse_first_entry','pulse_five_entries','pulse_first_comment',
    'pulse_ten_comments','pulse_ten_likes'
  )),
  earned_at timestamptz not null default now(),
  primary key (user_id, badge_id)
);
create index if not exists pulse_user_badges_user_earned_idx
  on public.pulse_user_badges (user_id, earned_at desc);
alter table public.pulse_user_badges enable row level security;
revoke all on public.pulse_user_badges from anon, authenticated;
grant select on public.pulse_user_badges to authenticated;
drop policy if exists "users read own pulse badges" on public.pulse_user_badges;
create policy "users read own pulse badges" on public.pulse_user_badges
for select to authenticated using (user_id = (select auth.uid()));

create or replace function public.award_pulse_badges()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  target_user uuid;
  entry_owner uuid;
  activity_count bigint;
begin
  if tg_table_name = 'pulse_entries' then
    target_user := new.user_id;
    insert into public.pulse_user_badges(user_id, badge_id)
      values (target_user, 'pulse_first_entry') on conflict do nothing;
    select count(*) into activity_count from public.pulse_entries where user_id = target_user;
    if activity_count >= 5 then
      insert into public.pulse_user_badges(user_id, badge_id)
        values (target_user, 'pulse_five_entries') on conflict do nothing;
    end if;
  elsif tg_table_name = 'pulse_comments' then
    target_user := new.user_id;
    insert into public.pulse_user_badges(user_id, badge_id)
      values (target_user, 'pulse_first_comment') on conflict do nothing;
    select count(*) into activity_count from public.pulse_comments where user_id = target_user;
    if activity_count >= 10 then
      insert into public.pulse_user_badges(user_id, badge_id)
        values (target_user, 'pulse_ten_comments') on conflict do nothing;
    end if;
  elsif tg_table_name = 'pulse_reactions' then
    select user_id into entry_owner from public.pulse_entries where id = new.entry_id;
    if entry_owner is not null then
      select count(*) into activity_count from public.pulse_reactions r
        join public.pulse_entries e on e.id = r.entry_id
        where e.user_id = entry_owner;
      if activity_count >= 10 then
        insert into public.pulse_user_badges(user_id, badge_id)
          values (entry_owner, 'pulse_ten_likes') on conflict do nothing;
      end if;
    end if;
  end if;
  return new;
end;
$$;
revoke all on function public.award_pulse_badges() from public, anon, authenticated;
drop trigger if exists pulse_award_entry_badges on public.pulse_entries;
create trigger pulse_award_entry_badges after insert on public.pulse_entries
for each row execute function public.award_pulse_badges();
drop trigger if exists pulse_award_comment_badges on public.pulse_comments;
create trigger pulse_award_comment_badges after insert on public.pulse_comments
for each row execute function public.award_pulse_badges();
drop trigger if exists pulse_award_reaction_badges on public.pulse_reactions;
create trigger pulse_award_reaction_badges after insert on public.pulse_reactions
for each row execute function public.award_pulse_badges();
