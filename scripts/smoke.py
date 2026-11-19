#!/usr/bin/env python3
"""End-to-end API checks over real Compose/PostgreSQL. Standard library only."""
import concurrent.futures
import json
import os
import time
import uuid
from urllib.request import Request, urlopen
from urllib.error import HTTPError

BASE=os.environ.get("BASE_URL","http://localhost:8088")

def call(method,path,data=None,key=None):
    headers={"Content-Type":"application/json"}
    if key: headers["Idempotency-Key"]=key
    request=Request(BASE+"/api"+path,data=None if data is None else json.dumps(data).encode(),headers=headers,method=method)
    try:
        with urlopen(request,timeout=15) as response:
            return response.status,json.load(response)
    except HTTPError as error:
        return error.code,json.load(error)

def setup(scenario):
    beneficiary="SMOKE-"+uuid.uuid4().hex[:12]
    status,_=call("GET",f"/beneficiarios/{beneficiary}/agenda")
    assert status==403,("opt-in required",status)
    status,auth=call("POST","/autorizacoes",{"beneficiarioId":beneficiary,"convenioId":"CONV-001","consentimento":True})
    assert status==201,auth
    _,repeat=call("POST","/autorizacoes",{"beneficiarioId":beneficiary,"convenioId":"CONV-001","consentimento":True})
    assert auth["id"]==repeat["id"]
    _,agenda=call("GET",f"/beneficiarios/{beneficiary}/agenda")
    assert len(agenda)==6 and sum(p["elegivel"] for p in agenda)==4
    request={"beneficiarioId":beneficiary,"parcelas":[agenda[0]["parcelaId"]],"credorCessionario":"Credor de teste","contaRecebimento":{"banco":"001","agencia":"1234","conta":"12345-6"},"cenario":scenario}
    return request

def execute(scenario):
    payload=setup(scenario)
    key=str(uuid.uuid4())
    # Six actual concurrent HTTP submissions, one durable local operation.
    with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
        responses=list(pool.map(lambda _:call("POST","/operacoes",payload,key),range(6)))
    assert all(s==202 for s,_ in responses),responses
    ids={body["id"] for _,body in responses}
    assert len(ids)==1,ids
    operation=responses[0][1]
    _,agenda=call("GET",f"/beneficiarios/{payload['beneficiarioId']}/agenda")
    assert agenda[0]["contaRecebimento"] is None,"Account changed before confirmation"
    conflict={**payload,"credorCessionario":"Outro credor"}
    assert call("POST","/operacoes",conflict,key)[0]==409
    assert call("POST","/operacoes",payload,str(uuid.uuid4()))[0]==409
    deadline=time.monotonic()+100
    while time.monotonic()<deadline:
        _,operation=call("GET","/operacoes/"+operation["id"])
        if operation["status"] in ("CONFIRMADA","REJEITADA","REVISAO_NECESSARIA"):break
        time.sleep(.5)
    expected="REJEITADA" if scenario=="REJECT" else "CONFIRMADA"
    assert operation["status"]==expected,(scenario,operation)
    _,agenda=call("GET",f"/beneficiarios/{payload['beneficiarioId']}/agenda")
    assert agenda[0]["contaRecebimento"]==(None if expected=="REJEITADA" else payload["contaRecebimento"])
    assert agenda[0]["elegivel"]==(expected=="REJEITADA")
    events=operation["eventos"]
    assert len({e["sequence"] for e in events})==len(events)
    assert [e["sequence"] for e in events]==sorted(e["sequence"] for e in events)
    assert any(e["tipo"]==expected for e in events)
    if scenario not in ("NORMAL","REJECT"):
        assert any(e["tipo"]=="FALHA_INTEGRACAO" for e in events),(scenario,events)
    _,repeat=call("POST","/operacoes",payload,key)
    assert repeat["id"]==operation["id"]
    return {"scenario":scenario,"operation":operation["id"],"status":operation["status"],"protocol":operation["protocolo"],"events":len(events)}

if __name__=="__main__":
    scenarios=["NORMAL","REJECT","UNAVAILABLE","TIMEOUT","TIMEOUT_AFTER_ACCEPTING","INVALID_RESPONSE","TAMPERED_RESPONSE"]
    with concurrent.futures.ThreadPoolExecutor(max_workers=7) as pool:
        results=list(pool.map(execute,scenarios))
    print(json.dumps({"passed":len(results),"results":results},indent=2))
