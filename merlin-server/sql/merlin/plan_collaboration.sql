-- TODO list:
--   - duplicate temporal subset of plan

-- Snapshot is a collection of the state of all the activities as they were at the time of the snapshot
-- as well as any other properties of the plan that can change
create table plan_snapshot(
  snapshot_id integer
    generated always as identity
    primary key,

  plan_id integer
    references plan
    on delete set null,

  revision integer not null,
  name text not null,
  duration interval not null,
  start_time timestamptz not null
);

create table plan_snapshot_activities(
   snapshot_id integer
      references plan_snapshot
      on delete cascade,
    id integer,

    name text,
    tags text[],
    source_scheduling_goal_id integer,
    created_at timestamptz not null,
    last_modified_at timestamptz not null,
    start_offset interval not null,
    type text not null,
    arguments merlin_argument_set not null,
    last_modified_arguments_at timestamptz not null,
    metadata merlin_activity_directive_metadata_set,

   anchor_id integer default null,
   anchored_to_start boolean default true not null,

    primary key (id, snapshot_id)
);

create table plan_snapshot_parent(
  snapshot_id integer
    references plan_snapshot,
  parent_snapshot_id integer
    references plan_snapshot,

  primary key (snapshot_id, parent_snapshot_id),
  constraint snapshot_cannot_be_own_parent
    check ( snapshot_id != parent_snapshot_id )
);

create table plan_latest_snapshot(
  plan_id integer,
  snapshot_id integer,

  primary key (plan_id, snapshot_id),
  foreign key (plan_id)
    references plan
    on update cascade
    on delete cascade,
  foreign key (snapshot_id)
    references plan_snapshot
    on update cascade
    on delete cascade
);

-- Captures the state of a plan and all of its activities
create function create_snapshot(plan_id integer)
  returns integer -- snapshot id inserted into the table
  language plpgsql as $$
  declare
    validate_planid integer;
    inserted_snapshot_id integer;
begin
  select id from plan where plan.id = plan_id into validate_planid;
  if validate_planid is null then
    raise exception 'Plan % does not exist.', plan_id;
  end if;

  insert into plan_snapshot(plan_id, revision, name, duration, start_time)
    select id, revision, name, duration, start_time
    from plan where id = plan_id
    returning snapshot_id into inserted_snapshot_id;
  insert into plan_snapshot_activities(
                snapshot_id, id, name, tags, source_scheduling_goal_id, created_at, last_modified_at, start_offset, type,
                arguments, last_modified_arguments_at, metadata, anchor_id, anchored_to_start
                )
    select
      inserted_snapshot_id,                                   -- this is the snapshot id
      id, name, tags,source_scheduling_goal_id, created_at,   -- these are the rest of the data for an activity row
      last_modified_at, start_offset, type, arguments, last_modified_arguments_at, metadata, anchor_id, anchored_to_start
    from activity_directive where activity_directive.plan_id = create_snapshot.plan_id;
  insert into preset_to_snapshot_directive(preset_id, activity_id, snapshot_id)
  select ptd.preset_id, ptd.activity_id, inserted_snapshot_id
    from preset_to_directive ptd
    where ptd.plan_id = create_snapshot.plan_id;

  --all snapshots in plan_latest_snapshot for plan plan_id become the parent of the current snapshot
  insert into plan_snapshot_parent(snapshot_id, parent_snapshot_id)
    select inserted_snapshot_id, snapshot_id
    from plan_latest_snapshot where plan_latest_snapshot.plan_id = create_snapshot.plan_id;

  --remove all of those entries from plan_latest_snapshot and add this new snapshot.
  delete from plan_latest_snapshot where plan_latest_snapshot.plan_id = create_snapshot.plan_id;
  insert into plan_latest_snapshot(plan_id, snapshot_id) values (create_snapshot.plan_id, inserted_snapshot_id);

  return inserted_snapshot_id;
  end;
$$;

/*
  Copies all of a given plan's properties and activities into a new plan with the specified name.
  When duplicating a plan, a snapshot is created of the original plan.
  Additionally, that snapshot becomes the latest snapshot of the new plan.
*/
create function duplicate_plan(plan_id integer, new_plan_name text, new_owner text)
  returns integer -- plan_id of the new plan
  security definer
  language plpgsql as $$
  declare
    validate_plan_id integer;
    new_plan_id integer;
    created_snapshot_id integer;
begin
  select id from plan where plan.id = duplicate_plan.plan_id into validate_plan_id;
  if(validate_plan_id is null) then
    raise exception 'Plan % does not exist.', plan_id;
  end if;

  select create_snapshot(plan_id) into created_snapshot_id;

  insert into plan(revision, name, model_id, duration, start_time, parent_id, owner)
    select
        0, new_plan_name, model_id, duration, start_time, plan_id, new_owner
    from plan where id = plan_id
    returning id into new_plan_id;
  insert into activity_directive(
      id, plan_id, name, tags, source_scheduling_goal_id, created_at, last_modified_at, start_offset, type, arguments,
      last_modified_arguments_at, metadata, anchor_id, anchored_to_start
    )
    select
      id, new_plan_id, name, tags, source_scheduling_goal_id, created_at, last_modified_at, start_offset, type, arguments,
      last_modified_arguments_at, metadata, anchor_id, anchored_to_start
    from activity_directive where activity_directive.plan_id = duplicate_plan.plan_id;

  with source_plan as (
    select simulation_template_id, arguments, simulation_start_time, simulation_end_time
    from simulation
    where simulation.plan_id = duplicate_plan.plan_id
  )
  update simulation s
  set simulation_template_id = source_plan.simulation_template_id,
      arguments = source_plan.arguments,
      simulation_start_time = source_plan.simulation_start_time,
      simulation_end_time = source_plan.simulation_end_time
  from source_plan
  where s.plan_id = new_plan_id;

  insert into preset_to_directive(preset_id, activity_id, plan_id)
  select preset_id, activity_id, new_plan_id
  from preset_to_directive ptd where ptd.plan_id = duplicate_plan.plan_id;

  insert into plan_latest_snapshot(plan_id, snapshot_id) values(new_plan_id, created_snapshot_id);
  return new_plan_id;
end
$$;

comment on function duplicate_plan(plan_id integer, new_plan_name text, new_owner text) is e''
  'Copies all of a given plan''s properties and activities into a new plan with the specified name.
  When duplicating a plan, a snapshot is created of the original plan.
  Additionally, that snapshot becomes the latest snapshot of the new plan.';


-- History of Snapshot notes (planid to snapshotid(s)):
--  - Get the whole history of both
--  - Get the max snapshot id of the intersection
create function get_snapshot_history_from_plan(starting_plan_id integer)
  returns setof integer
  language plpgsql as $$
  begin
    return query
      select get_snapshot_history(snapshot_id)  --runs the recursion
      from plan_latest_snapshot where plan_id = starting_plan_id; --supplies input for get_snapshot_history
  end
$$;

create function get_snapshot_history(starting_snapshot_id integer)
  returns setof integer
  language plpgsql as $$
  declare
    validate_id integer;
begin
  select plan_snapshot.snapshot_id from plan_snapshot where plan_snapshot.snapshot_id = starting_snapshot_id into validate_id;
  if validate_id is null then
    raise exception 'Snapshot ID % is not present in plan_snapshot table.', starting_snapshot_id;
  end if;

  return query with recursive history(id) as (
      values(starting_snapshot_id) --base case
    union
      select parent_snapshot_id from plan_snapshot_parent
        join history on id = plan_snapshot_parent.snapshot_id --recursive case
  ) select * from history;
end
$$;


-- History of Plans notes (planid to planid):
--  - Grab all non-null parents
create function get_plan_history(starting_plan_id integer)
  returns setof integer
  language plpgsql as $$
  declare
    validate_id integer;
  begin
    select plan.id from plan where plan.id = starting_plan_id into validate_id;
    if validate_id is null then
      raise exception 'Plan ID % is not present in plan table.', starting_plan_id;
    end if;

    return query with recursive history(id) as (
        values(starting_plan_id)                      -- base case
      union
        select parent_id from plan
          join history on history.id = plan.id and plan.parent_id is not null-- recursive case
    ) select * from history;
  end
$$
