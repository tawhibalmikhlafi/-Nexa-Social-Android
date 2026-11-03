-- Comment images live in the public post-media bucket under <uid>/comments/...
alter table public.comments
    add column if not exists image_uri text;

alter table public.comments
    drop constraint if exists comments_text_check;

alter table public.comments
    add constraint comments_text_check check (
        length(text) <= 1000
        and (length(text) >= 1 or image_uri is not null)
    );

notify pgrst, 'reload schema';
