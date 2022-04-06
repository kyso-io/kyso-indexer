# kyso-indexer

Retrieves the content of a file and inserts it at elasticsearch following the full text search structure

More information about how it works [here](https://handbook.kyso.io/docs/technical/full-text-search/#how-it-is-implemented)

## Assumptions

To work properly all the data must be at /data, for example following this structure

```
data/
├─ darkside/
│  ├─ public-team/
│  │  ├─ reports/
│  │  │  ├─ mapped-reads-for-genomic-features/
│  │  │  │  ├─ 1/
│  │  │  │  │  ├─ kyso.yaml
│  │  │  │  │  ├─ more_files_and_folders
├─ lightside/
│  ├─ protected-team/
│  │  ├─ reports/
│  │  │  ├─ other-report/
│  │  │  │  ├─ 1/
│  │  │  │  │  ├─ kyso.json
│  │  │  │  │  ├─ more_files_and_folders/
```

This is easy for Kubernetes, but for local testing take into account and place your test files at /data

## Usage

Single file indexer mode:

```shell
java -jar kyso-indexer.jar http://elasticsearch-url:9200 /path/to/file/to/index
```

Reindexer mode

```shell
java -jar kyso-indexer.jar http://elasticsearch-url:9200 /path/to/folder/to/reindex --reindex
```
