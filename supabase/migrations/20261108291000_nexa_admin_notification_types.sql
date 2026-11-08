-- Allow notification types emitted by owner announcements and login security alerts.
alter table public.notifications
  drop constraint if exists notifications_type_valid;

alter table public.notifications
  add constraint notifications_type_valid
  check (type = any (array[
    'post_like','post_comment','comment_reply','comment_like',
    'follow','follow_request_accepted','mention','message',
    'story_reply','system','group_join_request','group_join_approved',
    'group_join_rejected','group_post_report',
    'ANNOUNCEMENT','LOGIN_ALERT'
  ]::text[]));

notify pgrst, 'reload schema';
