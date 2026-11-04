-- Prevent the daily scheduler from appending an unlimited chain of future challenges.
-- Keep at most one upcoming published prompt and schedule the next one after the current prompt ends.
create or replace function public.publish_next_pulse_prompt()
returns void
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
  v_start timestamptz;
  v_title text;
  v_description text;
begin
  perform pg_advisory_xact_lock(hashtext('public.publish_next_pulse_prompt'));

  -- If a future challenge is already queued, do not create another one.
  if exists (
    select 1
    from public.pulse_prompts
    where is_published = true
      and starts_at > now()
  ) then
    return;
  end if;

  -- Schedule directly after the current challenge; if there is a gap, start now.
  select min(ends_at)
    into v_start
  from public.pulse_prompts
  where is_published = true
    and starts_at <= now()
    and ends_at > now();

  if v_start is null then
    v_start := now();
  end if;

  select b.title, b.description
    into v_title, v_description
  from public.pulse_prompt_bank b
  where b.is_active = true
    and not exists (
      select 1
      from public.pulse_prompts p
      where p.title = b.title
        and p.starts_at > now() - interval '30 days'
    )
  order by random()
  limit 1;

  -- If all prompts were used recently, allow a controlled repeat from the active bank.
  if v_title is null then
    select title, description
      into v_title, v_description
    from public.pulse_prompt_bank
    where is_active = true
    order by random()
    limit 1;
  end if;

  if v_title is not null then
    insert into public.pulse_prompts(title, description, starts_at, ends_at, is_published)
    values (v_title, coalesce(v_description, ''), v_start, v_start + interval '1 day', true);
  end if;
end;
$$;

revoke all on function public.publish_next_pulse_prompt() from public, anon, authenticated;

-- Ensure the daily job exists with one stable schedule.
do $$
begin
  if exists (select 1 from cron.job where jobname = 'nexa-pulse-publish-daily') then
    perform cron.unschedule('nexa-pulse-publish-daily');
  end if;

  perform cron.schedule(
    'nexa-pulse-publish-daily',
    '5 0 * * *',
    'select public.publish_next_pulse_prompt();'
  );
end;
$$;
