-- Account deletion support for Nexa.
-- Storage objects are enumerated through a caller-scoped RPC and removed via the Storage API.
-- Non-cascading foreign keys are anonymized before auth.users is deleted.

create or replace function public.get_my_storage_objects_for_deletion()
returns table(bucket_id text, name text)
language plpgsql
security definer
set search_path = ''
as $function$
declare
  current_user_id text := auth.uid()::text;
begin
  if current_user_id is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;

  return query
  select o.bucket_id::text, o.name::text
  from storage.objects o
  where o.owner_id = current_user_id
     or o.owner = current_user_id;
end;
$function$;

revoke all on function public.get_my_storage_objects_for_deletion() from public, anon;
grant execute on function public.get_my_storage_objects_for_deletion() to authenticated;

create or replace function public.prepare_my_account_deletion()
returns void
language plpgsql
security definer
set search_path = ''
as $function$
declare
  current_user_id uuid := auth.uid();
begin
  if current_user_id is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;

  -- Preserve audit history while removing identifying foreign-key references.
  update public.group_join_requests
     set reviewed_by = null
   where reviewed_by = current_user_id;

  update public.group_moderation_log
     set target_user_id = null
   where target_user_id = current_user_id;

  update public.nexa_profile_analytics_events
     set viewer_id = null
   where viewer_id = current_user_id;

  update public.nexa_profile_testimonials
     set author_id = null
   where author_id = current_user_id;

  update auth.scim_users
     set user_id = null
   where user_id = current_user_id;
end;
$function$;

revoke all on function public.prepare_my_account_deletion() from public, anon;
grant execute on function public.prepare_my_account_deletion() to authenticated;
