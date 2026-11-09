-- Allow both participants in a conversation to read only the message attachments
-- referenced by messages in conversations they belong to. Keep the bucket private.

drop policy if exists nexa_message_media_read on storage.objects;

create policy nexa_message_media_read
on storage.objects
for select
to authenticated
using (
    bucket_id = 'message-media'
    and exists (
        select 1
        from public.messages m
        join public.conversations c
          on c.id = m.conversation_id
        where m.attachment_path = storage.objects.name
          and (c.user_a = (select auth.uid()) or c.user_b = (select auth.uid()))
    )
);
