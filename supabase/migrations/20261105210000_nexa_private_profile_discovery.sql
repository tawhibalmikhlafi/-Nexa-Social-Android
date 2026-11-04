-- Keep a public privacy flag on profile rows so search/profile screens can show
-- the private-account shell without exposing private posts or relationship lists.
alter table public.profiles
    add column if not exists is_private_account boolean not null default false;

update public.profiles p
set is_private_account = coalesce(s.is_private_account, false)
from public.user_privacy_settings s
where s.user_id = p.id
  and p.is_private_account is distinct from coalesce(s.is_private_account, false);

create or replace function public.nexa_sync_profile_private_flag()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    update public.profiles
       set is_private_account = coalesce(new.is_private_account, false)
     where id = new.user_id;
    return new;
end;
$$;

drop trigger if exists nexa_sync_profile_private_flag on public.user_privacy_settings;
create trigger nexa_sync_profile_private_flag
after insert or update of is_private_account on public.user_privacy_settings
for each row execute function public.nexa_sync_profile_private_flag();

notify pgrst, 'reload schema';
