-- Enforce featured badge/favorite limits and provide deterministic expiry cleanup.
create or replace function public.nexa_limit_featured_profile_badges()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if new.is_featured and not old.is_featured then
    if (select count(*) from public.nexa_profile_achievements
        where user_id=new.user_id and is_featured and achievement_code<>new.achievement_code) >= 3 then
      raise exception 'You can feature at most three badges';
    end if;
  end if;
  return new;
end $$;
revoke all on function public.nexa_limit_featured_profile_badges() from public,anon,authenticated;
drop trigger if exists nexa_limit_featured_profile_badges_trigger on public.nexa_profile_achievements;
create trigger nexa_limit_featured_profile_badges_trigger
before update of is_featured on public.nexa_profile_achievements
for each row execute function public.nexa_limit_featured_profile_badges();

create or replace function public.nexa_limit_pinned_favorites()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if new.is_pinned and (tg_op='INSERT' or not old.is_pinned) then
    if (select count(*) from public.nexa_profile_favorites
        where user_id=new.user_id and is_pinned and id is distinct from new.id) >= 3 then
      raise exception 'You can pin at most three favorites';
    end if;
  end if;
  return new;
end $$;
revoke all on function public.nexa_limit_pinned_favorites() from public,anon,authenticated;
drop trigger if exists nexa_limit_pinned_favorites_trigger on public.nexa_profile_favorites;
create trigger nexa_limit_pinned_favorites_trigger
before insert or update of is_pinned on public.nexa_profile_favorites
for each row execute function public.nexa_limit_pinned_favorites();

create or replace function public.nexa_expire_profile_statuses()
returns integer language plpgsql security definer set search_path=public,pg_temp as $$
declare affected integer;
begin
  update public.nexa_profile_status
     set expires_at=expires_at, updated_at=now()
   where expires_at is not null and expires_at<=now();
  delete from public.nexa_profile_status
   where expires_at is not null and expires_at<=now();
  get diagnostics affected = row_count;
  return affected;
end $$;
revoke all on function public.nexa_expire_profile_statuses() from public,anon,authenticated;
-- Expired rows are already hidden by RLS even if no client has opened the profile.
notify pgrst,'reload schema';
