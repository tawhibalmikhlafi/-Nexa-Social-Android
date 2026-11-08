-- Fix the shared profile-activity trigger: NEW is a table-specific record,
-- so fields must only be read after branching on TG_TABLE_NAME.
create or replace function public.nexa_award_profile_activity()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $function$
declare
  owner_id uuid;
  badge_code text;
  total_points integer;
begin
  if tg_table_name in ('posts', 'comments', 'clips', 'stories') then
    owner_id := new.author_id;
  elsif tg_table_name = 'follows' then
    owner_id := new.follower_id;
  elsif tg_table_name = 'groups' then
    owner_id := new.owner_id;
  elsif tg_table_name = 'group_members' then
    owner_id := new.user_id;
  else
    return new;
  end if;

  if owner_id is null then return new; end if;

  if tg_table_name = 'posts' then
    badge_code := 'FIRST_POST';
  elsif tg_table_name = 'comments' then
    badge_code := 'HELPFUL_VOICE';
  elsif tg_table_name = 'follows' then
    badge_code := 'SOCIAL_CONNECTOR';
  elsif tg_table_name = 'clips' then
    badge_code := 'CLIP_MAKER';
  elsif tg_table_name = 'stories' then
    badge_code := 'STORYTELLER';
  elsif tg_table_name = 'groups' then
    badge_code := 'WORLD_BUILDER';
  elsif tg_table_name = 'group_members' then
    badge_code := 'WORLD_EXPLORER';
  end if;

  update public.nexa_profile_achievements
     set progress = progress + 1, updated_at = now()
   where user_id = owner_id and achievement_code = badge_code;

  if tg_table_name = 'posts' then
    update public.nexa_profile_achievements
       set progress = progress + 1, updated_at = now()
     where user_id = owner_id and achievement_code = 'ACTIVE_CREATOR';
  end if;

  update public.nexa_profile_achievements
     set unlocked_at = coalesce(unlocked_at, now()), updated_at = now()
   where user_id = owner_id and unlocked_at is null and progress >= target;

  select coalesce(sum(reward_points), 0)
    into total_points
    from public.nexa_profile_achievements
   where user_id = owner_id and unlocked_at is not null;

  update public.nexa_profile_achievements
     set progress = least(total_points, 150),
         unlocked_at = case when total_points >= 150 then coalesce(unlocked_at, now()) else unlocked_at end,
         updated_at = now()
   where user_id = owner_id and achievement_code = 'RISING_STAR';

  return new;
end;
$function$;

revoke all on function public.nexa_award_profile_activity() from public, anon, authenticated;
