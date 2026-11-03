-- Drop temporary custom sessions table if it exists
drop table if exists public.user_sessions cascade;

-- Drop any previous definition first: `create or replace` fails (and leaves the old function
-- in place) when the returned column set differs, e.g. the earlier user_sessions-backed version.
drop function if exists public.get_my_sessions();

-- Secure RPC function to fetch active sessions for the authenticated user from auth.sessions.
-- Session revocation is handled by the revoke-session Edge Function (see the later migration).
create or replace function public.get_my_sessions()
returns table (
    id uuid,
    user_id uuid,
    created_at timestamptz,
    updated_at timestamptz,
    refreshed_at timestamp,
    not_after timestamptz,
    user_agent text,
    ip inet
)
language plpgsql
security definer
set search_path = public, auth
as $$
begin
    if auth.uid() is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;

    return query
    select
        s.id,
        s.user_id,
        s.created_at,
        s.updated_at,
        s.refreshed_at::timestamp,
        s.not_after,
        s.user_agent,
        s.ip
    from auth.sessions s
    where s.user_id = auth.uid()
    order by s.updated_at desc nulls last;
end;
$$;

revoke all on function public.get_my_sessions() from public;
revoke all on function public.get_my_sessions() from anon;
grant execute on function public.get_my_sessions() to authenticated;

notify pgrst, 'reload schema';
