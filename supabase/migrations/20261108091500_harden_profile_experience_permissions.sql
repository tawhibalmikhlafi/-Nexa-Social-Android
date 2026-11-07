-- Tighten trust boundaries for profile verification, achievements and recommendations.
create or replace function public.nexa_can_view_profile(target_user uuid)
returns boolean
language sql
stable
security definer
set search_path = public, pg_temp
as $$
  select target_user = (select auth.uid())
    or (
      not exists (
        select 1 from public.blocks b
        where (b.blocker_id = (select auth.uid()) and b.blocked_id = target_user)
           or (b.blocker_id = target_user and b.blocked_id = (select auth.uid()))
      )
      and (
        exists (
          select 1 from public.profiles p
          where p.id = target_user and coalesce(p.is_private_account, false) = false
        )
        or exists (
          select 1 from public.follows f
          where f.follower_id = (select auth.uid()) and f.following_id = target_user
        )
      )
    );
$$;
revoke all on function public.nexa_can_view_profile(uuid) from public, anon;
grant execute on function public.nexa_can_view_profile(uuid) to authenticated;

create or replace function public.nexa_guard_profile_verified_flags()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if coalesce(auth.role(),'') <> 'service_role' then
    if tg_op = 'INSERT' then
      new.is_verified := false;
    else
      new.is_verified := old.is_verified;
    end if;
  end if;
  return new;
end;
$$;
revoke all on function public.nexa_guard_profile_verified_flags() from public, anon, authenticated;

drop trigger if exists nexa_credentials_verified_guard on public.nexa_profile_credentials;
create trigger nexa_credentials_verified_guard
before insert or update on public.nexa_profile_credentials
for each row execute function public.nexa_guard_profile_verified_flags();

drop trigger if exists nexa_languages_verified_guard on public.nexa_profile_languages;
create trigger nexa_languages_verified_guard
before insert or update on public.nexa_profile_languages
for each row execute function public.nexa_guard_profile_verified_flags();

drop trigger if exists nexa_testimonials_verified_guard on public.nexa_profile_testimonials;
create trigger nexa_testimonials_verified_guard
before insert or update on public.nexa_profile_testimonials
for each row execute function public.nexa_guard_profile_verified_flags();

-- A recommendation can only be added manually by the profile owner. A future
-- token-acceptance RPC will be the sole route for outside recommendations.
drop policy if exists nexa_testimonials_owner_insert on public.nexa_profile_testimonials;
create policy nexa_testimonials_owner_insert on public.nexa_profile_testimonials
for insert to authenticated
with check (profile_id = (select auth.uid()) and author_id is null and is_approved = true);

-- Achievement progress/unlocks are server-owned. Users can only choose visibility
-- and featured badges; they cannot grant themselves progress or unlocked_at.
drop policy if exists nexa_achievements_owner_write on public.nexa_profile_achievements;
drop policy if exists nexa_achievements_owner_update on public.nexa_profile_achievements;
create policy nexa_achievements_owner_update on public.nexa_profile_achievements
for update to authenticated
using (user_id = (select auth.uid()))
with check (user_id = (select auth.uid()));
revoke insert, delete on public.nexa_profile_achievements from authenticated;
revoke update on public.nexa_profile_achievements from authenticated;
grant update(is_visible,is_featured) on public.nexa_profile_achievements to authenticated;

-- Preserve an audit trail whenever a status is replaced or cleared.
create or replace function public.nexa_record_profile_status_history()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
  if tg_op = 'INSERT' then
    insert into public.nexa_profile_status_history(user_id,status_code,custom_text,expires_at)
    values(new.user_id,new.status_code,new.custom_text,new.expires_at);
    return new;
  elsif tg_op = 'UPDATE' then
    update public.nexa_profile_status_history
       set ended_at = now()
     where user_id = old.user_id and ended_at is null;
    insert into public.nexa_profile_status_history(user_id,status_code,custom_text,expires_at)
    values(new.user_id,new.status_code,new.custom_text,new.expires_at);
    return new;
  else
    update public.nexa_profile_status_history
       set ended_at = now()
     where user_id = old.user_id and ended_at is null;
    return old;
  end if;
end;
$$;
revoke all on function public.nexa_record_profile_status_history() from public, anon, authenticated;
drop trigger if exists nexa_profile_status_history_trigger on public.nexa_profile_status;
create trigger nexa_profile_status_history_trigger
after insert or update or delete on public.nexa_profile_status
for each row execute function public.nexa_record_profile_status_history();
