alter table files add column content_hash varchar(64);
alter table repositories add column index_version bigint not null default 0;
