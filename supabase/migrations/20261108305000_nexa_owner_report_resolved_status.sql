alter table public.content_reports drop constraint if exists content_reports_status_check;
alter table public.content_reports add constraint content_reports_status_check check(status=any(array['pending'::text,'reviewed'::text,'resolved'::text,'dismissed'::text]));
create or replace function public.nexa_owner_update_report(p_report_id uuid,p_status text,p_note text default '')
returns boolean language plpgsql security definer set search_path=public,auth as $$
declare old_status text; normalized_status text;
begin
 if not public.nexa_is_owner() then raise exception 'OWNER_REQUIRED' using errcode='42501'; end if;
 normalized_status:=lower(coalesce(p_status,''));
 if normalized_status not in ('pending','open','reviewed','resolved','dismissed') then raise exception 'INVALID_REPORT_STATUS'; end if;
 if normalized_status='open' then normalized_status:='pending'; end if;
 select coalesce(status,'pending') into old_status from public.content_reports where id=p_report_id for update;
 if not found then raise exception 'REPORT_NOT_FOUND'; end if;
 update public.content_reports set status=normalized_status,reviewed_by=auth.uid(),reviewed_at=now() where id=p_report_id;
 insert into public.nexa_owner_report_audit(report_id,actor_id,previous_status,new_status,note)
 values(p_report_id,auth.uid(),old_status,upper(normalized_status),left(trim(coalesce(p_note,'')),1000));
 return true;
end $$;
revoke all on function public.nexa_owner_update_report(uuid,text,text) from public,anon;
grant execute on function public.nexa_owner_update_report(uuid,text,text) to authenticated;
