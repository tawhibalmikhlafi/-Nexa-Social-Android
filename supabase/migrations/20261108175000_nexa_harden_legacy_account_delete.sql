-- Restrict the legacy RPC to signed-in users and pin its search path.
-- New app versions use the delete-own-account Edge Function for Storage cleanup + Auth deletion.

create or replace function public.delete_own_account()
returns void
language plpgsql
security definer
set search_path = ''
as $function$
begin
  if auth.uid() is null then
    raise exception 'not authenticated' using errcode = '42501';
  end if;
  delete from auth.users where id = auth.uid();
end;
$function$;

revoke all on function public.delete_own_account() from public, anon;
grant execute on function public.delete_own_account() to authenticated;
