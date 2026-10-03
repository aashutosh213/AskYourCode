create sequence repositories_seq start with 1 increment by 50;
create sequence files_seq start with 1 increment by 50;
create sequence code_chunks_seq start with 1 increment by 50;
create sequence embeddings_seq start with 1 increment by 50;

create table repositories (
    id bigint not null default nextval('repositories_seq'),
    path varchar(255) not null,
    name varchar(255),
    created_at timestamp with time zone,
    constraint pk_repositories primary key (id),
    constraint uk_repositories_path unique (path)
);

create table files (
    id bigint not null default nextval('files_seq'),
    relative_path varchar(255) not null,
    file_name varchar(255),
    language varchar(255),
    size_bytes bigint not null,
    repository_id bigint not null,
    constraint pk_files primary key (id),
    constraint fk_files_repository foreign key (repository_id) references repositories(id)
);

create table code_chunks (
    id bigint not null default nextval('code_chunks_seq'),
    symbol_name varchar(255),
    symbol_type varchar(255),
    start_line integer not null,
    end_line integer not null,
    content varchar(10000),
    file_id bigint not null,
    constraint pk_code_chunks primary key (id),
    constraint fk_code_chunks_file foreign key (file_id) references files(id)
);

create table embeddings (
    id bigint not null default nextval('embeddings_seq'),
    vector_json text,
    chunk_id bigint not null,
    constraint pk_embeddings primary key (id),
    constraint uk_embeddings_chunk unique (chunk_id),
    constraint fk_embeddings_chunk foreign key (chunk_id) references code_chunks(id)
);

create table index_jobs (
    id varchar(255) not null,
    repository_path varchar(255),
    status varchar(255),
    files_discovered integer not null,
    started_at timestamp with time zone,
    completed_at timestamp with time zone,
    message varchar(2000),
    constraint pk_index_jobs primary key (id)
);
