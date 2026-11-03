-- Real session management for the authenticated user.
--
-- Sessions are stored by Supabase Auth in auth.sessions. The Auth schema is never exposed through
-- PostgREST to the app; reads go through the security-definer RPC below. Individual revocation is
-- handled by the revoke-session Edge Function, which verifies the caller and delegates to the
-- service-role-only helper at the bottom of this file.

drop function if exists public.get_my_sessions();

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

-- Remove the previous client-callable delete RPC. The app now uses the Edge Function so that
-- identity and session ownership are verified server-side before any session is removed.
drop function if exists public.revoke_my_session(uuid);

-- Service-role-only helper for the revoke-session Edge Function.
--
-- The Edge Function first resolves the caller from their access token and passes the resulting
-- user id here, so a session can only be removed when it belongs to that user. Deleting the
-- auth.sessions row also cascades to auth.refresh_tokens.session_id, which invalidates the
-- session's refresh tokens (the same rows GoTrue removes when it signs a session out).
create or replace function public.admin_revoke_session(
    target_user_id uuid,
    target_session_id uuid
)
returns boolean
language plpgsql
security definer
set search_path = public, auth
as $$
declare
    removed integer;
begin
    delete from auth.sessions
    where id = target_session_id
      and user_id = target_user_id;

    get diagnostics removed = row_count;
    return removed > 0;
end;
$$;

revoke all on function public.admin_revoke_session(uuid, uuid) from public;
revoke all on function public.admin_revoke_session(uuid, uuid) from anon;
revoke all on function public.admin_revoke_session(uuid, uuid) from authenticated;
grant execute on function public.admin_revoke_session(uuid, uuid) to service_role;

notify pgrst, 'reload schema';
