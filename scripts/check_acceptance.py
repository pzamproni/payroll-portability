#!/usr/bin/env python3
"""Check every Gherkin scenario against a distinct, existing executable test."""
import json
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
feature=(root/'specs/acceptance.feature').read_text()
ids=re.findall(r'^\s*@(AC-\d+)\s*$',feature,re.M)
names=re.findall(r'^\s*Cenário:\s*(.+)$',feature,re.M)
mapping=json.loads((root/'specs/acceptance-map.json').read_text())
assert len(ids)==len(names)==len(set(ids)), 'Every scenario must have exactly one unique AC tag'
assert set(ids)==set(mapping), 'Scenario/test map is incomplete or contains obsolete entries'
references=[]
for ac,name in zip(ids,names):
    test=mapping[ac]
    assert test['scenario']==name, f'{ac}: scenario title differs from map'
    path=root/test['file']
    assert path.is_file(), f'{ac}: missing test file {path}'
    source=path.read_text()
    if path.suffix=='.java':
        pattern=r'@Test\s+(?:public\s+)?void\s+'+re.escape(test['test'])+r'\s*\('
        assert re.search(pattern,source), f'{ac}: missing JUnit test {test["test"]}'
    elif path.suffix=='.py':
        assert re.search(r'^def '+re.escape(test['test'])+r'\s*\(',source,re.M),f'{ac}: missing Python integration test'
    else:
        assert re.search(r'\bit\(\s*["\']'+re.escape(test['test'])+r'["\']',source), f'{ac}: missing Vitest test {test["test"]}'
    assert test['type'] in ('unit','integration'),f'{ac}: expected unit or integration test'
    references.append((test['file'],test['test']))
assert len(references)==len(set(references)), 'Each scenario must map to its own test'
print(f'PASS: {len(ids)} Gherkin scenarios; {len(references)} distinct executable tests; zero unmapped criteria.')

if '--results' in sys.argv:
    java_cases=[]
    for report in (root/'backend/target/surefire-reports').glob('TEST-*.xml'):
        java_cases.extend(ET.parse(report).getroot().iter('testcase'))
    frontend_report=root/'frontend/reports/vitest.xml'
    assert frontend_report.is_file(), 'Run npm test before checking frontend results'
    frontend_cases=list(ET.parse(frontend_report).getroot().iter('testcase'))
    for ac,test in mapping.items():
        if test['file'].endswith('.py'):
            result=json.loads((root/'reports/persistence.json').read_text())
            assert result.get('persistence')=='passed' and result.get('audit_update_blocked') and result.get('one_operation_each_database'),f'{ac}: persistence integration test did not pass'
            continue
        cases=java_cases if test['file'].endswith('.java') else frontend_cases
        matches=[case for case in cases if case.get('name','').endswith(test['test']) and (not test['file'].endswith('.java') or case.get('classname','').endswith(Path(test['file']).stem))]
        assert len(matches)==1,f'{ac}: expected one executed test result, found {len(matches)}'
        assert not any(matches[0].find(tag) is not None for tag in ('failure','error','skipped')),f'{ac}: test failed or was skipped'
    print(f'PASS: all {len(mapping)} acceptance criteria have a passing, non-skipped test result.')
