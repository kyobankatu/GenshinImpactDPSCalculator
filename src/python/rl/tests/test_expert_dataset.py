import gzip
import hashlib
import json
import math
import os
import sys

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from expert_dataset import load_expert_dataset


FIXTURE = os.path.join(
    os.path.dirname(__file__), "fixtures", "expert_dataset_v1.jsonl"
)


def test_fixture_round_trip_preserves_typed_fields():
    dataset = load_expert_dataset(FIXTURE)
    assert len(dataset.records) == 1
    record = dataset.records[0]
    assert record.record_id == "fixture-train-0"
    assert record.split == "train"
    assert record.decisions[0].action_id == 0
    assert len(record.decisions[0].observation) == 287
    assert record.terminal_objective["objectiveScore"] == 100.0


def test_compressed_multi_shard_manifest_round_trip(tmp_path):
    first = _fixture_payload()
    second = _fixture_payload()
    second["recordId"] = "fixture-train-1"
    second["trajectoryRank"] = 1
    _seal(second)
    shards = [_write_shard(tmp_path, [first]), _write_shard(tmp_path, [second])]
    manifest = {
        "schemaVersion": 1,
        "simulatorRevision": "rotation-simulator-v2",
        "totalRecords": 2,
        "shards": shards,
    }
    manifest_path = tmp_path / "manifest.json"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    dataset = load_expert_dataset(manifest_path)
    assert [record.record_id for record in dataset.records] == [
        "fixture-train-0",
        "fixture-train-1",
    ]


@pytest.mark.parametrize(
    ("mutation", "message"),
    [
        (lambda record: record.__setitem__("schemaVersion", 2), "schemaVersion"),
        (
            lambda record: record["decisions"][0].__setitem__("actionId", 5),
            "invalid or masked",
        ),
        (
            lambda record: record["decisions"][0]["observation"].__setitem__(
                0, float("nan")
            ),
            "Malformed",
        ),
    ],
)
def test_stale_masked_and_non_finite_records_fail_closed(
    tmp_path, mutation, message
):
    record = _fixture_payload()
    mutation(record)
    if not _contains_non_finite(record):
        _seal(record)
    path = tmp_path / "invalid.jsonl"
    path.write_text(json.dumps(record, separators=(",", ":")), encoding="utf-8")
    with pytest.raises(ValueError, match=message):
        load_expert_dataset(path)


def test_record_hash_and_truncated_shard_fail_closed(tmp_path):
    record = _fixture_payload()
    record["terminalObjective"]["objectiveScore"] = 999.0
    path = tmp_path / "wrong-hash.jsonl"
    path.write_text(json.dumps(record, separators=(",", ":")), encoding="utf-8")
    with pytest.raises(ValueError, match="record hash"):
        load_expert_dataset(path)

    shard = _write_shard(tmp_path, [_fixture_payload()])
    shard_path = tmp_path / shard["fileName"]
    shard_path.write_bytes(shard_path.read_bytes()[:8])
    manifest = {
        "schemaVersion": 1,
        "simulatorRevision": "rotation-simulator-v2",
        "totalRecords": 1,
        "shards": [shard],
    }
    manifest_path = tmp_path / "manifest.json"
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
    with pytest.raises(ValueError, match="shard hash"):
        load_expert_dataset(manifest_path)


def test_duplicate_id_and_cross_split_fingerprint_fail_closed(tmp_path):
    first = _fixture_payload()
    duplicate = _fixture_payload()
    path = tmp_path / "duplicates.jsonl"
    path.write_text(
        "\n".join(_canonical(record) for record in (first, duplicate)),
        encoding="utf-8",
    )
    with pytest.raises(ValueError, match="Duplicate dataset record ID"):
        load_expert_dataset(path)

    holdout = _fixture_payload()
    holdout["recordId"] = "fixture-holdout-0"
    holdout["split"] = "holdout"
    _seal(holdout)
    path.write_text(
        "\n".join(_canonical(record) for record in (first, holdout)),
        encoding="utf-8",
    )
    with pytest.raises(ValueError, match="multiple splits"):
        load_expert_dataset(path)


def _fixture_payload():
    with open(FIXTURE, encoding="utf-8") as handle:
        return json.load(handle)


def _canonical(record):
    return json.dumps(record, ensure_ascii=False, separators=(",", ":"))


def _seal(record):
    payload = dict(record)
    payload.pop("recordHash", None)
    record["recordHash"] = hashlib.sha256(_canonical(payload).encode()).hexdigest()


def _write_shard(directory, records):
    lines = "".join(_canonical(record) + "\n" for record in records).encode()
    compressed = gzip.compress(lines, mtime=0)
    digest = hashlib.sha256(compressed).hexdigest()
    file_name = f"shard-{digest}.jsonl.gz"
    (directory / file_name).write_bytes(compressed)
    return {
        "fileName": file_name,
        "sha256": digest,
        "recordCount": len(records),
        "fingerprintSplits": [
            {
                "fingerprint": records[0]["scenarioFingerprint"],
                "split": records[0]["split"],
            }
        ],
    }


def _contains_non_finite(value):
    if isinstance(value, float):
        return not math.isfinite(value)
    if isinstance(value, dict):
        return any(_contains_non_finite(item) for item in value.values())
    if isinstance(value, list):
        return any(_contains_non_finite(item) for item in value)
    return False
