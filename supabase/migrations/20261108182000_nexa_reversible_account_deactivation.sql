-- Support reversible account deactivation and hide inactive accounts from public surfaces.
alter table public.profiles
    drop constraint if exists profiles_account_status_valid;

alter table public.profiles
    add constraint profiles_account_status_valid
    check (account_status = any (array[
        'ACTIVE'::text,
        'PENDING_VERIFICATION'::text,
        'SUSPENDED'::text,
        'DEACTIVATED'::text,
        'DELETED'::text
    ]));

drop policy if exists nexa_profiles_select on public.profiles;
create policy nexa_profiles_select
    on public.profiles
    for select
    to authenticated
    using (id = auth.uid() or account_status = 'ACTIVE');

create or replace function public.nexa_can_view_post(target_post_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $function$
    select exists (
        select 1
        from public.posts p
        where p.id = target_post_id
          and (
            p.author_id = auth.uid()
            or (
                exists (
                    select 1
                    from public.profiles owner_profile
                    where owner_profile.id = p.author_id
                      and owner_profile.account_status = 'ACTIVE'
                )
                and (p.audience = 'PUBLIC_LOCAL' or p.audience = 'FOLLOWERS')
                and (
                    not coalesce((
                        select s.is_private_account
                        from public.user_privacy_settings s
                        where s.user_id = p.author_id
                    ), false)
                    or exists (
                        select 1 from public.follows f
                        where f.follower_id = auth.uid()
                          and f.following_id = p.author_id
                    )
                )
                and (
                    p.audience = 'PUBLIC_LOCAL'
                    or exists (
                        select 1 from public.follows f
                        where f.follower_id = auth.uid()
                          and f.following_id = p.author_id
                    )
                )
            )
          )
    );
$function$;

drop policy if exists nexa_stories_select on public.stories;
create policy nexa_stories_select
    on public.stories
    for select
    to authenticated
    using (
        expires_at > now()
        and (
            author_id = auth.uid()
            or exists (
                select 1 from public.profiles owner_profile
                where owner_profile.id = stories.author_id
                  and owner_profile.account_status = 'ACTIVE'
            )
        )
    );

notify pgrst, 'reload schema';
