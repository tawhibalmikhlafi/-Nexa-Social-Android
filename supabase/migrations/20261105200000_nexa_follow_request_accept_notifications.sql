-- Notify the requester when the private account accepts their follow request.
alter table public.notifications
    drop constraint if exists notifications_type_valid;
alter table public.notifications
    add constraint notifications_type_valid check (
        type in (
            'post_like', 'post_comment', 'comment_reply', 'comment_like',
            'follow', 'follow_request_accepted', 'mention', 'message',
            'story_reply', 'system', 'group_join_request',
            'group_join_approved', 'group_join_rejected'
        )
    );

create or replace function public.nexa_notify_follow_request_accepted()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if old.status = 'pending' and new.status = 'accepted' then
        insert into public.notifications (user_id, actor_id, type, preview)
        values (new.requester_id, new.target_id, 'follow_request_accepted', '');
    end if;
    return new;
end;
$$;

drop trigger if exists nexa_notify_follow_request_accepted on public.follow_requests;
create trigger nexa_notify_follow_request_accepted
after update of status on public.follow_requests
for each row execute function public.nexa_notify_follow_request_accepted();

notify pgrst, 'reload schema';
