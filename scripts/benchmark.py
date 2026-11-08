"""Repeatable local HTTP benchmark; fixture credentials stay in a mode-0600 temp file."""
import argparse
import datetime as dt
import hashlib
import http.cookiejar
import json
import math
import os
from pathlib import Path
import platform
import secrets
import statistics
import time
import urllib.parse
import urllib.request


parser = argparse.ArgumentParser()
parser.add_argument('command', choices=['prepare', 'measure', 'cleanup'])
parser.add_argument('--url', default='http://localhost:8080/mapmates')
parser.add_argument('--state', type=Path, required=True)
parser.add_argument('--output', type=Path)
parser.add_argument('--label', default='before')
parser.add_argument('--samples', type=int, default=60)
parser.add_argument('--warmup', type=int, default=10)
parser.add_argument('--plans', type=int, default=50)
args = parser.parse_args()
opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))


def request(path, method='GET', data=None):
    body = None if data is None else json.dumps(data).encode()
    req = urllib.request.Request(args.url+'/api/'+path, data=body, method=method,
                                 headers={'Content-Type': 'application/json'})
    with opener.open(req, timeout=30) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


if args.command == 'prepare':
    state = {'email': 'benchmark-'+secrets.token_hex(6)+'@mapmates.test',
             'password': 'Bench'+secrets.token_hex(12), 'plans': []}
    # Refuse to overwrite a fixture state, so interrupted runs can be cleaned up.
    with args.state.open('x') as f:
        os.chmod(args.state, 0o600)
        json.dump(state, f)
    request('members', 'POST', {**{k: state[k] for k in ['email', 'password']},
            'name': 'Benchmark', 'nickname': 'bench'+secrets.token_hex(5),
            'birthDate': '2000-01-01', 'gender': 'male', 'photo': ''})
    date = (dt.date.today()+dt.timedelta(days=10)).isoformat()
    for i in range(args.plans):
        plan = request('plans', 'POST', {'title': 'Benchmark plan '+str(i),
              'startDate': date, 'endDate': date, 'capacity': 3, 'budget': 0,
              'region': 'Seoul', 'theme': 'Culture', 'description': 'Disposable local benchmark',
              'items': [{'day': 1, 'time': f'{8+j:02}:00', 'memo': 'Stop '+str(j),
                         'place': {'id': 125452, 'title': 'Benchmark place',
                                   'lat': 37.4416867721, 'lng': 127.0543676446,
                                   'contentTypeId': 12}} for j in range(10)]})
        state['plans'].append(plan['id'])
        args.state.write_text(json.dumps(state))
    print('PREPARED', len(state['plans']), 'plans, 10 stops per plan')
else:
    state = json.loads(args.state.read_text())
    request('auth/login', 'POST', {k: state[k] for k in ['email', 'password']})
    if args.command == 'cleanup':
        request('members/me', 'DELETE', {'currentPassword': state['password']})
        args.state.unlink()
        print('CLEANED disposable benchmark member and plans')
    else:
        if not args.output or args.samples < 10 or args.warmup < 1:
            parser.error('measure needs --output, --samples >= 10, --warmup >= 1')
        cases = [('plans', 'plans'),
                 ('area_type', 'attractions?areaCode=1&contentTypeId=39&page=1&size=20'),
                 ('nearby', 'attractions/nearby?lat=37.5665&lng=126.978&radius=5&page=1&size=20'),
                 ('keyword', 'attractions?'+urllib.parse.urlencode({'keyword': '\ubc15\ubb3c\uad00', 'size': 20}))]
        result = {'label': args.label, 'measured_at': dt.datetime.now(dt.timezone.utc).isoformat(),
                  'base_url': args.url, 'platform': platform.platform(),
                  'fixture_plans': len(state['plans']), 'stops_per_plan': 10,
                  'samples': args.samples, 'warmup': args.warmup,
                  'concurrency': 1, 'clock': 'time.perf_counter_ns', 'cases': {}}
        for name, path in cases:
            timings, hashes, sizes = [], set(), set()
            for i in range(args.warmup+args.samples):
                start = time.perf_counter_ns()
                value = request(path)
                elapsed = (time.perf_counter_ns()-start)/1e6
                canonical = json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()
                if i >= args.warmup:
                    timings.append(elapsed)
                    hashes.add(hashlib.sha256(canonical).hexdigest())
                    sizes.add(len(canonical))
            assert len(hashes) == 1, 'Response changed during '+name
            result['cases'][name] = {'path': path, 'median_ms': statistics.median(timings),
                'p95_ms': sorted(timings)[math.ceil(.95*len(timings))-1],
                'min_ms': min(timings), 'max_ms': max(timings), 'samples_ms': timings,
                'response_sha256': hashes.pop(), 'canonical_bytes': sizes.pop()}
            print(name, 'median', round(statistics.median(timings), 3), 'ms', flush=True)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2)+'\n')
