"""Apply append-only migrations to an existing Compose database without resetting data."""
from pathlib import Path
import subprocess, sys
command=['docker','compose','exec','-T','db','sh','-c','export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec mysql -uroot --default-character-set=utf8mb4 -N -B mapmates']
def sql(text):return subprocess.run(command,input=text,text=True,capture_output=True,check=True).stdout.strip()
sql('CREATE TABLE IF NOT EXISTS schema_migrations(version VARCHAR(100) PRIMARY KEY,applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP);')
for path in sorted(Path('db/migrations').glob('*.sql')):
 if sql("SELECT COUNT(*) FROM schema_migrations WHERE version='"+path.stem+"';")=='1':print('SKIP',path.stem);continue
 try:sql(path.read_text())
 except subprocess.CalledProcessError as e:print(e.stderr,file=sys.stderr);raise
 print('APPLY',path.stem)
