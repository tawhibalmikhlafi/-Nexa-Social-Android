-- Atomic username update for the authenticated Supabase user.
create or replace function public.nexa_change_username(p_username text)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid uuid := auth.uid();
    v_username text := lower(btrim(coalesce(p_username, '')));
    v_current text;
begin
    if v_uid is null then
        raise exception 'not authenticated' using errcode = '42501';
    end if;
    if v_username !~ '^[a-z][a-z0-9_]{2,19}$' then
        raise exception 'username format is invalid' using errcode = '22023';
    end if;
    if v_username = any (array['admin','administrator','root','nexa','support','official','system','help','helpdesk','moderator','mod','staff','team','null','undefined','api','www','mail','security','about','guest']) then
        raise exception 'username is reserved' using errcode = '22023';
    end if;
    select username into v_current from public.profiles where id = v_uid for update;
    if not found then
        raise exception 'profile not found' using errcode = 'P0002';
    end if;
    update public.profiles set username = v_username, updated_at = now() where id = v_uid;
    insert into public.usernames(username, user_id) values (v_username, v_uid)
    on conflict (user_id) do update set username = excluded.username;
    return true;
end;
$$;
revoke all on function public.nexa_change_username(text) from public;
grant execute on function public.nexa_change_username(text) to authenticated;
