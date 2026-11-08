-- Owner moderation: distinct ban, restriction and restore actions with an immutable audit trail.
alter table public.profiles
  drop constraint if exists profiles_account_status_valid;
alter table public.profiles
  add constraint profiles_account_status_valid
  check (account_status = any (array[
    'ACTIVE'::text,
    'PENDING_VERIFICATION'::text,
    'SUSPENDED'::text,
    'BANNED'::text,
    'DEACTIVATED'::text,
    'DELETED'::text
  ]));

create table if not exists public.nexa_user_moderation_log (
  id uuid primary key default gen_random_uuid(),
  target_user_id uuid not null,
  actor_user_id uuid not null references auth.users(id),
  action text not null check (action in ('BAN','RESTRICT','RESTORE')),
  reason text not null,
  created_at timestamptz not null default now()
);
alter table public.nexa_user_moderation_log enable row level security;
revoke all on public.nexa_user_moderation_log from public, anon, authenticated;

create or replace function public.nexa_owner_moderate_user(
  p_user_id uuid,
  p_action text,
  p_reason text default 'إجراء إداري من لوحة مالك Nexa'
)
returns boolean
language plpgsql
security definer
set search_path = public, auth
as $$
declare next_status text;
begin
  if not public.nexa_is_owner() then
    raise exception 'OWNER_REQUIRED' using errcode = '42501';
  end if;
  if p_user_id is null or p_user_id = auth.uid() then
    raise exception 'CANNOT_MODERATE_SELF';
  end if;
  if exists(select 1 from public.nexa_admin_roles where user_id=p_user_id and role='OWNER') then
    raise exception 'CANNOT_MODERATE_OWNER';
  end if;
  next_status := case upper(p_action)
    when 'BAN' then 'BANNED'
    when 'RESTRICT' then 'SUSPENDED'
    when 'RESTORE' then 'ACTIVE'
    else null
  end;
  if next_status is null then raise exception 'INVALID_MODERATION_ACTION'; end if;

  update public.profiles
     set account_status=next_status, updated_at=now()
   where id=p_user_id;
  if not found then raise exception 'PROFILE_NOT_FOUND'; end if;

  insert into public.nexa_user_moderation_log(target_user_id,actor_user_id,action,reason)
  values(p_user_id,auth.uid(),upper(p_action),left(trim(coalesce(p_reason,'')),1000));
  return true;
end;
$$;
revoke all on function public.nexa_owner_moderate_user(uuid,text,text) from public, anon;
grant execute on function public.nexa_owner_moderate_user(uuid,text,text) to authenticated;

create or replace function public.nexa_owner_list_moderation_log(p_user_id uuid default null, p_limit integer default 100)
returns table(id uuid,target_user_id uuid,action text,reason text,actor_user_id uuid,created_at timestamptz)
language plpgsql
stable
security definer
set search_path = public, auth
as $$
begin
  if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
  return query
    select l.id,l.target_user_id,l.action,l.reason,l.actor_user_id,l.created_at
    from public.nexa_user_moderation_log l
    where p_user_id is null or l.target_user_id=p_user_id
    order by l.created_at desc
    limit greatest(1,least(coalesce(p_limit,100),200));
end;
$$;
revoke all on function public.nexa_owner_list_moderation_log(uuid,integer) from public, anon;
grant execute on function public.nexa_owner_list_moderation_log(uuid,integer) to authenticated;
notify pgrst, 'reload schema';
