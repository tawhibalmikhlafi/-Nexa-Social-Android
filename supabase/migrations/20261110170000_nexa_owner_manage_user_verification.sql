create or replace function public.nexa_owner_set_user_verified(
  p_user_id uuid,
  p_verified boolean,
  p_verified_label text default null
)
returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
  v_label text;
begin
  if not public.nexa_is_owner() then
    raise exception 'OWNER_REQUIRED' using errcode = '42501';
  end if;
  if p_user_id is null or p_verified is null then
    raise exception 'INVALID_VERIFICATION_REQUEST';
  end if;
  v_label := case
    when p_verified then coalesce(nullif(btrim(p_verified_label), ''), 'موثق من Nexa')
    else null
  end;
  if p_verified and length(v_label) > 48 then
    raise exception 'VERIFIED_LABEL_TOO_LONG';
  end if;
  update public.profiles
     set is_verified = p_verified,
         verified_label = v_label,
         updated_at = now()
   where id = p_user_id;
  if not found then return false; end if;
  insert into public.nexa_owner_audit_events(actor_id, action, entity_type, entity_id, details)
  values (auth.uid(),
    case when p_verified then 'USER_VERIFIED' else 'USER_UNVERIFIED' end,
    'USER', p_user_id,
    jsonb_build_object('verified', p_verified, 'verified_label', v_label));
  return true;
end;
$$;
revoke all on function public.nexa_owner_set_user_verified(uuid, boolean, text) from public, anon;
grant execute on function public.nexa_owner_set_user_verified(uuid, boolean, text) to authenticated;
notify pgrst, 'reload schema';
