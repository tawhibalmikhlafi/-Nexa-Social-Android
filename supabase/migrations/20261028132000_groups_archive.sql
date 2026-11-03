-- Groups expansion follow-up: allow owners/admins to archive a group.

create or replace function public.set_group_archived(p_group_id uuid, p_archived boolean)
returns void
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_role text;
begin
    if v_uid is null then raise exception 'NOT_AUTHENTICATED'; end if;
    select role into v_role from public.group_members
        where group_id = p_group_id and user_id = v_uid and status = 'active';
    if v_role not in ('owner', 'admin') then raise exception 'NOT_AUTHORIZED'; end if;

    update public.groups
        set is_archived = p_archived,
            updated_at = now()
        where id = p_group_id;

    insert into public.group_moderation_log (group_id, actor_id, action, metadata)
    values (
        p_group_id,
        v_uid,
        case when p_archived then 'archive' else 'unarchive' end,
        jsonb_build_object('is_archived', p_archived)
    );
end;
$$;
revoke all on function public.set_group_archived(uuid, boolean) from public, anon;
grant execute on function public.set_group_archived(uuid, boolean) to authenticated;

notify pgrst, 'reload schema';
