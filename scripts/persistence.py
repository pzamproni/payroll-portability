#!/usr/bin/env python3
"""Verify recovery across service restart and PostgreSQL append-only audit."""
import json
import os
import shlex
import subprocess
import time
import uuid
from smoke import call, setup

from pathlib import Path

def test_restart_and_immutable_audit():
    compose=shlex.split(os.environ.get("COMPOSE_COMMAND","docker compose"))
    database_user=subprocess.check_output(compose+["exec","-T","database","printenv","POSTGRES_USER"],text=True).strip()
    platform_database=subprocess.check_output(compose+["exec","-T","database","printenv","POSTGRES_DB"],text=True).strip()
    payload=setup("NORMAL")
    key=str(uuid.uuid4())
    status,operation=call("POST","/operacoes",payload,key)
    assert status==202,operation
    for _ in range(100):
        _,operation=call("GET","/operacoes/"+operation["id"])
        if operation["protocolo"]: break
        time.sleep(.1)
    assert operation["protocolo"],operation
    protocol=operation["protocolo"]
    subprocess.run(compose+["stop","web","api","central","database"],check=True,stdout=subprocess.DEVNULL)
    subprocess.run(compose+["up","-d","--wait","--wait-timeout","120"],check=True,stdout=subprocess.DEVNULL)
    for _ in range(120):
        _,recovered=call("GET","/operacoes/"+operation["id"])
        if recovered["status"]=="CONFIRMADA":break
        time.sleep(.5)
    assert recovered["status"]=="CONFIRMADA",recovered
    assert recovered["protocolo"]==protocol
    assert len(recovered["eventos"])>=len(operation["eventos"])
    assert all(event in recovered["eventos"] for event in operation["eventos"])
    _,repeat=call("POST","/operacoes",payload,key)
    assert repeat["id"]==operation["id"]
    # Constant SQL, fictitious test row only. Mutation must fail and transaction rollback.
    blocked=subprocess.run(compose+["exec","-T","database","psql","-U",database_user,"-d",platform_database,"-v","ON_ERROR_STOP=1","-c","UPDATE audit_events SET detail='mutation forbidden' WHERE sequence=(SELECT min(sequence) FROM audit_events);"],capture_output=True,text=True)
    assert blocked.returncode!=0 and "append-only" in blocked.stderr,blocked.stderr
    for database,sql in [(platform_database,"SELECT count(*) FROM operations WHERE idempotency_key='"+key+"'"),("central","SELECT count(*) FROM central_operations WHERE idempotency_key='"+key+"'")]:
        count=subprocess.check_output(compose+["exec","-T","database","psql","-U",database_user,"-d",database,"-tAc",sql],text=True).strip()
        assert count=="1",(database,count)
    result={"persistence":"passed","audit_update_blocked":True,"one_operation_each_database":True,"protocol_preserved":protocol}
    Path("reports").mkdir(exist_ok=True)
    Path("reports/persistence.json").write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps(result,indent=2))

if __name__=="__main__":
    test_restart_and_immutable_audit()
