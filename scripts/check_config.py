"""Validate dev/CI/production Compose without exposing configuration values or starting services."""
import os,json,subprocess
values={'MAPMATES_ROOT_PASSWORD':'config-check-only','MAPMATES_DB_PASSWORD':'config-check-only','MAPMATES_DOMAIN':'mapmates.example.test','MAPMATES_SMTP_HOST':'smtp.example.test','MAPMATES_SMTP_PORT':'587','MAPMATES_SMTP_USER':'config-check','MAPMATES_SMTP_PASSWORD':'config-check-only','MAPMATES_SMTP_FROM':'no-reply@example.test'}
env={**os.environ,**values}
def config(*files):
 args=['docker','compose']
 for f in files:args+=['-f',f]
 return json.loads(subprocess.check_output(args+['config','--format','json'],env=env))
prod=config('compose.yaml','compose.production.yaml')
assert not prod['services']['web'].get('ports')
assert str(prod['services']['db']['build']['args']['SEED_DEMO']).lower()=='false'
assert 'mail' not in prod['services']
assert prod['services']['web']['environment']['MAPMATES_PUBLIC_URL']=='https://mapmates.example.test/mapmates'
assert str(prod['services']['web']['environment']['MAPMATES_SMTP_STARTTLS']).lower()=='true'
assert len(prod['services']['proxy']['ports'])==2
ci=config('compose.yaml','compose.ci.yaml')
assert ci['services']['web']['ports'][0].get('host_ip','0.0.0.0')=='0.0.0.0'
print('PASS production HTTPS origin, private web port, no demo seed/mail UI; CI port override')
