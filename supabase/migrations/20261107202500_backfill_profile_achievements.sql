-- Backfill badge progress from existing Supabase activity so earlier work is retained.
update public.nexa_profile_achievements a
set progress = case a.achievement_code
  when 'FIRST_POST' then (select count(*)::integer from public.posts p where p.author_id=a.user_id)
  when 'ACTIVE_CREATOR' then (select count(*)::integer from public.posts p where p.author_id=a.user_id)
  when 'HELPFUL_VOICE' then (select count(*)::integer from public.comments c where c.author_id=a.user_id)
  when 'WORLD_EXPLORER' then (select count(*)::integer from public.group_members gm where gm.user_id=a.user_id)
  when 'WORLD_BUILDER' then (select count(*)::integer from public.groups g where g.owner_id=a.user_id)
  when 'SOCIAL_CONNECTOR' then (select count(*)::integer from public.follows f where f.follower_id=a.user_id)
  when 'CLIP_MAKER' then (select count(*)::integer from public.clips c where c.author_id=a.user_id)
  when 'STORYTELLER' then (select count(*)::integer from public.stories s where s.author_id=a.user_id)
  else a.progress
end,
updated_at=now()
where a.achievement_code <> 'RISING_STAR';

update public.nexa_profile_achievements a
set unlocked_at=case when a.progress>=a.target then coalesce(a.unlocked_at,now()) else a.unlocked_at end,
    updated_at=now()
where a.achievement_code <> 'RISING_STAR';

with earned as (
  select user_id,coalesce(sum(reward_points),0)::integer as points
  from public.nexa_profile_achievements
  where unlocked_at is not null and achievement_code <> 'RISING_STAR'
  group by user_id
)
update public.nexa_profile_achievements a
set progress=least(coalesce(e.points,0),a.target),
    unlocked_at=case when coalesce(e.points,0)>=a.target then coalesce(a.unlocked_at,now()) else a.unlocked_at end,
    updated_at=now()
from earned e
where a.user_id=e.user_id and a.achievement_code='RISING_STAR';
