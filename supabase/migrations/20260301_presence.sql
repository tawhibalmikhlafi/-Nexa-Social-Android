-- ═══════════════════════════════════════════════════════════════════════════
-- Presence: آخر ظهور في قاعدة البيانات + RPC للاستعلام.
-- آخر ظهور يُكتب من الخادم فقط عبر ping_presence، ويحترم show_activity_status.
-- ═══════════════════════════════════════════════════════════════════════════

alter table public.profiles add column if not exists last_seen_at timestamptz;

create index if not exists idx_profiles_last_seen
    on public.profiles (last_seen_at desc)
    where last_seen_at is not null;

-- ping — يحترم show_activity_status (لا يحدّث إن كان معطّلًا)
create or replace function public.ping_presence()
returns void
language plpgsql security definer set search_path = public as $$
declare
    v_uid uuid := auth.uid();
    v_allow boolean;
begin
    if v_uid is null then return; end if;
    select coalesce(s.show_activity_status, true)
        into v_allow
        from public.user_privacy_settings s
        where s.user_id = v_uid;
    if v_allow is null then v_allow := true; end if;
    if not v_allow then return; end if;
    update public.profiles set last_seen_at = now() where id = v_uid;
end;
$$;

revoke all on function public.ping_presence() from public, anon;
grant execute on function public.ping_presence() to authenticated;

-- استعلام دفعة (للقوائم) — is_online = false لمن أخفى نشاطه
create or replace function public.users_online(target_uids uuid[])
returns table (user_id uuid, is_online boolean)
language sql security definer set search_path = public as $$
    select p.id as user_id,
        coalesce(
            coalesce(s.show_activity_status, true) = true
            and p.last_seen_at is not null
            and (now() - p.last_seen_at) < interval '90 seconds',
            false
        ) as is_online
    from public.profiles p
    left join public.user_privacy_settings s on s.user_id = p.id
    where p.id = any(target_uids);
$$;

revoke all on function public.users_online(uuid[]) from public, anon;
grant execute on function public.users_online(uuid[]) to authenticated;

-- استعلام فردي
create or replace function public.is_user_online(target_uid uuid)
returns boolean
language sql security definer set search_path = public as $$
    select coalesce(
        (select u.is_online from public.users_online(array[target_uid]) u limit 1),
        false
    );
$$;

revoke all on function public.is_user_online(uuid) from public, anon;
grant execute on function public.is_user_online(uuid) to authenticated;

notify pgrst, 'reload schema';
