alter table index_jobs add column stage varchar(40);

update index_jobs
set stage = case
    when status = 'COMPLETED' then 'COMPLETED'
    when status = 'FAILED' then 'FAILED'
    when status = 'QUEUED' then 'SCANNING'
    else 'PARSING'
end
where stage is null;
