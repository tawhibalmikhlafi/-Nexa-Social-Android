-- Let an authenticated account remove a follower without exposing direct DELETE access.
-- The caller may only remove an edge whose following_id is the caller's own profile.
create or replace function public.nexa_remove_follower(p_follower_id uuid)
returns boolean
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    removed_count integer;
begin
    if auth.uid() is null or p_follower_id is null or p_follower_id = auth.uid() then
        return false;
    end if;

    delete from public.follows
    where follower_id = p_follower_id
      and following_id = auth.uid();

    get diagnostics removed_count = row_count;
    return removed_count > 0;
end;
$$;

revoke all on function public.nexa_remove_follower(uuid) from public;
grant execute on function public.nexa_remove_follower(uuid) to authenticated;

notify pgrst, 'reload schema';
