-- Profile suite workflows, verified testimonials, private analytics and server-awarded badges.
alter table public.nexa_profile_achievements
  add column if not exists reward_points integer not null default 0;

-- Seed the existing badge catalog for each current/new profile.
create or replace function public.nexa_seed_profile_achievements()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  insert into public.nexa_profile_achievements(user_id,achievement_code,progress,target,reward_points)
  values
    (new.id,'FIRST_POST',0,1,10),
    (new.id,'ACTIVE_CREATOR',0,10,50),
    (new.id,'HELPFUL_VOICE',0,5,25),
    (new.id,'WORLD_EXPLORER',0,3,30),
    (new.id,'WORLD_BUILDER',0,1,40),
    (new.id,'SOCIAL_CONNECTOR',0,5,35),
    (new.id,'CLIP_MAKER',0,1,20),
    (new.id,'STORYTELLER',0,1,15),
    (new.id,'RISING_STAR',0,150,0)
  on conflict(user_id,achievement_code) do nothing;
  return new;
end $$;
revoke all on function public.nexa_seed_profile_achievements() from public,anon,authenticated;
drop trigger if exists nexa_seed_profile_achievements_trigger on public.profiles;
create trigger nexa_seed_profile_achievements_trigger after insert on public.profiles
for each row execute function public.nexa_seed_profile_achievements();

insert into public.nexa_profile_achievements(user_id,achievement_code,progress,target,reward_points)
select p.id,b.code,0,b.target,b.reward
from public.profiles p
cross join (values
 ('FIRST_POST',1,10),('ACTIVE_CREATOR',10,50),('HELPFUL_VOICE',5,25),
 ('WORLD_EXPLORER',3,30),('WORLD_BUILDER',1,40),('SOCIAL_CONNECTOR',5,35),
 ('CLIP_MAKER',1,20),('STORYTELLER',1,15),('RISING_STAR',150,0)
) as b(code,target,reward)
on conflict(user_id,achievement_code) do nothing;

create or replace function public.nexa_award_profile_activity()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
declare
  owner_id uuid;
  badge_code text;
  earned_points integer;
  total_points integer;
begin
  owner_id := case tg_table_name
    when 'posts' then new.author_id
    when 'comments' then new.author_id
    when 'follows' then new.follower_id
    when 'clips' then new.author_id
    when 'stories' then new.author_id
    when 'groups' then new.owner_id
    when 'group_members' then new.user_id
    else null end;
  if owner_id is null then return new; end if;
  badge_code := case tg_table_name
    when 'posts' then 'FIRST_POST'
    when 'comments' then 'HELPFUL_VOICE'
    when 'follows' then 'SOCIAL_CONNECTOR'
    when 'clips' then 'CLIP_MAKER'
    when 'stories' then 'STORYTELLER'
    when 'groups' then 'WORLD_BUILDER'
    when 'group_members' then 'WORLD_EXPLORER'
    else null end;

  if badge_code is not null then
    update public.nexa_profile_achievements
       set progress = progress + 1, updated_at=now()
     where user_id=owner_id and achievement_code=badge_code;
    if tg_table_name='posts' then
      update public.nexa_profile_achievements
         set progress = progress + 1, updated_at=now()
       where user_id=owner_id and achievement_code='ACTIVE_CREATOR';
    end if;
    update public.nexa_profile_achievements
       set unlocked_at=coalesce(unlocked_at,now()), updated_at=now()
     where user_id=owner_id and unlocked_at is null and progress >= target;
    select coalesce(sum(reward_points),0) into total_points
      from public.nexa_profile_achievements
     where user_id=owner_id and unlocked_at is not null;
    update public.nexa_profile_achievements
       set progress=least(total_points,150),
           unlocked_at=case when total_points>=150 then coalesce(unlocked_at,now()) else unlocked_at end,
           updated_at=now()
     where user_id=owner_id and achievement_code='RISING_STAR';
  end if;
  return new;
end $$;
revoke all on function public.nexa_award_profile_activity() from public,anon,authenticated;

drop trigger if exists nexa_award_posts on public.posts;
create trigger nexa_award_posts after insert on public.posts for each row execute function public.nexa_award_profile_activity();
drop trigger if exists nexa_award_comments on public.comments;
create trigger nexa_award_comments after insert on public.comments for each row execute function public.nexa_award_profile_activity();
drop trigger if exists nexa_award_follows on public.follows;
create trigger nexa_award_follows after insert on public.follows for each row execute function public.nexa_award_profile_activity();
drop trigger if exists nexa_award_clips on public.clips;
create trigger nexa_award_clips after insert on public.clips for each row execute function public.nexa_award_profile_activity();
drop trigger if exists nexa_award_stories on public.stories;
create trigger nexa_award_stories after insert on public.stories for each row execute function public.nexa_award_profile_activity();
drop trigger if exists nexa_award_groups on public.groups;
create trigger nexa_award_groups after insert on public.groups for each row execute function public.nexa_award_profile_activity();
drop trigger if exists nexa_award_group_members on public.group_members;
create trigger nexa_award_group_members after insert on public.group_members for each row execute function public.nexa_award_profile_activity();

-- Public request URLs contain an unguessable token, but only a server function can consume it.
create or replace function public.nexa_create_testimonial_request(recipient_email text default null)
returns table(request_token uuid, expires_at timestamptz)
language plpgsql security definer set search_path=public,pg_temp as $$
declare created public.nexa_testimonial_requests;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  insert into public.nexa_testimonial_requests(profile_id,recipient_email)
  values(auth.uid(),nullif(btrim(recipient_email),''))
  returning * into created;
  return query select created.request_token,created.expires_at;
end $$;
revoke all on function public.nexa_create_testimonial_request(text) from public,anon;
grant execute on function public.nexa_create_testimonial_request(text) to authenticated;

create or replace function public.nexa_submit_testimonial(
  request_token uuid,
  author_name text,
  body text,
  rating smallint default null,
  author_title text default null,
  author_company text default null,
  linkedin_url text default null
)
returns uuid language plpgsql security definer set search_path=public,pg_temp as $$
declare req public.nexa_testimonial_requests;
declare new_id uuid;
begin
  if auth.uid() is null then raise exception 'Authentication required'; end if;
  if char_length(btrim(coalesce(author_name,''))) not between 1 and 100 then raise exception 'Invalid author name'; end if;
  if char_length(coalesce(body,'')) > 500 then raise exception 'Testimonial is too long'; end if;
  if rating is not null and rating not between 1 and 5 then raise exception 'Invalid rating'; end if;
  select * into req from public.nexa_testimonial_requests
   where nexa_testimonial_requests.request_token=nexa_submit_testimonial.request_token
     and used_at is null and expires_at>now()
   for update;
  if not found then raise exception 'Request link is invalid or expired'; end if;
  if req.profile_id=auth.uid() then raise exception 'You cannot recommend yourself'; end if;
  if req.recipient_email is not null and lower(req.recipient_email) <> lower(coalesce((select email from auth.users where id=auth.uid()),'')) then
    raise exception 'This request belongs to another email address';
  end if;
  perform set_config('nexa.verified_testimonial','true',true);
  insert into public.nexa_profile_testimonials(
    profile_id,author_id,author_name,author_title,author_company,body,rating,
    is_verified,is_approved,is_visible,source_type
  ) values (
    req.profile_id,auth.uid(),btrim(author_name),nullif(btrim(author_title),''),nullif(btrim(author_company),''),
    btrim(body),rating,true,true,true,'TEXT'
  ) returning id into new_id;
  update public.nexa_testimonial_requests set used_at=now() where id=req.id;
  insert into public.nexa_profile_analytics_events(profile_id,viewer_id,event_type,event_key)
  values(req.profile_id,auth.uid(),'TESTIMONIAL_RECEIVED',new_id::text);
  return new_id;
end $$;
revoke all on function public.nexa_submit_testimonial(uuid,text,text,smallint,text,text,text) from public,anon;
grant execute on function public.nexa_submit_testimonial(uuid,text,text,smallint,text,text,text) to authenticated;

-- A server-validated testimonial may be verified; clients cannot toggle verification flags.
create or replace function public.nexa_guard_profile_verified_flags()
returns trigger language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if coalesce(auth.role(),'') <> 'service_role'
     and current_setting('nexa.verified_testimonial',true) is distinct from 'true' then
    if tg_op='INSERT' then new.is_verified:=false;
    else new.is_verified:=old.is_verified;
    end if;
  end if;
  return new;
end $$;

create or replace function public.nexa_record_profile_analytics(
  target_profile uuid,event_kind text,event_label text default null
)
returns boolean language plpgsql security definer set search_path=public,pg_temp as $$
begin
  if auth.uid() is null or target_profile is null or target_profile=auth.uid() then return false; end if;
  if event_kind not in ('PROFILE_VIEW','SKILL_CLICK','SOCIAL_LINK_CLICK','CV_DOWNLOAD','QR_SCAN','TESTIMONIAL_RECEIVED') then return false; end if;
  if exists (
    select 1 from public.nexa_profile_analytics_events
    where profile_id=target_profile and viewer_id=auth.uid() and event_type=event_kind
      and coalesce(event_key,'')=coalesce(event_label,'')
      and created_at>now()-interval '15 minutes'
  ) then return true; end if;
  insert into public.nexa_profile_analytics_events(profile_id,viewer_id,event_type,event_key)
  values(target_profile,auth.uid(),event_kind,event_label);
  return true;
end $$;
revoke all on function public.nexa_record_profile_analytics(uuid,text,text) from public,anon;
grant execute on function public.nexa_record_profile_analytics(uuid,text,text) to authenticated;

notify pgrst,'reload schema';
