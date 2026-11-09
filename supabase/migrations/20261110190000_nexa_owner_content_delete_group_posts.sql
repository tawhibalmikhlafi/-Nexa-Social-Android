alter table public.nexa_owner_content_deletion_log
  drop constraint if exists nexa_owner_content_deletion_log_content_type_check;

alter table public.nexa_owner_content_deletion_log
  add constraint nexa_owner_content_deletion_log_content_type_check
  check (content_type = any (array[
    'POST',
    'COMMENT',
    'CLIP',
    'CLIP_COMMENT',
    'GROUP_POST'
  ]::text[]));
